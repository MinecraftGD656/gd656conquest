package org.mods.gd656conquest.server.facility.quickaccess;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.facility.support.SupportUiService;
import org.mods.gd656conquest.server.loadout.LoadoutUiService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomSessionTransitionService;
import org.mods.gd656conquest.server.room.core.RoomJoinResult;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.runtime.RoomListPresentationService;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.social.party.presentation.PartyActionController;
import org.mods.gd656conquest.server.social.party.presentation.PartyUiFacade;
import org.mods.gd656conquest.server.title.PlayerTitleService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class QuickAccessItemService {
    private static final int HOTBAR_QUICK_JOIN = 0;
    private static final int HOTBAR_ROOM_LIST = 1;
    private static final int HOTBAR_TEAM = 2;
    private static final int HOTBAR_MY_TITLE = 6;
    private static final int HOTBAR_LOADOUT = 7;
    private static final int HOTBAR_LEAVE_OR_RANGE = 8;

    private QuickAccessItemService() {
    }

    public enum Action {
        QUICK_JOIN,
        ROOM_LIST,
        PARTY_HELP,
        MY_TITLE,
        LOADOUT_GUIDE,
        SUPPORT,
        ENTER_RANGE,
        LEAVE_SESSION,
        LEAVE_PARTY
    }

    public static void onPlayerTick(ServerPlayer player) {
        QuickAccessInventoryPolicyService.onPlayerTick(player);
    }

    public static void refreshHotbar(ServerPlayer player) {
        QuickAccessInventoryPolicyService.refreshForCurrentContext(player);
    }

    public static boolean handleUse(ServerPlayer player, Action action) {
        if (player == null || player.server == null || action == null) {
            return false;
        }
        boolean success = switch (action) {
            case QUICK_JOIN -> quickJoin(player);
            case ROOM_LIST -> showRoomList(player);
            case PARTY_HELP -> showPartyHelp(player);
            case MY_TITLE -> showMyTitles(player);
            case LOADOUT_GUIDE -> showLoadoutGuide(player);
            case SUPPORT -> openSupportUi(player);
            case ENTER_RANGE -> enterRange(player);
            case LEAVE_SESSION -> leaveSession(player);
            case LEAVE_PARTY -> leaveParty(player);
        };
        if (shouldRefreshAfterUse(action)) {
            refreshHotbar(player);
        }
        return success;
    }

    private static boolean quickJoin(ServerPlayer player) {
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(player.server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(player.server);
        ParkourRuntimeManager parkourManager = ParkourRuntimeManager.of(player.server);
        if (coreManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            sendError(player, "你已在对局中，无法使用快速加入");
            return false;
        }
        if (rangeManager.isInRange(player.getUUID())) {
            sendError(player, "你已在靶场中，请先离开靶场");
            return false;
        }
        if (parkourManager.isInParkour(player.getUUID())) {
            parkourManager.leaveParkourForTransfer(player);
        }
        List<RoomDefinition> joinables = coreManager.listRooms().stream()
            .filter(room -> RoomListPresentationService.isJoinableForPlayer(room, player, coreManager))
            .filter(room -> room.lifecycleState() != RoomLifecycleState.MAINTENANCE)
            .sorted(Comparator
                .comparingInt((RoomDefinition room) -> RoomListPresentationService.resolveRoomPlayerCount(room, coreManager)).reversed()
                .thenComparing(RoomDefinition::roomId, String.CASE_INSENSITIVE_ORDER))
            .toList();
        if (joinables.isEmpty()) {
            sendWarn(player, "当前没有可加入的房间");
            return false;
        }
        RoomDefinition target = selectQuickJoinTarget(joinables, coreManager, player.getUUID());
        if (target == null) {
            sendWarn(player, "当前没有可加入的房间");
            return false;
        }
        RoomSessionTransitionService.JoinExecutionResult result = RoomSessionTransitionService.of(player.server)
            .joinRoom(player, target.roomId(), target.lifecycleState() == RoomLifecycleState.MAINTENANCE);
        if (result.denied()) {
            sendError(player, result.deniedMessage());
            return false;
        }
        boolean joined = result.success();
        String message = joined
            ? (target.lifecycleState() == RoomLifecycleState.MAINTENANCE
                ? "已进入维护房间: " + target.displayName()
                : (result.groupAction() && result.operatorMessage() != null && !result.operatorMessage().isBlank()
                    ? result.operatorMessage()
                    : describeJoinSuccess(target, result.operatorJoinResult())))
            : "加入房间失败: " + target.displayName();
        sendSystem(player, message, joined ? ChatFormatting.AQUA : ChatFormatting.RED);
        return joined;
    }

    private static boolean showRoomList(ServerPlayer player) {
        LoadoutUiService.sendSnapshot(player, true, 1);
        return true;
    }

    private static boolean showMyTitles(ServerPlayer player) {
        PlayerTitleService.of(player.server).sendMyTitleList(player);
        return true;
    }

    private static boolean showPartyHelp(ServerPlayer player) {
        return PartyActionController.openHelp(player);
    }

    private static boolean showLoadoutGuide(ServerPlayer player) {
        LoadoutUiService.sendSnapshot(player, true);
        return true;
    }

    private static boolean openSupportUi(ServerPlayer player) {
        SupportUiService.open(player);
        return true;
    }

    private static boolean enterRange(ServerPlayer player) {
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(player.server);
        if (coreManager.findPlayerRoomId(player.getUUID()).isPresent()) {
            sendError(player, "你已在对局中，无法进入靶场");
            return false;
        }
        if (ParkourRuntimeManager.of(player.server).isInParkour(player.getUUID())) {
            ParkourRuntimeManager.of(player.server).leaveParkourForTransfer(player);
        }
        String result = RangeRuntimeManager.of(player.server).joinRange(player);
        sendInfo(player, result);
        return true;
    }

    private static boolean leaveSession(ServerPlayer player) {
        RoomSessionTransitionService.ReturnToHubExecutionResult result = RoomSessionTransitionService.of(player.server)
            .returnToHub(player);
        if (result.denied()) {
            sendError(player, result.deniedMessage());
            return false;
        }
        if (!result.success()) {
            sendError(player, "当前不在对局、靶场或跑酷中，无法离开");
            return false;
        }
        sendInfo(player, result.operatorMessage());
        return true;
    }

    private static boolean leaveParty(ServerPlayer player) {
        return PartyActionController.leaveFromQuickItem(player);
    }

    private static void sendInfo(ServerPlayer player, String message) {
        sendSystem(player, message, ChatFormatting.AQUA);
    }

    private static void sendWarn(ServerPlayer player, String message) {
        sendSystem(player, message, ChatFormatting.YELLOW);
    }

    private static void sendError(ServerPlayer player, String message) {
        sendSystem(player, message, ChatFormatting.RED);
    }

    private static void sendSystem(ServerPlayer player, String message, ChatFormatting color) {
        ServerMessageSender.system(player, "快捷加入", message, color);
    }

    private static boolean shouldRefreshAfterUse(Action action) {
        return action == Action.ROOM_LIST
            || action == Action.LOADOUT_GUIDE
            || action == Action.LEAVE_PARTY;
    }

    private static RoomDefinition selectQuickJoinTarget(List<RoomDefinition> joinables, RoomCoreRuntimeManager coreManager, java.util.UUID playerId) {
        if (joinables == null || joinables.isEmpty()) {
            return null;
        }
        String rejoinRoomId = coreManager.findRejoinableRoomId(playerId).orElse(null);
        if (rejoinRoomId != null) {
            for (RoomDefinition room : joinables) {
                if (room != null && rejoinRoomId.equalsIgnoreCase(room.roomId())) {
                    return room;
                }
            }
        }
        int maxPlayers = 0;
        for (RoomDefinition room : joinables) {
            if (room == null) {
                continue;
            }
            int count = RoomListPresentationService.resolveRoomPlayerCount(room, coreManager);
            if (count > maxPlayers) {
                maxPlayers = count;
            }
        }
        List<RoomDefinition> candidates = new ArrayList<>();
        for (RoomDefinition room : joinables) {
            if (room != null && RoomListPresentationService.resolveRoomPlayerCount(room, coreManager) == maxPlayers) {
                candidates.add(room);
            }
        }
        if (candidates.isEmpty()) {
            return joinables.get(0);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private static String describeJoinSuccess(RoomDefinition room, RoomJoinResult result) {
        if (room == null || result == null) {
            return room == null ? "加入成功" : "已加入房间匹配队列: " + room.displayName();
        }
        return switch (result.kind()) {
            case MIDJOIN_RUNNING -> "已加入进行中的对局: " + room.displayName();
            case MIDJOIN_ENDING -> "已加入当前对局结算: " + room.displayName();
            case REJOIN_RUNNING -> "已重连进行中的对局: " + room.displayName();
            case REJOIN_ENDING -> "已重连当前对局结算: " + room.displayName();
            case MATCHING -> "已加入房间匹配队列: " + room.displayName();
        };
    }

    public static boolean isUtilityItem(net.minecraft.world.item.Item item) {
        return QuickAccessInventoryPolicyService.isQuickAccessItem(item)
            || item == org.mods.gd656conquest.common.item.ConquestItems.MEDIC_BAG.get()
            || item == org.mods.gd656conquest.common.item.ConquestItems.AMMO_BAG.get()
            || item == org.mods.gd656conquest.common.item.ConquestItems.RADIO_BEACON.get()
            || item == org.mods.gd656conquest.common.item.ConquestItems.SUPPORT_ITEM.get();
    }
}
