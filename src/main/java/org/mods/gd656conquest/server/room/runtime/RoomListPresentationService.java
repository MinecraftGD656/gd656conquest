package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomJoinAccessPolicy;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.ServerText;
import org.mods.gd656conquest.server.social.party.PlayerPartyManager;

public final class RoomListPresentationService {
    private RoomListPresentationService() {
    }

    public static MutableComponent buildRoomListLine(RoomDefinition room, ServerPlayer viewer, RoomCoreRuntimeManager coreManager) {
        if (room == null) {
            return Component.empty();
        }
        int current = resolveRoomPlayerCount(room, coreManager);
        int max = room.commonConfig().matchingConfig().campAMaxPlayers()
            + room.commonConfig().matchingConfig().campBMaxPlayers();
        return Component.literal(room.roomId()).withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(room.displayName()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(resolveRoomStateDisplayName(room.lifecycleState())).withStyle(resolveRoomStateColor(room.lifecycleState())))
            .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(current + "/" + max).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(buildJoinAction(room, viewer, coreManager));
    }

    public static Component buildJoinAction(RoomDefinition room, ServerPlayer viewer, RoomCoreRuntimeManager coreManager) {
        boolean joinable = isJoinableForPlayer(room, viewer, coreManager);
        if (!joinable) {
            return Component.literal("[不可加入]").withStyle(ChatFormatting.GRAY);
        }
        return ServerText.action(new ClickActionSpec(
            "[加入对局]",
            ChatFormatting.GOLD,
            "/gdc action joinroom " + room.roomId(),
            "点击加入该房间"
        ));
    }

    public static boolean isJoinableForPlayer(RoomDefinition room, ServerPlayer player, RoomCoreRuntimeManager coreManager) {
        if (room == null || player == null || coreManager == null || player.server == null) {
            return false;
        }
        PlayerPartyManager partyManager = PlayerPartyManager.of(player.server);
        if (!partyManager.operationDeniedMessageForNonLeader(player.getUUID()).isBlank()) {
            return false;
        }
        if (coreManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            return false;
        }
        String rejoinRoomId = coreManager.findRejoinableRoomId(player.getUUID()).orElse(null);
        RoomLifecycleState state = room.lifecycleState();
        if (state == RoomLifecycleState.RUNNING) {
            return (rejoinRoomId != null && rejoinRoomId.equalsIgnoreCase(room.roomId()))
                || coreManager.canJoinInProgressRoom(player.getUUID(), room.roomId());
        }
        if (state == RoomLifecycleState.ENDING) {
            return false;
        }
        return RoomJoinAccessPolicy.evaluate(player, room, coreManager).allowed();
    }

    public static int resolveRoomPlayerCount(RoomDefinition room, RoomCoreRuntimeManager coreManager) {
        if (room == null || coreManager == null) {
            return 0;
        }
        if (room.lifecycleState() == RoomLifecycleState.MATCHING) {
            return coreManager.findMatchingState(room.roomId())
                .map(state -> state.getQueuePlayers().size())
                .orElse(0);
        }
        return coreManager.countRoomPlayers(room.roomId());
    }

    public static String resolveRoomStateDisplayName(RoomLifecycleState state) {
        if (state == null) {
            return "未知";
        }
        return switch (state) {
            case RUNNING -> "进行中";
            case MATCHING -> "匹配中";
            case ENDING -> "结算中";
            case MAINTENANCE -> "维护中";
            case DISABLED -> "禁用";
            case CLEANING -> "清理中";
            case SAVING -> "保存中";
            case IDLE -> "空闲";
        };
    }

    public static ChatFormatting resolveRoomStateColor(RoomLifecycleState state) {
        if (state == null) {
            return ChatFormatting.GRAY;
        }
        return switch (state) {
            case RUNNING -> ChatFormatting.GREEN;
            case MATCHING -> ChatFormatting.YELLOW;
            case ENDING -> ChatFormatting.GOLD;
            case MAINTENANCE -> ChatFormatting.BLUE;
            case DISABLED -> ChatFormatting.DARK_GRAY;
            case CLEANING, SAVING -> ChatFormatting.RED;
            case IDLE -> ChatFormatting.WHITE;
        };
    }
}
