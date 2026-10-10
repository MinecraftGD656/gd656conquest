package org.mods.gd656conquest.server.support;
import java.util.*;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Hooks TACZ's collision-clipped path without changing its damage or Killicon's shooter scoring. */
public final class TaczSuppression {
    private static final ThreadLocal<Deque<UUID>> SHOTS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<EntityKineticBullet,Set<UUID>> SEEN=new WeakHashMap<>();
    private TaczSuppression(){}
    public static void beginShot(){SHOTS.get().push(UUID.randomUUID());}
    public static void endShot(){var stack=SHOTS.get();if(!stack.isEmpty())stack.pop();if(stack.isEmpty())SHOTS.remove();}
    public static void tag(EntityKineticBullet bullet){
        if(bullet.level().isClientSide)return;
        var stack=SHOTS.get();UUID id=stack.isEmpty()?bullet.getUUID():stack.peek();
        bullet.getPersistentData().putUUID("gd656_suppression_shot",id);
    }
    public static void path(Projectile raw,Vec3 start,Vec3 end) {
        if(!(raw instanceof EntityKineticBullet bullet) || raw.level().isClientSide
            || !(raw.getOwner() instanceof ServerPlayer shooter))return;
        var sc=SupportContext.of(shooter);if(sc==null || !sc.room().commonConfig().suppressionConfig().enabled())return;
        var cfg=sc.room().commonConfig().suppressionConfig();double radius=cfg.radiusBlocks();
        if(!finite(start)||!finite(end))return;
        var seen=SEEN.computeIfAbsent(bullet,b->new HashSet<>());
        var d=bullet.getPersistentData();UUID shot=d.hasUUID("gd656_suppression_shot")?d.getUUID("gd656_suppression_shot"):bullet.getUUID();
        Vec3 line=end.subtract(start);double length=line.lengthSqr();
        for(ServerPlayer p:raw.level().getEntitiesOfClass(ServerPlayer.class,new AABB(start,end).inflate(radius),SupportContext::available)) {
            if(p==shooter || seen.contains(p.getUUID()))continue;
            var c=SupportContext.of(p);if(c==null || c.camp()==sc.camp() || !c.room().roomId().equals(sc.room().roomId()))continue;
            AABB configured=HeadShotAABBConfigRead.getAABB(ForgeRegistries.ENTITY_TYPES.getKey(p.getType()));
            Vec3 head=configured==null?p.getEyePosition():configured.getCenter().add(p.position());
            double t=length<=1e-12?0:Math.max(0,Math.min(1,head.subtract(start).dot(line)/length));
            Vec3 closest=start.add(line.scale(t));double distance=head.distanceTo(closest);
            if(distance>radius)continue;
            seen.add(p.getUUID());
            double gain=SupportRules.nearGain(bullet.getDamage(closest),distance,cfg);
            var state=SupportRuntime.of(p.server).recipient(p);double spent=state.shotGains.getOrDefault(shot,0D);
            double accepted=Math.max(0,Math.min(gain,cfg.shotCap()-spent));
            if(accepted<=0)continue;
            state.shotGains.put(shot,spent+accepted);state.shotTimes.put(shot,(long)p.server.getTickCount());
            state.suppression.add(accepted,p.server.getTickCount(),cfg);
            boolean active=state.suppression.active(p.server.getTickCount(),cfg);
            if(active!=state.visual){state.visual=active;SuppressionNetwork.send(p,active,cfg.vignetteOpacity().floatValue());}
        }
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);}
}
