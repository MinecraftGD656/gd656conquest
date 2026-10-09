package org.mods.gd656conquest.network.clientbridge;

import net.minecraftforge.fml.DistExecutor;
import org.mods.gd656conquest.network.packet.SupportUiSnapshotPacket;

public final class ClientPacketBridgeHolder {
    private static final ClientPacketBridge INSTANCE = DistExecutor.safeRunForDist(
        () -> org.mods.gd656conquest.client.network.ClientPacketBridgeImpl::new,
        () -> NoopClientPacketBridge::new
    );

    private ClientPacketBridgeHolder() {
    }

    public static ClientPacketBridge get() {
        return INSTANCE;
    }

    private static final class NoopClientPacketBridge implements ClientPacketBridge {
        @Override
        public void openSupportUi() {
        }

        @Override
        public void applySupportUiSnapshot(SupportUiSnapshotPacket packet) {
        }

        @Override
        public void takeAndSendScreenshot(String sessionToken) {
        }
    }
}
