package org.mods.gd656conquest.network;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mods.gd656conquest.network.packet.DownedRagdollStatePacket;
import org.mods.gd656conquest.network.packet.RagdollCatalogPacket;
import org.mods.gd656conquest.network.packet.RagdollObservationPacket;
import org.mods.gd656conquest.network.packet.RagdollSubscribePacket;

/** Body and observation packets, kept separate from later camera and death-card protocols. */
public final class DownedRagdollNetwork {
    private DownedRagdollNetwork() {}

    public static int register(SimpleChannel channel, int nextId) {
        channel.registerMessage(nextId++, DownedRagdollStatePacket.class,
            DownedRagdollStatePacket::encode, DownedRagdollStatePacket::decode,
            DownedRagdollStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, RagdollCatalogPacket.class,
            RagdollCatalogPacket::encode, RagdollCatalogPacket::decode,
            RagdollCatalogPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, RagdollObservationPacket.class,
            RagdollObservationPacket::encode, RagdollObservationPacket::decode,
            RagdollObservationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, RagdollSubscribePacket.class,
            RagdollSubscribePacket::encode, RagdollSubscribePacket::decode,
            RagdollSubscribePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return nextId;
    }
}
