package org.mods.gd656conquest.server.compat.ragdoll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.common.player.DownedCameraMode;
import org.mods.gd656conquest.common.player.RevivedPronePose;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.DownedViewPacket;
import org.mods.gd656conquest.network.packet.DownedViewPacket.Action;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomPosition;

/** Gameplay ownership is independent of optional physics and of the camera mode. */
public final class DownedViewService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Session> PRONE = new HashMap<>();
    private static long nextSession;
    private DownedViewService() {}

    public static void start(ServerPlayer player, RunningRuntimeState state) {
        end(player);
        Session session = new Session(player, state, ++nextSession, DownedCameraConfig.mode());
        SESSIONS.put(player.getUUID(), session);
        send(session, Action.START, session.origin);
        org.mods.gd656conquest.server.room.entry.RoomDeathCauseService.publish(player);
    }
    /** Observe the ordinary corpse; never creates a rescue state or changes deployment timers. */
    public static void startDeath(MinecraftServer server, RunningRuntimeState state, UUID playerId, RoomPosition position) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || position == null) return;
        end(player);
        Session session = new Session(player,state,++nextSession,DownedCameraConfig.mode());
        session.rescuable = false;
        session.cameraAnchor = new Vec3(position.x(),position.y(),position.z());
        session.corpseRecord = DownedRagdollBridge.retainedRecord(player);
        SESSIONS.put(playerId,session);
        send(session,Action.START_DEATH,session.cameraAnchor);
        org.mods.gd656conquest.server.room.entry.RoomDeathCauseService.publish(player);
    }
    public static float reviveYaw(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.player == player ? session.yaw : player.getYRot();
    }
    static boolean observesCorpse(ServerPlayer player,Object record) {
        Session session=SESSIONS.get(player.getUUID());
        return session!=null && session.player==player && !session.rescuable && session.corpseRecord==record;
    }
    public static void prepareRevive(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        if (session == null || session.player != player || !session.rescuable) return;
        session.reviving = true;
        send(session, Action.PREPARE_REVIVE, DownedRagdollBridge.position(player));
    }
    /** Called after every old animator/downed clear has been sent. */
    public static void completeRevive(ServerPlayer player) {
        if (player == null) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.player != player || !session.rescuable || !session.reviving) return;
        SESSIONS.remove(player.getUUID());
        // Also applies when the optional ragdoll failed or was absent.
        Vec3 feet = safePronePosition(player, player.position(), session.origin);
        RevivedPronePose.begin(player, session.id);
        player.teleportTo(player.serverLevel(), feet.x, feet.y, feet.z, session.yaw, 0);
        player.setYRot(session.yaw); player.setXRot(0);
        player.setYBodyRot(session.yaw); player.setYHeadRot(session.yaw);
        player.yBodyRotO = session.yaw; player.yHeadRotO = session.yaw;
        player.yRotO = session.yaw; player.xRotO = 0;
        player.setDeltaMovement(0, 0, 0); player.fallDistance = 0;
        PRONE.put(player.getUUID(), session);
        send(session, Action.REVIVED, feet);
    }
    public static void releaseProne(ServerPlayer player, long id) {
        Session session = player == null ? null : PRONE.get(player.getUUID());
        if (session == null || session.player != player || session.id != id
            || session.state.isPlayerDowned(player.getUUID())) return;
        PRONE.remove(player.getUUID());
        RevivedPronePose.end(player);
        send(session, Action.PRONE_RELEASED, player.position());
    }
    public static void end(ServerPlayer player) {
        if (player == null) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.player == player) {
            SESSIONS.remove(player.getUUID()); send(session, Action.END, player.position());
        }
        session = PRONE.get(player.getUUID());
        if (session != null && session.player == player) {
            PRONE.remove(player.getUUID()); RevivedPronePose.end(player);
            send(session, Action.END, player.position());
        }
    }
    public static void clear(MinecraftServer server) {
        for (Session session : new ArrayList<>(SESSIONS.values())) if (session.player.server == server) end(session.player);
        for (Session session : new ArrayList<>(PRONE.values())) if (session.player.server == server) end(session.player);
    }
    public static void tick(MinecraftServer server) {
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = session.player;
            if (player.server != server) continue;
            if (!valid(session) || (session.rescuable ? !session.state.isPlayerDowned(player.getUUID())
            : !session.state.isInRespawnFlow(player.getUUID()) || session.state.getDeployViewActivePlayers().contains(player.getUUID())
                || session.state.getRespawnFinalizeTicks().containsKey(player.getUUID()))) { end(player); continue; }
            if (session.reviving) continue;
            Entity killer = session.killer;
            // Entity identity prevents following a respawned player or a reused entity ID.
            if (killer != null && (killer.level() != player.level() || killer.isRemoved()
                || killer instanceof LivingEntity living && !living.isAlive()
                || session.state.isInRespawnFlow(session.killerId))) session.killer = null;
            if (session.killer != null) session.killerPosition = session.killer.getEyePosition();
            if ((server.getTickCount() + session.id) % 4 == 0)
                send(session, Action.UPDATE, session.rescuable ? DownedRagdollBridge.position(player)
                    : (session.cameraAnchor = DownedRagdollBridge.ordinaryDeathAnchor(player,session.corpseRecord,session.cameraAnchor)));
        }
        for (Session session : new ArrayList<>(PRONE.values())) {
            ServerPlayer player = session.player;
            if (player.server != server) continue;
            if (!valid(session) || !player.isAlive() || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                || !session.state.getRoomPlayers().contains(player.getUUID())) { end(player); continue; }
            if (player.isPassenger() || player.isSwimming() || player.isFallFlying()) { releaseProne(player, session.id); continue; }
            RevivedPronePose.apply(player);
            // A light heartbeat confirms state after packet loss/late clear; never restores an old session.
            if ((server.getTickCount() + session.id) % 40 == 0) send(session, Action.REVIVED, player.position());
        }
    }
    private static boolean valid(Session session) {
        ServerPlayer player = session.player;
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player
            && player.level().dimension().location().toString().equals(session.dimension);
    }
    private static void send(Session session, Action action, Vec3 position) {
        NetworkManager.sendToPlayer(session.player, new DownedViewPacket(action, session.id, ++session.sequence,
            session.player.getId(), session.dimension, session.mode, session.yaw, position,
            session.killerId, session.killerPosition, session.killer != null));
    }
    public static Vec3 safePronePosition(ServerPlayer player, Vec3 anchor, Vec3 fallback) {
        // Check the actual pose dimensions, not the 1.8-block standing box.
        if (safe(player, anchor)) return anchor;
        BlockPos base = BlockPos.containing(anchor.x, anchor.y, anchor.z);
        for (int radius = 0; radius <= 3; radius++) for (int dy = 1; dy >= -3; dy--)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                Vec3 point = new Vec3(base.getX() + dx + .5, base.getY() + dy, base.getZ() + dz + .5);
                if (safe(player, point)) return point;
            }
        // Validate the origin too. Widen the search if the original floor was destroyed.
        if (safe(player, fallback)) return fallback;
        BlockPos origin = BlockPos.containing(fallback.x, fallback.y, fallback.z);
        for (int radius = 4; radius <= 8; radius++) for (int dy = 3; dy >= -8; dy--)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                Vec3 point = new Vec3(origin.getX() + dx + .5, origin.getY() + dy, origin.getZ() + dz + .5);
                if (safe(player, point)) return point;
            }
        // Never select a farther solid location merely to satisfy a standing clearance rule.
        return player.position();
    }
    private static boolean safe(ServerPlayer player, Vec3 feet) {
        var level = player.serverLevel();
        AABB box = player.getDimensions(Pose.SWIMMING).makeBoundingBox(feet);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1e-5, box.maxY - 1e-5, box.maxZ - 1e-5);
        for (int x = min.getX(); x <= max.getX(); x++)
            for (int y = min.getY(); y <= max.getY(); y++)
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos block = new BlockPos(x,y,z);
                    if (!level.hasChunkAt(block) || !level.getBlockState(block).getFluidState().isEmpty()) return false;
                }
        // Small support probe handles slabs, stairs and non-integral ragdoll heights.
        AABB support = new AABB(box.minX, box.minY - .12, box.minZ, box.maxX, box.minY, box.maxZ);
        return level.noCollision(box) && !level.noCollision(support);
    }
    private static final class Session {
        final ServerPlayer player; final RunningRuntimeState state; final long id;
        final DownedCameraMode mode; final float yaw; final Vec3 origin; final String dimension;
        final UUID killerId; Entity killer; Vec3 killerPosition; int sequence; boolean reviving;
        boolean rescuable = true; Object corpseRecord; Vec3 cameraAnchor;
        Session(ServerPlayer player, RunningRuntimeState state, long id, DownedCameraMode mode) {
            this.player = player; this.state = state; this.id = id; this.mode = mode;
            this.yaw = player.getYRot(); this.origin = player.position();
            this.dimension = player.level().dimension().location().toString();
            this.killer = player.serverLevel().getEntity(state.getDeathKillerEntityIdOrDefault(player.getUUID()));
            if (killer == player || killer != null && killer.isRemoved()) killer = null;
            this.killerId = killer == null ? null : killer.getUUID();
            this.killerPosition = killer == null ? origin : killer.getEyePosition();
        }
    }
}
