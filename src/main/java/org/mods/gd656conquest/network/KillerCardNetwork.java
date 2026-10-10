package org.mods.gd656conquest.network;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mods.gd656conquest.network.packet.DownedDeployPacket;
import org.mods.gd656conquest.network.packet.KillerCardPacket;

/** Append presentation snapshots after all existing message IDs. */
public final class KillerCardNetwork {
    private KillerCardNetwork() {}

    public static int register(SimpleChannel channel, int nextId) {
        channel.registerMessage(nextId++, KillerCardPacket.class,
            KillerCardPacket::encode, KillerCardPacket::decode, KillerCardPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, DownedDeployPacket.class,
            DownedDeployPacket::encode, DownedDeployPacket::decode, DownedDeployPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return nextId;
    }
}
