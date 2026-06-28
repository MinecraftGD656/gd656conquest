package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutVehicleDeployScreen;

final class ClientCaptureOverlayRuntime {
    private final ClientCaptureOverlayState state = new ClientCaptureOverlayState();
    private final ClientCaptureOverlayCodec codec = new ClientCaptureOverlayCodec();
    private final ClientCaptureBoundaryRenderer boundaryRenderer = new ClientCaptureBoundaryRenderer();
    private final ClientCaptureDeployInput deployInput = new ClientCaptureDeployInput();

    ClientCaptureOverlayState state() {
        return state;
    }

    ClientCaptureBoundaryRenderer boundaryRenderer() {
        return boundaryRenderer;
    }

    boolean isDeployMouseScreen(Screen screen) {
        return screen instanceof LoadoutConfigScreen || screen instanceof LoadoutVehicleDeployScreen;
    }

    boolean shouldRenderCaptureLinesForStage(RenderLevelStageEvent.Stage stage) {
        return codec.shouldRenderCaptureLinesForStage(stage);
    }

    void onDeployUiOpened() {
        state.prepareForDeployUiOpen();
    }

    void onDeployUiClosed() {
        state.handleDeployUiClosed();
    }

    void handleClientLogout() {
        state.reset();
    }

    boolean ensureWorldReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            state.reset();
            return false;
        }
        return true;
    }

    void captureWorldProjectionState(RenderLevelStageEvent event) {
        boundaryRenderer.captureWorldProjectionState(state, event);
    }

    void syncOverlayState() {
        codec.syncCaptureOverlayState(state);
    }

    float resolveCaptureRenderDeltaSeconds() {
        return codec.resolveCaptureRenderDeltaSeconds(state);
    }

    void updateInterpolatedProgress(float deltaSeconds) {
        codec.updateInterpolatedProgress(state, deltaSeconds);
    }

    boolean hasCaptureIcons() {
        return !state.cachedCaptureIcons.isEmpty();
    }

    void clearDeploySelection() {
        state.clearDeploySelection();
    }

    void renderCaptureBoundaryLines(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        syncOverlayState();
        if (!state.adminDebugOverlayActive) {
            return;
        }
        boundaryRenderer.renderCaptureBoundaryLines(state, event, resolveEffectiveSelfTeam(), false);
    }

    void handleMouseInput(InputEvent.MouseButton event) {
        deployInput.handleMouseInput(state, event, resolveSelfTeamToken());
    }

    ClientCaptureOverlayModel.TeamToken resolveSelfTeamToken() {
        return codec.resolveSelfTeamToken();
    }

    ClientCaptureOverlayModel.TeamToken resolveEffectiveSelfTeam() {
        ClientCaptureOverlayModel.TeamToken selfTeam = resolveSelfTeamToken();
        if (state.adminDebugOverlayActive && selfTeam == ClientCaptureOverlayModel.TeamToken.NEUTRAL) {
            return ClientCaptureOverlayModel.TeamToken.CAMP_A;
        }
        return selfTeam;
    }
}
