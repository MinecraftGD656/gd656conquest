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
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
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
        if (isLoadoutScreen(minecraft.screen)) {
            return;
        }
        int targetTabIndex = ClientLoadoutUiState.consumeOpenTabIndex();
        minecraft.setScreen(new LoadoutConfigScreen(minecraft.screen, targetTabIndex));
    }

    /**
     * GD656Mods: 快速打开载具部署界面 —— 供部署 HUD 右下角的"载具部署"按钮调用。
     * 已经打开时则关闭(与 {@link #requestOpenConfigScreen()} 的开关语义保持一致)。
     */
    public void requestOpenVehicleDeployScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.screen instanceof LoadoutVehicleDeployScreen vehicleDeployScreen) {
            vehicleDeployScreen.onClose();
            return;
        }
        // parent 传当前屏幕: 部署 HUD 下为 null, 关闭后直接回到游戏
        minecraft.setScreen(new LoadoutVehicleDeployScreen(minecraft.screen));
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
            || screen instanceof LoadoutVehicleAdminEditorScreen;
    }
}
