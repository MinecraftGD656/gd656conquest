package org.mods.gd656conquest.server.social;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.mods.gd656killicon.profile.ProfileServer;

@Mod.EventBusSubscriber(modid="gd656conquest",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SocialServerState {
    private static final Map<MinecraftServer,FriendStore> friends=new HashMap<>();
    private static final Map<MinecraftServer,SquadInvitationService> squads=new HashMap<>();
    private SocialServerState() {}
    public static FriendStore friends(MinecraftServer server)throws IOException {
        FriendStore value=friends.get(server);if(value==null){value=new FriendStore(server.getWorldPath(LevelResource.ROOT).resolve("gd656conquest/social"));friends.put(server,value);}return value;
    }
    public static SquadInvitationService squads(MinecraftServer server){return squads.computeIfAbsent(server,SquadInvitationService::new);}
    public static FriendStore.Profile profile(ServerPlayer player){return new FriendStore.Profile(player.getUUID(),player.getScoreboardName(),ProfileServer.displayName(player));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player)refresh(player);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){var value=squads.get(player.server);if(value!=null)value.invalidate(player.getUUID());refresh(player);}}
    private static void refresh(ServerPlayer player){try{friends(player.server).refresh(profile(player));}catch(IOException e){LogUtils.getLogger().error("Cannot save social profile for {} in {}",player.getUUID(),player.server.getWorldPath(LevelResource.ROOT),e);}}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var server=ServerLifecycleHooks.getCurrentServer();if(server==null||server.getTickCount()%20!=0)return;
        var service=squads.get(server);if(service!=null)service.prune();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){friends.remove(event.getServer());squads.remove(event.getServer());}
}
