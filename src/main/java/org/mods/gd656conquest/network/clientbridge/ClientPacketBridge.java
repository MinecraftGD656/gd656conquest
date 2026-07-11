package org.mods.gd656conquest.network.clientbridge;

import org.mods.gd656conquest.network.packet.SupportUiSnapshotPacket;

public interface ClientPacketBridge {
    void openSupportUi();

    void applySupportUiSnapshot(SupportUiSnapshotPacket packet);

    void takeAndSendScreenshot(String sessionToken);
}
