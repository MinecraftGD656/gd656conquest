package org.mods.gd656conquest.server.room.entry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import org.mods.gd656conquest.server.room.runtime.ScopedDeathAttribution;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.DeathCausePacket;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.room.entry.DeathCauseClassifier.Ownership;
import org.mods.gd656conquest.server.room.entry.DeathCauseClassifier.SystemDeath;
import org.mods.gd656conquest.server.room.runtime.RoomDeathBoundaryQuery;

/** Death-only snapshot; does not replace damage, assist or Killicon event handling. */
public final class RoomDeathCauseService {
    private record Death(ServerPlayer victim, RunningRuntimeState state, long id, String dimension,
                         DeathCause cause, ServerPlayer killer, int legacyKillerId, boolean outsideBattlefield) {}
    private record FireOwner(ServerPlayer victim, ServerPlayer owner, boolean outsideBattlefield) {}
    private static final Map<UUID, Death> DEATHS = new HashMap<>();
    private static final ScopedDeathAttribution<Entity, FireOwner> ATTRIBUTION = new ScopedDeathAttribution<>();
    private static long nextDeath;
    private RoomDeathCauseService() {}

    /** Scoped to one target invocation, including console/command-block and multi-target /kill. */
    public static void commandKill(Entity target) {
        ATTRIBUTION.commandKill(target, target::kill);
    }

    public static ServerPlayer attributedKiller(DamageSource source, Entity entity) {
        ServerPlayer killer = RoomDamageSourceResolver.resolveAttackingPlayer(source);
        FireOwner fire = ATTRIBUTION.fireSnapshot();
        if (killer == null && fire != null && fire.victim() == entity) killer = fire.owner();
        if (killer == null && entity instanceof LivingEntity living && living.getKillCredit() instanceof ServerPlayer credit)
            killer = credit;
        return killer;
    }

    public static ServerPlayer resolveKiller(DamageSource source, Entity entity) {
        ServerPlayer killer = attributedKiller(source, entity);
        if (!(entity instanceof ServerPlayer victim) || victim.server == null) return killer;
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(victim.server);
        RunningRuntimeState state = manager.findPlayerRoomId(victim.getUUID())
            .flatMap(manager::findRunningState).orElse(null);
        if (state == null) return killer;
        UUID victimId = victim.getUUID();
        SystemDeath system = ATTRIBUTION.commandTarget() == victim ? SystemDeath.COMMAND
            : state.getBoundaryDeathPendingPlayers().contains(victimId) ? SystemDeath.BOUNDARY
            : state.getDoubledRespawnPending().contains(victimId) ? SystemDeath.REDEPLOY : SystemDeath.NONE;
        // Forced system deaths have no player killer. The same result goes to the room and camera.
        if (system != SystemDeath.NONE) killer = null;
        Ownership ownership = Ownership.NONE;
        if (killer == victim) ownership = Ownership.SELF;
        else if (killer != null) {
            RoomTeamId victimTeam = state.getRoomTeams().get(victimId);
            RoomTeamId killerTeam = state.getRoomTeams().get(killer.getUUID());
            ownership = killer.server == victim.server && victimTeam != null && killerTeam != null
                && victimTeam != killerTeam ? Ownership.ENEMY : Ownership.OTHER;
        }
        String type = source == null ? "" : source.typeHolder().unwrapKey()
            .map(key -> key.location().toString()).orElse("");
        boolean burning = source != null && source.is(DamageTypeTags.IS_FIRE);
        DeathCause cause = DeathCauseClassifier.classify(new DeathCauseClassifier.Context(system, ownership, type, burning));
        Entity legacy = source == null ? null : source.getEntity() == null ? source.getDirectEntity() : source.getEntity();
        if (system == SystemDeath.BOUNDARY) legacy = null;
        long deathId = ++nextDeath;
        FireOwner snapshot = ATTRIBUTION.fireSnapshot();
        boolean outside = snapshot != null && snapshot.victim() == victim ? snapshot.outsideBattlefield()
            : RoomDeathBoundaryQuery.isOutside(victim);
        DEATHS.put(victimId, new Death(victim, state, deathId,
            victim.level().dimension().location().toString(), cause, killer, legacy == null ? -1 : legacy.getId(), outside));
        KillerIntelService.capture(victim, state, deathId, cause, killer, source);
        return killer;
    }

    /** Capture before death listeners clean fire history; never writes to Killicon's maps. */
    public static boolean withFireOwnership(ServerPlayer player, DamageSource source, BooleanSupplier transition) {
        // Freeze geometry before listeners can dismount, teleport or heal the player proxy.
        FireOwner snapshot = new FireOwner(player, KilliconFireOwnership.resolve(player, source), RoomDeathBoundaryQuery.isOutside(player));
        return ATTRIBUTION.withFireSnapshot(snapshot, transition);
    }

    /** Attribution changes camera targets, never the original 50/120 tick observation delay. */
    public static int legacyKillerEntityId(UUID playerId, int fallback) {
        Death death = DEATHS.get(playerId);
        return death == null ? fallback : death.legacyKillerId();
    }

    /** Keep legacy callers working when no accepted room death has produced a snapshot. */
    public static int killerEntityId(ServerPlayer player, int fallback) {
        Death death = current(player);
        return death == null ? fallback : death.killer() == null ? -1 : death.killer().getId();
    }

    public static boolean skipDowned(ServerPlayer player) {
        Death death = current(player);
        return death != null && (death.cause() == DeathCause.COMMAND_KILL
            || death.cause() == DeathCause.DESERTION || death.outsideBattlefield());
    }

    public static boolean consumeBoundaryDeath(Set<?> pending, Object id, ServerPlayer player) {
        boolean forced = pending.remove(id);
        return forced || skipDowned(player);
    }

    /** Sent after the camera start; metadata never changes when its tracking target disappears. */
    public static void publish(ServerPlayer player) {
        Death death = current(player);
        if (death != null && death.state().isInRespawnFlow(player.getUUID())
            && !death.state().getDeployViewActivePlayers().contains(player.getUUID())
            && !death.state().getRespawnFinalizeTicks().containsKey(player.getUUID())) {
            NetworkManager.sendToPlayer(player, new DeathCausePacket(death.id(), player.getId(), death.dimension(), death.cause()));
            KillerIntelService.publish(player, death.id());
        }
    }

    public static long deathId(ServerPlayer player) { Death death = current(player); return death == null ? 0 : death.id(); }

    private static Death current(ServerPlayer player) {
        if (player == null) return null;
        Death death = DEATHS.get(player.getUUID());
        return death != null && death.victim() == player ? death : null;
    }

    public static void forget(ServerPlayer player) {
        KillerIntelService.forget(player);
        if (current(player) != null) DEATHS.remove(player.getUUID());
    }
    public static void clear(MinecraftServer server) {
        KillerIntelService.clear(server);
        org.mods.gd656conquest.server.compat.vehicle.BoundaryVehicleDestruction.clear(server);
        DEATHS.values().removeIf(death -> death.victim().server == server);
    }
    public static void tick(MinecraftServer server) {
        KillerIntelService.tick(server);
        org.mods.gd656conquest.server.compat.vehicle.BoundaryVehicleDestruction.tick(server);
        DEATHS.values().removeIf(death -> {
            ServerPlayer player = death.victim();
            if (player.server != server) return false;
            UUID id = player.getUUID();
            return server.getPlayerList().getPlayer(id) != player
                || !player.level().dimension().location().toString().equals(death.dimension())
                || !death.state().isInRespawnFlow(id)
                || death.state().getDeployViewActivePlayers().contains(id)
                || death.state().getRespawnFinalizeTicks().containsKey(id);
        });
    }
}
