package org.mods.gd656conquest.social;

import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mods.gd656conquest.server.social.SocialUiService;

/** Additive social channel; the shipped Conquest protocol 107 remains unchanged. */
@Mod.EventBusSubscriber(modid="gd656conquest",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SocialNetwork {
    private static SimpleChannel channel;
    private SocialNetwork() {}
    public record Query() {}
    public record OpenUi() {}
    private record Response(SocialState.Snapshot state) {}
    private static boolean compatible(String value){return value.equals("2")||value.equals(NetworkRegistry.ABSENT)||value.equals(NetworkRegistry.ACCEPTVANILLA);}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(SocialNetwork::register);}
    public static void register(){
        if(channel!=null)return;
        channel=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("gd656conquest","social"),()->"2",SocialNetwork::compatible,SocialNetwork::compatible);
        channel.messageBuilder(Query.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{}).decoder(b->new Query()).consumerMainThread((p,c)->{var sender=c.get().getSender();if(sender!=null)SocialUiService.query(sender);c.get().setPacketHandled(true);}).add();
        channel.messageBuilder(SocialState.Action.class,1,NetworkDirection.PLAY_TO_SERVER).encoder(SocialNetwork::writeAction).decoder(SocialNetwork::readAction).consumerMainThread((p,c)->{var sender=c.get().getSender();if(sender!=null)SocialUiService.action(sender,p);c.get().setPacketHandled(true);}).add();
        channel.messageBuilder(Response.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->SocialCodec.write(p.state(),b)).decoder(b->new Response(SocialCodec.read(b))).consumerMainThread((p,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->org.mods.gd656conquest.client.social.SocialClient.receive(p.state()));c.get().setPacketHandled(true);}).add();
        channel.messageBuilder(OpenUi.class,3,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{}).decoder(b->new OpenUi()).consumerMainThread((p,c)->{DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->org.mods.gd656conquest.client.social.SocialClient.open());c.get().setPacketHandled(true);}).add();
    }
    public static void writeAction(SocialState.Action p,FriendlyByteBuf b){b.writeLong(p.requestId());b.writeUtf(p.kind(),32);b.writeUtf(p.target(),64);b.writeUtf(p.value(),16);}
    public static SocialState.Action readAction(FriendlyByteBuf b){return new SocialState.Action(b.readLong(),b.readUtf(32),b.readUtf(64),b.readUtf(16));}
    public static boolean supported(Connection connection){return channel!=null&&connection!=null&&channel.isRemotePresent(connection);}
    public static void query(){channel.sendToServer(new Query());}
    public static void act(SocialState.Action value){channel.sendToServer(value);}
    public static void reply(ServerPlayer player,SocialState.Snapshot value){if(supported(player.connection.connection))channel.send(PacketDistributor.PLAYER.with(()->player),new Response(value));}
    public static void open(ServerPlayer player){if(supported(player.connection.connection)){SocialUiService.query(player);channel.send(PacketDistributor.PLAYER.with(()->player),new OpenUi());}}
}
