package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.room.core.RoomJoinResult;
import org.mods.gd656conquest.server.social.party.application.PartyRoomBridgeService;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class RoomSessionTransitionService {
    private static final Map<MinecraftServer, RoomSessionTransitionService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;

    private RoomSessionTransitionService(MinecraftServer server) {
        this.server = server;
    }

    public static RoomSessionTransitionService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, RoomSessionTransitionService::new);
        }
    }

    public JoinExecutionResult joinRoom(ServerPlayer operator, String roomId, boolean maintenance) {
        if (operator == null || operator.server == null || roomId == null || roomId.isBlank()) {
            return new JoinExecutionResult(false, "", 0, false, null, "");
        }
        Optional<JoinExecutionResult> partyResult = PartyRoomBridgeService.of(server)
            .tryJoinAsPartyLeader(operator, roomId, maintenance);
        if (partyResult.isPresent()) {
            return partyResult.get();
        }
        String soloJoinDeniedMessage = denySoloTransitionForPartyMember(operator, "你当前在队伍中，只有队长可以发起整队进入房间");
        if (!soloJoinDeniedMessage.isBlank()) {
            return new JoinExecutionResult(false, soloJoinDeniedMessage, 0, false, null, "");
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        RoomJoinAccessPolicy.JoinCheckResult operatorAccess = RoomJoinAccessPolicy.evaluate(
            operator,
            roomManager.findRoom(roomId).orElse(null),
            roomManager
        );
        if (!operatorAccess.allowed()) {
            return new JoinExecutionResult(false, operatorAccess.deniedMessage(), 0, false, null, "");
        }
        boolean effectiveMaintenance = operatorAccess.maintenance();
        long gameTick = server.getTickCount();
        JoinAttempt attempt = joinOneMember(roomManager, operator, roomId, effectiveMaintenance, gameTick);
        QuickAccessItemService.refreshHotbar(operator);
        return new JoinExecutionResult(
            attempt.success(),
            attempt.deniedMessage(),
            attempt.success() ? 1 : 0,
            false,
            attempt.joinResult(),
            ""
        );
    }

    public LeaveExecutionResult leaveSession(ServerPlayer operator, LeaveMode leaveMode) {
        if (operator == null || operator.server == null || leaveMode == null) {
            return new LeaveExecutionResult(false, "", 0, false, false, "");
        }
        Optional<LeaveExecutionResult> partyResult = PartyRoomBridgeService.of(server)
            .tryLeaveAsPartyLeader(operator, leaveMode);
        if (partyResult.isPresent()) {
            return partyResult.get();
        }
        String soloLeaveDeniedMessage = denySoloTransitionForPartyMember(
            operator,
            leaveMode == LeaveMode.RETURN_TO_HUB
                ? "你当前在队伍中，只有队长可以发起整队返回大厅"
                : "你当前在队伍中，只有队长可以发起整队离开房间"
        );
        if (!soloLeaveDeniedMessage.isBlank()) {
            return new LeaveExecutionResult(false, soloLeaveDeniedMessage, 0, false, false, "");
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        LeaveAttempt attempt = leaveOneMember(roomManager, operator, leaveMode);
        QuickAccessItemService.refreshHotbar(operator);
        return new LeaveExecutionResult(attempt.changed(), "", attempt.changed() ? 1 : 0, false, attempt.hadSession(), attempt.message());
    }

    public ReturnToHubExecutionResult returnToHub(ServerPlayer operator) {
        if (operator == null || operator.server == null) {
            return new ReturnToHubExecutionResult(false, "", 0, false, false, "");
        }
        Optional<ReturnToHubExecutionResult> partyResult = PartyRoomBridgeService.of(server)
            .tryReturnToHubAsPartyLeader(operator);
        if (partyResult.isPresent()) {
            return partyResult.get();
        }
        String soloHubDeniedMessage = denySoloTransitionForPartyMember(operator, "你当前在队伍中，只有队长可以发起整队返回大厅");
        if (!soloHubDeniedMessage.isBlank()) {
            return new ReturnToHubExecutionResult(false, soloHubDeniedMessage, 0, false, false, "");
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(server);
        ParkourRuntimeManager parkourManager = ParkourRuntimeManager.of(server);
        ReturnToHubAttempt attempt = returnOneMemberToHub(roomManager, rangeManager, parkourManager, operator);
        QuickAccessItemService.refreshHotbar(operator);
        return new ReturnToHubExecutionResult(attempt.changed(), "", attempt.changed() ? 1 : 0, false, attempt.hadSession(), attempt.message());
    }

    private String denySoloTransitionForPartyMember(ServerPlayer operator, String deniedMessage) {
        if (operator == null || operator.server == null) {
            return "";
        }
        PartyAggregate party = InMemoryPartyRepository.of(server).findByPlayerId(operator.getUUID()).orElse(null);
        if (party == null || party.isLeader(operator.getUUID())) {
            return "";
        }
        return deniedMessage == null ? "" : deniedMessage;
    }

    private JoinAttempt joinOneMember(
        RoomCoreRuntimeManager roomManager,
        ServerPlayer member,
        String roomId,
        boolean maintenance,
        long gameTick
    ) {
        RoomJoinAccessPolicy.JoinCheckResult access = RoomJoinAccessPolicy.evaluate(
            member,
            roomManager.findRoom(roomId).orElse(null),
            roomManager
        );
        if (!access.allowed()) {
            return new JoinAttempt(false, null, member.getScoreboardName() + ": " + access.deniedMessage());
        }
        UUID memberId = member.getUUID();
        Optional<String> currentRoom = roomManager.findPlayerRoomId(memberId);
        if (currentRoom.filter(roomId::equalsIgnoreCase).isPresent()) {
            return new JoinAttempt(true, null, "");
        }
        ParkourRuntimeManager.of(server).leaveParkourForTransfer(member);
        if (currentRoom.isPresent()) {
            roomManager.sendPlayerToHub(member);
        }
        roomManager.leaveMatching(memberId);
        Optional<RoomJoinResult> joinResult = maintenance
            ? Optional.empty()
            : roomManager.joinMatching(memberId, roomId, gameTick);
        boolean joined = maintenance
            ? roomManager.joinMaintenanceRoom(roomId, member)
            : joinResult.isPresent();
        return new JoinAttempt(joined, joinResult.orElse(null), "");
    }

    private LeaveAttempt leaveOneMember(RoomCoreRuntimeManager roomManager, ServerPlayer member, LeaveMode leaveMode) {
        UUID memberId = member.getUUID();
        boolean hadSession = roomManager.findPlayerRoomId(memberId).isPresent();
        if (leaveMode == LeaveMode.RETURN_TO_HUB) {
            String result = hadSession ? roomManager.sendPlayerToHub(member) : "当前不在任何房间或匹配队列中";
            return new LeaveAttempt(hadSession, hadSession, result);
        }
        if (hadSession) {
            roomManager.removePlayerFromRoom(memberId);
        }
        roomManager.leaveMatching(memberId);
        return new LeaveAttempt(hadSession, hadSession, hadSession ? "已离开当前房间" : "已离开当前匹配队列");
    }

    private ReturnToHubAttempt returnOneMemberToHub(
        RoomCoreRuntimeManager roomManager,
        RangeRuntimeManager rangeManager,
        ParkourRuntimeManager parkourManager,
        ServerPlayer member
    ) {
        UUID memberId = member.getUUID();
        if (roomManager.isInitialized() && roomManager.findPlayerRoomId(memberId).isPresent()) {
            return new ReturnToHubAttempt(true, true, roomManager.sendPlayerToHub(member));
        }
        if (rangeManager.isInRange(memberId)) {
            return new ReturnToHubAttempt(true, true, rangeManager.leaveRange(member));
        }
        if (parkourManager.isInParkour(memberId)) {
            return new ReturnToHubAttempt(true, true, parkourManager.leaveParkour(member));
        }
        return new ReturnToHubAttempt(false, false, "当前不在对局、靶场或跑酷中");
    }

    public enum LeaveMode {
        DETACH_ONLY,
        RETURN_TO_HUB
    }

    public record JoinExecutionResult(
        boolean success,
        String deniedMessage,
        int handledCount,
        boolean groupAction,
        RoomJoinResult operatorJoinResult,
        String operatorMessage
    ) {
        public boolean denied() {
            return deniedMessage != null && !deniedMessage.isBlank();
        }
    }

    public record LeaveExecutionResult(
        boolean success,
        String deniedMessage,
        int changedCount,
        boolean groupAction,
        boolean operatorHadSession,
        String operatorMessage
    ) {
        public boolean denied() {
            return deniedMessage != null && !deniedMessage.isBlank();
        }
    }

    public record ReturnToHubExecutionResult(
        boolean success,
        String deniedMessage,
        int changedCount,
        boolean groupAction,
        boolean operatorHadSession,
        String operatorMessage
    ) {
        public boolean denied() {
            return deniedMessage != null && !deniedMessage.isBlank();
        }
    }

    private record JoinAttempt(boolean success, RoomJoinResult joinResult, String deniedMessage) {
    }

    private record LeaveAttempt(boolean changed, boolean hadSession, String message) {
    }

    private record ReturnToHubAttempt(boolean changed, boolean hadSession, String message) {
    }
}
