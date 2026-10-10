package org.mods.gd656conquest.client.room;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.*;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSyncScope;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSubscriptionPolicy;

/** Camera-side discovery. Unseen bodies have metadata, not an independently simulated replica. */
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT)
public final class ClientRagdollSubscriptions {
    private static final Map<Integer,Body> BODIES=new ConcurrentHashMap<>();
    private static final Map<Integer,Long> REMOVED=new HashMap<>();
    private static Object level;
    private static long tick,lastCheck=Long.MIN_VALUE,lastSend=Long.MIN_VALUE;
    private static Method fov,remove,persistent;
    private static boolean apiResolved;
    private static final ClassValue<Method> SNAPSHOTS=new ClassValue<>() {
        protected Method computeValue(Class<?> type) {
            try{return type.getMethod("getSnapshot");}
            catch(NoSuchMethodException error){throw new IllegalStateException(error);}
        }
    };
    private ClientRagdollSubscriptions() {}
    private static void world() {
        Object current=Minecraft.getInstance().level;
        if(current!=level) {BODIES.clear();REMOVED.clear();lastCheck=lastSend=Long.MIN_VALUE;level=current;}
    }
    public static void catalog(RagdollCatalogPacket packet) {
        world();Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().location().toString().equals(packet.dimension()))return;
        for(RagdollCatalogPacket.Entry e:packet.entries()) {
            if(e.token()<=REMOVED.getOrDefault(e.id(),0L))continue;
            Body previous=BODIES.get(e.id());
            if(previous!=null&&e.token()<previous.token)continue;
            if(e.removed()) {retire(e.id(),e.token(),e.generation());continue;}
            Body b=previous;
            if(b==null||b.token!=e.token()) {b=new Body(e.token(),e.generation());b.urgent=true;BODIES.put(e.id(),b);}
            if(b.serverTick>packet.serverTick())continue;
            if(b.anchor!=null&&packet.serverTick()>b.serverTick) {
                double dt=packet.serverTick()-b.serverTick;
                b.velocity=new Vec3((e.anchor().x-b.anchor.x)/dt,(e.anchor().y-b.anchor.y)/dt,(e.anchor().z-b.anchor.z)/dt);
            }
            if(!b.moving&&e.moving())b.urgent=true;
            b.serverTick=packet.serverTick();b.generation=e.generation();b.anchor=e.anchor();b.bounds=e.bounds();
            b.moving=e.moving();b.mandatory=e.mandatory();b.lastCatalog=tick;
        }
    }
    public static void observation(RagdollObservationPacket packet) {
        world();if(Minecraft.getInstance().level==null)return;
        if(packet.token()<=REMOVED.getOrDefault(packet.id(),0L))return;
        Body b=BODIES.get(packet.id());if(b!=null&&packet.token()<b.token)return;
        if(packet.removed()){retire(packet.id(),packet.token(),packet.generation());return;}
        if(b==null||b.token!=packet.token()){b=new Body(packet.token(),packet.generation());BODIES.put(packet.id(),b);}
        b.generation=packet.generation();b.active=packet.active();b.lastControl=tick;
        if(b.active) {b.cleaned=false;RagdollSyncScope.resumeClient(packet.id());}
        else {
            b.selected=false;
            RagdollSyncScope.suspendClient(packet.id());
            ClientDownedRagdollState.suspendObservation(packet.id(),b.generation);
        }
        if(b.pending&&b.requested==b.active)b.pending=false;
    }
    private static void retire(int id,long token,long generation) {
        REMOVED.merge(id,token,Math::max);
        Body b=BODIES.remove(id);long gen=b!=null?b.generation:generation;
        ClientDownedRagdollState.suspendObservation(id,gen);
        if(gen>0)RagdollSyncScope.endClient(id,gen);
        RagdollSyncScope.suspendClient(id);cleanup(id);
    }
    /** Used only by the renderer; physics snapshots and simulation ownership remain untouched. */
    public static boolean renders(int id) {Body b=BODIES.get(id);return b==null||b.active;}
    public static Object renderSnapshot(Object body) {
        if(!renders(RagdollSyncScope.packetId(body)))return null;
        try{return SNAPSHOTS.get(body.getClass()).invoke(body);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    public static boolean hasDirectory(){return !BODIES.isEmpty();}
    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        world();tick++;
        for(var e:BODIES.entrySet()) {
            Body b=e.getValue();
            if(!b.active&&!b.cleaned&&tick-b.lastControl>=RagdollSubscriptionPolicy.CLIENT_CACHE_TICKS) {cleanup(e.getKey());b.cleaned=true;}
            if(b.lastCatalog>0&&tick-b.lastCatalog>120) {
                b.active=false;RagdollSyncScope.suspendClient(e.getKey());
                ClientDownedRagdollState.suspendObservation(e.getKey(),b.generation);
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        world();Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||BODIES.isEmpty()||mc.getConnection()==null)return;
        if(lastCheck==tick)return;
        lastCheck=tick;
        Camera camera=event.getCamera();Vec3 eye=camera.getPosition();
        double tanY=Math.tan(Math.toRadians(fov(mc,camera,event.getPartialTick()))*.5)*1.08;
        double aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        Vector3f forward=camera.getLookVector(),up=camera.getUpVector(),left=camera.getLeftVector();
        // Cheap camera/near tests run once per tick. Expensive clipping has a separate budget.
        List<Map.Entry<Integer,Body>> bodies=new ArrayList<>(BODIES.entrySet());
        for(var e:bodies) {
            Body b=e.getValue();if(b.anchor==null||b.bounds==null)continue;
            double near=b.wanted?RagdollSubscriptionPolicy.NEAR_EXIT_DISTANCE:RagdollSubscriptionPolicy.NEAR_DISTANCE;
            b.automatic=b.mandatory||mc.player.position().distanceToSqr(b.anchor)<=near*near;
            if(b.automatic) {b.job=null;b.candidate=false;}
            else {
                AABB box=b.bounds;
                double x=(box.minX+box.maxX)*.5,y=(box.minY+box.maxY)*.5,z=(box.minZ+box.maxZ)*.5;
                double radius=Math.sqrt(square(box.maxX-box.minX)+square(box.maxY-box.minY)+square(box.maxZ-box.minZ))*.5;
                Vec3 prediction=b.moving?b.velocity.scale(4):Vec3.ZERO;
                // Bound bad/stale predictions; this is prefetch, not an authoritative position.
                double length=Math.sqrt(square(prediction.x)+square(prediction.y)+square(prediction.z));
                if(length>16)prediction=prediction.scale(16/length);
                boolean current=inView(x,y,z,radius,eye,forward,up,left,tanY,aspect);
                boolean future=b.moving&&inView(x+prediction.x,y+prediction.y,z+prediction.z,radius,eye,forward,up,left,tanY,aspect);
                boolean candidate=current||future;
                if(!candidate) {b.job=null;b.visible=false;}
                else {
                    if(b.job!=null&&b.job.eye.distanceToSqr(eye)>16)b.job=null;
                    if(b.job==null&&(!b.candidate||b.urgent||tick>=b.nextCheck)) {
                        List<Vec3> points=new ArrayList<>(4);
                        if(current) {
                            points.add(new Vec3(x,y,z));points.add(new Vec3(x,box.maxY-.15,z));
                            points.add(new Vec3(box.minX+.15,y,box.minZ+.15));
                        }
                        if(future)points.add(new Vec3(x+prediction.x,y+prediction.y,z+prediction.z));
                        b.job=new VisibilityJob(eye,b.anchor,points);b.lastWork=tick-4;
                    }
                }
                b.candidate=candidate;
            }
        }
        runVisibility(bodies);
        List<RagdollSubscribePacket.Entry> requests=new ArrayList<>();
        boolean sendDue=lastSend==Long.MIN_VALUE||tick-lastSend>=RagdollSubscriptionPolicy.VIEW_CHECK_TICKS;
        for(var e:bodies) {
            Body b=e.getValue();if(b.anchor==null||b.bounds==null)continue;
            boolean want=b.automatic||(b.candidate&&(b.visible||(b.job!=null&&b.wanted)));
            if(b.automatic||(b.candidate&&b.visible))b.lastVisible=tick;
            else if(b.wanted&&tick-b.lastVisible<RagdollSubscriptionPolicy.HIDDEN_GRACE_TICKS)want=true;
            b.wanted=want;
            boolean refresh=want&&(!b.active||(b.generation>0&&!ClientDownedRagdollState.hasObservation(e.getKey(),b.generation)));
            boolean request=want?(refresh||(!b.automatic&&(!b.selected||tick-b.lastRequest>=RagdollSubscriptionPolicy.REQUEST_RENEW_TICKS)))
                :(b.selected||b.active||b.pending);
            if(sendDue&&request&&(!b.pending||b.requested!=want||tick-b.lastRequest>=RagdollSubscriptionPolicy.REQUEST_RETRY_TICKS)) {
                requests.add(new RagdollSubscribePacket.Entry(e.getKey(),b.token,want,refresh));
                b.requested=want;b.pending=refresh||!want||want!=b.active;b.lastRequest=tick;
                if(want)b.selected=true;
            }
        }
        if(!requests.isEmpty())lastSend=tick;
        for(int start=0;start<requests.size();start+=RagdollSubscriptionPolicy.MAX_REQUEST_ENTRIES)
            NetworkManager.getChannel().sendToServer(new RagdollSubscribePacket(requests.subList(start,Math.min(requests.size(),start+RagdollSubscriptionPolicy.MAX_REQUEST_ENTRIES))));
    }
    private static void runVisibility(List<Map.Entry<Integer,Body>> bodies) {
        long started=System.nanoTime();int work=0;
        while(work<RagdollSubscriptionPolicy.VISIBILITY_SEGMENTS_PER_TICK) {
            if(work>0&&System.nanoTime()-started>=RagdollSubscriptionPolicy.VISIBILITY_BUDGET_NANOS)break;
            Body next=null;int nextId=Integer.MAX_VALUE;long score=Long.MAX_VALUE;
            for(var e:bodies) {
                Body b=e.getValue();if(b.job==null)continue;
                long priority=b.lastWork-(b.urgent?8:b.moving?4:0);
                if(priority<score||(priority==score&&e.getKey()<nextId)) {next=b;nextId=e.getKey();score=priority;}
            }
            if(next==null)break;
            VisibilityJob job=next.job;job.advance();work++;next.lastWork=tick;next.urgent=false;
            if(job.done) {
                // A moving body's old blocked ray must not hide its new exposed position.
                if(job.visible||job.anchor.distanceToSqr(next.anchor)<=1)next.visible=job.visible;
                next.job=null;next.nextCheck=tick+RagdollSubscriptionPolicy.VIEW_CHECK_TICKS;
                if(job.anchor.distanceToSqr(next.anchor)>1) {next.urgent=true;next.nextCheck=tick+1;}
            }
        }
    }
    private static double square(double d){return d*d;}
    private static boolean inView(double x,double y,double z,double r,Vec3 eye,Vector3f f,Vector3f u,Vector3f l,double tan,double aspect) {
        return RagdollVisibilityMath.inView(x-eye.x,y-eye.y,z-eye.z,r,f.x,f.y,f.z,u.x,u.y,u.z,l.x,l.y,l.z,tan,aspect);
    }
    private static double fov(Minecraft mc,Camera camera,float partial) {
        try {
            if(fov==null){try{fov=GameRenderer.class.getDeclaredMethod("getFov",Camera.class,float.class,boolean.class);}catch(NoSuchMethodException ignored){fov=GameRenderer.class.getDeclaredMethod("m_109141_",Camera.class,float.class,boolean.class);}fov.setAccessible(true);}
            return ((Number)fov.invoke(mc.gameRenderer,camera,partial,true)).doubleValue();
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Camera FOV API changed",error);}
    }
    private static boolean clearSegment(Vec3 from,Vec3 to) {
        Minecraft mc=Minecraft.getInstance();
        if(!loadedPath(from,to))return false;
        ClipContext context=new ClipContext(from,to,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player) {
            @Override public VoxelShape getBlockShape(BlockState state,BlockGetter world,BlockPos pos) {
                if(state.getBlock() instanceof HalfTransparentBlock||state.getBlock() instanceof StainedGlassPaneBlock||state.getBlock() instanceof LeavesBlock)return Shapes.empty();
                return super.getBlockShape(state,world,pos);
            }
        };
        return mc.level.clip(context).getType()==HitResult.Type.MISS;
    }
    /** Each clip visits at most a 64-block segment, irrespective of corpse distance. */
    private static final class VisibilityJob {
        final Vec3 eye,anchor;final List<Vec3> points;
        Vec3 from;int point;boolean done,visible;
        VisibilityJob(Vec3 eye,Vec3 anchor,List<Vec3> points){this.eye=eye;this.anchor=anchor;this.points=points;from=eye;}
        void advance() {
            Vec3 to=points.get(point);
            double dx=to.x-from.x,dy=to.y-from.y,dz=to.z-from.z;
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz),scale=length>RagdollSubscriptionPolicy.VISIBILITY_SEGMENT_LENGTH?RagdollSubscriptionPolicy.VISIBILITY_SEGMENT_LENGTH/length:1;
            Vec3 end=scale==1?to:new Vec3(from.x+dx*scale,from.y+dy*scale,from.z+dz*scale);
            if(!clearSegment(from,end)) {point++;from=eye;if(point==points.size())done=true;}
            else if(scale==1) {visible=true;done=true;}
            else from=end;
        }
    }
    /** Traverse chunk columns before clipping; never assume unloaded terrain is transparent. */
    private static boolean loadedPath(Vec3 from,Vec3 to) {
        Minecraft mc=Minecraft.getInstance();
        int x=(int)Math.floor(from.x/16),z=(int)Math.floor(from.z/16),endX=(int)Math.floor(to.x/16),endZ=(int)Math.floor(to.z/16);
        double dx=to.x-from.x,dz=to.z-from.z;
        int sx=dx>0?1:dx<0?-1:0,sz=dz>0?1:dz<0?-1:0;
        double tx=sx==0?Double.POSITIVE_INFINITY:((sx>0?(x+1)*16:x*16)-from.x)/dx;
        double tz=sz==0?Double.POSITIVE_INFINITY:((sz>0?(z+1)*16:z*16)-from.z)/dz;
        while(true) {
            if(!mc.level.hasChunkAt(new BlockPos(x*16,(int)from.y,z*16)))return false;
            if(x==endX&&z==endZ)return true;
            if(tx<tz){x+=sx;tx+=16/Math.abs(dx);}else{z+=sz;tz+=16/Math.abs(dz);}
        }
    }
    private static void cleanup(int id) {
        try {
            if(!apiResolved) {
                apiResolved=true;
                Class<?> api=Class.forName("com.raiiiden.ragdollified.api.RagdollifiedApi");remove=api.getMethod("remove",int.class);
                persistent=Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager").getMethod("setPersistent",int.class,boolean.class);
            }
            if(remove!=null){persistent.invoke(null,id,false);remove.invoke(null,id);}
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Ragdoll cleanup API changed",error);}
    }
    private static final class Body {
        final long token;long generation;volatile boolean active;
        boolean wanted,moving,mandatory,cleaned,selected,pending,requested,automatic,visible,candidate,urgent;
        long lastVisible,lastRequest,lastControl,lastCatalog,nextCheck,lastWork;VisibilityJob job;
        int serverTick=Integer.MIN_VALUE;Vec3 anchor,velocity=Vec3.ZERO;AABB bounds;
        Body(long token,long generation){this.token=token;this.generation=generation;}
    }
}
