package org.mods.gd656conquest.server.facility.support;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.packet.SupportUiSnapshotPacket;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.SupportUiOpenPacket;
import org.mods.gd656conquest.common.support.SupportHeroKitTypeRegistry;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.herokit.HeroKitDataModel;
import org.mods.gd656conquest.server.data.shelling.ShellingDataModel;
import org.mods.gd656conquest.server.data.shelling.ShellingImpactType;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomShellingRuntimeService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SupportUiService {
    private SupportUiService() {
    }

    public static void open(ServerPlayer player) {
        if (player == null) {
            return;
        }
        NetworkManager.sendToPlayer(player, new SupportUiOpenPacket());
    }

    public static void sendSnapshot(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        NetworkManager.sendToPlayer(player, buildSnapshot(player));
    }

    public static SupportUiSnapshotPacket buildSnapshot(ServerPlayer player) {
        if (player == null || player.server == null) {
            return new SupportUiSnapshotPacket("", "", "", 0, 0, 0, "当前无法读取阵地支援数据", List.of());
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        String roomId = roomManager.findPlayerRoomId(player.getUUID()).orElse("");
        RoomDefinition room = roomManager.findRoom(roomId).orElse(null);
        if (room == null) {
            return new SupportUiSnapshotPacket("", "", "", 0, 0, 0, "当前不在对局房间", List.of());
        }
        if (room.lifecycleState() != RoomLifecycleState.RUNNING) {
            return new SupportUiSnapshotPacket(
                room.roomId(),
                room.displayName(),
                "",
                0,
                0,
                0,
                "当前不在进行中的对局",
                List.of()
            );
        }
        RoomTeamId teamId = roomManager.findCurrentTeam(player.getUUID()).orElse(null);
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            return new SupportUiSnapshotPacket(
                room.roomId(),
                room.displayName(),
                "",
                0,
                0,
                0,
                "当前未加入可用阵营",
                List.of()
            );
        }

        int spentScore = roomManager.getPlayerSupportSpentScore(player.getUUID());
        int remainingScore = roomManager.getPlayerRemainingSupportScore(player.getUUID());
        int currentScore = Math.max(0, spentScore + remainingScore);
        boolean isCurrentlyHeroKit = false;
        org.mods.gd656conquest.server.room.core.RunningRuntimeState state = roomManager.findRunningState(roomId).orElse(null);
        if (state != null) {
            isCurrentlyHeroKit = state.getActiveHeroKitIdsByPlayer().containsKey(player.getUUID());
        }
        List<String> configuredHeroKitIds = resolveConfiguredHeroKitIds(room, teamId);
        List<String> configuredShellingIds = resolveConfiguredShellingIds(room, teamId);
        List<SupportUiSnapshotPacket.SupportEntryPayload> entries = new ArrayList<>();
        entries.addAll(buildHeroKitEntries(player, remainingScore, configuredHeroKitIds, isCurrentlyHeroKit, state, teamId));
        entries.addAll(buildShellingEntries(player, room.roomId(), remainingScore, configuredShellingIds, state, roomManager.shellingRuntimeService(), teamId));
        entries.sort(Comparator
            .comparing(SupportUiSnapshotPacket.SupportEntryPayload::available, Comparator.reverseOrder())
            .thenComparingInt(SupportUiSnapshotPacket.SupportEntryPayload::cost)
            .thenComparing(SupportUiSnapshotPacket.SupportEntryPayload::kind, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SupportUiSnapshotPacket.SupportEntryPayload::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SupportUiSnapshotPacket.SupportEntryPayload::id, String.CASE_INSENSITIVE_ORDER));
        String statusText = resolveStatusText(configuredHeroKitIds, configuredShellingIds, entries);
        return new SupportUiSnapshotPacket(
            room.roomId(),
            room.displayName(),
            resolveTeamDisplayName(room, teamId),
            currentScore,
            spentScore,
            remainingScore,
            statusText,
            entries
        );
    }

    private static List<SupportUiSnapshotPacket.SupportEntryPayload> buildHeroKitEntries(
        ServerPlayer player,
        int remainingScore,
        List<String> configuredHeroKitIds,
        boolean isCurrentlyHeroKit,
        RunningRuntimeState state,
        RoomTeamId teamId
    ) {
        if (player == null || player.server == null || configuredHeroKitIds.isEmpty()) {
            return List.of();
        }
        Map<String, HeroKitDataModel.HeroKitEntry> heroKitsById = ConquestDataManager.of(player.server)
            .heroKits()
            .get()
            .getHeroKitsById();
        long gameTick = player.server.getTickCount();
        List<SupportUiSnapshotPacket.SupportEntryPayload> entries = new ArrayList<>();
        for (String heroKitId : configuredHeroKitIds) {
            HeroKitDataModel.HeroKitEntry entry = heroKitsById.get(heroKitId);
            if (entry == null || entry.getId().isBlank()) {
                continue;
            }
            int cost = entry.getCost();
            HeroKitSupportService.HeroKitOccupancy occupancy = HeroKitSupportService.resolveHeroKitOccupancy(
                state,
                teamId,
                entry.getId(),
                player.getUUID(),
                gameTick
            );
            boolean locked = occupancy.occupied();
            boolean coolingDown = occupancy.coolingDown();
            String holderName = locked ? resolvePlayerName(player, occupancy.occupiedByPlayerId()) : "";
            boolean available = remainingScore >= cost && !isCurrentlyHeroKit && !locked && !coolingDown;
            String typeLabel = SupportHeroKitTypeRegistry.displayName(entry.getType());
            String detailText;
            String buttonText;
            if (isCurrentlyHeroKit) {
                detailText = "您当前已经是精英兵状态，无法再次购买";
                buttonText = "锁定";
            } else if (locked) {
                detailText = holderName.isBlank()
                    ? "同阵营已有玩家正在使用该精英兵"
                    : holderName + " 正在使用该精英兵";
                buttonText = "锁定";
            } else if (coolingDown) {
                detailText = "该精英兵冷却中，还需 " + occupancy.remainingCooldownSeconds() + " 秒";
                buttonText = "冷却";
            } else {
                detailText = available
                    ? "消耗 " + cost + " 分，点击后立即请求部署"
                    : "需要 " + cost + " 分，当前条件不足";
                buttonText = available ? "使用" : "未解锁";
            }
            entries.add(new SupportUiSnapshotPacket.SupportEntryPayload(
                "hero_kit",
                entry.getId(),
                entry.getDisplayName().isBlank() ? entry.getId() : entry.getDisplayName(),
                entry.getType(),
                buildHeroKitSubtitle(typeLabel, entry),
                detailText,
                buttonText,
                cost,
                available
            ));
        }
        return entries;
    }

    private static List<SupportUiSnapshotPacket.SupportEntryPayload> buildShellingEntries(
        ServerPlayer player,
        String roomId,
        int remainingScore,
        List<String> configuredShellingIds,
        org.mods.gd656conquest.server.room.core.RunningRuntimeState state,
        RoomShellingRuntimeService shellingRuntimeService,
        RoomTeamId teamId
    ) {
        if (player == null || player.server == null || configuredShellingIds.isEmpty()) {
            return List.of();
        }
        Map<String, ShellingDataModel.ShellingEntry> shellingsById = ConquestDataManager.of(player.server)
            .shellings()
            .get()
            .getShellingsById();
        List<SupportUiSnapshotPacket.SupportEntryPayload> entries = new ArrayList<>();
        for (String shellingId : configuredShellingIds) {
            ShellingDataModel.ShellingEntry entry = shellingsById.get(shellingId);
            if (entry == null || entry.getId().isBlank()) {
                continue;
            }
            RoomShellingRuntimeService.ShellingOccupancy occupancy = shellingRuntimeService == null
                ? RoomShellingRuntimeService.ShellingOccupancy.idle()
                : shellingRuntimeService.resolveShellingOccupancy(roomId, state, teamId, shellingId, player.getUUID());
            int cost = entry.getCost();
            ShellingImpactType impactType = entry.resolveImpactType();
            boolean locked = occupancy.occupied();
            boolean lockedBySelf = locked && player.getUUID().equals(occupancy.occupiedByPlayerId());
            String lockedPlayerName = lockedBySelf ? "" : resolvePlayerName(player, occupancy.occupiedByPlayerId());
            boolean available = !locked && remainingScore >= cost;
            String detailText;
            String buttonText;
            if (locked) {
                buttonText = "锁定";
                if (lockedBySelf) {
                    detailText = "你已进入该炮击的坐标选定流程";
                } else if (occupancy.sequenceRunning()) {
                    detailText = lockedPlayerName.isBlank()
                        ? "同阵营已有玩家正在释放该炮击支援"
                        : lockedPlayerName + " 正在释放该炮击支援";
                } else {
                    detailText = lockedPlayerName.isBlank()
                        ? "同阵营已有玩家正在部署该炮击支援"
                        : lockedPlayerName + " 正在部署该炮击支援";
                }
            } else if (available) {
                buttonText = "选定";
                detailText = "消耗 " + cost + " 分，点击后进入坐标选定";
            } else {
                buttonText = "未解锁";
                detailText = "需要 " + cost + " 分，当前条件不足";
            }
            entries.add(new SupportUiSnapshotPacket.SupportEntryPayload(
                "shelling",
                entry.getId(),
                resolveShellingDisplayName(entry),
                "",
                "炮击 | " + impactType.displayName(),
                detailText,
                buttonText,
                cost,
                available
            ));
        }
        return entries;
    }

    private static List<String> resolveConfiguredHeroKitIds(RoomDefinition room, RoomTeamId teamId) {
        if (room == null) {
            return List.of();
        }
        List<String> rawValues = teamId == RoomTeamId.CAMP_A
            ? room.commonConfig().supportCampAHeroKits()
            : room.commonConfig().supportCampBHeroKits();
        return normalizeConfiguredIds(rawValues);
    }

    private static List<String> resolveConfiguredShellingIds(RoomDefinition room, RoomTeamId teamId) {
        if (room == null) {
            return List.of();
        }
        List<String> rawValues = teamId == RoomTeamId.CAMP_A
            ? room.commonConfig().supportCampAShellings()
            : room.commonConfig().supportCampBShellings();
        return normalizeConfiguredIds(rawValues);
    }

    private static List<String> normalizeConfiguredIds(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            normalized.add(rawValue.trim());
        }
        return List.copyOf(normalized);
    }

    private static String resolveTeamDisplayName(RoomDefinition room, RoomTeamId teamId) {
        if (room == null || teamId == null) {
            return "";
        }
        return teamId == RoomTeamId.CAMP_A
            ? room.commonConfig().campADisplayName()
            : room.commonConfig().campBDisplayName();
    }

    private static String resolveShellingDisplayName(ShellingDataModel.ShellingEntry entry) {
        if (entry == null) {
            return "";
        }
        if (!entry.getDisplayName().isBlank()) {
            return entry.getDisplayName();
        }
        return entry.getId();
    }

    private static String buildHeroKitSubtitle(String typeLabel, HeroKitDataModel.HeroKitEntry entry) {
        if (entry == null) {
            return "精英兵 | " + typeLabel;
        }
        return "精英兵 | "
            + typeLabel
            + " | 冷却 "
            + entry.getCooldownSeconds()
            + "s";
    }

    private static String resolveStatusText(
        List<String> configuredHeroKitIds,
        List<String> configuredShellingIds,
        List<SupportUiSnapshotPacket.SupportEntryPayload> entries
    ) {
        boolean hasConfiguredHeroKits = configuredHeroKitIds != null && !configuredHeroKitIds.isEmpty();
        boolean hasConfiguredShellings = configuredShellingIds != null && !configuredShellingIds.isEmpty();
        if (!hasConfiguredHeroKits && !hasConfiguredShellings) {
            return "当前阵营没有开放支援项";
        }
        return entries == null || entries.isEmpty() ? "当前阵营可用支援配置为空或无效" : "";
    }

    private static String resolvePlayerName(ServerPlayer requester, UUID playerId) {
        if (requester == null || requester.server == null || playerId == null) {
            return "";
        }
        ServerPlayer holder = requester.server.getPlayerList().getPlayer(playerId);
        if (holder == null || holder.getGameProfile() == null || holder.getGameProfile().getName() == null) {
            return "";
        }
        return holder.getGameProfile().getName();
    }
}
