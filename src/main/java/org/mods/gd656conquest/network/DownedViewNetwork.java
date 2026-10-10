package org.mods.gd656conquest.network;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mods.gd656conquest.network.packet.DownedViewPacket;
import org.mods.gd656conquest.network.packet.RevivedProneReleasePacket;

/** Appends camera messages without changing earlier registrations. */
public final class DownedViewNetwork {
    private DownedViewNetwork() {}

    public static int register(SimpleChannel channel, int nextId) {
        channel.registerMessage(nextId++, DownedViewPacket.class, DownedViewPacket::encode,
            DownedViewPacket::decode, DownedViewPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, RevivedProneReleasePacket.class, RevivedProneReleasePacket::encode,
            RevivedProneReleasePacket::decode, RevivedProneReleasePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return nextId;
    }
}
