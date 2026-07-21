package org.mods.gd656conquest.server.room.gameplay.gunmaster;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataModel;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.core.RoomPlayerInvalidationReason;
import org.mods.gd656conquest.server.room.gameplay.RoomGameplayHudState;
import org.mods.gd656conquest.server.room.gameplay.RoomMatchEndRequest;
import org.mods.gd656conquest.server.room.gameplay.deathmatch.DeathmatchGameplayRuntime;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomGunmasterWeaponPoolRules;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService;
import org.mods.gd656conquest.server.room.runtime.RoomSpawnCandidateSelectionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GunmasterGameplayRuntime extends DeathmatchGameplayRuntime {
    private static final String DEFAULT_NEXT_WEAPON_NAME = "AK-12";
    private final MinecraftServer gunmasterServer;
    private final RunningRuntimeState gunmasterRunningState;
    private final RoomPlayerLoadoutService gunmasterLoadoutService;
    private RoomMatchEndRequest pendingEndRequest;

    public GunmasterGameplayRuntime(MinecraftServer server, RunningRuntimeState runningState) {
        super(server, runningState);
        this.gunmasterServer = server;
        this.gunmasterRunningState = runningState;
        this.gunmasterLoadoutService = new RoomPlayerLoadoutService(server);
    }

    @Override
    public void onEnterMainPhase(RoomDefinition room, long gameTick) {
        super.onEnterMainPhase(room, gameTick);
        pendingEndRequest = null;
        gunmasterRunningState.getGunmasterStageIndexByPlayer().clear();
        gunmasterRunningState.getGunmasterStageKillsByPlayer().clear();
        gunmasterRunningState.getGunmasterMatchWeaponSequenceIds().clear();
        List<String> sequence = buildWeaponSequence(room);
        gunmasterRunningState.getGunmasterMatchWeaponSequenceIds().addAll(sequence);
        for (UUID playerId : gunmasterRunningState.getRoomPlayers()) {
            gunmasterRunningState.getGunmasterStageIndexByPlayer().put(playerId, 0);
            gunmasterRunningState.getGunmasterStageKillsByPlayer().put(playerId, 0);
        }
    }

    @Override
    public void onPlayerInvalidated(
        RoomDefinition room,
        UUID playerId,
        RoomTeamId teamId,
        RoomPlayerInvalidationReason reason,
        int killerEntityId,
        long gameTick
    ) {
        if (pendingEndRequest != null || room == null || playerId == null || reason != RoomPlayerInvalidationReason.DEATH || killerEntityId < 0) {
            return;
        }
        ServerPlayer killer = resolveKillerPlayer(killerEntityId);
        if (killer == null) {
            return;
        }
        UUID killerId = killer.getUUID();
        if (!gunmasterRunningState.getRoomPlayers().contains(killerId) || killerId.equals(playerId)) {
            return;
        }
        if (!room.gunmasterConfig().isSoloGrouping()) {
            RoomTeamId killerTeam = gunmasterRunningState.getRoomTeams().get(killerId);
            if (killerTeam == null || killerTeam == teamId) {
                return;
            }
        }
        advancePlayerStage(room, killerId);
    }

    @Override
    public Optional<RoomMatchEndRequest> pollMatchEndRequest() {
        RoomMatchEndRequest request = pendingEndRequest;
        pendingEndRequest = null;
        return Optional.ofNullable(request);
    }

    @Override
    public RoomPosition resolveRespawnAnchorPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        if (room != null && room.gunmasterConfig().isSoloGrouping()) {
            RoomPosition lockedTarget = state == null || player == null ? null : state.getRespawnTargetPositions().get(player.getUUID());
            if (lockedTarget != null) {
                return lockedTarget;
            }
            return pickSoloSpawn(room, state, player);
        }
        return super.resolveRespawnAnchorPosition(room, state, player);
    }

    @Override
    public RoomPosition resolveRespawnHoldingPosition(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player
    ) {
        if (room != null && room.gunmasterConfig().isSoloGrouping()) {
            return resolveRespawnAnchorPosition(room, state, player);
        }
        return super.resolveRespawnHoldingPosition(room, state, player);
    }

    @Override
    public RoomPosition resolveTeamDeployIconPosition(RoomDefinition room, RunningRuntimeState state, RoomTeamId teamId) {
        if (room != null && room.gunmasterConfig().isSoloGrouping()) {
            return room.gunmasterConfig().soloSpawnPositions().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
        return super.resolveTeamDeployIconPosition(room, state, teamId);
    }

    private String buildHudExtraPayload(
        boolean soloGrouping,
        int currentStageIndex,
        int[] stageTotalCounts,
        int[] stageEnemyCounts
    ) {
        return String.format(
            Locale.ROOT,
            "GMHUD|solo=%d|next=%s|stage=%d|counts=%s|enemy=%s",
            soloGrouping ? 1 : 0,
            encode(resolveNextWeaponName(currentStageIndex)),
            currentStageIndex + 1,
            join(stageTotalCounts),
            join(stageEnemyCounts)
        );
    }

    @Override
    public RoomGameplayHudState buildHudState(
        RoomDefinition room,
        RunningRuntimeState state,
        ServerPlayer player,
        boolean inDeployFlow,
        long gameTick
    ) {
        RoomGameplayHudState base = super.buildHudState(room, state, player, inDeployFlow, gameTick);
        if (room == null) {
            return base;
        }
        boolean initialLockActive = state != null && player != null && state.isInitialMovementLockActive(player.getUUID(), gameTick);
        RoomTeamId selfTeam = state == null || player == null ? null : state.getRoomTeams().get(player.getUUID());
        boolean soloGrouping = room.gunmasterConfig().isSoloGrouping();
        int stageCount = Math.max(1, gunmasterRunningState.getGunmasterMatchWeaponSequenceIds().size());
        int[] stageTotalCounts = new int[stageCount];
        int[] stageEnemyCounts = new int[stageCount];
        int currentStageIndex = Math.min(stageCount - 1, Math.max(0, state == null || player == null ? 0 : state.getGunmasterStageIndexByPlayer().getOrDefault(player.getUUID(), 0)));
        if (state != null) {
            for (UUID playerId : state.getRoomPlayers()) {
                RoomTeamId teamId = state.getRoomTeams().get(playerId);
                int stageIndex = Math.min(stageCount - 1, Math.max(0, state.getGunmasterStageIndexByPlayer().getOrDefault(playerId, 0)));
                stageTotalCounts[stageIndex]++;
                if (soloGrouping) {
                    if (player == null || !playerId.equals(player.getUUID())) {
                        stageEnemyCounts[stageIndex]++;
                    }
                } else if (selfTeam != null && teamId != null && teamId != selfTeam) {
                    stageEnemyCounts[stageIndex]++;
                }
            }
        }
        return new RoomGameplayHudState(
            base.campAScore(),
            base.campBScore(),
            base.elapsedSeconds(),
            "GUNMASTER",
            stageCount,
            stageCount,
            base.remainSeconds(),
            "进行中",
            initialLockActive ? "击杀敌军升级枪械" : base.eventText(),
            base.focusPayload(),
            base.audioState(),
            buildHudExtraPayload(soloGrouping, currentStageIndex, stageTotalCounts, stageEnemyCounts)
        );
    }

    private RoomPosition pickSoloSpawn(RoomDefinition room, RunningRuntimeState state, ServerPlayer player) {
        if (room == null || player == null) {
            return null;
        }
        return new RoomSpawnCandidateSelectionService(player.server).pickSpawn(
            room.gunmasterConfig().soloSpawnPositions(),
            state,
            null
        );
    }

    private List<String> buildWeaponSequence(RoomDefinition room) {
        if (room == null) {
            return List.of();
        }
        List<String> initialCandidates = normalizePool(room.gunmasterConfig().initialWeaponPool());
        List<String> middleCandidates = normalizePool(room.gunmasterConfig().middleWeaponPool());
        List<String> finalCandidates = normalizePool(room.gunmasterConfig().finalWeaponPool());
        List<String> sequence = new ArrayList<>();
        String initial = pickSingleWeapon(initialCandidates);
        if (!initial.isBlank()) {
            sequence.add(initial);
        }
        sequence.addAll(pickMiddleWeapons(middleCandidates, room.gunmasterConfig().enabledMiddleWeaponCount()));
        String ending = pickSingleWeapon(finalCandidates);
        if (!ending.isBlank()) {
            sequence.add(ending);
        }
        return sequence.isEmpty() ? List.of(DEFAULT_NEXT_WEAPON_NAME) : List.copyOf(sequence);
    }

    private String resolveNextWeaponName(int currentStageIndex) {
        List<String> sequence = gunmasterRunningState.getGunmasterMatchWeaponSequenceIds();
        int nextStageIndex = Math.max(0, currentStageIndex) + 1;
        if (sequence.isEmpty() || nextStageIndex >= sequence.size()) {
            return "-";
        }
        String nextWeaponId = sequence.get(nextStageIndex);
        if (nextWeaponId == null || nextWeaponId.isBlank()) {
            return "-";
        }
        EquipmentDataModel equipmentData = ConquestDataManager.of(gunmasterServer).equipmentData().getData();
        EquipmentDataModel.EquipmentEntry entry = RoomGunmasterWeaponPoolRules.resolveEntry(equipmentData, nextWeaponId);
        if (entry == null) {
            return nextWeaponId;
        }
        String displayName = entry.getDisplayName();
        return displayName == null || displayName.isBlank() ? nextWeaponId : displayName.trim();
    }

    private List<String> normalizePool(List<String> raw) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                String normalized = value == null ? "" : value.trim();
                if (!normalized.isBlank()) {
                    unique.add(normalized);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private String pickSingleWeapon(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private List<String> pickMiddleWeapons(List<String> candidates, int targetCount) {
        int count = Math.max(0, targetCount);
        if (count <= 0 || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() >= count) {
            List<String> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            return List.copyOf(shuffled.subList(0, count));
        }
        List<String> result = new ArrayList<>(count);
        while (result.size() < count) {
            List<String> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, ThreadLocalRandom.current());
            for (String value : shuffled) {
                result.add(value);
                if (result.size() >= count) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private void advancePlayerStage(RoomDefinition room, UUID killerId) {
        List<String> sequence = gunmasterRunningState.getGunmasterMatchWeaponSequenceIds();
        if (sequence.isEmpty()) {
            return;
        }
        int killsNeeded = Math.max(1, room.gunmasterConfig().killsPerWeaponSwitch());
        int currentStage = Math.max(0, gunmasterRunningState.getGunmasterStageIndexByPlayer().getOrDefault(killerId, 0));
        int nextKills = gunmasterRunningState.getGunmasterStageKillsByPlayer().getOrDefault(killerId, 0) + 1;
        int finalStageIndex = Math.max(0, sequence.size() - 1);
        if (currentStage >= finalStageIndex) {
            if (nextKills >= killsNeeded) {
                RoomTeamId killerTeam = gunmasterRunningState.getRoomTeams().get(killerId);
                pendingEndRequest = room.gunmasterConfig().isSoloGrouping()
                    ? new RoomMatchEndRequest(killerId, "最终武器达成击杀")
                    : new RoomMatchEndRequest(killerTeam, killerId, room.gameplayTeamDisplayName(killerTeam) + "以最终武器完成终结击杀");
            } else {
                gunmasterRunningState.getGunmasterStageKillsByPlayer().put(killerId, nextKills);
            }
            return;
        }
        if (nextKills >= killsNeeded) {
            gunmasterRunningState.getGunmasterStageIndexByPlayer().put(killerId, currentStage + 1);
            gunmasterRunningState.getGunmasterStageKillsByPlayer().put(killerId, 0);
            ServerPlayer killer = gunmasterServer.getPlayerList().getPlayer(killerId);
            if (killer != null) {
                gunmasterLoadoutService.applySpawnLoadout(gunmasterRunningState, killer);
            }
        } else {
            gunmasterRunningState.getGunmasterStageKillsByPlayer().put(killerId, nextKills);
        }
    }

    private ServerPlayer resolveKillerPlayer(int killerEntityId) {
        if (killerEntityId < 0) {
            return null;
        }
        for (UUID playerId : gunmasterRunningState.getRoomPlayers()) {
            ServerPlayer candidate = gunmasterServer.getPlayerList().getPlayer(playerId);
            if (candidate != null && candidate.getId() == killerEntityId) {
                return candidate;
            }
        }
        return null;
    }

    private String join(int[] values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append('.');
            }
            builder.append(Math.max(0, values[index]));
        }
        return builder.toString();
    }

    private String encode(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("|", "/").replace("=", ":").trim();
    }
}
