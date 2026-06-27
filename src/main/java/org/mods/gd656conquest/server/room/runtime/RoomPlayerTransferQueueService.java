package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

public final class RoomPlayerTransferQueueService {
    private static final int TRANSFER_INTERVAL_TICKS = 2;
    private static final int VIEW_TRANSFER_DELAY_TICKS = 2;

    private final MinecraftServer server;
    private final RoomPlayerLaunchService playerLaunchService;
    private final RoomLobbyTransferService lobbyTransferService;
    private final ArrayDeque<QueuedTransfer> queuedTransfers = new ArrayDeque<>();
    private long lastTransferTick = -1L;

    public RoomPlayerTransferQueueService(
        MinecraftServer server,
        RoomPlayerLaunchService playerLaunchService,
        RoomLobbyTransferService lobbyTransferService
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerLaunchService = Objects.requireNonNull(playerLaunchService, "playerLaunchService");
        this.lobbyTransferService = Objects.requireNonNull(lobbyTransferService, "lobbyTransferService");
    }

    public void enqueueDeployViewTeleport(ServerPlayer player, RoomDefinition room, RoomViewPosition viewPosition, long gameTick) {
        if (player == null || room == null || viewPosition == null) {
            return;
        }
        removePending(player.getUUID(), TransferKind.DEPLOY_VIEW);
        queuedTransfers.addLast(new QueuedTransfer(
            player.getUUID(),
            gameTick + VIEW_TRANSFER_DELAY_TICKS,
            TransferKind.DEPLOY_VIEW,
            room,
            viewPosition
        ));
    }

    public void enqueueLobbyTransfer(ServerPlayer player, long gameTick) {
        if (player == null) {
            return;
        }
        removePending(player.getUUID(), TransferKind.LOBBY);
        queuedTransfers.addLast(new QueuedTransfer(
            player.getUUID(),
            Math.max(0L, gameTick),
            TransferKind.LOBBY,
            null,
            null
        ));
    }

    public void cancelPendingTransfers(UUID playerId) {
        if (playerId == null) {
            return;
        }
        queuedTransfers.removeIf(transfer -> playerId.equals(transfer.playerId()));
    }

    public void tick(long gameTick) {
        if (queuedTransfers.isEmpty()) {
            return;
        }
        if (lastTransferTick >= 0 && gameTick - lastTransferTick < TRANSFER_INTERVAL_TICKS) {
            return;
        }
        Iterator<QueuedTransfer> iterator = queuedTransfers.iterator();
        while (iterator.hasNext()) {
            QueuedTransfer transfer = iterator.next();
            if (gameTick < transfer.dueTick()) {
                continue;
            }
            iterator.remove();
            execute(transfer);
            lastTransferTick = gameTick;
            return;
        }
    }

    private void removePending(UUID playerId, TransferKind kind) {
        if (playerId == null || kind == null || queuedTransfers.isEmpty()) {
            return;
        }
        queuedTransfers.removeIf(transfer -> playerId.equals(transfer.playerId()) && transfer.kind() == kind);
    }

    private void execute(QueuedTransfer transfer) {
        if (transfer == null || transfer.playerId() == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(transfer.playerId());
        if (player == null) {
            return;
        }
        if (transfer.kind() == TransferKind.LOBBY) {
            lobbyTransferService.transferToLobby(player);
            return;
        }
        RoomDefinition room = transfer.room();
        if (room == null || !playerLaunchService.teleportToViewPosition(player, room, transfer.viewPosition())) {
            return;
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private enum TransferKind {
        DEPLOY_VIEW,
        LOBBY
    }

    private record QueuedTransfer(
        UUID playerId,
        long dueTick,
        TransferKind kind,
        RoomDefinition room,
        RoomViewPosition viewPosition
    ) {
    }
}
