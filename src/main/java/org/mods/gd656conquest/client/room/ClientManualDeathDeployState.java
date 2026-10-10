package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.common.player.ManualDeathDeployModel;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.DeathCausePacket;
import org.mods.gd656conquest.network.packet.DownedDeployPacket;
import org.mods.gd656conquest.network.packet.RoomDownedActionPacket;

@Mod.EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT)
public final class ClientManualDeathDeployState {
    private static final ManualDeathDeployModel MODEL = new ManualDeathDeployModel();
    private static Object level;
    private ClientManualDeathDeployState() {}
    public static void begin(DeathCausePacket packet) { MODEL.begin(packet.deathId()); level = Minecraft.getInstance().level; }
    public static void accept(DownedDeployPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (!p.confirmedDeath() || mc.player == null || mc.level != level || mc.options == null
            || mc.player.getId() != p.entityId() || !ClientDownedCamera.observingDeath()
            || !mc.level.dimension().location().toString().equals(p.dimension())) return;
        MODEL.accept(p.deathId(), p.deploymentTicks(), now(), mc.options.keyJump.isDown());
    }
    private static long now() { var world = Minecraft.getInstance().level; return world == null ? 0 : world.getGameTime(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level || mc.player == null) { MODEL.reset(); level = null; return; }
        if (!ClientDownedCamera.observingDeath() || ClientDeathFlowState.shouldRenderDeployBoundaries()) { MODEL.close(); return; }
        if (mc.options != null && MODEL.press(mc.options.keyJump.isDown(), mc.screen == null, now()))
            NetworkManager.getChannel().sendToServer(new RoomDownedActionPacket("death_deploy:" + MODEL.deathId()));
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!MODEL.active() || !ClientDownedCamera.observingDeath() || mc.level != level
            || mc.options == null || mc.options.hideGui) return;
        int seconds = MODEL.seconds(now());
        ClientKillerCardState.renderConfirmedDeath(event.getGuiGraphics(), mc.font,
            seconds == 0 ? "已可部署" : "下一次部署 " + seconds + " 秒", seconds == 0);
    }
}
