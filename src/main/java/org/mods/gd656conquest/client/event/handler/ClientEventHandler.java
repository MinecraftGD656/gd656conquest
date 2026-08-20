package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;
import org.mods.gd656conquest.client.compat.superbwarfare.ClientSuperbWarfareShellingHook;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;
import org.mods.gd656conquest.client.audio.ClientMusicTriggerService;
import org.mods.gd656conquest.client.hud.ClientHudDebugPreviewState;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.hud.ClientHudRenderer;
import org.mods.gd656conquest.client.hud.ClientHudSubtitleManager;
import org.mods.gd656conquest.client.hud.ClientMatchLoadState;
import org.mods.gd656conquest.client.hud.ClientMatchResultOverlayState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiController;
import org.mods.gd656conquest.client.marker.ClientMarkerDebugPreviewState;
import org.mods.gd656conquest.client.marker.ClientRoomMarkerState;
import org.mods.gd656conquest.client.parkour.ClientParkourLeaderboardState;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;
import org.mods.gd656conquest.client.pack.ClientPackAuditService;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientBoundaryHeightReportService;
import org.mods.gd656conquest.client.room.ClientDeployDeathMarkerState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import org.mods.gd656conquest.client.room.ClientReviveState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.client.room.ClientRoomTeamMirror;
import org.mods.gd656conquest.common.audio.AudioManager;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;

import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

public final class ClientEventHandler {
    private static final ClientEventHandler INSTANCE = new ClientEventHandler();

    private final ClientHudRenderer hudRenderer = new ClientHudRenderer();
    private final ClientLoadoutUiController loadoutUiController = new ClientLoadoutUiController();
    private final ClientCaptureOverlayController captureOverlayController = new ClientCaptureOverlayController();
    private final ClientCaptureOutlineController captureOutlineController = new ClientCaptureOutlineController();
    private final ClientRoomSessionController roomSessionController = new ClientRoomSessionController(loadoutUiController);
    private final ClientSuperbWarfareShellingHook superbWarfareShellingHook = new ClientSuperbWarfareShellingHook();

    public static ClientEventHandler getInstance() {
        return INSTANCE;
    }

    private ClientEventHandler() {
    }

    public void handleClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            if (minecraft.player != null && minecraft.level != null) {
                TaczClientInputCompat.tickCombatInputSuppression(minecraft);
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        org.mods.gd656conquest.client.input.ClientFovScrollService.onClientTick();
        AudioManager.getInstance().tickClient();
        ClientMusicTriggerService.tick();
        ClientPlayerAnimatorDebugState.tick();
        ClientDeployDeathMarkerState.tick();
        ClientHudSubtitleManager.tick();
        if (minecraft.player == null || minecraft.level == null) {
            ClientHudSubtitleManager.reset();
            ClientParkourRuntimeState.clear();
            ClientParkourLeaderboardState.clear();
            ClientDeployDeathMarkerState.clear();
            captureOverlayController.handleClientLogout();
            captureOutlineController.clear();
            return;
        }
        while (org.mods.gd656conquest.client.input.ClientKeyMappings.PLACE_TACTICAL_MARKER.consumeClick()) {
            ClientRoomMarkerState.handleKeyPress(minecraft);
        }
        ClientRoomMarkerState.tick();
        ClientParkourRuntimeState.tick(minecraft);
        ClientDeathFlowState.tick();
        ClientPackAuditService.tick(minecraft);
        ClientBoundaryHeightReportService.tick(minecraft);
        ClientDownedState.tick();
        ClientReviveState.tick();
        ClientMatchResultOverlayState.tick();
        ClientMatchLoadState.tick();
        superbWarfareShellingHook.tick(minecraft);
        String roomId = ClientHudDebugState.getRoomId();
        String roomState = ClientHudDebugState.getRoomState();
        boolean inRoom = roomId != null && !roomId.isBlank() && !"-".equals(roomId) && (roomState == null || !roomState.contains("未加入"));
        if (!inRoom) {
            ClientHudSubtitleManager.reset();
        }
        roomSessionController.tick(minecraft, captureOverlayController);
        captureOutlineController.tick(minecraft);
    }

    public void handleRenderLevel(RenderLevelStageEvent event) {
        if (!captureOverlayController.shouldRenderCaptureLinesForStage(event.getStage())) {
            return;
        }
        ClientDeathFlowState.renderFrame(event.getPartialTick());
        captureOverlayController.captureWorldProjectionState(event);
        ClientRoomMarkerState.captureProjectionState(event);
        try {
            captureOverlayController.renderCaptureBoundaryLines(event);
        } catch (Throwable ignored) {
        }
        try {
            captureOutlineController.renderCaptureBlockOutlineLines(event, captureOverlayController.resolveSelfTeamToken());
        } catch (Throwable ignored) {
        }
    }

    public void handleCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        roomSessionController.enforceFirstPersonDuringCameraSetup();
        if (!ClientDeathFlowState.hasActiveCameraOverride()) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        ClientDeathFlowState.applyCameraOverride(event.getCamera(), partialTick);
        event.setYaw(ClientDeathFlowState.getInterpolatedCameraYaw(partialTick));
        event.setPitch(ClientDeathFlowState.getInterpolatedCameraPitch(partialTick));
    }

    public void handleComputeFov(ViewportEvent.ComputeFov event) {
        org.mods.gd656conquest.client.input.ClientFovScrollService.onComputeFov(event);
    }

    public void handleBlockHighlight(RenderHighlightEvent.Block event) {
        if (event.getTarget().getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        BlockState targetState = minecraft.level.getBlockState(event.getTarget().getBlockPos());
        if (ConquestBlocks.isCapturePoint(targetState) || ConquestBlocks.isOutOfBoundsBlock(targetState)) {
            boolean holdingMatching = false;
            if (ConquestBlocks.isOutOfBoundsBlock(targetState)) {
                holdingMatching = ConquestBlocks.isOutOfBoundsBlockItem(minecraft.player.getMainHandItem().getItem()) ||
                                  ConquestBlocks.isOutOfBoundsBlockItem(minecraft.player.getOffhandItem().getItem());
            } else {
                Optional<String> code = ConquestBlocks.resolveCaptureCode(targetState);
                if (code.isPresent()) {
                    Item matchItem = ConquestBlocks.getCaptureBlockItem(code.get());
                    if (matchItem != null) {
                        holdingMatching = minecraft.player.getMainHandItem().is(matchItem) ||
                                          minecraft.player.getOffhandItem().is(matchItem);
                    }
                }
            }
            if (!holdingMatching) {
                event.setCanceled(true);
            }
        }
    }

    public void handleHudRender(RenderGuiOverlayEvent.Post event, boolean isMainOverlayPass) {
        // Main HUD now renders through registered Forge overlays so we can control ordering
        // relative to third-party HUDs and F1 visibility consistently.
    }

    public void renderBaseHudEffects(net.minecraftforge.client.gui.overlay.ForgeGui gui, net.minecraft.client.gui.GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        captureOverlayController.renderDeployBoundaryHudOverlay(guiGraphics, screenWidth, screenHeight);
        roomSessionController.renderVignetteOverlay(guiGraphics, screenWidth, screenHeight);
        renderDebugOverlayIfActive(guiGraphics);
    }

    private void renderDebugOverlayIfActive(net.minecraft.client.gui.GuiGraphics guiGraphics) {
        if (!ClientHudDebugState.isAdminDebugActive()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int x = 4;
        int y = 4;
        int rendererCache = captureOverlayController.debugTotalRendererCacheSize();
        int outlineCache = captureOutlineController.debugTotalOutlineCacheSize();
        int totalCache = rendererCache + outlineCache;
        String line = "垃圾: R=" + rendererCache + " O=" + outlineCache + " T=" + totalCache;
        guiGraphics.drawString(font, Component.literal(line).withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
    }

    public void renderMainHud(net.minecraftforge.client.gui.overlay.ForgeGui gui, net.minecraft.client.gui.GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        // 客户端配置禁用屏幕HUD时,非部署阶段不再渲染本模组的任何顶部HUD、据点HUD与3D空间据点图标;
        // 死亡后的部署阶段(部署点选择)视为部署界面,所有元素照常渲染。
        if (!org.mods.gd656conquest.client.config.ClientScreenHudConfig.isScreenHudEnabled()
            && !ClientDeathFlowState.shouldRenderDeployBoundaries()) {
            return;
        }
        // During match-loading phases 1-3: show only the loading overlay, hide all other HUD
        if (ClientMatchLoadState.shouldHideAllHud()) {
            ClientMatchLoadState.render(guiGraphics, partialTick);
            return;
        }

        if (org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.shouldHideAllHudButKeepHand()
            || org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.shouldHideAllHudAndHand()
            || org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.shouldHideTopHudOnly()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            if (!ClientMatchResultOverlayState.shouldHideConquestHud()) {
                hudRenderer.render(guiGraphics);
                captureOverlayController.renderCaptureIconHudOverlay(guiGraphics);
                ClientRoomMarkerState.renderHudOverlay(guiGraphics);
            }
            ClientMarkerDebugPreviewState.renderHudOverlay(guiGraphics);
            roomSessionController.renderSessionOverlay(guiGraphics, partialTick);
            ClientMatchResultOverlayState.render(guiGraphics);
            ClientMatchLoadState.render(guiGraphics, partialTick);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    public void handleRenderHand(RenderHandEvent event) {
        if (event == null) {
            return;
        }
        if (org.mods.gd656conquest.client.hud.ClientHudVisibilityCycleService.shouldHideAllHudAndHand()) {
            event.setCanceled(true);
        }
    }

    public void handleClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMusicTriggerService.reset();
        AudioManager.getInstance().stopAllMusic(0);
        ClientRoomTeamMirror.reset();
        ClientHudDebugState.reset();
        ClientHudSubtitleManager.reset();
        ClientParkourRuntimeState.clear();
        ClientParkourLeaderboardState.clear();
        ClientMatchResultOverlayState.clear();
        ClientMatchLoadState.clear();
        ClientDeathFlowState.clear();
        ClientPackAuditService.clear();
        ClientBoundaryHeightReportService.clear();
        ClientDownedState.clear();
        ClientDeployDeathMarkerState.clear();
        ClientReviveState.reset();
        ClientRoomLockState.reset();
        ClientRoomMarkerState.reset();
        ClientMarkerDebugPreviewState.reset();
        ClientHudDebugPreviewState.reset();
        ClientPlayerAnimatorDebugState.reset();
        TaczClientInputCompat.resetSuppression();
        superbWarfareShellingHook.reset();
        roomSessionController.handleLogout();
        captureOverlayController.handleClientLogout();
        captureOutlineController.clear();
    }

    public void handleClientDimensionChange(ClientPlayerNetworkEvent.Clone event) {
        ClientMatchLoadState.clear();
        String roomId = ClientHudDebugState.getRoomId();
        if (roomId != null && !roomId.isBlank() && !"-".equals(roomId)) {
            ClientDeployDeathMarkerState.clear();
            captureOverlayController.clearRenderCaches();
            captureOutlineController.clear();
        }
    }

    public void handleScreenOpening(ScreenEvent.Opening event) {
        roomSessionController.handleScreenOpening(event, captureOverlayController);
    }

    public void handleClientKeyInput(InputEvent.Key event) {
        if (org.mods.gd656conquest.client.input.ClientFovScrollService.handleKeyInput(event)) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            return;
        }
        if (superbWarfareShellingHook.handleKeyInput(Minecraft.getInstance(), event)) {
            if (event != null && event.isCancelable()) {
                event.setCanceled(true);
            }
            return;
        }
        if (captureOverlayController.handleKeyInput(event)) {
            return;
        }
        roomSessionController.handleKeyInput(event);
    }

    public void handleMouseInput(InputEvent.MouseButton event) {
        if (roomSessionController.handleMouseButton(event)) {
            return;
        }
        captureOverlayController.handleMouseInput(event);
        if (event.isCancelable() && event.isCanceled()) {
            return;
        }
        if (ClientPlayerAnimatorDebugState.handleMouseInput(event)) {
            if (event.isCancelable() && Minecraft.getInstance().screen == null) {
                event.setCanceled(true);
            }
            return;
        }
    }

    public void handleMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (org.mods.gd656conquest.client.input.ClientFovScrollService.handleMouseScroll(event)) {
            return;
        }
        roomSessionController.handleMouseScroll(event);
    }

    public void handleMovementInput(MovementInputUpdateEvent event) {
        roomSessionController.handleMovementInput(event);
    }

    public void handleEntitySize(EntityEvent.Size event) {
        PlayerAnimatorDebugDimensionsState.handleEntitySize(event);
    }

    public void handleScreenCharTyped(ScreenEvent.CharacterTyped event) {
        roomSessionController.handleScreenCharTyped(event);
    }
}
