package org.mods.gd656conquest.server.social;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;
import org.mods.gd656conquest.social.RoomSocialAccess;

/** Membership is editable in the hub and matching queue, then fixed through the match. */
public final class SocialRosterPolicy {
    public static final String LOCKED = "阵营选择已开始，编制已锁定；返回大厅后可调整";
    private SocialRosterPolicy() {}

    public static String roomFailure(RoomDefinition room, RoomRuntimeState runtime) {
        if (room == null || runtime == null) return LOCKED;
        var phase = room.lifecycleState();
        return (phase == RoomLifecycleState.IDLE || phase == RoomLifecycleState.MATCHING)
            && runtime.getMatchingState().getPreMatchDeployEndTick() <= 0 ? null : LOCKED;
    }

    private static String playerFailure(MinecraftServer server, UUID player) {
        var manager = RoomCoreRuntimeManager.of(server);
        String room = manager.findPlayerRoomId(player).orElse("");
        if (room.isBlank()) return null;
        var runtime = ((RoomSocialAccess)(Object)manager.coreService()).socialRuntime(room).orElse(null);
        return roomFailure(manager.findRoom(room).orElse(null), runtime);
    }

    /** Include the whole existing/destination party so a hub player cannot edit a deployed party. */
    public static String failure(MinecraftServer server, UUID actor, String partyId, UUID target) {
        String failure = playerFailure(server, actor);
        if (failure != null) return failure;
        var repo = InMemoryPartyRepository.of(server);
        var party = partyId == null ? repo.findByPlayerId(actor).orElse(null) : repo.findByPartyId(partyId).orElse(null);
        if (party != null) {
            for (UUID member : party.memberIds()) {
                failure = playerFailure(server, member);
                if (failure != null) return failure;
            }
        }
        return target == null ? null : playerFailure(server, target);
    }

    public static void requireEditable(MinecraftServer server, UUID actor, String partyId, UUID target) {
        String failure = failure(server, actor, partyId, target);
        if (failure != null) throw new PartyError(failure);
    }
}
