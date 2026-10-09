package org.mods.gd656conquest.server.facility.support;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.gameplay.RoomHudSubtitleBroadcastService;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.concurrent.atomic.AtomicLong;

public final class SupportHudSubtitleService {
    private static final String KEY_PREFIX = "support_event";
    private static final AtomicLong EVENT_SEQUENCE = new AtomicLong();
    private static final RoomHudSubtitleBroadcastService BROADCAST_SERVICE = new RoomHudSubtitleBroadcastService();

    private SupportHudSubtitleService() {
    }

    public static void broadcastHeroKitDeployed(
        RunningRuntimeState runningState,
        RoomTeamId teamId,
        ServerPlayer actor,
        String heroKitDisplayName
    ) {
        String actorName = resolvePlayerName(actor);
        String supportName = normalizeText(heroKitDisplayName);
        if (runningState == null || teamId == null || actorName.isBlank() || supportName.isBlank()) {
            return;
        }
        broadcastToTeam(
            runningState,
            teamId,
            "hero_kit_deployed",
            actorName + " 部署了 " + supportName
        );
    }

    public static void broadcastShellingStarted(
        RunningRuntimeState runningState,
        RoomTeamId teamId,
        ServerPlayer actor,
        BlockPos targetPos,
        String shellingTypeDisplayName
    ) {
        String actorName = resolvePlayerName(actor);
        String shellingType = normalizeText(shellingTypeDisplayName);
        if (runningState == null || teamId == null || actorName.isBlank() || targetPos == null || shellingType.isBlank()) {
            return;
        }
        String coordinates = "(" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")";
        broadcastToTeam(
            runningState,
            teamId,
            "shelling_started",
            actorName + " 已对 " + coordinates + " 位置发起 " + shellingType
        );
    }

    public static void broadcastToTeam(
        RunningRuntimeState runningState,
        RoomTeamId alliedTeam,
        String eventType,
        String alliedText
    ) {
        if (runningState == null || alliedTeam == null) {
            return;
        }
        String normalizedType = normalizeKeyPart(eventType);
        String normalizedText = normalizeText(alliedText);
        if (normalizedType.isBlank() || normalizedText.isBlank()) {
            return;
        }
        BROADCAST_SERVICE.broadcastPerspectiveOnce(
            runningState,
            KEY_PREFIX + ":" + normalizedType + ":" + EVENT_SEQUENCE.incrementAndGet(),
            alliedTeam,
            normalizedText,
            ""
        );
    }

    public static void broadcastToPerspective(
        RunningRuntimeState runningState,
        RoomTeamId alliedTeam,
        String eventType,
        String alliedText,
        String enemyText
    ) {
        if (runningState == null || alliedTeam == null) {
            return;
        }
        String normalizedType = normalizeKeyPart(eventType);
        String normalizedAlliedText = normalizeText(alliedText);
        String normalizedEnemyText = normalizeText(enemyText);
        if (normalizedType.isBlank() || (normalizedAlliedText.isBlank() && normalizedEnemyText.isBlank())) {
            return;
        }
        BROADCAST_SERVICE.broadcastPerspectiveOnce(
            runningState,
            KEY_PREFIX + ":" + normalizedType + ":" + EVENT_SEQUENCE.incrementAndGet(),
            alliedTeam,
            normalizedAlliedText,
            normalizedEnemyText
        );
    }

    public static void broadcastMissileCountdown(
        RunningRuntimeState runningState,
        RoomTeamId alliedTeam,
        int remainSeconds,
        BlockPos targetPos
    ) {
        if (runningState == null || alliedTeam == null || targetPos == null || remainSeconds <= 0) {
            return;
        }
        String coordinates = targetPos.getX() + "," + targetPos.getY() + "," + targetPos.getZ();
        broadcastToPerspective(
            runningState,
            alliedTeam,
            "missile_countdown",
            "导弹打击还有 " + remainSeconds + " 秒到达",
            "敌军导弹打击即将到达 坐标" + coordinates
        );
    }

    private static String resolvePlayerName(ServerPlayer player) {
        if (player == null || player.getGameProfile() == null || player.getGameProfile().getName() == null) {
            return "";
        }
        return player.getGameProfile().getName().trim();
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().replace(' ', '_');
    }
}
