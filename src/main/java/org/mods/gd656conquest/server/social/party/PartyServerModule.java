package org.mods.gd656conquest.server.social.party;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.social.party.domain.PartyAggregate;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;
import org.mods.gd656conquest.server.social.party.application.PartyRoomBridgeService;
import org.mods.gd656conquest.server.social.party.presentation.PartyUiFacade;
import org.mods.gd656conquest.server.social.party.sync.PartySnapshotService;

public final class PartyServerModule implements ServerModule {
    @Override
    public void onServerStarting(MinecraftServer server) {
        if (server == null) {
            return;
        }
        InMemoryPartyRepository.of(server).initialize();
    }

    @Override
    public void onServerTick(MinecraftServer server, long gameTick) {
        if (server == null) {
            return;
        }
        InMemoryPartyRepository.of(server).pruneEmptyParties();
        PartyUiFacade.of(server).expireRequests();
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        if (server == null) {
            return;
        }
        InMemoryPartyRepository.of(server).shutdown();
        InMemoryPartyRepository.forget(server);
        PartyUiFacade.forget(server);
        PartySnapshotService.forget(server);
        PartyRoomBridgeService.forget(server);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        PartyAggregate party = InMemoryPartyRepository.of(player.server).findByPlayerId(player.getUUID()).orElse(null);
        if (party != null && party.isLeader(player.getUUID())) {
            notifyRemainingMembersOnLeaderLogout(player, party);
            PartySnapshotService.of(player.server).clearPartyState(party.partyId());
        }
        InMemoryPartyRepository.of(player.server).handlePlayerLogout(player);
    }

    private void notifyRemainingMembersOnLeaderLogout(ServerPlayer leader, PartyAggregate party) {
        if (leader == null || party == null || leader.server == null) {
            return;
        }
        for (var memberId : party.memberIds()) {
            if (memberId == null || memberId.equals(leader.getUUID())) {
                continue;
            }
            ServerPlayer member = leader.server.getPlayerList().getPlayer(memberId);
            if (member == null) {
                continue;
            }
            ServerMessageSender.system(member, null, "队长已离线，当前队伍已解散；你当前的房间状态不受影响", net.minecraft.ChatFormatting.GRAY);
        }
    }
}
