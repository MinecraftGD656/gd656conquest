package org.mods.gd656conquest.client.event.handler;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.CaptureIconEntry;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.HoverLabel;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.RenderedIconBounds;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.ScreenProjection;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeammateIconScreenState;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.VehicleHudState;
import org.mods.gd656conquest.client.render.ClientCrosshairFadeSupport;
import org.mods.gd656conquest.client.render.ClientWorldHudProjectionSupport;
import org.mods.gd656conquest.client.render.CaptureDiamondRenderer;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.lwjgl.glfw.GLFW;

import java.util.*;

final class ClientCaptureWorldIconRenderer {
    private final ClientCaptureDebugIconSupport debugSupport = new ClientCaptureDebugIconSupport();

    void renderWorldAnchoredCaptureIcons(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        Minecraft minecraft,
        TeamToken selfTeam,
        float partialTick,
        float deltaSeconds
    ) {
        boolean deployPhaseActive = ClientDeathFlowState.shouldRenderDeployBoundaries();
        boolean deployWorldIconsVisible = !deployPhaseActive || ClientDeathFlowState.isDeployOpenPhase();
        double bestHoverDistance = Double.MAX_VALUE;
        double bestLabelDistance = Double.MAX_VALUE;
        HoverLabel hoverLabel = null;
        MultiBufferSource.BufferSource textBufferSource = minecraft.renderBuffers().bufferSource();
        Set<String> dynamicIconSeen = new HashSet<>();
        for (ClientCaptureOverlayModel.WorldIconRenderState renderState : state.worldIconRenderStates.values()) {
            renderState.seenThisFrame = false;
        }
        ClientCaptureOverlayModel.SelectedCrosshairRenderState selectedCrosshairState = state.selectedCrosshairState;
        if (selectedCrosshairState != null) {
            selectedCrosshairState.seenThisFrame = false;
        }
        ScreenProjection selectedProjection = null;
        String selectedRenderId = state.selectedDeployIconId;
        for (CaptureIconEntry entry : state.cachedCaptureIcons) {
            ScreenProjection projection = projectToScreen(state, minecraft, entry);
            if (projection == null) {
                continue;
            }
            if (entry.isTeammateMarker() || entry.isVehicleMarker()) {
                dynamicIconSeen.add(entry.renderId());
                projection = smoothDynamicProjection(state, entry.renderId(), projection, deltaSeconds);
            }
            float deployIconAlpha = resolveDeployVisualAlpha(entry, deployPhaseActive, deployWorldIconsVisible);
            boolean deployable = entry.isDeployableFor(selfTeam);
            if (deployIconAlpha <= 0.01F) {
                deployable = false;
            }
            ClientCaptureOverlayModel.WorldIconRenderState renderState = state.worldIconRenderStates.get(entry.renderId());
            if (renderState == null) {
                renderState = new ClientCaptureOverlayModel.WorldIconRenderState(entry, projection.screenX(), projection.screenY(), 0.0F);
                state.worldIconRenderStates.put(entry.renderId(), renderState);
            }
            renderState.entry = entry;
            renderState.screenX = projection.screenX();
            renderState.screenY = projection.screenY();
            renderState.seenThisFrame = true;
            float crosshairWeightedAlpha = deployPhaseActive ? 1.0F : resolveCrosshairWeightedIconAlpha(gui, projection);
            float weightedAlpha = crosshairWeightedAlpha * deployIconAlpha;
            renderState.alpha = approachAlpha(renderState.alpha, weightedAlpha, deltaSeconds);
            if (renderState.alpha <= 0.01F) {
                continue;
            }
            ScreenProjection stableProjection = new ScreenProjection(renderState.screenX, renderState.screenY);
            double hoverDistance = resolveMouseDistance(minecraft, stableProjection.screenX(), stableProjection.screenY());
            double hoverRadius = resolveWorldIconHoverRadius(entry);
            boolean hoveredByCursor = deployPhaseActive && hoverDistance <= hoverRadius;
            boolean hovered = hoveredByCursor && deployable;
            boolean selected = entry.renderId().equals(state.selectedDeployIconId);
            if (deployable) {
                state.renderedDeployIcons.add(new RenderedIconBounds(entry, stableProjection.screenX(), stableProjection.screenY(), hoverRadius));
                if (hovered && hoverDistance < bestHoverDistance) {
                    state.hoveredDeployIcon = entry;
                    bestHoverDistance = hoverDistance;
                }
            }
            if (hoveredByCursor) {
                String hoverText = buildHoverText(entry);
                if (hoverText != null && !hoverText.isBlank() && hoverDistance < bestLabelDistance) {
                    bestLabelDistance = hoverDistance;
                    hoverLabel = new HoverLabel(
                        hoverText,
                        stableProjection.screenX(),
                        stableProjection.screenY() + resolveHoverLabelYOffset(entry),
                        renderState.alpha * 0.95F,
                        resolveHoverLabelColor(entry)
                    );
                }
            }
            if (selected) {
                selectedProjection = stableProjection;
                selectedRenderId = entry.renderId();
            }
            renderSingleWorldIcon(state, gui, minecraft, stableProjection, entry, selfTeam, selected, hovered, renderState.alpha, deltaSeconds, textBufferSource);
            if (entry.isDebugMarker()) {
                debugSupport.renderDebugText(gui, minecraft, stableProjection, entry, renderState.alpha, textBufferSource);
            }
        }
        Iterator<Map.Entry<String, ClientCaptureOverlayModel.WorldIconRenderState>> iterator = state.worldIconRenderStates.entrySet().iterator();
        while (iterator.hasNext()) {
            ClientCaptureOverlayModel.WorldIconRenderState renderState = iterator.next().getValue();
            if (renderState.seenThisFrame) {
                continue;
            }
            renderState.alpha = approachAlpha(renderState.alpha, 0.0F, deltaSeconds);
            if (renderState.alpha <= 0.01F || renderState.entry == null) {
                iterator.remove();
                continue;
            }
            ScreenProjection fadingProjection = new ScreenProjection(renderState.screenX, renderState.screenY);
            renderSingleWorldIcon(
                state,
                gui,
                minecraft,
                fadingProjection,
                renderState.entry,
                selfTeam,
                false,
                false,
                renderState.alpha,
                deltaSeconds,
                textBufferSource
            );
            if (renderState.entry.isDebugMarker()) {
                debugSupport.renderDebugText(gui, minecraft, fadingProjection, renderState.entry, renderState.alpha, textBufferSource);
            }
        }
        updateSelectedCrosshairState(state, selectedRenderId, selectedProjection, deltaSeconds);
        renderMouseDeployCrosshair(state, gui, minecraft, deltaSeconds, deployPhaseActive);
        renderSelectedDeployCrosshairs(state, gui, deltaSeconds);
        if (hoverLabel != null) {
            CaptureDiamondRenderer.drawCenteredHudText(
                gui,
                minecraft.font,
                hoverLabel.text(),
                (float) hoverLabel.centerX(),
                (float) hoverLabel.topY(),
                hoverLabel.rgb(),
                hoverLabel.alpha(),
                true,
                0.56F,
                textBufferSource
            );
        }
        textBufferSource.endBatch();
        state.teammateIconScreenStates.keySet().retainAll(dynamicIconSeen);
    }

    void renderPersistentSelectedDeployCrosshairs(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        float deltaSeconds
    ) {
        renderSelectedDeployCrosshairs(state, gui, deltaSeconds);
    }

    private float resolveDeployVisualAlpha(CaptureIconEntry entry, boolean deployPhaseActive, boolean deployWorldIconsVisible) {
        if (!deployPhaseActive) {
            if (entry.isBeaconMarker() || entry.isTeammateMarker() || entry.isVehicleMarker()) {
                return 0.0F;
            }
            return 1.0F;
        }
        if (entry.isDebugMarker()) {
            return 1.0F;
        }
        if (!deployWorldIconsVisible) {
            return 0.0F;
        }
        return ClientDeathFlowState.currentDeployIconAlpha();
    }

    static double resolveMouseDistance(Minecraft minecraft, double screenX, double screenY) {
        double mouseGuiX = minecraft.mouseHandler.xpos()
            * minecraft.getWindow().getGuiScaledWidth()
            / Math.max(minecraft.getWindow().getScreenWidth(), 1);
        double mouseGuiY = minecraft.mouseHandler.ypos()
            * minecraft.getWindow().getGuiScaledHeight()
            / Math.max(minecraft.getWindow().getScreenHeight(), 1);
        double dx = screenX - mouseGuiX;
        double dy = screenY - mouseGuiY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void renderSingleWorldIcon(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        Minecraft minecraft,
        ScreenProjection projection,
        CaptureIconEntry entry,
        TeamToken selfTeam,
        boolean selected,
        boolean hovered,
        float weightedAlpha,
        float deltaSeconds,
        MultiBufferSource.BufferSource textBufferSource
    ) {
        if (entry.isDebugMarker()) {
            CaptureDiamondRenderer.drawCaptureIconInHud(
                gui,
                minecraft.font,
                debugSupport.resolveDebugMarkerGlyph(entry),
                true,
                (float) projection.screenX(),
                (float) projection.screenY(),
                ClientCaptureOverlayModel.CAPTURE_ICON_RADIUS,
                100.0F,
                weightedAlpha,
                debugSupport.resolveDebugOwnerToken(entry),
                "NEUTRAL",
                ClientCaptureOverlayModel.WORLD_LABEL_SCALE,
                0.5F,
                false,
                false,
                textBufferSource
            );
            return;
        }
        if (entry.isTeammateMarker()) {
            drawOrientedTeammateIcon(state, gui, minecraft, projection, entry, selected, hovered, weightedAlpha, deltaSeconds, textBufferSource);
            return;
        }
        if (entry.isVehicleMarker()) {
            drawVehicleDeployIcon(gui, minecraft, projection, entry, selected, hovered, weightedAlpha, textBufferSource);
            return;
        }
        if (entry.isBeaconMarker() && selfTeam != TeamToken.NEUTRAL && entry.owner() == selfTeam) {
            drawHighlightedBeaconIcon(gui, minecraft, projection, selected, hovered, weightedAlpha, textBufferSource);
            return;
        }
        CaptureDiamondRenderer.drawCaptureIconInHud(
            gui,
            minecraft.font,
            entry.kind().iconGlyph(entry.code()),
            entry.kind().useCompactGlyph(),
            (float) projection.screenX(),
            (float) projection.screenY(),
            entry.kind().iconRadius(),
            resolveDisplayProgress(state, entry, selfTeam),
            weightedAlpha,
            resolveOwnerPerspectiveToken(entry, selfTeam),
            resolveDrivingPerspectiveToken(entry, selfTeam),
            ClientCaptureOverlayModel.WORLD_LABEL_SCALE,
            0.5F,
            selected,
            hovered,
            textBufferSource
        );
    }

    private ScreenProjection projectToScreen(ClientCaptureOverlayState state, Minecraft minecraft, CaptureIconEntry entry) {
        if (minecraft.player == null || state.worldProjectionMatrix == null) {
            return null;
        }
        Vec3 worldAnchor = resolveWorldIconAnchor(minecraft, state, entry);
        double edgePadding = entry.kind().iconRadius() + 2.0D;
        ClientWorldHudProjectionSupport.ProjectedHudPoint projected =
            ClientWorldHudProjectionSupport.projectToHud(minecraft, state.worldProjectionMatrix, worldAnchor, edgePadding);
        if (projected == null) {
            return null;
        }
        if (!projected.insideViewport() && !shouldKeepIconPinnedToScreenEdge(entry)) {
            return null;
        }
        return new ScreenProjection(projected.screenX(), projected.screenY());
    }

    private boolean shouldKeepIconPinnedToScreenEdge(CaptureIconEntry entry) {
        return entry != null && entry.kind() == ClientCaptureOverlayModel.IconKind.CAPTURE;
    }

    private Vec3 resolveWorldIconAnchor(Minecraft minecraft, ClientCaptureOverlayState state, CaptureIconEntry entry) {
        float partialTick = state.worldProjectionPartialTick;
        if (entry.isTeammateMarker()) {
            Player tracked = resolveTrackedTeammatePlayer(minecraft, entry);
            if (tracked != null) {
                Vec3 pos = tracked.getPosition(partialTick);
                return pos.add(0.0D, tracked.getBbHeight() + 0.55D, 0.0D);
            }
        }
        return new Vec3(entry.x(), entry.y(), entry.z());
    }

    private Player resolveTrackedTeammatePlayer(Minecraft minecraft, CaptureIconEntry entry) {
        if (minecraft.level == null || !entry.isTeammateMarker()) {
            return null;
        }
        UUID teammateId = null;
        try {
            teammateId = UUID.fromString(entry.code());
        } catch (IllegalArgumentException ignored) {
        }
        String noteName = entry.note() == null ? "" : entry.note().trim();
        for (Player player : minecraft.level.players()) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (teammateId != null && teammateId.equals(player.getUUID())) {
                return player;
            }
            if (!noteName.isBlank() && noteName.equalsIgnoreCase(player.getGameProfile().getName())) {
                return player;
            }
        }
        return null;
    }

    private String buildHoverText(CaptureIconEntry entry) {
        if (entry.isTeammateMarker()) {
            String name = entry.note() == null ? "" : entry.note().trim();
            return name.isBlank() ? "\u961f\u53cb" : name;
        }
        if (entry.isBeaconMarker()) {
            String ownerName = entry.note() == null ? "" : entry.note().trim();
            return ownerName.isBlank() ? "\u5c0f\u961f\u4fe1\u6807" : ownerName;
        }
        if (entry.isVehicleMarker()) {
            String name = entry.note() == null ? "" : entry.note().trim();
            String state = entry.vehicleState() == null ? "" : entry.vehicleState().label();
            if (name.isBlank()) {
                return state.isBlank() ? "\u8f7d\u5177" : "\u8f7d\u5177 · " + state;
            }
            return state.isBlank() ? name : name + " · " + state;
        }
        if (entry.isSpawnPoint()) {
            String spawnName = entry.note() == null ? "" : entry.note().trim();
            return spawnName.isBlank() ? "\u90e8\u7f72\u70b9" : spawnName;
        }
        if (!entry.isDebugMarker()) {
            String captureName = entry.note() == null ? "" : entry.note().trim();
            if (!captureName.isBlank() && I18n.exists(captureName)) {
                return I18n.get(captureName);
            }
            if (!captureName.isBlank()) {
                return captureName;
            }
            return entry.code() == null || entry.code().isBlank() ? "\u636e\u70b9" : entry.code();
        }
        return "";
    }

    private double resolveWorldIconHoverRadius(CaptureIconEntry entry) {
        return entry.kind().iconRadius() + ClientCaptureOverlayModel.WORLD_ICON_HOVER_PADDING_PIXELS;
    }

    private double resolveHoverLabelYOffset(CaptureIconEntry entry) {
        return entry.kind().iconRadius() + 1.0F;
    }

    private int resolveHoverLabelColor(CaptureIconEntry entry) {
        if (entry.isVehicleMarker()) {
            return resolveVehicleLabelRgb(entry);
        }
        return entry.isTeammateMarker() && entry.teammateCombat() ? 0x00FF8E8E : 0x00E6EEF8;
    }

    private float resolveDisplayProgress(ClientCaptureOverlayState state, CaptureIconEntry entry, TeamToken selfTeam) {
        float progress = resolveProgress(state, entry);
        if (selfTeam == TeamToken.CAMP_A) {
            return 100.0F - progress;
        }
        return progress;
    }

    private float resolveProgress(ClientCaptureOverlayState state, CaptureIconEntry entry) {
        ClientCaptureOverlayModel.ProgressInterpolationState interpolationState = state.progressInterpolation.get(entry.renderId());
        return interpolationState == null ? Mth.clamp(entry.progress(), 0.0F, 100.0F) : interpolationState.current();
    }

    private ScreenProjection smoothDynamicProjection(
        ClientCaptureOverlayState state,
        String renderId,
        ScreenProjection target,
        float deltaSeconds
    ) {
        TeammateIconScreenState iconState = state.teammateIconScreenStates.get(renderId);
        if (iconState == null) {
            state.teammateIconScreenStates.put(renderId, TeammateIconScreenState.create(target.screenX(), target.screenY(), 0.0F));
            return target;
        }
        if (Math.abs(iconState.targetX - target.screenX()) > 0.01D || Math.abs(iconState.targetY - target.screenY()) > 0.01D) {
            iconState.startX = iconState.screenX;
            iconState.startY = iconState.screenY;
            iconState.targetX = target.screenX();
            iconState.targetY = target.screenY();
            iconState.positionElapsed = 0.0F;
        }
        iconState.positionElapsed = Math.min(
            ClientCaptureOverlayModel.TEAMMATE_ICON_INTERP_SECONDS,
            iconState.positionElapsed + Math.max(0.0F, deltaSeconds)
        );
        float t = ClientCaptureOverlayModel.TEAMMATE_ICON_INTERP_SECONDS <= 1.0E-6F
            ? 1.0F
            : Mth.clamp(iconState.positionElapsed / ClientCaptureOverlayModel.TEAMMATE_ICON_INTERP_SECONDS, 0.0F, 1.0F);
        iconState.screenX = Mth.lerp(t, iconState.startX, iconState.targetX);
        iconState.screenY = Mth.lerp(t, iconState.startY, iconState.targetY);
        return new ScreenProjection(iconState.screenX, iconState.screenY);
    }

    private void drawOrientedTeammateIcon(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        Minecraft minecraft,
        ScreenProjection projection,
        CaptureIconEntry entry,
        boolean selected,
        boolean hovered,
        float alpha,
        float deltaSeconds,
        MultiBufferSource.BufferSource textBufferSource
    ) {
        TeammateIconScreenState iconState = state.teammateIconScreenStates.computeIfAbsent(
            entry.renderId(),
            key -> TeammateIconScreenState.create(projection.screenX(), projection.screenY(), entry.progress())
        );
        iconState.yaw = smoothTeammateYaw(iconState, entry.progress(), deltaSeconds);
        gui.pose().pushPose();
        gui.pose().translate((float) projection.screenX(), (float) projection.screenY(), 0.0F);
        gui.pose().mulPose(Axis.ZP.rotationDegrees(-iconState.yaw));
        CaptureDiamondRenderer.drawCaptureIconInHud(
            gui,
            minecraft.font,
            "",
            true,
            0.0F,
            0.0F,
            ClientCaptureOverlayModel.TEAMMATE_ICON_RADIUS,
            100.0F,
            alpha,
            "ALLY",
            entry.teammateCombat() ? "ENEMY" : "NEUTRAL",
            ClientCaptureOverlayModel.WORLD_LABEL_SCALE * 0.72F,
            0.5F,
            selected,
            hovered,
            textBufferSource
        );
        int symbolRgb = entry.teammateCombat() ? 0x00FF6B6B : 0x008FC4FF;
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, "\u25BD", 0.0F, -2.3F, symbolRgb, alpha, true, 0.75F, textBufferSource);
        gui.pose().popPose();
    }

    private void drawHighlightedBeaconIcon(
        GuiGraphics gui,
        Minecraft minecraft,
        ScreenProjection projection,
        boolean selected,
        boolean hovered,
        float alpha,
        MultiBufferSource.BufferSource textBufferSource
    ) {
        CaptureDiamondRenderer.drawCaptureIconInHud(
            gui,
            minecraft.font,
            ClientCaptureOverlayModel.BEACON_ICON_GLYPH,
            true,
            (float) projection.screenX(),
            (float) projection.screenY(),
            ClientCaptureOverlayModel.BEACON_ICON_RADIUS,
            100.0F,
            alpha,
            "ALLY",
            "NEUTRAL",
            ClientCaptureOverlayModel.WORLD_LABEL_SCALE * 0.72F,
            0.5F,
            selected,
            hovered,
            textBufferSource
        );
    }

    private void drawVehicleDeployIcon(
        GuiGraphics gui,
        Minecraft minecraft,
        ScreenProjection projection,
        CaptureIconEntry entry,
        boolean selected,
        boolean hovered,
        float alpha,
        MultiBufferSource.BufferSource textBufferSource
    ) {
        int fillRgb = resolveVehicleFillRgb(entry);
        int accentRgb = resolveVehicleLabelRgb(entry);
        CaptureDiamondRenderer.drawVehicleIconInHud(
            gui,
            minecraft.font,
            entry.kind().iconGlyph(entry.code()),
            (float) projection.screenX(),
            (float) projection.screenY(),
            entry.kind().iconRadius(),
            alpha,
            fillRgb,
            accentRgb,
            accentRgb,
            ClientCaptureOverlayModel.WORLD_LABEL_SCALE,
            selected,
            hovered,
            textBufferSource
        );
    }

    private void updateSelectedCrosshairState(
        ClientCaptureOverlayState state,
        String renderId,
        ScreenProjection projection,
        float deltaSeconds
    ) {
        ClientCaptureOverlayModel.SelectedCrosshairRenderState crosshairState = state.selectedCrosshairState;
        boolean selected = renderId != null && !renderId.isBlank() && projection != null;
        if (crosshairState == null) {
            if (!selected) {
                return;
            }
            crosshairState = new ClientCaptureOverlayModel.SelectedCrosshairRenderState(renderId, projection.screenX(), projection.screenY());
            state.selectedCrosshairState = crosshairState;
        } else if (selected && !crosshairState.renderId.equals(renderId)) {
            crosshairState.renderId = renderId;
        }
        if (selected) {
            crosshairState.screenX = projection.screenX();
            crosshairState.screenY = projection.screenY();
            crosshairState.seenThisFrame = true;
        }
        float targetAlpha = selected ? ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_ALPHA : 0.0F;
        crosshairState.alpha = approachSelectedCrosshairAlpha(crosshairState.alpha, targetAlpha, deltaSeconds);
    }

    private void renderMouseDeployCrosshair(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        Minecraft minecraft,
        float deltaSeconds,
        boolean deployPhaseActive
    ) {
        if (!deployPhaseActive || minecraft == null || minecraft.getWindow() == null) {
            return;
        }
        float deployAlpha = ClientDeathFlowState.currentDeployIconAlpha();
        if (deployAlpha <= 0.01F) {
            return;
        }
        float mouseGuiX = (float) (minecraft.mouseHandler.xpos()
            * gui.guiWidth()
            / Math.max(minecraft.getWindow().getScreenWidth(), 1));
        float mouseGuiY = (float) (minecraft.mouseHandler.ypos()
            * gui.guiHeight()
            / Math.max(minecraft.getWindow().getScreenHeight(), 1));
        boolean pressed = isMousePressed(minecraft);
        boolean hovered = state.hoveredDeployIcon != null;
        float targetGap = pressed
            ? ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_GAP_PRESS_PIXELS
            : hovered
                ? ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_GAP_HOVER_PIXELS
                : ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_GAP_PIXELS;
        float targetFeather = pressed
            ? ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_FEATHER_PRESS_PIXELS
            : hovered
                ? ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_FEATHER_HOVER_PIXELS
                : ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_FEATHER_PIXELS;
        state.mouseCrossGapPixels = approachMouseCrossMetric(state.mouseCrossGapPixels, targetGap, deltaSeconds);
        state.mouseCrossFeatherPixels = approachMouseCrossMetric(state.mouseCrossFeatherPixels, targetFeather, deltaSeconds);
        float alpha = pressed
            ? ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_PRESS_ALPHA
            : hovered
                ? ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_HOVER_ALPHA
                : ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_ALPHA;
        CaptureDiamondRenderer.drawScreenCrosshair(
            gui,
            mouseGuiX,
            mouseGuiY,
            gui.guiWidth(),
            gui.guiHeight(),
            state.mouseCrossGapPixels,
            state.mouseCrossGapPixels,
            state.mouseCrossFeatherPixels,
            state.mouseCrossFeatherPixels,
            ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_LINE_THICKNESS,
            0x00FFFFFF,
            alpha * deployAlpha
        );
    }

    private void renderSelectedDeployCrosshairs(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        float deltaSeconds
    ) {
        ClientCaptureOverlayModel.SelectedCrosshairRenderState renderState = state.selectedCrosshairState;
        if (renderState == null) {
            return;
        }
        float deployAlpha = ClientDeathFlowState.currentDeployIconAlpha();
        long now = System.currentTimeMillis();
        float pulseDuration = Math.max(0.1F, ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_PULSE_SECONDS);
        float pulsePhase = (now % (long) (pulseDuration * 1000.0F)) / (pulseDuration * 1000.0F);
        float pulse = 0.5F + 0.5F * Mth.sin((float) (pulsePhase * Math.PI * 2.0D));
        int pulseRgb = interpolateRgb(0x005EAFFF, 0x00FFFFFF, pulse);
        boolean deployOpenPhase = ClientDeathFlowState.isDeployOpenPhase();
        if (!renderState.seenThisFrame && deployOpenPhase) {
            renderState.alpha = approachSelectedCrosshairAlpha(renderState.alpha, 0.0F, deltaSeconds);
        }
        float renderAlpha = renderState.alpha * deployAlpha;
        if (renderAlpha <= 0.01F) {
            state.selectedCrosshairState = null;
            return;
        }
        CaptureDiamondRenderer.drawScreenCrosshair(
            gui,
            (float) renderState.screenX,
            (float) renderState.screenY,
            gui.guiWidth(),
            gui.guiHeight(),
            ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_GAP_PIXELS,
            ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_GAP_PIXELS,
            ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_FEATHER_PIXELS,
            ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_FEATHER_PIXELS,
            ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_LINE_THICKNESS,
            pulseRgb,
            renderAlpha
        );
    }

    private float smoothTeammateYaw(TeammateIconScreenState iconState, float targetYaw, float deltaSeconds) {
        float delta = Mth.wrapDegrees(targetYaw - iconState.targetYaw);
        if (Math.abs(delta) > 0.1F) {
            iconState.yawStart = iconState.yaw;
            iconState.targetYaw = targetYaw;
            iconState.yawElapsed = 0.0F;
        }
        iconState.yawElapsed = Math.min(
            ClientCaptureOverlayModel.TEAMMATE_ICON_INTERP_SECONDS,
            iconState.yawElapsed + Math.max(0.0F, deltaSeconds)
        );
        float t = ClientCaptureOverlayModel.TEAMMATE_ICON_INTERP_SECONDS <= 1.0E-6F
            ? 1.0F
            : Mth.clamp(iconState.yawElapsed / ClientCaptureOverlayModel.TEAMMATE_ICON_INTERP_SECONDS, 0.0F, 1.0F);
        float diff = Mth.wrapDegrees(iconState.targetYaw - iconState.yawStart);
        return Mth.wrapDegrees(iconState.yawStart + diff * t);
    }

    private float approachAlpha(float current, float target, float deltaSeconds) {
        if (Math.abs(target - current) <= 0.001F) {
            return target;
        }
        float duration = ClientCaptureOverlayModel.WORLD_ICON_VISIBILITY_INTERP_SECONDS;
        if (duration <= 1.0E-6F) {
            return target;
        }
        float step = Mth.clamp(deltaSeconds / duration, 0.0F, 1.0F);
        return Mth.lerp(step, current, target);
    }

    private float approachSelectedCrosshairAlpha(float current, float target, float deltaSeconds) {
        if (Math.abs(target - current) <= 0.001F) {
            return target;
        }
        float duration = ClientCaptureOverlayModel.DEPLOY_SELECTED_CROSS_FADE_SECONDS;
        if (duration <= 1.0E-6F) {
            return target;
        }
        float step = Mth.clamp(deltaSeconds / duration, 0.0F, 1.0F);
        return Mth.lerp(step, current, target);
    }

    private float approachMouseCrossMetric(float current, float target, float deltaSeconds) {
        if (current <= 0.001F) {
            return target;
        }
        float t = 1.0F - (float) Math.exp(-ClientCaptureOverlayModel.DEPLOY_MOUSE_CROSS_ANIMATION_SHARPNESS * Math.max(0.0F, deltaSeconds));
        return Mth.lerp(Mth.clamp(t, 0.0F, 1.0F), current, target);
    }

    private float resolveCrosshairWeightedIconAlpha(GuiGraphics gui, ScreenProjection projection) {
        if (ClientDeathFlowState.hasActiveCameraOverride()) {
            return ClientCaptureOverlayModel.WORLD_ICON_ALPHA_FAR_CROSSHAIR;
        }
        return ClientCrosshairFadeSupport.resolveWeightedAlpha(
            gui.guiWidth(),
            gui.guiHeight(),
            projection.screenX(),
            projection.screenY(),
            ClientCaptureOverlayModel.WORLD_ICON_ALPHA_NEAR_CROSSHAIR,
            ClientCaptureOverlayModel.WORLD_ICON_ALPHA_FAR_CROSSHAIR,
            ClientCaptureOverlayModel.WORLD_ICON_CROSSHAIR_ALPHA_RADIUS_PIXELS,
            ClientCaptureOverlayModel.WORLD_ICON_CROSSHAIR_ALPHA_CURVE
        );
    }

    private String toPerspectiveToken(TeamToken token, TeamToken selfTeam) {
        if (token == TeamToken.NEUTRAL || selfTeam == TeamToken.NEUTRAL) {
            return "NEUTRAL";
        }
        return token == selfTeam ? "ALLY" : "ENEMY";
    }

    private String resolveOwnerPerspectiveToken(CaptureIconEntry entry, TeamToken selfTeam) {
        if (entry != null && entry.isStageObjectiveProxy()) {
            return "ALLY_STAGE_PROXY";
        }
        return toPerspectiveToken(entry == null ? TeamToken.NEUTRAL : entry.owner(), selfTeam);
    }

    private String resolveDrivingPerspectiveToken(CaptureIconEntry entry, TeamToken selfTeam) {
        if (entry != null && entry.isStageObjectiveProxy()) {
            return "ALLY";
        }
        return toPerspectiveToken(entry == null ? TeamToken.NEUTRAL : entry.driving(), selfTeam);
    }

    private int resolveVehicleFillRgb(CaptureIconEntry entry) {
        VehicleHudState vehicleState = entry == null || entry.vehicleState() == null
            ? VehicleHudState.READY
            : entry.vehicleState();
        return switch (vehicleState) {
            case READY, EMPTY -> 0x00747C84;
            case SQUAD -> 0x002C8A56;
            case TEAM, FULL -> 0x002E5CAA;
            case WAITING -> 0x00424952;
            case ENEMY -> 0x00823838;
        };
    }

    private int resolveVehicleLabelRgb(CaptureIconEntry entry) {
        if (entry != null && entry.vehicleLowHealth()) {
            return 0x00FFE36A;
        }
        VehicleHudState vehicleState = entry == null || entry.vehicleState() == null
            ? VehicleHudState.READY
            : entry.vehicleState();
        return switch (vehicleState) {
            case FULL, ENEMY -> 0x00FF8C8C;
            case SQUAD -> 0x008FE0A2;
            case TEAM -> 0x009BC2FF;
            case WAITING -> 0x00C9D0D8;
            case READY, EMPTY -> 0x00E6EEF8;
        };
    }

    private int interpolateRgb(int fromRgb, int toRgb, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        int red = Mth.floor(Mth.lerp(clamped, (fromRgb >>> 16) & 0xFF, (toRgb >>> 16) & 0xFF));
        int green = Mth.floor(Mth.lerp(clamped, (fromRgb >>> 8) & 0xFF, (toRgb >>> 8) & 0xFF));
        int blue = Mth.floor(Mth.lerp(clamped, fromRgb & 0xFF, toRgb & 0xFF));
        return (red << 16) | (green << 8) | blue;
    }

    private boolean isMousePressed(Minecraft minecraft) {
        if (minecraft == null || minecraft.getWindow() == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    }
}
