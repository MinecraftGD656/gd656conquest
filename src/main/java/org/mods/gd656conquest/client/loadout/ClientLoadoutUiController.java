package org.mods.gd656conquest.client.loadout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutAttachmentEditorScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutItemEditorScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutRoomEditorScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutVehicleAdminEditorScreen;
import org.mods.gd656conquest.client.loadout.screen.LoadoutVehicleDeployScreen;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;

public final class ClientLoadoutUiController {
    public ClientLoadoutUiController() {}

    public void onClientLogout() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && isLoadoutScreen(minecraft.screen)) {
            minecraft.setScreen(null);
        }
        ClientLoadoutUiState.clear();
        ClientRoomEditorState.clear();
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (ClientRoomEditorState.consumeOpenPanelRequest()) {
            var snapshot = ClientRoomEditorState.snapshot();
            if (snapshot != null && snapshot.admin()) {
                if (minecraft.screen instanceof LoadoutConfigScreen screen) {
                    screen.openRoomEditorTab();
                } else {
                    Screen parent = minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen ? null : minecraft.screen;
                    minecraft.setScreen(new LoadoutConfigScreen(parent, LoadoutConfigScreen.ROOM_EDITOR_TAB_INDEX));
                }
            }
            return;
        }
        // Administrator room editing is independent of the combat/deployment UI lock.
        if (org.mods.gd656conquest.client.room.ClientRoomLockState.requiresCombatFreeze()) return;
        if (ClientLoadoutUiState.consumeOpenPanelRequest()) {
            openConfigScreenFromServerRequest();
        }
    }

    public void requestOpenConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.screen instanceof LoadoutConfigScreen loadoutConfigScreen) {
            loadoutConfigScreen.onClose();
            return;
        }
        requestSnapshot(true);
    }

    private void openConfigScreenFromServerRequest() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        int targetTabIndex = ClientLoadoutUiState.consumeOpenTabIndex();
        if (isLoadoutScreen(minecraft.screen)) {
            return;
        }
        minecraft.setScreen(new LoadoutConfigScreen(minecraft.screen, targetTabIndex));
    }

    public void toggleConfigScreen() {
        requestOpenConfigScreen();
    }

    private void requestSnapshot(boolean openPanel) {
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(openPanel));
    }

    private boolean isLoadoutScreen(Screen screen) {
        return screen instanceof LoadoutConfigScreen
            || screen instanceof LoadoutVehicleDeployScreen
            || screen instanceof LoadoutAttachmentEditorScreen
            || screen instanceof LoadoutItemEditorScreen
            || screen instanceof LoadoutRoomEditorScreen
            || screen instanceof LoadoutVehicleAdminEditorScreen
            || screen instanceof org.mods.gd656conquest.client.loadout.screen.BriefingTimelineScreen;
    }
}
