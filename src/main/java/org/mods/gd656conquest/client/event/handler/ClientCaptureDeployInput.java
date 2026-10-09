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
import org.mods.gd656conquest.network.packet.RoomDeathmatchDeployViewRequestPacket;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClientCaptureDeployInput {
    private static final long DEPLOY_CONFIRM_DOUBLE_PRESS_MILLIS = 280L;
    private static final long MOUSE_PRESS_DEBOUNCE_MILLIS = 35L;

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
        if (state.lastHandledMousePressMillis > 0L && now - state.lastHandledMousePressMillis < MOUSE_PRESS_DEBOUNCE_MILLIS) {
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
            && now - previousMousePressMillis <= DEPLOY_CONFIRM_DOUBLE_PRESS_MILLIS;
        state.lastClickedDeployIconId = currentId;
        state.lastHandledMousePressMillis = now;
        if (doubleClick && ClientDeathFlowState.canConfirmDeploySelection()) {
            sendDeployConfirm(clickedIcon);
        }
    }

    boolean handleKeyInput(ClientCaptureOverlayState state, InputEvent.Key event, TeamToken selfTeam) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || event == null) {
            return false;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return false;
        }
        
        boolean isSpace = event.getKey() == GLFW.GLFW_KEY_SPACE;
        boolean isCycle = org.mods.gd656conquest.client.input.ClientKeyMappings.CYCLE_DEPLOY_TARGET.matches(event.getKey(), event.getScanCode());
        
        if (!isSpace && !isCycle) {
            return false;
        }

        if (isSpace && ClientDeathFlowState.isRespawnHoldActive() && !ClientRoomLockState.isDeployUiActive() && minecraft.screen == null) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            if (minecraft.player != null) {
                minecraft.player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
            NetworkManager.getChannel().sendToServer(new RoomDeathmatchDeployViewRequestPacket());
            return true;
        }
        
        if (!ClientRoomLockState.shouldReleaseMouseForUi()
            || !ClientRoomLockState.isDeployUiActive()
            || ClientRoomLockState.isEndingState()
            || minecraft.screen != null) {
            return false;
        }
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        long now = System.currentTimeMillis();
        
        if (isSpace) {
            CaptureIconEntry selectedIcon = resolveSelectedDeployIcon(state, selfTeam);
            boolean doublePressConfirm = selectedIcon != null
                && selectedIcon.renderId().equals(state.lastKeyboardSelectedDeployIconId)
                && state.lastHandledKeyboardPressMillis > 0L
                && now - state.lastHandledKeyboardPressMillis <= DEPLOY_CONFIRM_DOUBLE_PRESS_MILLIS;
            if (doublePressConfirm) {
                if (ClientDeathFlowState.canConfirmDeploySelection()) {
                    sendDeployConfirm(selectedIcon);
                }
                state.lastHandledKeyboardPressMillis = now;
                state.lastKeyboardSelectedDeployIconId = selectedIcon.renderId();
                return true;
            }
            if (selectedIcon != null) {
                state.lastHandledKeyboardPressMillis = now;
                state.lastKeyboardSelectedDeployIconId = selectedIcon.renderId();
            } else {
                state.lastHandledKeyboardPressMillis = -1L;
                state.lastKeyboardSelectedDeployIconId = "";
            }
            return true;
        }
        
        if (isCycle) {
            CaptureIconEntry nextIcon = resolveNextDeployIcon(state, minecraft, selfTeam);
            if (nextIcon == null) {
                return true;
            }
            state.selectedDeployIconId = nextIcon.renderId();
            sendDeploySelection(nextIcon);
            return true;
        }
        
        return true;
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

    private CaptureIconEntry resolveSelectedDeployIcon(ClientCaptureOverlayState state, TeamToken selfTeam) {
        String selectedId = state.selectedDeployIconId == null ? "" : state.selectedDeployIconId.trim();
        if (selectedId.isBlank()) {
            return null;
        }
        for (RenderedIconBounds bounds : state.renderedDeployIcons) {
            CaptureIconEntry entry = bounds == null ? null : bounds.entry();
            if (entry == null || !entry.isDeployableFor(selfTeam)) {
                continue;
            }
            if (selectedId.equals(entry.renderId())) {
                return entry;
            }
        }
        return null;
    }

    private CaptureIconEntry resolveNextDeployIcon(ClientCaptureOverlayState state, Minecraft minecraft, TeamToken selfTeam) {
        List<RenderedIconBounds> orderedIcons = collectOrderedDeployIcons(state, minecraft, selfTeam);
        if (orderedIcons.isEmpty()) {
            return null;
        }
        String selectedId = state.selectedDeployIconId == null ? "" : state.selectedDeployIconId.trim();
        if (selectedId.isBlank()) {
            return orderedIcons.get(0).entry();
        }
        for (int index = 0; index < orderedIcons.size(); index++) {
            CaptureIconEntry entry = orderedIcons.get(index).entry();
            if (entry == null || !selectedId.equals(entry.renderId())) {
                continue;
            }
            return orderedIcons.get((index + 1) % orderedIcons.size()).entry();
        }
        return orderedIcons.get(0).entry();
    }

    private List<RenderedIconBounds> collectOrderedDeployIcons(ClientCaptureOverlayState state, Minecraft minecraft, TeamToken selfTeam) {
        List<RenderedIconBounds> orderedIcons = new ArrayList<>();
        Set<String> seenRenderIds = new HashSet<>();
        for (RenderedIconBounds bounds : state.renderedDeployIcons) {
            CaptureIconEntry entry = bounds == null ? null : bounds.entry();
            if (entry == null || !entry.isDeployableFor(selfTeam)) {
                continue;
            }
            String renderId = entry.renderId();
            if (renderId == null || renderId.isBlank() || !seenRenderIds.add(renderId)) {
                continue;
            }
            orderedIcons.add(bounds);
        }
        orderedIcons.sort(
            Comparator
                .<RenderedIconBounds>comparingDouble(bounds -> resolveCrosshairDistanceSquared(bounds, minecraft))
                .thenComparing(bounds -> bounds.entry().renderId(), String.CASE_INSENSITIVE_ORDER)
        );
        return orderedIcons;
    }

    private double resolveCrosshairDistanceSquared(RenderedIconBounds bounds, Minecraft minecraft) {
        if (bounds == null || minecraft == null || minecraft.getWindow() == null) {
            return Double.MAX_VALUE;
        }
        double centerX = minecraft.getWindow().getGuiScaledWidth() * 0.5D;
        double centerY = minecraft.getWindow().getGuiScaledHeight() * 0.5D;
        double dx = bounds.screenX() - centerX;
        double dy = bounds.screenY() - centerY;
        return dx * dx + dy * dy;
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
