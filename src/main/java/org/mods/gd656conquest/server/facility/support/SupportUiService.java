package org.mods.gd656conquest.server.facility.support;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.network.packet.SupportUiSnapshotPacket;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.SupportUiOpenPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.herokit.HeroKitDataModel;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        List<String> configuredHeroKitIds = resolveConfiguredHeroKitIds(room, teamId);
        if (configuredHeroKitIds.isEmpty()) {
            return new SupportUiSnapshotPacket(
                room.roomId(),
                room.displayName(),
                resolveTeamDisplayName(room, teamId),
                currentScore,
                spentScore,
                remainingScore,
                "当前阵营没有开放精英兵",
                List.of()
            );
        }

        Map<String, HeroKitDataModel.HeroKitEntry> heroKitsById = ConquestDataManager.of(player.server)
            .heroKits()
            .get()
            .getHeroKitsById();
        List<SupportUiSnapshotPacket.HeroKitPayload> heroKits = new ArrayList<>();
        for (String heroKitId : configuredHeroKitIds) {
            HeroKitDataModel.HeroKitEntry entry = heroKitsById.get(heroKitId);
            if (entry == null || entry.getId().isBlank()) {
                continue;
            }
            int cost = entry.getCost();
            heroKits.add(new SupportUiSnapshotPacket.HeroKitPayload(
                entry.getId(),
                entry.getDisplayName().isBlank() ? entry.getId() : entry.getDisplayName(),
                entry.getType(),
                cost,
                remainingScore >= cost
            ));
        }
        heroKits.sort(Comparator
            .comparingInt(SupportUiSnapshotPacket.HeroKitPayload::cost)
            .thenComparing(SupportUiSnapshotPacket.HeroKitPayload::id, String.CASE_INSENSITIVE_ORDER));
        String statusText = heroKits.isEmpty() ? "当前阵营可用精英兵配置为空或无效" : "";
        return new SupportUiSnapshotPacket(
            room.roomId(),
            room.displayName(),
            resolveTeamDisplayName(room, teamId),
            currentScore,
            spentScore,
            remainingScore,
            statusText,
            heroKits
        );
    }

    private static List<String> resolveConfiguredHeroKitIds(RoomDefinition room, RoomTeamId teamId) {
        if (room == null) {
            return List.of();
        }
        List<String> rawValues = teamId == RoomTeamId.CAMP_A
            ? room.commonConfig().supportCampAHeroKits()
            : room.commonConfig().supportCampBHeroKits();
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
}
