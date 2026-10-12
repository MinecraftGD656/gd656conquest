package org.mods.gd656conquest.client.room;

import org.mods.gd656conquest.client.audio.ClientMusicTriggerService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.common.match.BattalionSequence.Phase;
import org.mods.gd656conquest.network.packet.BattalionBriefingPacket;

/** Presentation follows phase packets; it never unlocks gameplay on a local timer. */
public final class ClientBattalionBriefingState {
    private static BattalionBriefingPacket packet;
    private static int ticks;
    private static Object level;
    private static net.minecraft.client.CameraType originalCameraType;
    private ClientBattalionBriefingState() {}

    public static void accept(BattalionBriefingPacket next) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (level != mc.level) {restoreCameraType();packet = null; ticks = 0; level = null; }
        if (packet != null && packet.roomId().equals(next.roomId()) && packet.matchId() == next.matchId()) {
            if (next.sequenceId() < packet.sequenceId()) return;
            if (next.sequenceId() == packet.sequenceId() && next.phase().presentationOrder() <= packet.phase().presentationOrder()) return;
        }
        level = mc.level;
        boolean preserve=org.mods.gd656conquest.common.match.BattalionIntermissionLifecycle.preservesScene(next.phase());
        if(preserve){if(originalCameraType==null)originalCameraType=mc.options.getCameraType();mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);}
        else restoreCameraType();
        ticks = Math.max(0, next.elapsedTicks());
        if (next.phase() == Phase.COMBAT || next.phase() == Phase.FINISHED) {
            ClientBriefingPlayback.finish(next.roomId(),next.matchId(),next.sequenceId());
            if (next.phase() == Phase.FINISHED && next.view() != null && (next.attackersWon() || next.battalion() >= next.totalBattalions())) {
                var view = next.view();
                ClientDeathFlowState.holdCinematicCameraAt(view.x()+.5,view.y(),view.z()+.5,view.yaw(),view.pitch());
            }
            packet = next;
            return;
        }
        ClientReviveState.reset();
        if (mc.screen instanceof AbstractContainerScreen<?>
            || (mc.screen instanceof LoadoutConfigScreen config && !config.isRoomEditorTabOpen())) mc.setScreen(null);
        var view = next.view();
        switch (next.phase()) {
            case RESULT -> ClientDeathFlowState.pullBackCinematicCamera(ticks);
            case TRAVEL_TO_BRIEFING, TRAVEL_TO_DEPLOY -> {
                if (view != null) ClientDeathFlowState.travelCinematicCamera(view.x() + .5, view.y(), view.z() + .5,
                    view.yaw(), view.pitch(), Math.max(1, next.durationTicks() - ticks));
            }
            case BRIEFING, BRIEFING_FADE_OUT, NEXT_ASSAULT -> {
                if (view != null) ClientDeathFlowState.holdCinematicCameraAt(view.x() + .5, view.y(), view.z() + .5, view.yaw(), view.pitch());
            }
            case PREPARATION -> {
                org.mods.gd656conquest.client.audio.ClientAudioManager.getInstance().enqueueStopShellingPresentation();
                // Explicit handoff: remove briefing presentation and enter the existing deploy phase.
                if (view != null) ClientDeathFlowState.openDeployInstant(view.x() + .5, view.y(), view.z() + .5,
                    view.yaw(), view.pitch(), Math.max(0, next.durationTicks() - ticks));
                ClientMusicTriggerService.onDeployOpened();
            }
            default -> {}
        }
        packet = next;
        level = mc.level;
        ticks = Math.max(0, next.elapsedTicks());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || level != mc.level) { reset(); return; }
        if (packet != null) {
            ticks = Math.min(packet.durationTicks(), ticks + 1);
            if (org.mods.gd656conquest.common.match.BattalionIntermissionLifecycle.preservesScene(packet.phase()))
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }
    }

    public static BattalionBriefingPacket snapshot() { return packet; }
    public static double elapsed(float partialTick) { return ticks + Math.max(0, Math.min(1, partialTick)); }
    public static boolean isCinematicActive() {
        return packet != null && packet.phase() != Phase.COMBAT && packet.phase() != Phase.PREPARATION && packet.phase() != Phase.FINISHED;
    }
    public static boolean showingBriefing() {
        return packet != null && org.mods.gd656conquest.common.match.BattalionSequence.briefingBoundaryAlpha(packet.phase(), ticks + 1) > 0;
    }
    private static void restoreCameraType(){if(originalCameraType!=null){Minecraft.getInstance().options.setCameraType(originalCameraType);originalCameraType=null;}}
    public static void reset() {restoreCameraType(); ClientBriefingPlayback.reset(); packet = null; ticks = 0; level = null; }
}
