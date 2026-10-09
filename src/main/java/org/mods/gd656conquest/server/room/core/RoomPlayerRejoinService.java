package org.mods.gd656conquest.server.room.core;

import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoomPlayerRejoinService {
    private final RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;

    public RoomPlayerRejoinService(RoomRuntimeRepository<RoomRuntimeState> runtimeRepository) {
        this.runtimeRepository = Objects.requireNonNull(runtimeRepository, "runtimeRepository");
    }

    public void markRejoinable(RoomDefinition room, RunningRuntimeState state, UUID playerId, long gameTick) {
        if (room == null || state == null || playerId == null) {
            return;
        }
        long matchInstanceId = state.getActiveMatchInstanceId();
        RoomTeamId teamId = state.getRoomTeams().get(playerId);
        if (matchInstanceId <= 0L || (teamId != RoomTeamId.CAMP_A && teamId != RoomTeamId.CAMP_B)) {
            return;
        }
        state.getRejoinTickets().put(playerId, new RunningRuntimeState.RejoinTicket(
            matchInstanceId,
            teamId,
            state.getSquadLabelsByPlayer().get(playerId),
            gameTick
        ));
    }

    public Optional<RejoinTarget> findRejoinTarget(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        for (RoomRuntimeSlot<RoomRuntimeState> slot : runtimeRepository.list()) {
            if (slot == null) {
                continue;
            }
            RoomDefinition room = slot.room();
            RunningRuntimeState state = slot.runtime().getRunningState();
            RunningRuntimeState.RejoinTicket ticket = resolveValidTicket(room, state, playerId).orElse(null);
            if (ticket == null) {
                continue;
            }
            return Optional.of(new RejoinTarget(room, ticket));
        }
        return Optional.empty();
    }

    public Optional<RunningRuntimeState.RejoinTicket> resolveValidTicket(RoomDefinition room, RunningRuntimeState state, UUID playerId) {
        if (room == null || state == null || playerId == null) {
            return Optional.empty();
        }
        if (room.lifecycleState() != RoomLifecycleState.RUNNING && room.lifecycleState() != RoomLifecycleState.ENDING) {
            state.getRejoinTickets().remove(playerId);
            return Optional.empty();
        }
        RunningRuntimeState.RejoinTicket ticket = state.getRejoinTickets().get(playerId);
        if (ticket == null) {
            return Optional.empty();
        }
        if (ticket.matchInstanceId() <= 0L || ticket.matchInstanceId() != state.getActiveMatchInstanceId()) {
            state.getRejoinTickets().remove(playerId);
            return Optional.empty();
        }
        if (ticket.teamId() != RoomTeamId.CAMP_A && ticket.teamId() != RoomTeamId.CAMP_B) {
            state.getRejoinTickets().remove(playerId);
            return Optional.empty();
        }
        return Optional.of(ticket);
    }

    public void clearPlayerTickets(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (RoomRuntimeSlot<RoomRuntimeState> slot : runtimeRepository.list()) {
            if (slot != null) {
                slot.runtime().getRunningState().getRejoinTickets().remove(playerId);
            }
        }
    }

    public record RejoinTarget(
        RoomDefinition room,
        RunningRuntimeState.RejoinTicket ticket
    ) {
    }
}
