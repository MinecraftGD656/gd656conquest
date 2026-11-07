package org.mods.gd656conquest.server.profile;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656killicon.profile.BattlefieldProfile.Career;
import org.mods.gd656killicon.profile.ProfileServer;

/** Reads the original UUID archive and level/unlock rules; writes stay in Conquest. */
@Mod.EventBusSubscriber(modid="gd656conquest",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ConquestCareerProvider {
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(()->ProfileServer.register(player->{
        var data=ConquestDataManager.of(player.server).playerData().getPlayerData(player.getUUID());
        var stats=data.getMatchStatistics();var combat=ProfileServer.coreCareer(player);
        int max=PlayerLevelService.resolveMaxLevel(player.server),level=data.getLevel(),needed=level>=max?0:player.getXpNeededForNextLevel();
        int into=needed==0?0:Math.max(0,Math.min(needed,Math.round(player.experienceProgress*needed)));
        return new Career(level,max,data.getLevelExperience(),into,needed,combat.score(),combat.kills(),combat.deaths(),combat.assists(),combat.revives(),
            stats.getMatchesPlayed(),stats.getWins(),stats.getLosses(),stats.getDraws(),stats.getMatchActiveMinutes());
    }));}
}
