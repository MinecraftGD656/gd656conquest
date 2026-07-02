package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.room.core.RoomJoinResult;
import org.mods.gd656conquest.server.room.core.RoomGroupPlacementPlan;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.social.party.PlayerPartyManager;

import java.util.ArrayList;
import java.util.List;
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
            return new JoinExecutionResult(false, "", 0, false, null);
        }
        PlayerPartyManager partyManager = PlayerPartyManager.of(server);
        String denied = partyManager.operationDeniedMessageForNonLeader(operator.getUUID());
        if (!denied.isBlank()) {
            return new JoinExecutionResult(false, denied, 0, false, null);
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        RoomJoinAccessPolicy.JoinCheckResult operatorAccess = RoomJoinAccessPolicy.evaluate(
            operator,
            roomManager.findRoom(roomId).orElse(null),
            roomManager
        );
        if (!operatorAccess.allowed()) {
            return new JoinExecutionResult(false, operatorAccess.deniedMessage(), 0, false, null);
        }
        boolean effectiveMaintenance = operatorAccess.maintenance();
        long gameTick = server.getTickCount();
        if (partyManager.isLeader(operator.getUUID())) {
            List<ServerPlayer> members = resolveOnlinePartyMembers(partyManager, operator.getUUID());
            GroupJoinPlacement placement = effectiveMaintenance ? GroupJoinPlacement.none() : resolveGroupJoinPlacement(roomManager, roomId, members);
            if (!effectiveMaintenance && placement == null) {
                refreshMembers(members);
                return new JoinExecutionResult(false, "", 0, true, null);
            }
            List<ServerPlayer> transitionedMembers = new ArrayList<>();
            int handled = 0;
            for (ServerPlayer member : members) {
                JoinAttempt attempt = joinOneMember(roomManager, member, roomId, effectiveMaintenance, gameTick);
                if (!attempt.success()) {
                    rollbackGroupJoin(roomManager, transitionedMembers, roomId);
                    refreshMembers(members);
                    return new JoinExecutionResult(false, attempt.deniedMessage(), 0, true, null);
                }
                transitionedMembers.add(member);
                if (placement != null && !roomManager.applyGroupPlacement(roomId, member.getUUID(), placement.teamId(), placement.squadLabel(), gameTick)) {
                    rollbackGroupJoin(roomManager, transitionedMembers, roomId);
                    refreshMembers(members);
                    return new JoinExecutionResult(false, "", 0, true, null);
                }
                handled++;
            }
            refreshMembers(members);
            return new JoinExecutionResult(handled > 0, "", handled, true, null);
        }
        JoinAttempt attempt = joinOneMember(roomManager, operator, roomId, effectiveMaintenance, gameTick);
        QuickAccessItemService.refreshHotbar(operator);
        return new JoinExecutionResult(
            attempt.success(),
            attempt.deniedMessage(),
            attempt.success() ? 1 : 0,
            false,
            attempt.joinResult()
        );
    }

    public LeaveExecutionResult leaveSession(ServerPlayer operator, LeaveMode leaveMode) {
        if (operator == null || operator.server == null || leaveMode == null) {
            return new LeaveExecutionResult(false, "", 0, false, false, "");
        }
        PlayerPartyManager partyManager = PlayerPartyManager.of(server);
        String denied = partyManager.operationDeniedMessageForNonLeader(operator.getUUID());
        if (!denied.isBlank()) {
            return new LeaveExecutionResult(false, denied, 0, false, false, "");
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        if (partyManager.isLeader(operator.getUUID())) {
            int changed = 0;
            for (UUID memberId : partyManager.getPartyMembers(operator.getUUID())) {
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member == null) {
                    continue;
                }
                LeaveAttempt attempt = leaveOneMember(roomManager, member, leaveMode);
                if (attempt.changed()) {
                    changed++;
                }
                QuickAccessItemService.refreshHotbar(member);
            }
            return new LeaveExecutionResult(changed > 0, "", changed, true, false, "");
        }
        LeaveAttempt attempt = leaveOneMember(roomManager, operator, leaveMode);
        QuickAccessItemService.refreshHotbar(operator);
        return new LeaveExecutionResult(attempt.changed(), "", attempt.changed() ? 1 : 0, false, attempt.hadSession(), attempt.message());
    }

    public ReturnToHubExecutionResult returnToHub(ServerPlayer operator) {
        if (operator == null || operator.server == null) {
            return new ReturnToHubExecutionResult(false, "", 0, false, false, "");
        }
        PlayerPartyManager partyManager = PlayerPartyManager.of(server);
        String denied = partyManager.operationDeniedMessageForNonLeader(operator.getUUID());
        if (!denied.isBlank()) {
            return new ReturnToHubExecutionResult(false, denied, 0, false, false, "");
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(server);
        ParkourRuntimeManager parkourManager = ParkourRuntimeManager.of(server);
        if (partyManager.isLeader(operator.getUUID())) {
            int changed = 0;
            for (UUID memberId : partyManager.getPartyMembers(operator.getUUID())) {
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member == null) {
                    continue;
                }
                if (returnOneMemberToHub(roomManager, rangeManager, parkourManager, member).changed()) {
                    changed++;
                }
                QuickAccessItemService.refreshHotbar(member);
            }
            return new ReturnToHubExecutionResult(changed > 0, "", changed, true, false, "");
        }
        ReturnToHubAttempt attempt = returnOneMemberToHub(roomManager, rangeManager, parkourManager, operator);
        QuickAccessItemService.refreshHotbar(operator);
        return new ReturnToHubExecutionResult(attempt.changed(), "", attempt.changed() ? 1 : 0, false, attempt.hadSession(), attempt.message());
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

    private List<ServerPlayer> resolveOnlinePartyMembers(PlayerPartyManager partyManager, UUID operatorId) {
        List<ServerPlayer> members = new ArrayList<>();
        for (UUID memberId : partyManager.getPartyMembers(operatorId)) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    private GroupJoinPlacement resolveGroupJoinPlacement(
        RoomCoreRuntimeManager roomManager,
        String roomId,
        List<ServerPlayer> members
    ) {
        if (roomManager == null || roomId == null || roomId.isBlank() || members == null || members.isEmpty()) {
            return null;
        }
        for (ServerPlayer member : members) {
            if (member == null) {
                continue;
            }
            UUID memberId = member.getUUID();
            if (roomManager.findPlayerRoomId(memberId).filter(roomId::equalsIgnoreCase).isEmpty()) {
                continue;
            }
            Optional<RoomGroupPlacementPlan> anchorPlanOptional = roomManager.resolvePlayerPlacement(roomId, memberId);
            if (anchorPlanOptional.isEmpty()) {
                continue;
            }
            RoomGroupPlacementPlan anchorPlan = anchorPlanOptional.get();
            RoomTeamId teamId = anchorPlan.teamId();
            if (teamId == null) {
                return GroupJoinPlacement.none();
            }
            int requiredSlots = 0;
            for (ServerPlayer candidate : members) {
                if (candidate == null) {
                    continue;
                }
                Optional<String> candidateRoom = roomManager.findPlayerRoomId(candidate.getUUID());
                if (candidateRoom.filter(roomId::equalsIgnoreCase).isPresent()) {
                    RoomTeamId candidateTeam = roomManager.findCurrentTeam(candidate.getUUID()).orElse(null);
                    if (candidateTeam == teamId) {
                        continue;
                    }
                }
                requiredSlots++;
            }
            if (!roomManager.canFitGroupInTeam(roomId, teamId, requiredSlots)) {
                return null;
            }
            String squadLabel = anchorPlan.squadLabel();
            if (squadLabel == null || squadLabel.isBlank()) {
                squadLabel = roomManager.reserveSquadLabel(roomId, teamId).orElse("");
            }
            return new GroupJoinPlacement(teamId, squadLabel);
        }
        RoomGroupPlacementPlan planned = roomManager.planGroupJoinPlacement(roomId, members.size()).orElse(null);
        if (planned == null) {
            return null;
        }
        return new GroupJoinPlacement(planned.teamId(), planned.squadLabel());
    }

    private void rollbackGroupJoin(RoomCoreRuntimeManager roomManager, List<ServerPlayer> members, String roomId) {
        if (roomManager == null || members == null || members.isEmpty() || roomId == null || roomId.isBlank()) {
            return;
        }
        for (ServerPlayer member : members) {
            if (member == null) {
                continue;
            }
            if (roomManager.findPlayerRoomId(member.getUUID()).filter(roomId::equalsIgnoreCase).isPresent()) {
                roomManager.sendPlayerToHub(member);
            }
        }
    }

    private void refreshMembers(List<ServerPlayer> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        for (ServerPlayer member : members) {
            if (member != null) {
                QuickAccessItemService.refreshHotbar(member);
            }
        }
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
        RoomJoinResult operatorJoinResult
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

    private record GroupJoinPlacement(RoomTeamId teamId, String squadLabel) {
        private static GroupJoinPlacement none() {
            return new GroupJoinPlacement(null, "");
        }
    }

    private record LeaveAttempt(boolean changed, boolean hadSession, String message) {
    }

    private record ReturnToHubAttempt(boolean changed, boolean hadSession, String message) {
    }
}
