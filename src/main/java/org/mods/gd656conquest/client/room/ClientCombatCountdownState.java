package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.client.config.ClientPacketBypassConfig;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.common.match.CombatCountdownState;
import org.mods.gd656conquest.network.packet.RoomCombatCountdownPacket;

public final class ClientCombatCountdownState {
    private static final CombatCountdownState STATE = new CombatCountdownState();
    private static Object level, player;
    private static String roomId;

    private ClientCombatCountdownState() {}

    public static void accept(RoomCombatCountdownPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientPacketBypassConfig.bypassDeathFlowPackets()) {
            reset();
            return;
        }
        if (packet.remainingTicks() <= 0) {
            STATE.clear(packet.kind());
            return;
        }
        level = mc.level;
        player = mc.player;
        roomId = ClientHudDebugState.getRoomId();
        STATE.update(packet.kind(), packet.text(), packet.remainingTicks());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!validSession(mc) || !mc.player.isAlive()) {
            reset();
            return;
        }
        STATE.tick();
    }

    public static void render(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        if (STATE.active() && validSession(mc) && !mc.options.hideGui && !ClientBoundaryState.isActive()
            && !ClientRetreatState.isActive() && !ClientDeathFilter.deathActive()
            && !ClientDeathFlowState.shouldRenderDeployBoundaries()) {
            CombatAreaWarningRenderer.render(gui, STATE.text(), STATE.remainingTicks());
        }
    }

    private static boolean validSession(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.player == player && mc.level == level
            && java.util.Objects.equals(roomId, ClientHudDebugState.getRoomId())
            && !"-".equals(ClientHudDebugState.getRoomId())
            && !"QUEUE".equals(ClientHudDebugState.getScoreMethod())
            && !"ENDING".equals(ClientHudDebugState.getAudioState())
            && !ClientBattalionBriefingState.isCinematicActive();
    }

    public static void reset() { STATE.reset(); level = null; player = null; roomId = null; }
}
