package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public final class RoomPlayerLaunchService {
    private final MinecraftServer server;

    public RoomPlayerLaunchService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void launchQueuedPlayers(RoomDefinition room, MatchingRuntimeState state) {
        Objects.requireNonNull(room, "room");
        Objects.requireNonNull(state, "state");
        Optional<ServerLevel> level = findRoomLevel(room);
        if (level.isEmpty()) {
            return;
        }

        for (UUID playerId : state.getQueuePlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            RoomTeamId teamId = state.getQueueTeams().get(playerId);
            RoomPosition target = resolveSpawn(room, teamId);
            teleportTo(player, level.get(), target);
        }
    }

    public ServerPlayer findOnlinePlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(playerId);
    }

    public boolean respawnPlayer(RoomDefinition room, UUID playerId, RoomTeamId teamId) {
        Objects.requireNonNull(room, "room");
        if (playerId == null) {
            return false;
        }
        Optional<ServerLevel> level = findRoomLevel(room);
        if (level.isEmpty()) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return false;
        }
        return teleportTo(player, level.get(), resolveSpawn(room, teamId));
    }

    public boolean teleportToViewPosition(ServerPlayer player, RoomDefinition room) {
        return teleportToViewPosition(player, room, room == null ? null : room.conquestConfig().deployViewPosition());
    }

    public boolean teleportToViewPosition(ServerPlayer player, RoomDefinition room, RoomViewPosition view) {
        Objects.requireNonNull(room, "room");
        if (player == null || view == null) {
            return false;
        }
        Optional<ServerLevel> level = findRoomLevel(room);
        if (level.isEmpty()) {
            return false;
        }
        player.teleportTo(level.get(), view.x() + 0.5D, view.y(), view.z() + 0.5D, view.yaw(), view.pitch());
        player.fallDistance = 0.0F;
        return player.serverLevel() == level.get();
    }

    public boolean teleportToQueueSpawn(ServerPlayer player, RoomDefinition room) {
        Objects.requireNonNull(room, "room");
        if (player == null) {
            return false;
        }
        Optional<ServerLevel> level = findRoomLevel(room);
        if (level.isEmpty()) {
            return false;
        }
        RoomPosition target = room.commonConfig().queueSpawn();
        if (target != null) {
            return teleportTo(player, level.get(), target);
        }
        var view = room.conquestConfig().deployViewPosition();
        if (view != null) {
            player.teleportTo(level.get(), view.x() + 0.5D, view.y(), view.z() + 0.5D, view.yaw(), view.pitch());
            player.fallDistance = 0.0F;
            return player.serverLevel() == level.get();
        }
        target = room.conquestConfig().pickRandomTeamSpawn(RoomTeamId.CAMP_A).orElse(null);
        if (target == null) {
            target = room.conquestConfig().pickRandomTeamSpawn(RoomTeamId.CAMP_B).orElse(null);
        }
        if (target == null) {
            return false;
        }
        return teleportTo(player, level.get(), target);
    }

    public boolean teleportToRoomPosition(ServerPlayer player, RoomDefinition room, RoomPosition position) {
        Objects.requireNonNull(room, "room");
        if (player == null || position == null) {
            return false;
        }
        Optional<ServerLevel> level = findRoomLevel(room);
        if (level.isEmpty()) {
            return false;
        }
        return teleportTo(player, level.get(), position);
    }

    public Optional<ServerLevel> findRoomLevel(RoomDefinition room) {
        ResourceLocation location = resolveDimensionLocation(room);
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, location);
        return Optional.ofNullable(server.getLevel(key));
    }

    private RoomPosition resolveSpawn(RoomDefinition room, RoomTeamId teamId) {
        if (room != null
            && org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(room.gameplayId())
            && room.gunmasterConfig().isSoloGrouping()) {
            RoomPosition soloSpawn = pickSpawnFromPositions(room.gunmasterConfig().soloSpawnPositions());
            if (soloSpawn != null) {
                return soloSpawn;
            }
        }
        if (teamId != null) {
            if (room != null && room.isArenaGameplay()) {
                if (teamId == RoomTeamId.CAMP_A) {
                    RoomPosition spawn = pickSpawnFromGroup(room.arenaConfig().campASpawnGroup());
                    if (spawn != null) {
                        return spawn;
                    }
                }
                if (teamId == RoomTeamId.CAMP_B) {
                    RoomPosition spawn = pickSpawnFromGroup(room.arenaConfig().campBSpawnGroup());
                    if (spawn != null) {
                        return spawn;
                    }
                }
            }
            RoomPosition randomTeamSpawn = room.conquestConfig().pickRandomTeamSpawn(teamId).orElse(null);
            if (randomTeamSpawn != null) {
                return randomTeamSpawn;
            }
        }
        if (room.commonConfig().queueSpawn() != null) {
            return room.commonConfig().queueSpawn();
        }
        return new RoomPosition(0, 100.0D, 0);
    }

    private RoomPosition pickSpawnFromGroup(RoomSpawnGroupConfig group) {
        if (group == null || !group.enabled() || group.spawns().isEmpty()) {
            return null;
        }
        var candidates = group.spawns().stream()
            .filter(Objects::nonNull)
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private RoomPosition pickSpawnFromPositions(java.util.List<RoomPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        var candidates = positions.stream()
            .filter(Objects::nonNull)
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean teleportTo(ServerPlayer player, ServerLevel level, RoomPosition position) {
        player.teleportTo(level, position.x() + 0.5D, position.y(), position.z() + 0.5D, player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;
        return player.serverLevel() == level;
    }

    private ResourceLocation resolveDimensionLocation(RoomDefinition room) {
        ResourceLocation configured = ResourceLocation.tryParse(room.commonConfig().dimensionId());
        if (configured != null) {
            return configured;
        }
        String normalizedRoomId = room.roomId() == null || room.roomId().isBlank()
            ? "room_01"
            : room.roomId().toLowerCase(Locale.ROOT);
        ResourceLocation fallback = ResourceLocation.tryParse("gd656conquest:" + normalizedRoomId);
        if (fallback != null) {
            return fallback;
        }
        return ResourceLocation.tryParse("gd656conquest:room_01");
    }
}
