package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.CaptureIconEntry;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.DeployTargetAction;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.DeployTargetType;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.RenderedIconBounds;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.TeamToken;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;

final class ClientCaptureDeployInput {
    void handleMouseInput(ClientCaptureOverlayState state, InputEvent.MouseButton event, TeamToken selfTeam) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (!ClientRoomLockState.shouldReleaseMouseForUi()) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (state.lastHandledMousePressMillis > 0L && now - state.lastHandledMousePressMillis < 35L) {
            return;
        }
        long previousMousePressMillis = state.lastHandledMousePressMillis;
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        CaptureIconEntry clickedIcon = resolveHoveredDeployIconFromCursor(state, minecraft);
        if (clickedIcon == null) {
            clickedIcon = state.hoveredDeployIcon;
        }
        if (clickedIcon == null || !clickedIcon.isDeployableFor(selfTeam)) {
            return;
        }
        String currentId = clickedIcon.renderId();
        state.selectedDeployIconId = currentId;
        sendDeploySelection(clickedIcon);
        boolean doubleClick = currentId.equals(state.lastClickedDeployIconId)
            && previousMousePressMillis > 0L
            && now - previousMousePressMillis <= 280L;
        state.lastClickedDeployIconId = currentId;
        state.lastHandledMousePressMillis = now;
        if (doubleClick && ClientDeathFlowState.canConfirmDeploySelection()) {
            sendDeployConfirm(clickedIcon);
        }
    }

    private CaptureIconEntry resolveHoveredDeployIconFromCursor(ClientCaptureOverlayState state, Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || state.renderedDeployIcons.isEmpty()) {
            return null;
        }
        double bestDistance = Double.MAX_VALUE;
        CaptureIconEntry best = null;
        for (RenderedIconBounds bounds : state.renderedDeployIcons) {
            double distance = ClientCaptureIconRenderer.resolveMouseDistance(minecraft, bounds.screenX(), bounds.screenY());
            if (distance <= bounds.hoverRadius() && distance < bestDistance) {
                bestDistance = distance;
                best = bounds.entry();
            }
        }
        return best;
    }

    private void sendDeploySelection(CaptureIconEntry clickedIcon) {
        sendDeployPacket(clickedIcon == null ? null : clickedIcon.resolveDeployTarget(), false);
    }

    private void sendDeployConfirm(CaptureIconEntry clickedIcon) {
        sendDeployPacket(clickedIcon == null ? null : clickedIcon.resolveDeployTarget(), true);
    }

    private void sendDeployPacket(DeployTargetAction action, boolean confirm) {
        if (action == null || action.targetId() == null || action.targetId().isBlank()) {
            return;
        }
        switch (action.type()) {
            case TEAMMATE -> NetworkManager.getChannel().sendToServer(
                confirm ? RoomDeploySelectPacket.confirmTeammate(action.targetId()) : RoomDeploySelectPacket.selectTeammate(action.targetId())
            );
            case BEACON -> NetworkManager.getChannel().sendToServer(
                confirm ? RoomDeploySelectPacket.confirmBeacon(action.targetId()) : RoomDeploySelectPacket.selectBeacon(action.targetId())
            );
            case TEAM -> NetworkManager.getChannel().sendToServer(
                confirm ? RoomDeploySelectPacket.confirmTeam(action.targetId()) : RoomDeploySelectPacket.selectTeam(action.targetId())
            );
            case CAPTURE -> NetworkManager.getChannel().sendToServer(
                confirm ? RoomDeploySelectPacket.confirmCapture(action.targetId()) : RoomDeploySelectPacket.selectCapture(action.targetId())
            );
            case VEHICLE -> NetworkManager.getChannel().sendToServer(
                confirm ? RoomDeploySelectPacket.confirmVehicle(action.targetId()) : RoomDeploySelectPacket.selectVehicle(action.targetId())
            );
        }
    }
}
