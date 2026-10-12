package org.mods.gd656conquest.client.room;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.network.packet.DeathCausePacket;

@Mod.EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT)
public final class ClientDeathCauseState {
    private static final DeathCauseBanner BANNER = new DeathCauseBanner();
    private static Object level, playerIdentity;
    private static long lastFrame;
    private ClientDeathCauseState() {}

    public static void accept(DeathCausePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.getId() != packet.entityId()
            || !mc.level.dimension().location().toString().equals(packet.dimension())) return;
        if (level != mc.level || playerIdentity != mc.player) reset();
        level = mc.level; playerIdentity = mc.player;
        if (BANNER.begin(packet.deathId(), packet.cause())) {
            lastFrame = System.nanoTime();
            ClientKillerCardState.begin(packet);
            ClientManualDeathDeployState.begin(packet);
        }
    }
    public static void hide() {
        BANNER.hide();
        if (!ClientDownedState.isActive() && !ClientDownedCamera.deathFilterActive()) ClientKillerCardState.hide();
    }
    public static void reset() { BANNER.reset(); ClientKillerCardState.reset(); level = null; playerIdentity = null; lastFrame = 0; }

    public static String label(DeathCause cause) {
        if (cause == null) cause = DeathCause.KILLED_IN_ACTION;
        if (cause == DeathCause.PLAYER_KILL) return "";
        String key = "gd656conquest.death_cause." + cause.name().toLowerCase(Locale.ROOT);
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key) && !translated.isBlank()) return translated;
        // Display-only fallback, including incomplete third-party language/resource packs.
        return switch (cause) {
            case RETREAT_FAILED -> "撤退失败";
            case ACCIDENT -> "意外阵亡";
            case FALL -> "摔死";
            case DESERTION -> "逃兵";
            case BURNED -> "烧死";
            case CRUSHED -> "压死";
            case DROWNED -> "溺死";
            case REDEPLOY -> "重新部署中";
            case COMMAND_KILL -> "被管理员杀死";
            default -> "阵亡";
        };
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    /** Post-HUD rendering keeps the title outside the world desaturation pass. */
    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || level != mc.level || playerIdentity != mc.player) {
            reset(); return;
        }
        long now = System.nanoTime();
        float elapsed = lastFrame == 0 ? 0 : Math.max(0, (now - lastFrame) / 1e9F);
        lastFrame = now;
        BANNER.advance(elapsed, ClientDeathFilter.deathActive());
        float alpha = BANNER.alpha();
        if (alpha <= .01F || mc.options.hideGui || ClientBattalionBriefingState.shouldHidePlayerHud()) return;
        String text = label(BANNER.cause());
        if (text.isEmpty()) return;
        int[] letters = text.codePoints().toArray();
        float textWidth = 0;
        for (int letter : letters) textWidth += mc.font.width(new String(Character.toChars(letter)));
        DeathCauseBanner.Layout box = DeathCauseBanner.layout(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), textWidth, letters.length);
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill((int) Math.floor(box.left()), (int) Math.floor(box.top()),
            (int) Math.ceil(box.left() + box.width()), (int) Math.ceil(box.top() + box.height()),
            Math.round(89 * alpha) << 24 | 0x20282A);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(box.left() + 6 * box.scale(), box.top() + 2 * box.scale(), 0);
            graphics.pose().scale(box.scale(), box.scale(), 1);
            float x = 0;
            int color = Math.max(4, Math.round(255 * alpha)) << 24 | 0xFFFFFF;
            for (int letter : letters) {
                String glyph = new String(Character.toChars(letter));
                graphics.drawString(mc.font, glyph, Math.round(x), 0, color, false);
                x += mc.font.width(glyph) + box.spacing();
            }
        } finally { graphics.pose().popPose(); }
    }
}
