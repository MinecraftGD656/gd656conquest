package org.mods.gd656conquest.client.room;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.network.packet.BriefingPlaybackPacket;
import org.mods.gd656conquest.common.match.BriefingTimeline;

@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT)
public final class ClientBriefingPlayback {
    private static final BriefingTrackPlayer PLAYER=new BriefingTrackPlayer();
    private static BriefingPlaybackPacket snapshot;
    private static Object level,playerIdentity;
    private static final org.mods.gd656conquest.common.match.TimelinePlaybackClock CLOCK=new org.mods.gd656conquest.common.match.TimelinePlaybackClock();
    private static double ticks;
    private ClientBriefingPlayback() {}
    public static void accept(BriefingPlaybackPacket packet) {
        var mc=Minecraft.getInstance(); if(mc.level==null||mc.player==null)return;
        if(level!=mc.level||playerIdentity!=mc.player) reset();
        if(!CLOCK.accept(packet.roomId(),packet.matchId(),packet.sequenceId(),packet.elapsedTicks(),BriefingTimeline.length(packet.scene(),packet.briefingSeconds()),packet.active()))return;
        boolean fresh=snapshot==null||snapshot.sequenceId()!=packet.sequenceId()||snapshot.matchId()!=packet.matchId()||!snapshot.roomId().equals(packet.roomId());
        if(fresh || !snapshot.track().equals(packet.track())) { ticks=packet.elapsedTicks();PLAYER.load(packet.track(),packet.briefingSeconds(),ticks); }
        else ticks=packet.elapsedTicks();
        snapshot=packet;level=mc.level;playerIdentity=mc.player;
        PLAYER.position(ticks,packet.active());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=level||mc.player!=playerIdentity||mc.player==null) {reset();return;}
        if(snapshot!=null && snapshot.active()) {
            CLOCK.tick();ticks=CLOCK.ticks();PLAYER.position(ticks,CLOCK.playing());
        }
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(snapshot==null||!snapshot.active()||mc.options.hideGui||mc.screen instanceof org.mods.gd656conquest.client.loadout.screen.BriefingTimelineScreen)return;
        GuiGraphics gui=event.getGuiGraphics();PLAYER.render(gui,gui.guiWidth()/2,(int)(gui.guiHeight()*.80), (int)(gui.guiWidth()*.85));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {reset();}
    public static void finish(String roomId,long matchId,long sequenceId) {
        if(snapshot!=null && snapshot.roomId().equals(roomId) && snapshot.matchId()==matchId && sequenceId>=snapshot.sequenceId()) {
            CLOCK.stop();PLAYER.stop(); snapshot=new BriefingPlaybackPacket(snapshot.roomId(),snapshot.matchId(),snapshot.sequenceId(),snapshot.scene(),snapshot.briefingSeconds(),snapshot.elapsedTicks(),false,snapshot.track());
        }
    }
    public static void reset() {CLOCK.reset();PLAYER.stop();snapshot=null;level=null;playerIdentity=null;ticks=0;}
}
