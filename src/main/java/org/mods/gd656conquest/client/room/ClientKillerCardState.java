package org.mods.gd656conquest.client.room;

import java.util.List;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.common.player.KillerCardInfo;
import org.mods.gd656conquest.network.packet.DeathCausePacket;
import org.mods.gd656conquest.network.packet.KillerCardPacket;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;

@Mod.EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT)
public final class ClientKillerCardState {
    private static final KillerCardModel MODEL = new KillerCardModel();
    private static Object level, player;
    private static boolean rescueVisible;
    private static ResourceLocation skin;
    private ClientKillerCardState() {}

    private static boolean scope(int entityId, String dimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.getId() != entityId
            || !mc.level.dimension().location().toString().equals(dimension)) return false;
        ensureWorld(mc); return true;
    }
    private static void ensureWorld(Minecraft mc) {
        if (level != mc.level || player != mc.player) {
            reset(); level = mc.level; player = mc.player;
        }
    }
    public static void begin(DeathCausePacket packet) {
        if (scope(packet.entityId(), packet.dimension()) && MODEL.begin(packet.deathId(), packet.cause())) {
            skin = null; ClientDownedDeployState.begin(packet);
        }
    }
    public static void accept(KillerCardPacket packet) {
        if (scope(packet.entityId(), packet.dimension()) && MODEL.accept(packet.deathId(), packet.info())) skin = null;
    }
    /** Called after original entry logic, since that logic can clear the previous death flow. */
    public static void downedStarted() {
        ensureWorld(Minecraft.getInstance()); rescueVisible = true;
    }
    public static void hide() { rescueVisible = false; MODEL.close(); skin = null; ClientDownedDeployState.hide(); }
    public static void reset() { hide(); MODEL.reset(); ClientDownedDeployState.reset(); level = player = null; }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    /** Replaces only the original downed renderer; its input handling, sounds and rescuer state remain intact. */
    public static void render(GuiGraphics gui, Font font, String surrender, int surrenderColor,
                              String help, int helpColor, List<ClientDownedState.RescuerEntry> rescuers) {
        Minecraft mc = Minecraft.getInstance();
        if (gui == null || font == null || !ClientDownedState.isActive() || mc.player == null || mc.level == null
            || mc.options.hideGui) return;
        ensureWorld(mc);
        if (!rescueVisible || ClientDownedCamera.returning() || ClientDeathFlowState.shouldRenderDeployBoundaries()) return;
        boolean ready = ClientDownedDeployState.ready();
        if (ready && surrender.startsWith("长按")) surrender = "点按空格返回部署";
        drawFooter(gui, font, MODEL.info(),
            ClientDownedDeployState.enabled() ? ClientDownedDeployState.deploymentText() : "", ready,
            surrender, ready ? 0xE7E8E9 : surrenderColor, help, helpColor,
            ClientDownedDeployState.deathText(), rescuers, true);
    }

    /** Confirmed deaths use the same panel geometry while leaving all killer fields empty. */
    public static void renderConfirmedDeath(GuiGraphics gui, Font font, String timer, boolean ready) {
        drawFooter(gui, font, null, timer, ready, "点按空格返回部署", 0xE7E8E9,
            "", 0, "", List.of(), false);
    }

    private static void drawFooter(GuiGraphics gui, Font font, KillerCardInfo info,
                                   String timer, boolean ready, String surrender, int surrenderColor,
                                   String help, int helpColor, String death,
                                   List<ClientDownedState.RescuerEntry> rescuers, boolean rescuable) {
        Minecraft mc = Minecraft.getInstance();
        float alpha = ClientDeathFilter.cardOpacity();
        if (alpha <= .02F) return;
        KillerCardLayout box = KillerCardLayout.calculate(gui.guiWidth(), gui.guiHeight(), info != null,
            rescuers.size(), 0, 0, 0);
        gui.pose().pushPose();
        try {
            gui.pose().translate(box.left(), box.top(), 0);
            gui.pose().scale(box.scale(), box.scale(), 1);
            gui.fill(0, 0, box.width(), box.height(), color(0x101214, .72F * alpha));
            gui.fill(0, 0, box.width(), 1, color(0xB8C0C4, .30F * alpha));
            int y = (box.height() - 80) / 2;
            divider(gui, box.weaponLeft(), box.height(), alpha);
            divider(gui, box.actionLeft(), box.height(), alpha);
            divider(gui, box.rescueLeft(), box.height(), alpha);
            if (info != null) {
                int size = Math.min(box.height() - 16, box.avatarWidth() - 16);
                avatar(gui, mc, info, box.contentLeft() + (box.avatarWidth() - size) / 2,
                    (box.height() - size) / 2, size, alpha);
                int x = box.playerLeft();
                text(gui, font, "击杀者", x + 10, y + 7, 0xB8BEC2, alpha);
                String name = fit(font, info.name(), (int)((box.playerWidth() - 20 - (info.headshot() ? 16 : 0)) / 1.15F));
                gui.pose().pushPose();
                gui.pose().translate(x + 10, y + 21, 0); gui.pose().scale(1.15F, 1.15F, 1);
                text(gui, font, name, 0, 0, 0xFFFFFF, alpha);
                gui.pose().popPose();
                if (info.headshot()) {
                    RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); gui.setColor(1, 1, 1, alpha);
                    gui.blit(new ResourceLocation("gd656conquest", "textures/gui/killer_card/headshot.png"),
                        x + 14 + Math.round(font.width(name) * 1.15F), y + 20, 0F, 0F, 12, 12, 12, 12);
                    gui.setColor(1, 1, 1, 1);
                }
                int barWidth = box.playerWidth() - 20;
                String levelText = "Lv." + info.level(), healthText = "生命 " + info.healthPercent() + "%";
                int levelWidth = font.width(levelText), healthWidth = font.width(healthText);
                float rowScale = Math.min(1F, barWidth / (float)(levelWidth + 8 + healthWidth));
                gui.pose().pushPose();
                gui.pose().translate(x + 10, y + 39, 0); gui.pose().scale(rowScale, rowScale, 1);
                text(gui, font, levelText, 0, 0, PlayerLevelService.resolveLevelColorRgb(info.level()), alpha);
                text(gui, font, healthText, Math.round(barWidth / rowScale) - healthWidth, 0, 0xE6E8E9, alpha);
                gui.pose().popPose();
                gui.fill(x + 10, y + 52, x + 10 + barWidth, y + 57, color(0x502024, alpha));
                gui.fill(x + 10, y + 52, x + 10 + Math.round(barWidth * info.health() / info.maxHealth()), y + 57, color(0xD52E37, alpha));
                scaledText(gui, font, "玩家 VS 敌方：" + info.playerKills() + " : " + info.enemyKills(),
                    x + 10, y + 65, barWidth, 0xC6CBCE, alpha);
                x = box.weaponLeft();
                text(gui, font, "击杀武器", x + 10, y + 7, 0xB8BEC2, alpha);
                List<FormattedCharSequence> weapon = font.split(Component.literal(info.weaponName()), box.weaponWidth() - 20);
                float weaponScale = Math.min(1F, (box.height() - y - 30F) / Math.max(12, weapon.size() * 12));
                gui.pose().pushPose();
                gui.pose().translate(x + 10, y + 24, 0); gui.pose().scale(weaponScale, weaponScale, 1);
                for (int i = 0; i < weapon.size(); i++) gui.drawString(font, weapon.get(i), 0, i * 12, color(0xFFFFFF, alpha), false);
                gui.pose().popPose();
            }
            int x = box.actionLeft(), actionWidth = box.actionWidth() - 20;
            text(gui, font, "倒地状态", x + 10, y + 7, 0xB8BEC2, alpha);
            if (!timer.isEmpty()) scaledText(gui, font, timer, x + 10, y + 24, actionWidth,
                ready ? 0x89C976 : 0xE7E8E9, alpha);
            scaledText(gui, font, surrender, x + 10, y + 38, actionWidth, surrenderColor, alpha);
            if (!help.isEmpty()) scaledText(gui, font, help, x + 10, y + 52, actionWidth, helpColor, alpha);
            if (!death.isEmpty()) scaledText(gui, font, death, x + 10, y + 65, actionWidth, 0xD54D4D, alpha);
            x = box.rescueLeft();
            int contentWidth = box.rescueWidth() - 20;
            text(gui, font, "附近救援者", x + 10, y + 7, 0xB8BEC2, alpha);
            if (!rescuable) {
                text(gui, font, "不可被救援", x + 10, y + 24, 0xD54D4D, alpha);
            } else if (rescuers.isEmpty()) {
                text(gui, font, "30m 内暂无队友", x + 10, y + 24, 0xA8AFB3, alpha);
            }
            if (rescuable) for (int i = 0; i < Math.min(4, rescuers.size()); i++) {
                var entry = rescuers.get(i); String distance = entry.distanceMeters() + "米";
                int distanceWidth = font.width(distance);
                text(gui, font, fit(font, entry.name(), contentWidth - distanceWidth - 8), x + 10, y + 24 + i * 12,
                    entry.squadMate() ? 0xAABB58 : 0x60ACB7, alpha);
                text(gui, font, distance, x + box.rescueWidth() - 10 - distanceWidth, y + 24 + i * 12, 0x55B6AE, alpha);
            }
        } finally {
            gui.setColor(1, 1, 1, 1); RenderSystem.defaultBlendFunc(); gui.pose().popPose();
        }
    }
    private static void scaledText(GuiGraphics gui, Font font, String value, int x, int y, int width, int rgb, float alpha) {
        float scale = Math.min(1F, width / (float)Math.max(1, font.width(value)));
        gui.pose().pushPose();
        try {
            gui.pose().translate(x, y, 0); gui.pose().scale(scale, scale, 1);
            text(gui, font, value, 0, 0, rgb, alpha);
        } finally { gui.pose().popPose(); }
    }
    private static void avatar(GuiGraphics gui, Minecraft mc, KillerCardInfo info, int x, int y, int size, float alpha) {
        PlayerInfo playerInfo = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(info.killerId());
        if (playerInfo != null) {
            ResourceLocation candidate = playerInfo.getSkinLocation(); // Also starts the native asynchronous skin request.
            if (skin == null || playerInfo.isSkinLoaded()) skin = candidate;
        }
        ResourceLocation texture = skin == null ? DefaultPlayerSkin.getDefaultSkin(info.killerId()) : skin;
        gui.pose().pushPose();
        try {
            gui.pose().translate(x, y, 0); gui.pose().scale(size / 8F, size / 8F, 1);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); gui.setColor(1, 1, 1, alpha);
            gui.blit(texture, 0, 0, 8F, 8F, 8, 8, 64, 64);
            gui.blit(texture, 0, 0, 40F, 8F, 8, 8, 64, 64);
        } finally { gui.setColor(1, 1, 1, 1); gui.pose().popPose(); }
    }
    private static void divider(GuiGraphics gui, int x, int height, float alpha) {
        gui.fill(x, 13, x + 1, height - 13, color(0xBEC5C8, .25F * alpha));
    }
    private static void text(GuiGraphics gui, Font font, String text, int x, int y, int rgb, float alpha) {
        gui.drawString(font, text, x, y, color(rgb, alpha), false);
    }
    private static int color(int rgb, float alpha) { return Math.round(255 * alpha) << 24 | rgb & 0xFFFFFF; }
    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
}
