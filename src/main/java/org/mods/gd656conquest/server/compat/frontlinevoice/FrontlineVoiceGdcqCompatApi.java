package org.mods.gd656conquest.server.compat.frontlinevoice;

import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.model.RoomVoicePack;
import org.mods.gd656conquest.server.room.runtime.audio.RoomTeamVoicePackResolver;

public final class FrontlineVoiceGdcqCompatApi {
    private FrontlineVoiceGdcqCompatApi() {
    }

    public static String resolveVoicePack(ServerPlayer player) {
        if (player == null || player.server == null) {
            return null;
        }
        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(player.server);
        String roomId = runtimeManager.findPlayerRoomId(player.getUUID()).orElse(null);
        RoomTeamId teamId = runtimeManager.findCurrentTeam(player.getUUID()).orElse(null);
        RoomDefinition room = roomId == null ? null : runtimeManager.findRoom(roomId).orElse(null);
        if (room == null || teamId == null) {
            return null;
        }
        RoomVoicePack voicePack = RoomTeamVoicePackResolver.resolve(room, teamId);
        return voicePack == null ? null : voicePack.id();
    }

    public static boolean isSameCamp(ServerPlayer left, ServerPlayer right) {
        if (left == null || right == null || left.server == null || right.server == null || left.server != right.server) {
            return false;
        }
        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(left.server);
        String leftRoomId = runtimeManager.findPlayerRoomId(left.getUUID()).orElse(null);
        String rightRoomId = runtimeManager.findPlayerRoomId(right.getUUID()).orElse(null);
        if (leftRoomId == null || !leftRoomId.equals(rightRoomId)) {
            return false;
        }
        RoomTeamId leftTeam = runtimeManager.findCurrentTeam(left.getUUID()).orElse(null);
        RoomTeamId rightTeam = runtimeManager.findCurrentTeam(right.getUUID()).orElse(null);
        return leftTeam != null && leftTeam == rightTeam;
    }

    public static boolean shouldDeferDeathCue(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        RoomCoreRuntimeManager runtimeManager = RoomCoreRuntimeManager.of(player.server);
        String roomId = runtimeManager.findPlayerRoomId(player.getUUID()).orElse(null);
        if (roomId == null) {
            return false;
        }
        RoomDefinition room = runtimeManager.findRoom(roomId).orElse(null);
        RunningRuntimeState state = runtimeManager.findRunningState(roomId).orElse(null);
        if (room == null || state == null) {
            return false;
        }
        if ((room.lifecycleState() != RoomLifecycleState.RUNNING && room.lifecycleState() != RoomLifecycleState.ENDING)
            || !state.getRoomPlayers().contains(player.getUUID())
            || !room.commonConfig().enablePlayerDowned()) {
            return false;
        }
        boolean bypassDowned = state.getDoubledRespawnPending().contains(player.getUUID())
            || state.getBoundaryDeathPendingPlayers().contains(player.getUUID());
        return !bypassDowned && !state.isInRespawnFlow(player.getUUID());
    }
}
