package org.mods.gd656conquest.client.network;

import net.minecraft.client.Minecraft;
import org.mods.gd656conquest.client.event.handler.ScreenshotClientHandler;
import org.mods.gd656conquest.client.support.ClientSupportUiState;
import org.mods.gd656conquest.client.support.SupportScreen;
import org.mods.gd656conquest.network.clientbridge.ClientPacketBridge;
import org.mods.gd656conquest.network.packet.SupportUiSnapshotPacket;

public final class ClientPacketBridgeImpl implements ClientPacketBridge {
    @Override
    public void openSupportUi() {
        ClientSupportUiState.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.screen instanceof SupportScreen) {
            return;
        }
        minecraft.setScreen(new SupportScreen(minecraft.screen));
    }

    @Override
    public void applySupportUiSnapshot(SupportUiSnapshotPacket packet) {
        ClientSupportUiState.apply(packet);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof SupportScreen supportScreen) {
            supportScreen.refreshFromState();
        }
    }

    @Override
    public void takeAndSendScreenshot(String sessionToken) {
        ScreenshotClientHandler.takeAndSendScreenshot(sessionToken);
    }
}
