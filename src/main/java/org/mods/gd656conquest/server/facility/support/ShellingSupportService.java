package org.mods.gd656conquest.server.facility.support;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.shelling.ShellingDataModel;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.runtime.RoomShellingRuntimeService;
import org.mods.gd656conquest.server.shared.util.ItemStackSnbtCodec;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

public final class ShellingSupportService {
    private static final List<String> SHELLING_DEPLOY_VOICE_IDS = List.of(
        "gd656conquest:voice.shelling.artillery_friendly_1",
        "gd656conquest:voice.shelling.artillery_friendly_2",
        "gd656conquest:voice.shelling.artillery_friendly_3"
    );

    private ShellingSupportService() {
    }

    public static void handleStartRequest(ServerPlayer player, String rawShellingId) {
        if (player == null || player.server == null) {
            return;
        }
        String shellingId = normalizeShellingId(rawShellingId);
        if (shellingId.isBlank()) {
            ServerMessageSender.system(player, "阵地支援", "无效的炮击 ID", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        String roomId = roomManager.findPlayerRoomId(player.getUUID()).orElse("");
        RoomDefinition room = roomManager.findRoom(roomId).orElse(null);
        if (room == null || room.lifecycleState() != RoomLifecycleState.RUNNING) {
            ServerMessageSender.system(player, "阵地支援", "当前不在进行中的对局", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        RunningChecks checks = resolveRunningChecks(player, roomManager, room, shellingId);
        if (!checks.success()) {
            SupportUiService.sendSnapshot(player);
            return;
        }
        if (!SuperbWarfareCompatBridge.isAvailable()) {
            ServerMessageSender.system(player, "阵地支援", "未检测到 SuperbWarfare，当前无法发起炮击", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        String originalSlot9ItemSnbt = ItemStackSnbtCodec.serialize(
            player.getInventory().getItem(RoomShellingRuntimeService.SHELLING_INDICATOR_HOTBAR_SLOT)
        );
        RunningRuntimeState runningState = roomManager.findRunningState(room.roomId()).orElse(null);
        RoomShellingRuntimeService shellingRuntimeService = roomManager.shellingRuntimeService();
        if (runningState == null || shellingRuntimeService == null) {
            ServerMessageSender.system(player, "阵地支援", "当前无法进入炮击流程", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        RoomShellingRuntimeService.StartRequestResult result = roomManager.startShellingRequest(
            player,
            shellingId,
            checks.entry().getCost(),
            originalSlot9ItemSnbt,
            player.server.getTickCount()
        );
        if (!result.success()) {
            handleStartResult(player, roomManager, result, null);
            SupportUiService.sendSnapshot(player);
            return;
        }
        SupportScoreService.ConsumptionResult consumeResult = roomManager.tryConsumePlayerSupportScore(player.getUUID(), Math.max(0, checks.entry().getCost()));
        if (!consumeResult.success()) {
            shellingRuntimeService.cancelShellingRequest(room, runningState, player.getUUID());
            ServerMessageSender.system(player, "阵地支援", "剩余分数不足", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        boolean indicatorIssued = shellingRuntimeService.issueShellingIndicator(
            room,
            runningState,
            player,
            result.request()
        );
        if (!indicatorIssued) {
            shellingRuntimeService.cancelShellingRequest(room, runningState, player.getUUID());
            roomManager.refundPlayerSupportScore(player.getUUID(), Math.max(0, checks.entry().getCost()));
            ServerMessageSender.system(player, "阵地支援", "炮击指示器发放失败", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        handleStartResult(player, roomManager, result, consumeResult);
        SupportUiService.sendSnapshot(player);
    }

    private static String normalizeShellingId(String rawShellingId) {
        return rawShellingId == null ? "" : rawShellingId.trim();
    }

    public static void handleConfirmTarget(ServerPlayer player, String rawShellingId, BlockPos requestedTargetPos) {
        if (player == null || player.server == null) {
            return;
        }
        ManagedIndicatorContext context = resolveManagedIndicatorContext(player, normalizeShellingId(rawShellingId));
        if (context == null) {
            return;
        }
        String shellingId = context.request().shellingId();
        BlockPos serverTargetPos = SuperbWarfareCompatBridge.getManagedTargetPos(context.stack());
        BlockPos resolvedTargetPos = serverTargetPos != null ? serverTargetPos : requestedTargetPos;
        if (resolvedTargetPos == null) {
            ServerMessageSender.system(player, "阵地支援", "请先标定炮击坐标", ChatFormatting.YELLOW);
            return;
        }
        if (!context.roomManager().updateShellingTarget(player, resolvedTargetPos)) {
            return;
        }
        ShellingDataModel.ShellingEntry shellingEntry = ConquestDataManager.of(player.server)
            .shellings()
            .get()
            .getShellingsById()
            .get(shellingId);
        if (shellingEntry == null) {
            ServerMessageSender.system(player, "阵地支援", "炮击模板不存在", ChatFormatting.RED);
            return;
        }
        RoomShellingRuntimeService.ConfirmExecutionResult result = context.shellingRuntimeService().executeConfirmedShelling(
            context.room(),
            context.runningState(),
            player,
            shellingEntry,
            context.roomManager().coreService().markerRuntimeService(),
            player.server.getTickCount()
        );
        if (!result.success()) {
            ServerMessageSender.system(player, "阵地支援", result.message(), ChatFormatting.RED);
            return;
        }
        playDeployVoice(player);
        SupportHudSubtitleService.broadcastShellingStarted(
            context.runningState(),
            context.request().teamId(),
            player,
            resolvedTargetPos,
            shellingEntry.resolveImpactType().displayName()
        );
        ServerMessageSender.system(
            player,
            "阵地支援",
            "已确认炮击坐标 [" + resolvedTargetPos.getX() + ", " + resolvedTargetPos.getY() + ", " + resolvedTargetPos.getZ() + "]",
            ChatFormatting.GOLD
        );
        SupportUiService.sendSnapshot(player);
    }

    public static boolean handleIndicatorCancelRequest(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        ManagedIndicatorContext context = resolveManagedIndicatorContext(player, "");
        if (context == null) {
            return false;
        }
        context.shellingRuntimeService().abortShellingRequest(
            context.room(),
            context.runningState(),
            player,
            player.getUUID(),
            RoomShellingRuntimeService.AbortReason.PLAYER_CANCELLED
        );
        SupportUiService.sendSnapshot(player);
        return true;
    }

    private static ManagedIndicatorContext resolveManagedIndicatorContext(ServerPlayer player, String expectedShellingId) {
        if (player == null || player.server == null) {
            return null;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        String roomId = roomManager.findPlayerRoomId(player.getUUID()).orElse("");
        RoomDefinition room = roomManager.findRoom(roomId).orElse(null);
        RunningRuntimeState runningState = room == null ? null : roomManager.findRunningState(room.roomId()).orElse(null);
        RoomShellingRuntimeService shellingRuntimeService = roomManager.shellingRuntimeService();
        if (room == null || runningState == null || shellingRuntimeService == null || room.lifecycleState() != RoomLifecycleState.RUNNING) {
            return null;
        }
        RunningRuntimeState.ShellingRequestState request = runningState.getActiveShellingRequest(player.getUUID());
        if (request == null || request.shellingId().isBlank()) {
            return null;
        }
        if (!expectedShellingId.isBlank() && !expectedShellingId.equals(request.shellingId())) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        if (!SuperbWarfareCompatBridge.isManagedArtilleryIndicator(stack)) {
            return null;
        }
        if (!room.roomId().equals(SuperbWarfareCompatBridge.getManagedShellingRoomId(stack))) {
            return null;
        }
        if (!request.shellingId().equals(SuperbWarfareCompatBridge.getManagedShellingId(stack))) {
            return null;
        }
        UUID taggedPlayerId = SuperbWarfareCompatBridge.getManagedShellingPlayerId(stack);
        if (taggedPlayerId != null && !taggedPlayerId.equals(player.getUUID())) {
            return null;
        }
        return new ManagedIndicatorContext(roomManager, room, runningState, shellingRuntimeService, request, stack);
    }

    private static RunningChecks resolveRunningChecks(
        ServerPlayer player,
        RoomCoreRuntimeManager roomManager,
        RoomDefinition room,
        String shellingId
    ) {
        if (player == null || player.server == null || roomManager == null || room == null) {
            return RunningChecks.failure();
        }
        RoomTeamId teamId = roomManager.findCurrentTeam(player.getUUID()).orElse(null);
        if (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B) {
            ServerMessageSender.system(player, "阵地支援", "当前未加入可用阵营", ChatFormatting.RED);
            return RunningChecks.failure();
        }
        if (!player.isAlive() || player.isSpectator()) {
            ServerMessageSender.system(player, "阵地支援", "当前状态无法发起炮击", ChatFormatting.RED);
            return RunningChecks.failure();
        }
        List<String> allowedShellingIds = resolveAllowedShellingIds(room, teamId);
        if (allowedShellingIds.stream().noneMatch(shellingId::equalsIgnoreCase)) {
            ServerMessageSender.system(player, "阵地支援", "当前阵营未开放该炮击", ChatFormatting.RED);
            return RunningChecks.failure();
        }
        ShellingDataModel.ShellingEntry entry = ConquestDataManager.of(player.server)
            .shellings()
            .get()
            .getShellingsById()
            .get(shellingId);
        if (entry == null || entry.getId().isBlank()) {
            ServerMessageSender.system(player, "阵地支援", "炮击数据不存在", ChatFormatting.RED);
            return RunningChecks.failure();
        }
        int remainingScore = roomManager.getPlayerRemainingSupportScore(player.getUUID());
        if (remainingScore < entry.getCost()) {
            ServerMessageSender.system(player, "阵地支援", "剩余分数不足", ChatFormatting.RED);
            return RunningChecks.failure();
        }
        return RunningChecks.success(entry);
    }

    private static void handleStartResult(
        ServerPlayer player,
        RoomCoreRuntimeManager roomManager,
        RoomShellingRuntimeService.StartRequestResult result,
        SupportScoreService.ConsumptionResult consumeResult
    ) {
        if (player == null || result == null) {
            return;
        }
        if (result.success()) {
            int remainingScore = consumeResult == null ? roomManager.getPlayerRemainingSupportScore(player.getUUID()) : consumeResult.remainingScore();
            ServerMessageSender.system(player, "阵地支援", "已进入炮击坐标选定，剩余分数 " + Math.max(0, remainingScore), ChatFormatting.GREEN);
            return;
        }
        RoomShellingRuntimeService.StartFailureReason reason = result.failureReason();
        if (reason == RoomShellingRuntimeService.StartFailureReason.SHELLING_BUSY) {
            String holderName = resolvePlayerName(player, result.occupiedByPlayerId());
            String actionText = result.occupiedByActiveSequence() ? "正在释放该炮击" : "正在部署该炮击";
            String message = holderName.isBlank()
                ? "当前同阵营已有玩家" + actionText
                : holderName + actionText;
            ServerMessageSender.system(player, "阵地支援", message, ChatFormatting.RED);
            return;
        }
        if (reason == RoomShellingRuntimeService.StartFailureReason.ALREADY_ACTIVE) {
            ServerMessageSender.system(player, "阵地支援", "你已处于炮击流程中", ChatFormatting.YELLOW);
            return;
        }
        if (reason == RoomShellingRuntimeService.StartFailureReason.PLAYER_NOT_ELIGIBLE) {
            ServerMessageSender.system(player, "阵地支援", "当前状态无法发起炮击", ChatFormatting.RED);
            return;
        }
        if (reason == RoomShellingRuntimeService.StartFailureReason.INVALID_TEAM) {
            ServerMessageSender.system(player, "阵地支援", "当前未加入可用阵营", ChatFormatting.RED);
            return;
        }
        ServerMessageSender.system(player, "阵地支援", "当前无法进入炮击流程", ChatFormatting.RED);
    }

    private static List<String> resolveAllowedShellingIds(RoomDefinition room, RoomTeamId teamId) {
        if (room == null || teamId == null) {
            return List.of();
        }
        List<String> rawValues = teamId == RoomTeamId.CAMP_A
            ? room.commonConfig().supportCampAShellings()
            : room.commonConfig().supportCampBShellings();
        return rawValues == null ? List.of() : rawValues.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .sorted(Comparator.comparing(String::toLowerCase))
            .toList();
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

    private static void playDeployVoice(ServerPlayer player) {
        if (player == null || SHELLING_DEPLOY_VOICE_IDS.isEmpty()) {
            return;
        }
        String soundId = SHELLING_DEPLOY_VOICE_IDS.get(ThreadLocalRandom.current().nextInt(SHELLING_DEPLOY_VOICE_IDS.size()));
        if (soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientShortSoundPacket.oneShot(soundId, 1.0F, 1.0F));
    }

    private record RunningChecks(boolean success, ShellingDataModel.ShellingEntry entry) {
        private static RunningChecks success(ShellingDataModel.ShellingEntry entry) {
            return new RunningChecks(true, entry);
        }

        private static RunningChecks failure() {
            return new RunningChecks(false, null);
        }
    }

    private record ManagedIndicatorContext(
        RoomCoreRuntimeManager roomManager,
        RoomDefinition room,
        RunningRuntimeState runningState,
        RoomShellingRuntimeService shellingRuntimeService,
        RunningRuntimeState.ShellingRequestState request,
        ItemStack stack
    ) {
    }
}
