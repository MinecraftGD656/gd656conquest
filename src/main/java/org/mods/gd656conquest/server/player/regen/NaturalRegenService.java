package org.mods.gd656conquest.server.player.regen;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomNaturalRegenConfig;

/** Resolves the current room policy; global configuration retains its existing values. */
public final class NaturalRegenService {
    private NaturalRegenService() {}
    private static RoomNaturalRegenConfig room(ServerPlayer p) {
        var manager=RoomCoreRuntimeManager.of(p.server);
        return manager.findPlayerRoomId(p.getUUID()).flatMap(manager::findRoom)
            .map(r->r.commonConfig().naturalRegenConfig()).orElse(null);
    }
    private static RoomNaturalRegenConfig.Settings settings(ServerPlayer p) {
        var global=ConquestDataManager.of(p.server).serverConfig();
        var config=room(p);if(config==null)config=new RoomNaturalRegenConfig("全局",null,null,null,null,null);
        return config.resolve(global.isPassiveRegenerationEnabled(),global.isNaturalRegenerationEnabled(),
            global.getPassiveRegenStartDelaySeconds(),global.getPassiveRegenIntervalTicks(),global.getPassiveRegenHealAmount());
    }
    public static boolean enabled(ServerPlayer p) {
        if(p==null || p.server==null)return false;
        var s=settings(p);return s.passiveEnabled() && s.healAmount()>0;
    }
    public static int startDelay(ServerPlayer p,int original){return settings(p).startDelaySeconds();}
    public static int interval(ServerPlayer p,int original){return settings(p).intervalTicks();}
    public static float amount(ServerPlayer p){return settings(p).healAmount();}
    public static boolean vanillaAllowed(ServerPlayer p,boolean gameRule) {
        if(p==null || p.server==null || room(p)==null)return gameRule;
        return settings(p).vanillaEnabled();
    }
}
