package org.mods.gd656conquest.deployment;

import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

/** The deployment view and confirmation share the server's existing deploy-ready deadline. */
@Mod.EventBusSubscriber(modid="gd656conquest",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DeploymentClock {
    public static final int UPDATE_INTERVAL=5;
    private static SimpleChannel channel;
    private static final Map<MinecraftServer,Map<UUID,Update>> previous=new HashMap<>();
    public record Update(UUID player,ResourceLocation dimension,long serverTick,long readyTick) {
        public int remainingTicks(){return readyTick<0?-1:(int)Math.min(Integer.MAX_VALUE,Math.max(0,readyTick-serverTick));}
    }
    private DeploymentClock() {}
    private static boolean compatible(String version){return version.equals("1")||version.equals(NetworkRegistry.ABSENT)||version.equals(NetworkRegistry.ACCEPTVANILLA);}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(DeploymentClock::register);}
    public static void register(){
        if(channel!=null)return;
        channel=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("gd656conquest","deployment_clock"),()->"1",DeploymentClock::compatible,DeploymentClock::compatible);
        channel.messageBuilder(Update.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(DeploymentClock::write).decoder(DeploymentClock::read).consumerMainThread((p,c)->{
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->org.mods.gd656conquest.client.room.ClientDeathFlowState.synchronizeDeployClock(p.player(),p.dimension(),p.serverTick(),p.readyTick()));
            c.get().setPacketHandled(true);
        }).add();
    }
    public static void write(Update update,FriendlyByteBuf buffer){buffer.writeUUID(update.player());buffer.writeResourceLocation(update.dimension());buffer.writeLong(update.serverTick());buffer.writeLong(update.readyTick());}
    public static Update read(FriendlyByteBuf buffer){return new Update(buffer.readUUID(),buffer.readResourceLocation(),buffer.readLong(),buffer.readLong());}
    public static boolean supported(Connection connection){return channel!=null&&connection!=null&&channel.isRemotePresent(connection);}
    public static Update snapshot(ServerPlayer player,long tick){
        var manager=RoomCoreRuntimeManager.of(player.server);
        var room=manager.findPlayerRoomId(player.getUUID()).orElse("");
        var state=manager.findRunningState(room).orElse(null);
        Long ready=state==null?null:state.getDeployReadyTicks().get(player.getUUID());
        return ready==null?null:new Update(player.getUUID(),player.level().dimension().location(),tick,ready);
    }
    public static void synchronize(MinecraftServer server,long tick){
        if(tick%UPDATE_INTERVAL!=0)return;
        var sent=previous.computeIfAbsent(server,key->new HashMap<>());
        for(var player:server.getPlayerList().getPlayers()){
            if(!supported(player.connection.connection))continue;
            var update=snapshot(player,tick);var last=sent.get(player.getUUID());
            if(update==null){
                if(last!=null){channel.send(PacketDistributor.PLAYER.with(()->player),new Update(player.getUUID(),last.dimension(),tick,-1));sent.remove(player.getUUID());}
            }else{
                // Keep readiness available when the same deployment view is opened again.
                channel.send(PacketDistributor.PLAYER.with(()->player),update);sent.put(player.getUUID(),update);
            }
        }
    }
    @Mod.EventBusSubscriber(modid="gd656conquest",bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END){var server=ServerLifecycleHooks.getCurrentServer();if(server!=null)synchronize(server,server.getTickCount());}}
        @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){var sent=previous.get(player.server);if(sent!=null)sent.remove(player.getUUID());}}
        @SubscribeEvent public static void stopped(ServerStoppedEvent event){previous.remove(event.getServer());}
    }
}
