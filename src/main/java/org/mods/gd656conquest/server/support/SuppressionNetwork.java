package org.mods.gd656conquest.server.support;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.SuppressionPacket;
public final class SuppressionNetwork {
    private SuppressionNetwork(){}
    /** Append after existing message IDs; accept only client-bound traffic. */
    public static int register(net.minecraftforge.network.simple.SimpleChannel channel,int id){
        channel.registerMessage(id++,SuppressionPacket.class,SuppressionPacket::encode,SuppressionPacket::decode,
            SuppressionPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        return id;
    }
    public static void send(ServerPlayer p,boolean active,float opacity){
        NetworkManager.getChannel().send(PacketDistributor.PLAYER.with(()->p),new SuppressionPacket(active,opacity));
    }
}
