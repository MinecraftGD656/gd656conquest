package org.mods.gd656conquest.network;

import java.util.Optional;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mods.gd656conquest.network.packet.DeathCausePacket;

public final class DeathCauseNetwork {
    private DeathCauseNetwork() {}

    public static int register(SimpleChannel channel, int nextId) {
        channel.registerMessage(nextId++, DeathCausePacket.class,
            DeathCausePacket::encode, DeathCausePacket::decode, DeathCausePacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return nextId;
    }
}
