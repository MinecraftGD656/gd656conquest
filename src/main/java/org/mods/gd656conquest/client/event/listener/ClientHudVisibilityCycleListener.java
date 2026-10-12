package org.mods.gd656conquest.client.event.listener;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientBattalionBriefingState;
import org.mods.gd656conquest.common.match.BattalionHudVisibility;

public final class ClientHudVisibilityCycleListener {
    private static final ResourceLocation SIMPLE_HEALTH_BAR_OVERLAY = new ResourceLocation("simplehealthbar", "health_bar");

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_F1) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ClientHudVisibilityCycleService.onF1Pressed();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientHudVisibilityCycleService.tick();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderHudPre(RenderGuiOverlayEvent.Pre event) {
        if (event == null || event.getOverlay() == null) {
            return;
        }

        ResourceLocation id = event.getOverlay().id();
        if (ClientBattalionBriefingState.shouldHidePlayerHud()) {
            if (!BattalionHudVisibility.keepsOverlay(id.toString())) event.setCanceled(true);
            return;
        }

        if (ClientHudVisibilityCycleService.shouldApplyForcedHudOnlyFilter() && shouldHideOverlay(event)) {
            event.setCanceled(true);
            return;
        }

        if (ClientHudVisibilityCycleService.shouldHideAllHudButKeepHand()) {
            event.setCanceled(true);
            return;
        }

        if (ClientHudVisibilityCycleService.shouldHideTopHudOnly()) {
            if (!isBottomHudOverlay(id)) {
                event.setCanceled(true);
            }
        }
    }

    private boolean isBottomHudOverlay(ResourceLocation id) {
        return id.equals(VanillaGuiOverlay.HOTBAR.id())
            || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
            || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
            || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
            || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())
            || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
            || id.equals(VanillaGuiOverlay.JUMP_BAR.id())
            || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
            || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
            || id.equals(SIMPLE_HEALTH_BAR_OVERLAY);
    }

    private boolean shouldHideOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event == null || event.getOverlay() == null) {
            return false;
        }
        ResourceLocation overlayId = event.getOverlay().id();
        if (ClientDeathFlowState.isRespawnHoldActive()
            && (overlayId.equals(VanillaGuiOverlay.TITLE_TEXT.id())
            || overlayId.equals(VanillaGuiOverlay.SUBTITLES.id()))) {
            return false;
        }
        return overlayId.equals(VanillaGuiOverlay.HOTBAR.id())
            || overlayId.equals(VanillaGuiOverlay.CROSSHAIR.id())
            || overlayId.equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())
            || overlayId.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
            || overlayId.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
            || overlayId.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
            || overlayId.equals(VanillaGuiOverlay.AIR_LEVEL.id())
            || overlayId.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
            || overlayId.equals(VanillaGuiOverlay.JUMP_BAR.id())
            || overlayId.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
            || overlayId.equals(VanillaGuiOverlay.ITEM_NAME.id())
            || overlayId.equals(VanillaGuiOverlay.POTION_ICONS.id())
            || overlayId.equals(VanillaGuiOverlay.SCOREBOARD.id())
            || overlayId.equals(VanillaGuiOverlay.TITLE_TEXT.id())
            || overlayId.equals(VanillaGuiOverlay.SUBTITLES.id())
            || overlayId.equals(SIMPLE_HEALTH_BAR_OVERLAY);
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHudVisibilityCycleService.reset();
    }

    @SubscribeEvent
    public void onClientDimensionChange(ClientPlayerNetworkEvent.Clone event) {
        ClientHudVisibilityCycleService.reset();
    }
}
