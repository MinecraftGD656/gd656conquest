package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;

final class ClientCaptureIconRenderer {
    private final ClientCaptureWorldIconRenderer worldIconRenderer = new ClientCaptureWorldIconRenderer();
    private final ClientCaptureHudQueueRenderer hudQueueRenderer = new ClientCaptureHudQueueRenderer();

    void renderCaptureIconHudOverlay(
        ClientCaptureOverlayState state,
        RenderGuiOverlayEvent.Post event,
        TeamToken selfTeam,
        float deltaSeconds
    ) {
        renderCaptureIconHudOverlay(state, event.getGuiGraphics(), selfTeam, deltaSeconds);
    }

    void renderCaptureIconHudOverlay(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        TeamToken selfTeam,
        float deltaSeconds
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        state.hoveredDeployIcon = null;
        state.renderedDeployIcons.clear();
        float partialTick = minecraft.getFrameTime();
        worldIconRenderer.renderWorldAnchoredCaptureIcons(state, gui, minecraft, selfTeam, partialTick, deltaSeconds);
        hudQueueRenderer.renderHudCaptureQueue(state, gui, minecraft, deltaSeconds, selfTeam);
    }

    void renderHudQueueOnly(
        ClientCaptureOverlayState state,
        RenderGuiOverlayEvent.Post event,
        TeamToken selfTeam,
        float deltaSeconds
    ) {
        renderHudQueueOnly(state, event.getGuiGraphics(), selfTeam, deltaSeconds);
    }

    void renderHudQueueOnly(
        ClientCaptureOverlayState state,
        GuiGraphics gui,
        TeamToken selfTeam,
        float deltaSeconds
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        state.hoveredDeployIcon = null;
        state.renderedDeployIcons.clear();
        worldIconRenderer.renderPersistentSelectedDeployCrosshairs(state, gui, deltaSeconds);
        hudQueueRenderer.renderHudCaptureQueue(state, gui, minecraft, deltaSeconds, selfTeam);
    }

    static double resolveMouseDistance(Minecraft minecraft, double screenX, double screenY) {
        return ClientCaptureWorldIconRenderer.resolveMouseDistance(minecraft, screenX, screenY);
    }
}
