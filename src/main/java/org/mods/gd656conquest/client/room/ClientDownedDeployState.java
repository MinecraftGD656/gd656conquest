package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.mods.gd656conquest.common.player.DownedDeployModel;
import org.mods.gd656conquest.network.packet.DeathCausePacket;
import org.mods.gd656conquest.network.packet.DownedDeployPacket;

public final class ClientDownedDeployState {
    private static final DownedDeployModel MODEL = new DownedDeployModel();
    private ClientDownedDeployState() {}
    public static void begin(DeathCausePacket p) { MODEL.begin(p.deathId()); }
    public static void accept(DownedDeployPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientDownedState.isActive()
            || mc.player.getId() != p.entityId() || !mc.level.dimension().location().toString().equals(p.dimension())) return;
        MODEL.accept(p.deathId(), p.enabled(), p.deploymentTicks(), p.deathTicks(), now(), mc.options.keyJump.isDown());
    }
    private static long now() { var world = Minecraft.getInstance().level; return world == null ? 0 : world.getGameTime(); }
    public static boolean enabled() { return MODEL.enabled(); }
    public static boolean ready() { return MODEL.ready(); }
    public static String deploymentText() { return ready() ? "已可部署" : "下一次部署 " + MODEL.deploymentSeconds(now()) + " 秒"; }
    public static String deathText() {
        int seconds = MODEL.known() ? MODEL.deathSeconds(now()) : (int)Math.ceil(ClientDownedState.killerCardRemainingTicks() / 20D);
        return "彻底死亡 " + seconds + " 秒";
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        boolean down = mc.options.keyJump.isDown();
        boolean allowed = ClientDownedState.isActive() && mc.screen == null
            && !"being_revived".equals(ClientRoomLockState.snapshot().reason());
        if (MODEL.press(down, allowed)) ClientDownedState.requestReadyDeployment();
    }
    /** The original hold-to-surrender code continues to run before readiness. */
    public static boolean allowLongPress(KeyMapping jump) { return !ready() && jump.isDown(); }
    public static void hide() { MODEL.close(); }
    public static void reset() { MODEL.reset(); }
}
