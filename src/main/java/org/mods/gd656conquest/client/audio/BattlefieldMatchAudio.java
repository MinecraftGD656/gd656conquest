package org.mods.gd656conquest.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656killicon.client.sounds.BattlefieldAudio;

/** Consumes the original same-room MATCHING/QUEUE -> running HUD snapshot edge. */
@Mod.EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BattlefieldMatchAudio {
    private static final Gate GATE = new Gate();
    private static Object connection;
    private BattlefieldMatchAudio() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Object current = mc.getConnection();
        if (current != connection) { GATE.clear(); connection = current; }
        if (current == null) { GATE.clear(); return; }
        if (mc.player == null || mc.level == null) return;
        if (GATE.observe(ClientHudDebugState.getRoomId(), ClientHudDebugState.getScoreMethod(),
                ClientHudDebugState.getRoomState(), ClientHudDebugState.getAudioState()))
            BattlefieldAudio.play(BattlefieldAudio.Event.MATCH_FOUND);
    }

    public static final class Gate {
        private String queuedRoom;
        public boolean observe(String room, String mode, String state, String audio) {
            if (room == null || room.isBlank() || room.equals("-")) { clear(); return false; }
            if ("QUEUE".equals(mode) && "匹配中".equals(state) && "MATCHING".equals(audio)) {
                queuedRoom = room; return false;
            }
            boolean same = room.equals(queuedRoom);
            if (same && "匹配中".equals(state)) return false;
            boolean started = same && !"QUEUE".equals(mode) && "进行中".equals(state) && !"MATCHING".equals(audio);
            clear(); return started;
        }
        public void clear() { queuedRoom = null; }
    }
}
