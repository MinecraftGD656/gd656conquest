package org.mods.gd656conquest.server.player.regen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PassiveRegenService {
    private static final ResourceLocation PLAYER_HEAL_SOUND_ID = ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.player_heal");

    private static final Map<UUID, Long> LAST_DAMAGE_TICK_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Long> LAST_HEAL_TICK_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Boolean> REGEN_STARTED_SOUND_PLAYED_BY_PLAYER = new HashMap<>();

    private PassiveRegenService() {
    }

    public static void markPlayerDamaged(ServerPlayer player, long gameTick) {
        if (player == null || gameTick < 0L) {
            return;
        }
        UUID playerId = player.getUUID();
        LAST_DAMAGE_TICK_BY_PLAYER.put(playerId, gameTick);
        LAST_HEAL_TICK_BY_PLAYER.remove(playerId);
        REGEN_STARTED_SOUND_PLAYED_BY_PLAYER.remove(playerId);
    }

    public static void onPlayerTick(ServerPlayer player, long gameTick, int startDelaySeconds, int healIntervalTicks) {
        if (!NaturalRegenService.enabled(player) || org.mods.gd656conquest.server.support.SupportRuntime.pausePassive(player)) return;
        startDelaySeconds = NaturalRegenService.startDelay(player, startDelaySeconds);
        healIntervalTicks = NaturalRegenService.interval(player, healIntervalTicks);
        if (player == null || gameTick < 0L || !player.isAlive() || player.isSpectator()) {
            return;
        }
        int safeDelaySeconds = Math.max(0, startDelaySeconds);
        int safeIntervalTicks = Math.max(1, healIntervalTicks);
        long noDamageDelayTicks = safeDelaySeconds * 20L;
        if (player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        UUID playerId = player.getUUID();
        long lastDamageTick = LAST_DAMAGE_TICK_BY_PLAYER.getOrDefault(playerId, gameTick);
        if (gameTick - lastDamageTick < noDamageDelayTicks) {
            return;
        }
        long lastHealTick = LAST_HEAL_TICK_BY_PLAYER.getOrDefault(playerId, lastDamageTick + noDamageDelayTicks - safeIntervalTicks);
        if (gameTick - lastHealTick < safeIntervalTicks) {
            return;
        }
        float healthBefore = player.getHealth();
        player.heal(NaturalRegenService.amount(player));
        if (player.getHealth() <= healthBefore) {
            return;
        }
        if (player.serverLevel() != null && !Boolean.TRUE.equals(REGEN_STARTED_SOUND_PLAYED_BY_PLAYER.get(playerId))) {
            player.playNotifySound(SoundEvent.createVariableRangeEvent(PLAYER_HEAL_SOUND_ID),
                SoundSource.PLAYERS, 1.0F, 1.0F);
            REGEN_STARTED_SOUND_PLAYED_BY_PLAYER.put(playerId, true);
        }
        LAST_HEAL_TICK_BY_PLAYER.put(playerId, gameTick);
    }

    public static void resetPlayer(ServerPlayer player) {
        org.mods.gd656conquest.server.support.SupportRuntime.reset(player);
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        LAST_DAMAGE_TICK_BY_PLAYER.remove(playerId);
        LAST_HEAL_TICK_BY_PLAYER.remove(playerId);
        REGEN_STARTED_SOUND_PLAYED_BY_PLAYER.remove(playerId);
    }

    public static void clearAll() {
        LAST_DAMAGE_TICK_BY_PLAYER.clear();
        LAST_HEAL_TICK_BY_PLAYER.clear();
        REGEN_STARTED_SOUND_PLAYED_BY_PLAYER.clear();
    }
}
