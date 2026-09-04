package org.mods.gd656conquest.server.social.party.application;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.facility.parkour.ParkourRuntimeManager;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.player.identity.PlayerIdentityService;
import org.mods.gd656conquest.server.room.core.RoomGroupPlacementPlan;
import org.mods.gd656conquest.server.room.core.RoomJoinResult;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomJoinAccessPolicy;
import org.mods.gd656conquest.server.room.entry.RoomSessionTransitionService;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomMatchingConfig;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.matching.MatchingRuntimeState;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.domain.PartyLockState;
import org.mods.gd656conquest.server.social.party.domain.PartyRoomActionState;
import org.mods.gd656conquest.server.social.party.domain.PartyStatus;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;
import org.mods.gd656conquest.server.social.party.infra.PartyRepository;
import org.mods.gd656conquest.server.social.party.sync.PartyRoomTransitionRecord;
import org.mods.gd656conquest.server.social.party.sync.PartySnapshot;
import org.mods.gd656conquest.server.social.party.sync.PartySnapshotService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PartyRoomBridgeService {
    private static final Map<MinecraftServer, PartyRoomBridgeService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final PartyRepository repository;
    private final PartySnapshotService snapshotService;
    private final PlayerIdentityService identityService;

    private PartyRoomBridgeService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.repository = InMemoryPartyRepository.of(server);
        this.snapshotService = PartySnapshotService.of(server);
        this.identityService = PlayerIdentityService.of(server);
    }

    public static PartyRoomBridgeService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, PartyRoomBridgeService::new);
        }
    }

    public static void forget(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public Optional<RoomSessionTransitionService.JoinExecutionResult> tryJoinAsPartyLeader(
        ServerPlayer operator,
        String roomId,
        boolean maintenance
    ) {
        if (operator == null || operator.server == null || roomId == null || roomId.isBlank()) {
            return Optional.empty();
        }
        PartyAggregate party = resolveLeaderParty(operator);
        if (party == null) {
            return Optional.empty();
        }
        if (party.memberCount() > PartyAggregate.MAX_MEMBER_COUNT) {
            return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                false,
                "当前队伍人数超过上限 4 人，无法整队进入房间",
                0,
                true,
                null,
                ""
            ));
        }
        if (party.lockState() == PartyLockState.ROOM_TRANSITION_LOCKED) {
            return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                false,
                "当前队伍正在执行房间联动，请稍后再试",
                0,
                true,
                null,
                ""
            ));
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        RoomDefinition room = roomManager.findRoom(roomId).orElse(null);
        if (room == null) {
            return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                false,
                "未找到房间: " + roomId,
                0,
                true,
                null,
                ""
            ));
        }
        List<ServerPlayer> members = resolveOnlineMembers(new ArrayList<>(party.memberIds()));
        if (members.size() != party.memberCount()) {
            return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                false,
                "队伍成员必须全部在线后才能整队进入房间",
                0,
                true,
                null,
                ""
            ));
        }
        for (ServerPlayer member : members) {
            RoomJoinAccessPolicy.JoinCheckResult access = RoomJoinAccessPolicy.evaluate(member, room, roomManager);
            if (!access.allowed()) {
                return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                    false,
                    resolvePlayerName(member.getUUID()) + ": " + access.deniedMessage(),
                    0,
                    true,
                    null,
                    ""
                ));
            }
        }
        GroupJoinPlacement placement = maintenance ? GroupJoinPlacement.none() : resolveGroupJoinPlacement(roomManager, roomId, members);
        if (!maintenance && placement == null) {
            return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                false,
                "目标房间当前无法容纳整支队伍，请稍后再试",
                0,
                true,
                null,
                ""
            ));
        }
        long gameTick = server.getTickCount();
        PartySnapshot snapshot = snapshotService.createSnapshot(party, roomId, gameTick);
        PartyRoomTransitionRecord record = new PartyRoomTransitionRecord(
            snapshot,
            PartyRoomActionState.PREPARING_JOIN,
            party.version(),
            party.status(),
            party.lockState(),
            party.pendingRoomAction(),
            captureMemberOrigins(members, roomManager)
        );
        snapshotService.saveTransitionRecord(record);
        repository.save(copyWithState(
            party,
            PartyStatus.PREPARING_ROOM_JOIN,
            PartyLockState.ROOM_TRANSITION_LOCKED,
            PartyRoomActionState.PREPARING_JOIN,
            gameTick
        ));
        int successCount = 0;
        RoomJoinResult operatorJoinResult = null;
        try {
            for (ServerPlayer member : members) {
                JoinMemberResult result = joinSingleMember(roomManager, member, roomId, maintenance, gameTick);
                if (!result.success()) {
                    throw new IllegalStateException(result.deniedMessage());
                }
                if (!maintenance && placement != null && !placement.isEmpty()) {
                    boolean applied = roomManager.applyGroupPlacement(
                        roomId,
                        member.getUUID(),
                        placement.targetTeam(),
                        placement.squadLabel(),
                        gameTick
                    );
                    if (!applied) {
                        throw new IllegalStateException(resolvePlayerName(member.getUUID()) + ": 队伍房间投放失败");
                    }
                }
                if (member.getUUID().equals(operator.getUUID())) {
                    operatorJoinResult = result.joinResult();
                }
                successCount++;
            }
            PartyAggregate latest = repository.findByPartyId(party.partyId()).orElse(party);
            repository.save(copyWithState(
                latest,
                PartyStatus.IN_ROOM_BOUND,
                PartyLockState.MEMBERSHIP_LOCKED,
                PartyRoomActionState.NONE,
                server.getTickCount()
            ));
            snapshotService.bindSnapshot(snapshot);
            snapshotService.clearTransitionRecord(party.partyId());
            notifyGroupMembers(members, operator.getUUID(), "队长已带领队伍进入房间: " + roomId);
            String operatorMessage = maintenance
                ? "已带领队伍进入维护房间: " + roomId + " | 已处理 " + successCount + " 名队员"
                : "已带领队伍进入房间: " + roomId + " | 已处理 " + successCount + " 名队员";
            ServerLogger.info("[PARTY][房间桥接][进房成功][队伍:%s][房间:%s][人数:%d]", party.partyId(), roomId, successCount);
            return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                true,
                "",
                successCount,
                true,
                operatorJoinResult,
                operatorMessage
            ));
        } catch (Exception exception) {
            rollbackMembers(record, resolveOnlineMembers(snapshot.memberIds()));
            PartyAggregate latest = repository.findByPartyId(party.partyId()).orElse(party);
            repository.save(copyWithState(
                latest,
                record.previousStatus(),
                record.previousLockState(),
                record.previousPendingRoomAction(),
                server.getTickCount()
            ));
            snapshotService.clearTransitionRecord(party.partyId());
            ServerLogger.failure(
                "[PARTY][房间桥接][进房失败][队伍:%s][房间:%s] %s",
                party.partyId(),
                roomId,
                exception.getMessage()
            );
            return Optional.of(new RoomSessionTransitionService.JoinExecutionResult(
                false,
                exception.getMessage() == null || exception.getMessage().isBlank() ? "整队进入房间失败" : exception.getMessage(),
                successCount,
                true,
                operatorJoinResult,
                ""
            ));
        }
    }

    public Optional<RoomSessionTransitionService.LeaveExecutionResult> tryLeaveAsPartyLeader(
        ServerPlayer operator,
        RoomSessionTransitionService.LeaveMode leaveMode
    ) {
        if (operator == null || operator.server == null || leaveMode != RoomSessionTransitionService.LeaveMode.DETACH_ONLY) {
            return Optional.empty();
        }
        PartyAggregate party = resolveLeaderParty(operator);
        if (party == null) {
            return Optional.empty();
        }
        PartySnapshot snapshot = snapshotService.findBoundSnapshot(party.partyId()).orElse(null);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (party.lockState() == PartyLockState.ROOM_TRANSITION_LOCKED) {
            return Optional.of(new RoomSessionTransitionService.LeaveExecutionResult(
                false,
                "当前队伍正在执行房间联动，请稍后再试",
                0,
                true,
                false,
                ""
            ));
        }
        List<ServerPlayer> members = resolveOnlineMembers(snapshot.memberIds());
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        long gameTick = server.getTickCount();
        PartyRoomTransitionRecord record = new PartyRoomTransitionRecord(
            snapshot,
            PartyRoomActionState.PREPARING_LEAVE,
            party.version(),
            party.status(),
            party.lockState(),
            party.pendingRoomAction(),
            captureMemberOrigins(members, roomManager)
        );
        snapshotService.saveTransitionRecord(record);
        repository.save(copyWithState(
            party,
            party.status(),
            PartyLockState.ROOM_TRANSITION_LOCKED,
            PartyRoomActionState.PREPARING_LEAVE,
            gameTick
        ));
        int changedCount = 0;
        boolean operatorHadSession = false;
        try {
            for (ServerPlayer member : members) {
                LeaveMemberResult result = leaveSingleMember(roomManager, member, leaveMode);
                changedCount += result.changed() ? 1 : 0;
                if (member.getUUID().equals(operator.getUUID())) {
                    operatorHadSession = result.hadSession();
                }
            }
            PartyAggregate latest = repository.findByPartyId(party.partyId()).orElse(party);
            repository.save(copyWithState(
                latest,
                PartyStatus.IDLE,
                PartyLockState.NONE,
                PartyRoomActionState.NONE,
                server.getTickCount()
            ));
            snapshotService.clearTransitionRecord(party.partyId());
            snapshotService.clearBoundSnapshot(party.partyId());
            notifyGroupMembers(members, operator.getUUID(), "队长已带领队伍离开当前房间/匹配");
            String operatorMessage = changedCount > 0
                ? "已带领队伍离开当前房间/匹配，共处理 " + changedCount + " 名队员"
                : "队伍当前没有成员处于房间或匹配队列中";
            return Optional.of(new RoomSessionTransitionService.LeaveExecutionResult(
                true,
                "",
                changedCount,
                true,
                operatorHadSession,
                operatorMessage
            ));
        } catch (Exception exception) {
            rollbackMembers(record, members);
            PartyAggregate latest = repository.findByPartyId(party.partyId()).orElse(party);
            repository.save(copyWithState(
                latest,
                record.previousStatus(),
                record.previousLockState(),
                record.previousPendingRoomAction(),
                server.getTickCount()
            ));
            snapshotService.clearTransitionRecord(party.partyId());
            return Optional.of(new RoomSessionTransitionService.LeaveExecutionResult(
                false,
                exception.getMessage() == null || exception.getMessage().isBlank() ? "整队离房失败" : exception.getMessage(),
                changedCount,
                true,
                operatorHadSession,
                ""
            ));
        }
    }

    public Optional<RoomSessionTransitionService.ReturnToHubExecutionResult> tryReturnToHubAsPartyLeader(ServerPlayer operator) {
        if (operator == null || operator.server == null) {
            return Optional.empty();
        }
        PartyAggregate party = resolveLeaderParty(operator);
        if (party == null) {
            return Optional.empty();
        }
        PartySnapshot snapshot = snapshotService.findBoundSnapshot(party.partyId()).orElse(null);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (party.lockState() == PartyLockState.ROOM_TRANSITION_LOCKED) {
            return Optional.of(new RoomSessionTransitionService.ReturnToHubExecutionResult(
                false,
                "当前队伍正在执行房间联动，请稍后再试",
                0,
                true,
                false,
                ""
            ));
        }
        List<ServerPlayer> members = resolveOnlineMembers(snapshot.memberIds());
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        long gameTick = server.getTickCount();
        PartyRoomTransitionRecord record = new PartyRoomTransitionRecord(
            snapshot,
            PartyRoomActionState.PREPARING_RETURN_TO_HUB,
            party.version(),
            party.status(),
            party.lockState(),
            party.pendingRoomAction(),
            captureMemberOrigins(members, roomManager)
        );
        snapshotService.saveTransitionRecord(record);
        repository.save(copyWithState(
            party,
            party.status(),
            PartyLockState.ROOM_TRANSITION_LOCKED,
            PartyRoomActionState.PREPARING_RETURN_TO_HUB,
            gameTick
        ));
        int changedCount = 0;
        boolean operatorHadSession = false;
        try {
            for (ServerPlayer member : members) {
                ReturnMemberResult result = returnSingleMemberToHub(member);
                changedCount += result.changed() ? 1 : 0;
                if (member.getUUID().equals(operator.getUUID())) {
                    operatorHadSession = result.hadSession();
                }
            }
            PartyAggregate latest = repository.findByPartyId(party.partyId()).orElse(party);
            repository.save(copyWithState(
                latest,
                PartyStatus.IDLE,
                PartyLockState.NONE,
                PartyRoomActionState.NONE,
                server.getTickCount()
            ));
            snapshotService.clearTransitionRecord(party.partyId());
            snapshotService.clearBoundSnapshot(party.partyId());
            notifyGroupMembers(members, operator.getUUID(), "队长已带领队伍返回大厅");
            String operatorMessage = changedCount > 0
                ? "已带领队伍返回大厅，共处理 " + changedCount + " 名队员"
                : "队伍当前没有成员处于对局、靶场或跑酷中";
            return Optional.of(new RoomSessionTransitionService.ReturnToHubExecutionResult(
                true,
                "",
                changedCount,
                true,
                operatorHadSession,
                operatorMessage
            ));
        } catch (Exception exception) {
            rollbackMembers(record, members);
            PartyAggregate latest = repository.findByPartyId(party.partyId()).orElse(party);
            repository.save(copyWithState(
                latest,
                record.previousStatus(),
                record.previousLockState(),
                record.previousPendingRoomAction(),
                server.getTickCount()
            ));
            snapshotService.clearTransitionRecord(party.partyId());
            return Optional.of(new RoomSessionTransitionService.ReturnToHubExecutionResult(
                false,
                exception.getMessage() == null || exception.getMessage().isBlank() ? "整队返回大厅失败" : exception.getMessage(),
                changedCount,
                true,
                operatorHadSession,
                ""
            ));
        }
    }

    private PartyAggregate resolveLeaderParty(ServerPlayer operator) {
        PartyAggregate party = repository.findByPlayerId(operator.getUUID()).orElse(null);
        if (party == null || !party.isLeader(operator.getUUID())) {
            return null;
        }
        return party;
    }

    private List<ServerPlayer> resolveOnlineMembers(List<UUID> memberIds) {
        List<ServerPlayer> members = new ArrayList<>();
        if (memberIds == null) {
            return members;
        }
        for (UUID memberId : memberIds) {
            if (memberId == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                members.add(player);
            }
        }
        return members;
    }

    private Map<UUID, PartyRoomTransitionRecord.MemberOriginState> captureMemberOrigins(
        List<ServerPlayer> members,
        RoomCoreRuntimeManager roomManager
    ) {
        Map<UUID, PartyRoomTransitionRecord.MemberOriginState> result = new LinkedHashMap<>();
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(server);
        ParkourRuntimeManager parkourManager = ParkourRuntimeManager.of(server);
        for (ServerPlayer member : members) {
            UUID playerId = member.getUUID();
            String roomId = roomManager.findPlayerRoomId(playerId).orElse("");
            boolean inMatchingQueue = !roomId.isBlank()
                && roomManager.findMatchingState(roomId).map(state -> state.getQueuePlayers().contains(playerId)).orElse(false);
            RoomTeamId teamId = roomManager.findCurrentTeam(playerId)
                .orElseGet(() -> roomId.isBlank() ? null : roomManager.findQueueTeam(roomId, playerId).orElse(null));
            String squadLabel = roomManager.findPlayerSquadLabel(playerId).orElse("");
            result.put(playerId, new PartyRoomTransitionRecord.MemberOriginState(
                playerId,
                roomId,
                inMatchingQueue,
                rangeManager.isInRange(playerId),
                parkourManager.isInParkour(playerId),
                teamId,
                squadLabel
            ));
        }
        return result;
    }

    private GroupJoinPlacement resolveGroupJoinPlacement(
        RoomCoreRuntimeManager roomManager,
        String roomId,
        List<ServerPlayer> members
    ) {
        RoomDefinition room = roomManager.findRoom(roomId).orElse(null);
        if (room == null) {
            return null;
        }
        List<ServerPlayer> alreadyInsideTarget = new ArrayList<>();
        for (ServerPlayer member : members) {
            String currentRoomId = roomManager.findPlayerRoomId(member.getUUID()).orElse("");
            if (roomId.equalsIgnoreCase(currentRoomId)) {
                alreadyInsideTarget.add(member);
            }
        }
        int externalJoiners = Math.max(0, members.size() - alreadyInsideTarget.size());
        if (!alreadyInsideTarget.isEmpty()) {
            RoomGroupPlacementPlan plan = roomManager.resolvePlayerPlacement(roomId, alreadyInsideTarget.get(0).getUUID()).orElse(null);
            if (plan == null) {
                return null;
            }
            if (plan.teamId() != null && !roomManager.canFitGroupInTeam(roomId, plan.teamId(), externalJoiners)) {
                return null;
            }
            return new GroupJoinPlacement(plan.teamId(), plan.squadLabel());
        }
        GroupJoinPlacement emptyTeamPlacement = resolveEmptyTeamPlacement(roomManager, room, members.size());
        if (emptyTeamPlacement != null) {
            return emptyTeamPlacement;
        }
        RoomGroupPlacementPlan plan = roomManager.planGroupJoinPlacement(roomId, Math.max(1, members.size())).orElse(null);
        if (plan == null) {
            return null;
        }
        return new GroupJoinPlacement(plan.teamId(), plan.squadLabel());
    }

    private GroupJoinPlacement resolveEmptyTeamPlacement(
        RoomCoreRuntimeManager roomManager,
        RoomDefinition room,
        int groupSize
    ) {
        if (roomManager == null || room == null || groupSize <= 0) {
            return null;
        }
        String roomId = room.roomId();
        RoomMatchingConfig matchingConfig = room.commonConfig().matchingConfig();
        if (matchingConfig == null) {
            return null;
        }
        for (RoomTeamId teamId : matchingConfig.enabledTeams()) {
            if (teamId == null) {
                continue;
            }
            if (matchingConfig.maxPlayersForTeam(teamId) < groupSize) {
                continue;
            }
            if (!roomManager.canFitGroupInTeam(roomId, teamId, groupSize)) {
                continue;
            }
            if (countTeamOccupancy(roomManager, room, teamId) != 0) {
                continue;
            }
            String squadLabel = roomManager.reserveSquadLabel(roomId, teamId).orElse("");
            return new GroupJoinPlacement(teamId, squadLabel);
        }
        return null;
    }

    private int countTeamOccupancy(RoomCoreRuntimeManager roomManager, RoomDefinition room, RoomTeamId teamId) {
        if (roomManager == null || room == null || teamId == null) {
            return Integer.MAX_VALUE;
        }
        String roomId = room.roomId();
        RoomLifecycleState lifecycleState = room.lifecycleState();
        if (lifecycleState == RoomLifecycleState.IDLE || lifecycleState == RoomLifecycleState.MATCHING) {
            MatchingRuntimeState matchingState = roomManager.findMatchingState(roomId).orElse(null);
            if (matchingState == null) {
                return 0;
            }
            int count = 0;
            for (UUID playerId : matchingState.getQueuePlayers()) {
                if (matchingState.getQueueTeams().get(playerId) == teamId) {
                    count++;
                }
            }
            return count;
        }
        if (lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING) {
            RunningRuntimeState runningState = roomManager.findRunningState(roomId).orElse(null);
            if (runningState == null) {
                return 0;
            }
            int count = 0;
            for (UUID playerId : runningState.getRoomPlayers()) {
                if (runningState.getRoomTeams().get(playerId) == teamId) {
                    count++;
                }
            }
            return count;
        }
        return Integer.MAX_VALUE;
    }

    private JoinMemberResult joinSingleMember(
        RoomCoreRuntimeManager roomManager,
        ServerPlayer member,
        String roomId,
        boolean maintenance,
        long gameTick
    ) {
        UUID memberId = member.getUUID();
        Optional<String> currentRoom = roomManager.findPlayerRoomId(memberId);
        if (currentRoom.filter(roomId::equalsIgnoreCase).isPresent()) {
            return new JoinMemberResult(true, null, "");
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
        String deniedMessage = joined ? "" : resolvePlayerName(memberId) + ": 加入房间失败";
        return new JoinMemberResult(joined, joinResult.orElse(null), deniedMessage);
    }

    private LeaveMemberResult leaveSingleMember(
        RoomCoreRuntimeManager roomManager,
        ServerPlayer member,
        RoomSessionTransitionService.LeaveMode leaveMode
    ) {
        UUID memberId = member.getUUID();
        boolean hadSession = roomManager.findPlayerRoomId(memberId).isPresent();
        if (leaveMode == RoomSessionTransitionService.LeaveMode.RETURN_TO_HUB) {
            if (hadSession) {
                roomManager.sendPlayerToHub(member);
            }
            return new LeaveMemberResult(hadSession, hadSession);
        }
        if (hadSession) {
            roomManager.removePlayerFromRoom(memberId);
        }
        roomManager.leaveMatching(memberId);
        return new LeaveMemberResult(hadSession, hadSession);
    }

    private ReturnMemberResult returnSingleMemberToHub(ServerPlayer member) {
        UUID memberId = member.getUUID();
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(server);
        ParkourRuntimeManager parkourManager = ParkourRuntimeManager.of(server);
        if (roomManager.isInitialized() && roomManager.findPlayerRoomId(memberId).isPresent()) {
            roomManager.sendPlayerToHub(member);
            return new ReturnMemberResult(true, true);
        }
        if (rangeManager.isInRange(memberId)) {
            rangeManager.leaveRange(member);
            return new ReturnMemberResult(true, true);
        }
        if (parkourManager.isInParkour(memberId)) {
            parkourManager.leaveParkour(member);
            return new ReturnMemberResult(true, true);
        }
        return new ReturnMemberResult(false, false);
    }

    private void rollbackMembers(PartyRoomTransitionRecord record, List<ServerPlayer> onlineMembers) {
        if (record == null || onlineMembers == null || onlineMembers.isEmpty()) {
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(server);
        RangeRuntimeManager rangeManager = RangeRuntimeManager.of(server);
        ParkourRuntimeManager parkourManager = ParkourRuntimeManager.of(server);
        long gameTick = server.getTickCount();
        for (ServerPlayer member : onlineMembers) {
            if (member == null) {
                continue;
            }
            UUID playerId = member.getUUID();
            PartyRoomTransitionRecord.MemberOriginState origin = record.memberOrigins().get(playerId);
            if (origin == null) {
                continue;
            }
            clearCurrentRuntimeState(member, roomManager, rangeManager, parkourManager);
            restoreOriginalRuntimeState(member, origin, roomManager, rangeManager, parkourManager, gameTick);
        }
    }

    private void clearCurrentRuntimeState(
        ServerPlayer player,
        RoomCoreRuntimeManager roomManager,
        RangeRuntimeManager rangeManager,
        ParkourRuntimeManager parkourManager
    ) {
        UUID playerId = player.getUUID();
        if (roomManager.findPlayerRoomId(playerId).isPresent()) {
            roomManager.sendPlayerToHub(player);
        }
        if (rangeManager.isInRange(playerId)) {
            rangeManager.leaveRangeForTransfer(player);
        }
        if (parkourManager.isInParkour(playerId)) {
            parkourManager.leaveParkourForTransfer(player);
        }
    }

    private void restoreOriginalRuntimeState(
        ServerPlayer player,
        PartyRoomTransitionRecord.MemberOriginState origin,
        RoomCoreRuntimeManager roomManager,
        RangeRuntimeManager rangeManager,
        ParkourRuntimeManager parkourManager,
        long gameTick
    ) {
        if (origin == null) {
            return;
        }
        if (origin.hasRoomSession()) {
            RoomDefinition originalRoom = roomManager.findRoom(origin.roomId()).orElse(null);
            if (originalRoom != null) {
                RoomJoinAccessPolicy.JoinCheckResult access = RoomJoinAccessPolicy.evaluate(player, originalRoom, roomManager);
                if (access.allowed()) {
                    JoinMemberResult restored = joinSingleMember(roomManager, player, origin.roomId(), access.maintenance(), gameTick);
                    if (restored.success()) {
                        if (origin.teamId() != null || !origin.squadLabel().isBlank()) {
                            roomManager.applyGroupPlacement(origin.roomId(), player.getUUID(), origin.teamId(), origin.squadLabel(), gameTick);
                        }
                        return;
                    }
                }
            }
        }
        if (origin.inRange()) {
            rangeManager.joinRange(player);
            return;
        }
        if (origin.inParkour()) {
            parkourManager.joinParkour(player);
        }
    }

    private void notifyGroupMembers(List<ServerPlayer> members, UUID operatorId, String message) {
        if (members == null || members.isEmpty() || message == null || message.isBlank()) {
            return;
        }
        for (ServerPlayer member : members) {
            if (member == null || member.getUUID().equals(operatorId)) {
                continue;
            }
            ServerMessageSender.system(member, null, message, net.minecraft.ChatFormatting.GRAY);
        }
    }

    private String resolvePlayerName(UUID playerId) {
        return identityService.resolvePlayerNameOrUuid(playerId);
    }

    private PartyAggregate copyWithState(
        PartyAggregate party,
        PartyStatus status,
        PartyLockState lockState,
        PartyRoomActionState pendingRoomAction,
        long gameTick
    ) {
        return PartyAggregate.restore(
            party.partyId(),
            party.leaderId(),
            party.memberIds(),
            party.createdTick(),
            Math.max(0L, gameTick),
            party.version() + 1L,
            status,
            lockState,
            pendingRoomAction
        );
    }

    private record GroupJoinPlacement(RoomTeamId targetTeam, String squadLabel) {
        private static GroupJoinPlacement none() {
            return new GroupJoinPlacement(null, "");
        }

        private boolean isEmpty() {
            return targetTeam == null && (squadLabel == null || squadLabel.isBlank());
        }
    }

    private record JoinMemberResult(boolean success, RoomJoinResult joinResult, String deniedMessage) {
    }

    private record LeaveMemberResult(boolean changed, boolean hadSession) {
    }

    private record ReturnMemberResult(boolean changed, boolean hadSession) {
    }
}
