package org.mods.gd656conquest.client.downed;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import org.mods.gd656conquest.client.room.ClientRoomViewScope;
import org.mods.gd656killicon.client.downed.DownedClient;
import org.mods.gd656killicon.client.profile.ProfileClient;
import static org.mods.gd656killicon.client.downed.DownedTimeline.*;

/** Exact 0.10.6 snapshots are sampled after the native input/state tick. */
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ConquestDownedProvider {
    private static final org.mods.gd656killicon.client.downed.DownedEvidence EVIDENCE=new org.mods.gd656killicon.client.downed.DownedEvidence();
    private static boolean wasVisible;
    private static Phase previousPhase=Phase.NONE;
    private static Object level,connection;
    private static String room="";
    private static java.util.UUID markerTarget;
    private static int markerRefresh=5;
    private static long markerExpires;
    private ConquestDownedProvider() {}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        org.mods.gd656killicon.client.downed.DownedHighlight.target(null);
        var d=ClientDownedState.visualSnapshot();var death=ClientDeathFlowState.visualSnapshot();
        String nextRoom=ClientHudDebugState.getRoomId();
        if(level!=mc.level||connection!=mc.getConnection()||!room.equals(nextRoom)) {
            DownedClient.observe(null);EVIDENCE.reset();wasVisible=false;previousPhase=Phase.NONE;markerTarget=null;markerRefresh=5;markerExpires=0;level=mc.level;connection=mc.getConnection();room=nextRoom;
        }
        if(mc.player==null||mc.level==null||mc.getConnection()==null||!d.debug()&&!ClientRoomViewScope.current()) {DownedClient.observe(null);EVIDENCE.reset();wasVisible=false;previousPhase=Phase.NONE;return;}
        boolean downed=d.active()&&d.level()==mc.level,deathCurrent=death.level()==mc.level;
        Phase phase=deathCurrent&&death.deploying()?Phase.DEPLOYING:downed?(d.beingRevived()?Phase.REVIVING:d.surrenderRequested()?Phase.GIVING_UP:Phase.DOWNED)
            :deathCurrent&&(death.observation()||death.respawnHold())?Phase.DEAD:Phase.NONE;
        int killerId=downed?d.killerEntityId():death.killerEntityId();
        var killer=killerId<0?null:mc.level.getEntity(killerId);
        String session=mc.player.getUUID()+"/"+mc.level.dimension().location()+"/"+room;
        long sequence=downed&&d.debug()?-d.sequence():death.sequence(),started=downed?d.startedNanos():death.startedNanos();
        boolean visible=phase!=Phase.NONE&&phase!=Phase.DEPLOYING;
        Attacker fallback=killer==null?Attacker.EMPTY:new Attacker(killer.getUUID(),ProfileClient.label(killer.getUUID(),killer.getName().getString()),"","","");
        if(visible) {
            boolean confirmedFromDowned=wasVisible&&phase==Phase.DEAD&&(previousPhase==Phase.DOWNED||previousPhase==Phase.REVIVING||previousPhase==Phase.GIVING_UP);
            EVIDENCE.begin(session,sequence,started,fallback,confirmedFromDowned);
            String scope=room==null||room.isBlank()||room.equals("-")?"dimension:"+mc.level.dimension().location():"room:"+room;
            var receipt=org.mods.gd656killicon.client.killfeed.DeathObservationClient.currentReceipt(mc.player.getUUID(),scope);
            if(receipt!=null&&!(downed&&d.debug())) {
                var facts=receipt.event();
                if(killer==null||facts.attackerId()==null||killer.getUUID().equals(facts.attackerId()))
                    EVIDENCE.accept(facts.eventId(),receipt.receivedAtNanos(),new Attacker(facts.attackerId(),ProfileClient.label(facts.attackerId(),facts.attackerName()),facts.weaponId(),
                        DownedClient.weaponName(facts.weaponId(),facts.weaponName()),org.mods.gd656killicon.client.killfeed.KillFeedLayout.kindLabel(facts.kind()),facts.attackerHealth(),facts.attackerMaxHealth(),facts.attackerLevel(),facts.weaponKills()));
            }
        } else {
            if(wasVisible)org.mods.gd656killicon.client.killfeed.DeathObservationClient.clear(mc.player.getUUID());
            EVIDENCE.clear();
        }
        wasVisible=visible;previousPhase=phase;
        Attacker attacker=EVIDENCE.current();
        if(phase!=Phase.NONE&&phase!=Phase.DEPLOYING&&killer!=null) {
            if(++markerRefresh>=5) {
                markerRefresh=0;
                var marker=org.mods.gd656conquest.client.marker.ClientRoomMarkerState.snapshotMinimapMarkers().stream()
                    .filter(v->v.remainingTicks()>0&&killer.getUUID().equals(v.followPlayerId())).findFirst().orElse(null);
                markerTarget=marker==null?null:marker.followPlayerId();
                markerExpires=marker==null?0:mc.level.getGameTime()+marker.remainingTicks();
            }
            if(killer.getUUID().equals(markerTarget)&&mc.level.getGameTime()<markerExpires)org.mods.gd656killicon.client.downed.DownedHighlight.target(markerTarget);
        } else {markerTarget=null;markerRefresh=5;markerExpires=0;}
        List<Rescuer> rescuers=downed?d.rescuers().stream().map(v->new Rescuer(v.name(),v.distanceMeters(),v.squadMate())).toList():List.of();
        DownedClient.observe(new Snapshot(session,sequence,started,phase,downed?d.remainingTicks():death.respawnHold()?death.respawnRemainingTicks():-1,
            downed?d.surrenderProgress():0,downed&&d.callingForHelp(),mc.options.keyJump.getTranslatedKeyMessage().getString(),attacker,rescuers,death.darkness()));
    }
}
