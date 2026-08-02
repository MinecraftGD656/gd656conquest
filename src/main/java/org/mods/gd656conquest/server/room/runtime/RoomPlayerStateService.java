package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;
import org.mods.gd656conquest.server.compat.tacz.TaczServerPoseCompat;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RoomPlayerStateService {
    private static final float DOWNED_HEALTH = 0.1F;
    private static final int DOWNED_LOCKED_HOTBAR_SLOT = 6;
    private static final float REVIVED_HEALTH_FRACTION_ASSAULT = 0.50F;
    private static final float REVIVED_HEALTH_FRACTION_NON_ASSAULT = 0.25F;
    private final MinecraftServer server;
    private final Map<UUID, Long> invulnerableUntilTickByPlayer = new HashMap<>();

    public RoomPlayerStateService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void applyLaunchState(RoomDefinition room, MatchingRuntimeState state, long gameTick) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        for (UUID playerId : state.getQueuePlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            applyPlayerState(room, player, gameTick);
        }
    }

    public void applyMatchingState(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return;
        }
        applyConfiguredGameMode(room, player, GameType.ADVENTURE);
        refillHealth(player);
        player.setInvulnerable(true);
        invulnerableUntilTickByPlayer.remove(player.getUUID());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    public void tick(long gameTick) {
        java.util.Iterator<Map.Entry<UUID, Long>> iterator = invulnerableUntilTickByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (gameTick < entry.getValue()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.setInvulnerable(false);
            }
            iterator.remove();
        }
    }

    public void maintainMatchingState(RoomDefinition room, MatchingRuntimeState state) {
        if (room == null || state == null) {
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        for (UUID playerId : state.getQueuePlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (roomManager.isPlayerInMatchLoad(playerId)) {
                if (!player.isInvulnerable()) {
                    player.setInvulnerable(true);
                }
                continue;
            }
            if (shouldEnforceConfiguredGameMode(player)
                && player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                player.setGameMode(GameType.ADVENTURE);
            }
            if (!player.isInvulnerable()) {
                player.setInvulnerable(true);
            }
        }
    }

    public void maintainRunningState(RoomDefinition room, Iterable<UUID> playerIds) {
        maintainRunningState(room, null, playerIds);
    }

    public void maintainRunningState(RoomDefinition room, RunningRuntimeState state, Iterable<UUID> playerIds) {
        if (room == null || playerIds == null) {
            return;
        }
        long gameTick = server.getTickCount();
        for (UUID playerId : playerIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                if (!player.isInvulnerable()) {
                    player.setInvulnerable(true);
                }
                continue;
            }
            Long invulnerableUntilTick = invulnerableUntilTickByPlayer.get(playerId);
            if (invulnerableUntilTick != null && gameTick < invulnerableUntilTick) {
                if (!player.isInvulnerable()) {
                    player.setInvulnerable(true);
                }
                continue;
            }
            if (invulnerableUntilTick != null) {
                invulnerableUntilTickByPlayer.remove(playerId);
            }
            if (player.isInvulnerable()) {
                player.setInvulnerable(false);
            }
            if (state != null && !state.isPlayerPendingDowned(playerId) && !state.isPlayerDowned(playerId)) {
                PlayerAnimatorDebugDimensionsState.clearPlayer(player);
            }
        }
    }

    public void clearPlayerState(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            normalizePlayerModelState(player);
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.ADVENTURE);
            }
            player.setInvulnerable(false);
            player.setRemainingFireTicks(0);
            refillHealth(player);
        }
        invulnerableUntilTickByPlayer.remove(playerId);
    }

    public void normalizePlayerModelState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        clearTransientItemUseState(player);
        if (player.isPassenger()) {
            player.stopRiding();
        }
        TaczServerPoseCompat.clearCrawlState(player);
        PlayerAnimatorDebugDimensionsState.clearPlayer(player);
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.setForcedPose(null);
        player.setPose(Pose.STANDING);
        player.setShiftKeyDown(false);
        player.refreshDimensions();
    }

    public void applyRespawnState(RoomDefinition room, UUID playerId, long gameTick) {
        if (room == null || playerId == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        applyPlayerState(room, player, gameTick);
    }

    public void setSpectator(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.setGameMode(GameType.SPECTATOR);
        player.setInvulnerable(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    public void applyDeployHoldingState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        clearTransientItemUseState(player);
        setSpectator(player);
        player.setRemainingFireTicks(0);
        refillHealth(player);
    }

    public void applyDownedState(RoomDefinition room, ServerPlayer player) {
        if (room == null || player == null) {
            return;
        }
        clearTransientItemUseState(player);
        applyConfiguredGameMode(room, player, GameType.ADVENTURE);
        player.setInvulnerable(true);
        invulnerableUntilTickByPlayer.remove(player.getUUID());
        // Lock horizontal movement but keep downward physics so downed players do not hover in midair.
        player.setNoGravity(false);
        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.fallDistance = 0.0F;
        player.setRemainingFireTicks(0);
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        player.setHealth(Math.min(maxHealth, DOWNED_HEALTH));
        syncSelectedHotbarSlot(player, DOWNED_LOCKED_HOTBAR_SLOT);
    }

    public void applyRevivedState(RoomDefinition room, ServerPlayer player, long gameTick, boolean revivedByAssault) {
        if (room == null || player == null) {
            return;
        }
        clearTransientItemUseState(player);
        applyConfiguredGameMode(room, player, resolveGameMode(room.commonConfig().playerStateConfig().gameMode()));
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.setRemainingFireTicks(0);
        player.setShiftKeyDown(false);
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float healthFraction = revivedByAssault ? REVIVED_HEALTH_FRACTION_ASSAULT : REVIVED_HEALTH_FRACTION_NON_ASSAULT;
        player.setHealth(Math.min(maxHealth, Math.max(2.0F, maxHealth * healthFraction)));
        applyInvulnerability(player, room.commonConfig().playerStateConfig().revivedInvulnerableSeconds(), gameTick);
    }

    private void applyPlayerState(RoomDefinition room, ServerPlayer player, long gameTick) {
        clearTransientItemUseState(player);
        applyConfiguredGameMode(room, player, resolveGameMode(room.commonConfig().playerStateConfig().gameMode()));
        refillHealth(player);
        applyInvulnerability(player, room.commonConfig().playerStateConfig().invulnerableSeconds(), gameTick);
    }

    private void clearTransientItemUseState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        player.setShiftKeyDown(false);
    }

    private void syncSelectedHotbarSlot(ServerPlayer player, int slot) {
        if (player == null) {
            return;
        }
        int normalizedSlot = Math.max(0, Math.min(8, slot));
        if (player.getInventory().selected != normalizedSlot) {
            player.getInventory().selected = normalizedSlot;
        }
        if (player.connection != null) {
            player.connection.send(new ClientboundSetCarriedItemPacket(normalizedSlot));
        }
    }

    private void applyConfiguredGameMode(RoomDefinition room, ServerPlayer player, GameType configuredGameMode) {
        if (player == null || configuredGameMode == null) {
            return;
        }
        if (!shouldEnforceConfiguredGameMode(player) && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            return;
        }
        player.setGameMode(configuredGameMode);
    }

    private boolean shouldEnforceConfiguredGameMode(ServerPlayer player) {
        return player != null && !player.hasPermissions(2);
    }

    private void refillHealth(ServerPlayer player) {
        float currentMax = player.getMaxHealth();
        if (currentMax > 0.0F) {
            player.setHealth(currentMax);
        }
    }

    private void applyInvulnerability(ServerPlayer player, double invulnerableSeconds, long gameTick) {
        if (invulnerableSeconds <= 0) {
            player.setInvulnerable(false);
            invulnerableUntilTickByPlayer.remove(player.getUUID());
            return;
        }
        player.setInvulnerable(true);
        long durationTicks = Math.max(1L, (long) Math.ceil(invulnerableSeconds * 20.0D));
        invulnerableUntilTickByPlayer.put(player.getUUID(), gameTick + durationTicks);
    }

    private GameType resolveGameMode(String configured) {
        return GameType.byName(configured, GameType.ADVENTURE);
    }
}
