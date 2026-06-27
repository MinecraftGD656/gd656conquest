package org.mods.gd656conquest.server.room.vehicle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class VehicleRuntimeTeamResolver {
    private VehicleRuntimeTeamResolver() {
    }

    public static RoomTeamId resolveConfiguredTeam(String configuredTeam) {
        if (configuredTeam == null || configuredTeam.isBlank()) {
            return null;
        }
        return switch (configuredTeam.trim().toLowerCase()) {
            case "campa", "camp_a", "a" -> RoomTeamId.CAMP_A;
            case "campb", "camp_b", "b" -> RoomTeamId.CAMP_B;
            default -> null;
        };
    }

    public static RoomTeamId resolveConfiguredTeam(VehicleRuntimeRecord record) {
        return record == null ? null : resolveConfiguredTeam(record.getConfiguredTeam());
    }

    static Set<UUID> collectPassengerPlayers(Entity vehicle) {
        Set<UUID> passengerIds = new LinkedHashSet<>();
        if (vehicle == null) {
            return passengerIds;
        }
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                passengerIds.add(player.getUUID());
            }
        }
        return passengerIds;
    }

    static int countFriendlyPassengers(Set<UUID> passengerIds, RunningRuntimeState state, RoomTeamId configuredTeam) {
        if (passengerIds == null || passengerIds.isEmpty() || state == null || configuredTeam == null) {
            return 0;
        }
        int count = 0;
        for (UUID passengerId : passengerIds) {
            if (configuredTeam == state.getRoomTeams().get(passengerId)) {
                count++;
            }
        }
        return count;
    }

    static int countEnemyPassengers(Set<UUID> passengerIds, RunningRuntimeState state, RoomTeamId configuredTeam) {
        if (passengerIds == null || passengerIds.isEmpty() || state == null || configuredTeam == null) {
            return 0;
        }
        int count = 0;
        for (UUID passengerId : passengerIds) {
            RoomTeamId passengerTeam = state.getRoomTeams().get(passengerId);
            if (passengerTeam != null && passengerTeam != configuredTeam) {
                count++;
            }
        }
        return count;
    }
}
