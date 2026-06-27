package org.mods.gd656conquest.client.loadout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
        if (minecraft != null && minecraft.screen instanceof LoadoutConfigScreen) {
            minecraft.setScreen(null);
        }
        ClientLoadoutUiState.clear();
    }

    public void render(GuiGraphics guiGraphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (ClientLoadoutUiState.consumeOpenPanelRequest()) {
            openConfigScreenFromServerRequest();
        }
    }

    public void openConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.player != null && minecraft.level != null) {
            requestSnapshot(false);
        }
        if (!(minecraft.screen instanceof LoadoutConfigScreen)) {
            minecraft.setScreen(new LoadoutConfigScreen(minecraft.screen));
        }
    }

    private void openConfigScreenFromServerRequest() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (isLoadoutSubScreen(minecraft.screen)) {
            return;
        }
        openConfigScreen();
    }

    public void toggleConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.screen instanceof LoadoutConfigScreen loadoutConfigScreen) {
            loadoutConfigScreen.onClose();
            return;
        }
        openConfigScreen();
    }

    private void requestSnapshot(boolean openPanel) {
        NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(openPanel));
    }

    private boolean isLoadoutSubScreen(Screen screen) {
        return screen instanceof LoadoutVehicleDeployScreen
            || screen instanceof LoadoutItemEditorScreen
            || screen instanceof LoadoutRoomEditorScreen
            || screen instanceof LoadoutVehicleAdminEditorScreen;
    }
}
