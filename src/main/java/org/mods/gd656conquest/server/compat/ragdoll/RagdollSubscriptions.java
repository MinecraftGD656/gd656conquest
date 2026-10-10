package org.mods.gd656conquest.server.compat.ragdoll;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.*;

/** Server-main-thread discovery and observer leases, independent of simulation ownership. */
@Mod.EventBusSubscriber(modid="gd656conquest")
public final class RagdollSubscriptions {
    private static final Map<Integer,Track> TRACKS=new HashMap<>();
    private static final Map<UUID,Viewer> VIEWERS=new HashMap<>();
    private static MinecraftServer currentServer;
    private static long nextToken;
    private static Field records, nearbySent;
    private static Method sendRetained;
    private static boolean resolved;
    private RagdollSubscriptions() {}

    private static boolean resolve() {
        if(resolved) return records!=null;
        resolved=true;
        if(!ModList.get().isLoaded("ragdollified")) return false;
        try {
            Class<?> sync=Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager");
            Class<?> record=Class.forName(sync.getName()+"$RetainedRagdoll");
            records=sync.getDeclaredField("RETAINED");records.setAccessible(true);
            nearbySent=sync.getDeclaredField("NEARBY_SENT");nearbySent.setAccessible(true);
            sendRetained=sync.getDeclaredMethod("sendRetained",ServerPlayer.class,record,int.class);sendRetained.setAccessible(true);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Ragdollified subscription API changed",error); }
        return true;
    }
    @SuppressWarnings("unchecked")
    private static Map<Integer,?> records() {
        try { return (Map<Integer,?>)records.get(null); }
        catch(IllegalAccessException error) {throw new IllegalStateException(error);}
    }
    private static void server(MinecraftServer s) {
        if(currentServer!=s) {TRACKS.clear();VIEWERS.clear();currentServer=s;}
    }
    private static Track track(Object record,MinecraftServer s) {
        server(s);
        int id=RagdollSyncScope.packetId(record), tick=s.getTickCount();
        Track t=TRACKS.get(id);
        if(t==null||t.record!=record) {
            t=new Track(record,++nextToken); TRACKS.put(id,t);
        }
        if(t.tick!=tick) {
            if(t.view==null||!t.view.reusable(record))t.view=new RagdollRecordView(record);
            t.tick=tick;
        }
        return t;
    }
    public static boolean shouldReceive(ServerPlayer p,Object record,double originalRadius) {
        if(!resolve()||!DownedRagdollBridge.availableFor(p)) return false;
        Track t=track(record,p.server);
        if(!p.level().dimension().equals(t.view.dimension))return false;
        return wanted(p,t);
    }
    public static boolean observes(ServerPlayer p,int id) {
        if(!resolve())return false;
        Object record=records().get(id);
        return record!=null&&wanted(p,track(record,p.server));
    }
    private static boolean mandatory(ServerPlayer p,Track t) {
        return p.getUUID().equals(t.view.owner)||(t.view.live&&p.getUUID().equals(t.view.victim))
            || DownedViewService.observesCorpse(p,t.record);
    }
    private static boolean wanted(ServerPlayer p,Track t) {
        if(!p.level().dimension().equals(t.view.dimension))return false;
        if(mandatory(p,t))return true;
        Viewer v=VIEWERS.get(p.getUUID());
        boolean active=v!=null&&Objects.equals(v.active.get(t.view.id),t.token);
        double near=active?RagdollSubscriptionPolicy.NEAR_EXIT_DISTANCE:RagdollSubscriptionPolicy.NEAR_DISTANCE;
        if(p.position().distanceToSqr(t.view.anchor)<=near*near)return true;
        Selection selected=v==null?null:v.selected.get(t.view.id);
        return selected!=null&&selected.token==t.token&&selected.expires>p.server.getTickCount();
    }
    /** Runs before the original native snapshot, keeping the GD binding ordered before poses. */
    public static void beforeRetained(ServerPlayer p,Object record) {
        Track t=track(record,p.server);
        Viewer v=VIEWERS.computeIfAbsent(p.getUUID(),ignored->new Viewer());
        if(v.dimension.isEmpty())v.dimension=p.level().dimension().location().toString();
        if(!Objects.equals(v.active.get(t.view.id),t.token)) {
            v.active.put(t.view.id,t.token);
            NetworkManager.sendToPlayer(p,new RagdollObservationPacket(t.view.id,t.token,RagdollSyncScope.serverGeneration(t.view.id),true,false));
            DownedRagdollBridge.observe(p,t.view.id);
        }
    }
    @SuppressWarnings("unchecked")
    private static void activate(ServerPlayer p,Track t) {
        beforeRetained(p,t.record);
        try {
            sendRetained.invoke(null,p,t.record,p.server.getTickCount());
            ((Map<UUID,Set<Integer>>)nearbySent.get(null)).computeIfAbsent(p.getUUID(),ignored->new HashSet<>()).add(t.view.id);
        } catch(ReflectiveOperationException error) {throw new IllegalStateException("Could not publish native ragdoll snapshot",error);}
    }
    private static void pause(ServerPlayer p,Viewer v,int id,long token,long generation,boolean removed) {
        v.active.remove(id);
        NetworkManager.sendToPlayer(p,new RagdollObservationPacket(id,token,generation,false,removed));
        DownedRagdollBridge.unobserve(p,id);
    }
    public static void request(ServerPlayer p,RagdollSubscribePacket packet) {
        if(p==null||!resolve()||!DownedRagdollBridge.availableFor(p))return;
        server(p.server);
        Viewer v=VIEWERS.computeIfAbsent(p.getUUID(),ignored->new Viewer());int now=p.server.getTickCount();
        if(now-v.window>=20){v.window=now;v.requests=0;v.snapshots=0;}
        if(++v.requests>10)return;
        Set<Integer> seen=new HashSet<>();
        for(RagdollSubscribePacket.Entry e:packet.entries()) {
            if(!seen.add(e.id()))continue;
            Object record=records().get(e.id());if(record==null)continue;
            Track t=track(record,p.server);
            if(t.token!=e.token()||!Objects.equals(v.known.get(e.id()),t.token)||!p.level().dimension().equals(t.view.dimension))continue;
            Integer last=v.requestTicks.get(e.id());
            if(last!=null&&now-last<RagdollSubscriptionPolicy.VIEW_CHECK_TICKS)continue;
            v.requestTicks.put(e.id(),now);
            boolean active=Objects.equals(v.active.get(e.id()),t.token);
            if(e.subscribe()&&!active&&v.snapshots>=32)continue;
            if(e.subscribe())v.selected.put(e.id(),new Selection(t.token,now+RagdollSubscriptionPolicy.SUBSCRIPTION_LEASE_TICKS));
            else v.selected.remove(e.id());
            if(wanted(p,t)) {
                if((!active||e.refresh())&&v.snapshots<32) {
                    v.snapshots++;
                    if(active) {v.active.remove(e.id());DownedRagdollBridge.unobserve(p,e.id());}
                    activate(p,t);
                }
            }
            else if(active)pause(p,v,e.id(),t.token,RagdollSyncScope.serverGeneration(e.id()),false);
            else NetworkManager.sendToPlayer(p,new RagdollObservationPacket(e.id(),t.token,RagdollSyncScope.serverGeneration(e.id()),false,false));
        }
    }
    /** Called by the native impulse invalidation hook. No observer controls the physics wake-up. */
    public static void wake(int id) {
        Track t=TRACKS.get(id);if(t!=null&&currentServer!=null) {
            t.boostUntil=currentServer.getTickCount()+20;t.tick=Integer.MIN_VALUE;
        }
    }
    @SubscribeEvent public static void onTick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.END)tick(event.getServer());
    }
    public static void tick(MinecraftServer s) {
        if(!resolve())return;
        server(s);int now=s.getTickCount();Map<Integer,?> nativeRecords=records();
        for(Object record:nativeRecords.values())track(record,s);
        Set<UUID> connected=new HashSet<>();
        for(ServerPlayer p:s.getPlayerList().getPlayers()) {
            UUID uuid=p.getUUID();connected.add(uuid);if(!DownedRagdollBridge.availableFor(p))continue;
            Viewer v=VIEWERS.computeIfAbsent(uuid,ignored->new Viewer());
            String dimension=p.level().dimension().location().toString();
            if(!dimension.equals(v.dimension)){v.active.clear();v.selected.clear();v.known.clear();v.lastSent.clear();v.requestTicks.clear();v.dimension=dimension;}
            List<RagdollCatalogPacket.Entry> catalog=new ArrayList<>();
            for(Track t:TRACKS.values()) {
                int id=t.view.id;long gen=RagdollSyncScope.serverGeneration(id);
                boolean present=nativeRecords.get(id)==t.record&&now<t.view.expiresTick;
                if(!present||!p.level().dimension().equals(t.view.dimension)) {
                    Long known=v.known.remove(id);
                    if(known!=null) {catalog.add(entry(t,true,false));pause(p,v,id,known,gen,true);v.selected.remove(id);v.lastSent.remove(id);v.requestTicks.remove(id);}
                    continue;
                }
                boolean active=Objects.equals(v.active.get(id),t.token), want=wanted(p,t);
                if(want&&!active)activate(p,t);
                else if(!want&&active)pause(p,v,id,t.token,gen,false);
                int interval=now<t.boostUntil?2:(t.view.moving?RagdollSubscriptionPolicy.MOVING_DIRECTORY_TICKS:RagdollSubscriptionPolicy.STATIC_DIRECTORY_TICKS);
                boolean force=mandatory(p,t);
                long flags=(t.token<<2)|(force?2:0)|(t.view.moving?1:0);
                Sent sent=v.lastSent.get(id);
                if(sent==null||sent.flags!=flags||now-sent.tick>=interval) {
                    catalog.add(entry(t,false,force));v.known.put(id,t.token);v.lastSent.put(id,new Sent(now,flags));
                }
            }
            for(int start=0;start<catalog.size();start+=RagdollSubscriptionPolicy.MAX_DIRECTORY_ENTRIES)
                NetworkManager.sendToPlayer(p,new RagdollCatalogPacket(dimension,now,catalog.subList(start,Math.min(catalog.size(),start+RagdollSubscriptionPolicy.MAX_DIRECTORY_ENTRIES))));
            v.selected.entrySet().removeIf(e->e.getValue().expires<=now);
        }
        VIEWERS.keySet().retainAll(connected);
        TRACKS.entrySet().removeIf(e->nativeRecords.get(e.getKey())!=e.getValue().record||now>=e.getValue().view.expiresTick);
    }
    private static RagdollCatalogPacket.Entry entry(Track t,boolean removed,boolean force) {
        return new RagdollCatalogPacket.Entry(t.view.id,t.token,RagdollSyncScope.serverGeneration(t.view.id),removed,t.view.moving,force,t.view.anchor,t.view.bounds);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){TRACKS.clear();VIEWERS.clear();currentServer=null;}
    private static final class Track {
        final Object record;final long token;int tick=Integer.MIN_VALUE,boostUntil;RagdollRecordView view;
        Track(Object record,long token){this.record=record;this.token=token;}
    }
    private record Selection(long token,int expires) {}
    private record Sent(int tick,long flags) {}
    private static final class Viewer {
        String dimension="";int window,requests,snapshots;
        final Map<Integer,Long> active=new HashMap<>(),known=new HashMap<>();
        final Map<Integer,Selection> selected=new HashMap<>();
        final Map<Integer,Sent> lastSent=new HashMap<>();
        final Map<Integer,Integer> requestTicks=new HashMap<>();
    }
}
