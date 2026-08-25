package org.mods.gd656conquest.server.vehicle;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class DroneLifecycleService {
    private static final String MONITOR_ITEM_CLASS = "com.atsuishio.superbwarfare.item.misc.MonitorItem";
    private static final String MONITOR_TAG_LINKED = "Linked";
    private static final String MONITOR_TAG_LINKED_DRONE = "LinkedDrone";
    private static final Map<MinecraftServer, DroneLifecycleService> INSTANCES = new WeakHashMap<>();
    private static final String ACTIONBAR_SOURCE_DRONE_LIFECYCLE = "drone_lifecycle";
    private static final int COUNTDOWN_ACTIONBAR_TICKS = 25;
    private static final int FINAL_ACTIONBAR_TICKS = 60;
    private static final int TICKS_PER_SECOND = 20;

    private final MinecraftServer server;
    private final Map<UUID, TrackedDroneState> trackedDrones = new LinkedHashMap<>();

    private DroneLifecycleService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static DroneLifecycleService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, DroneLifecycleService::new);
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

    public void registerPlacedDrone(ServerPlayer owner, Entity entity, long gameTick) {
        if (owner == null || entity == null || owner.server == null || owner.server != server) {
            return;
        }
        if (!(entity instanceof DroneEntity drone)) {
            return;
        }
        String roomId = RoomCoreRuntimeManager.of(server).findPlayerRoomId(owner.getUUID()).orElse("");
        if (roomId.isBlank()) {
            return;
        }
        int normalDurationSeconds = ConquestDataManager.of(server).serverConfig().getDroneBatteryDurationSeconds();
        int kamikazeDurationSeconds = ConquestDataManager.of(server).serverConfig().getKamikazeDroneBatteryDurationSeconds();
        long expireTick = gameTick + (long) normalDurationSeconds * TICKS_PER_SECOND;
        boolean isKamikaze = isDroneKamikaze(drone);
        if (isKamikaze) {
            expireTick = Math.min(expireTick, gameTick + (long) kamikazeDurationSeconds * TICKS_PER_SECOND);
        }
        trackedDrones.put(
            drone.getUUID(),
            new TrackedDroneState(
                drone.getUUID(),
                owner.getUUID(),
                roomId,
                gameTick,
                expireTick,
                normalDurationSeconds,
                kamikazeDurationSeconds,
                isKamikaze,
                isKamikaze,
                false,
                Integer.MIN_VALUE,
                Integer.MIN_VALUE
            )
        );
    }

    public void onServerTick(long gameTick) {
        if (trackedDrones.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, TrackedDroneState>> iterator = trackedDrones.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedDroneState> entry = iterator.next();
            TrackedDroneState state = entry.getValue();
            DroneEntity drone = resolveTrackedDrone(state.droneId());
            ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerId());
            Optional<String> currentRoomId = RoomCoreRuntimeManager.of(server).findPlayerRoomId(state.ownerId());

            if (drone == null || !drone.isAlive()) {
                finalizeMissingDrone(state, owner);
                iterator.remove();
                continue;
            }

            boolean droneKamikaze = isDroneKamikaze(drone);
            state = state.withLastObservedKamikaze(droneKamikaze || state.lastObservedKamikaze());

            if (owner == null) {
                destroyTrackedDrone(state, drone, null, false);
                iterator.remove();
                continue;
            }

            if (currentRoomId.isEmpty() || !state.roomId().equalsIgnoreCase(currentRoomId.get())) {
                destroyTrackedDrone(state, drone, owner, true);
                iterator.remove();
                continue;
            }

            if (!owner.isAlive() && !RoomCoreRuntimeManager.of(server).isPlayerDowned(owner.getUUID())) {
                destroyTrackedDrone(state, drone, owner, true);
                iterator.remove();
                continue;
            }

            if (!state.enteredKamikazeMode() && droneKamikaze) {
                long kamikazeExpireTick = gameTick + (long) state.kamikazeDurationSeconds() * TICKS_PER_SECOND;
                long nextExpireTick = Math.min(state.expireTick(), kamikazeExpireTick);
                state = state.enterKamikazeMode(nextExpireTick);
            }

            long remainingTicks = Math.max(0L, state.expireTick() - gameTick);
            if (remainingTicks <= 0L) {
                destroyTrackedDrone(state, drone, owner, true);
                iterator.remove();
                continue;
            }

            state = pushCountdownActionbar(state, owner, remainingTicks);
            entry.setValue(state);
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        destroyOwnedDrones(player, false);
    }

    public void onPlayerConfirmedDeath(ServerPlayer player) {
        destroyOwnedDrones(player, true);
    }

    private void destroyOwnedDrones(ServerPlayer player, boolean showActionbar) {
        if (player == null || player.server == null || player.server != server || trackedDrones.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, TrackedDroneState>> iterator = trackedDrones.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedDroneState> entry = iterator.next();
            TrackedDroneState state = entry.getValue();
            if (!player.getUUID().equals(state.ownerId())) {
                continue;
            }
            DroneEntity drone = resolveTrackedDrone(state.droneId());
            if (drone == null || !drone.isAlive()) {
                finalizeMissingDrone(state, showActionbar ? player : null);
                iterator.remove();
                continue;
            }
            destroyTrackedDrone(state, drone, player, showActionbar);
            iterator.remove();
        }
    }

    private TrackedDroneState pushCountdownActionbar(TrackedDroneState state, ServerPlayer owner, long remainingTicks) {
        if (owner == null) {
            return state;
        }
        int remainingSeconds = ceilSeconds(remainingTicks);
        if (state.enteredKamikazeMode()) {
            if (remainingSeconds != state.lastKamikazeCountdownSecond()) {
                ServerActionBarService.showBuilder(
                    owner,
                    ACTIONBAR_SOURCE_DRONE_LIFECYCLE,
                    ServerActionBarService.builder()
                        .text("即将自毁 ", ChatFormatting.RED)
                        .text(remainingSeconds + "秒", ChatFormatting.DARK_RED),
                    COUNTDOWN_ACTIONBAR_TICKS
                );
                return state.withLastKamikazeCountdownSecond(remainingSeconds);
            }
            return state;
        }

        long halfTickThreshold = (long) state.normalDurationSeconds() * TICKS_PER_SECOND / 2L;
        if (!state.halfBatteryWarned() && remainingTicks <= halfTickThreshold) {
            ServerActionBarService.showBuilder(
                owner,
                ACTIONBAR_SOURCE_DRONE_LIFECYCLE,
                ServerActionBarService.builder()
                    .text("无人机剩余电量还可支持 ", ChatFormatting.GRAY)
                    .text(remainingSeconds + "秒", ChatFormatting.AQUA),
                COUNTDOWN_ACTIONBAR_TICKS
            );
            state = state.withHalfBatteryWarned(true);
        }

        long quarterTickThreshold = Math.max(TICKS_PER_SECOND, (long) state.normalDurationSeconds() * TICKS_PER_SECOND / 4L);
        if (remainingTicks <= quarterTickThreshold && remainingSeconds != state.lastLowBatteryCountdownSecond()) {
            ServerActionBarService.showBuilder(
                owner,
                ACTIONBAR_SOURCE_DRONE_LIFECYCLE,
                ServerActionBarService.builder()
                    .text("电量即将耗尽 ", ChatFormatting.GRAY)
                    .text(remainingSeconds + "秒", resolveLowBatteryColor(remainingSeconds)),
                COUNTDOWN_ACTIONBAR_TICKS
            );
            state = state.withLastLowBatteryCountdownSecond(remainingSeconds);
        }
        return state;
    }

    private void finalizeMissingDrone(TrackedDroneState state, ServerPlayer owner) {
        if (state == null) {
            return;
        }
        if (owner != null) {
            unlinkMonitor(owner, state.droneId());
            showFinalActionbar(owner, state.lastObservedKamikaze() || state.enteredKamikazeMode());
        }
    }

    private void destroyTrackedDrone(TrackedDroneState state, DroneEntity drone, ServerPlayer owner, boolean showActionbar) {
        boolean kamikaze = state.lastObservedKamikaze() || state.enteredKamikazeMode() || isDroneKamikaze(drone);
        if (owner != null) {
            unlinkMonitor(owner, state.droneId());
        }
        if (kamikaze) {
            drone.destroy();
        } else {
            shutdownDroneSilently(drone, owner);
        }
        if (owner != null && showActionbar) {
            showFinalActionbar(owner, kamikaze);
        }
    }

    private void shutdownDroneSilently(DroneEntity drone, ServerPlayer owner) {
        if (drone == null) {
            return;
        }
        try {
            drone.getEntityData().set(DroneEntity.LINKED, false);
            drone.getEntityData().set(DroneEntity.CONTROLLER, "none");
        } catch (Exception ignored) {
        }
        if (owner != null) {
            unlinkMonitor(owner, drone.getUUID());
        }
        drone.discard();
    }

    private void unlinkMonitor(ServerPlayer owner, UUID droneId) {
        if (owner == null || droneId == null) {
            return;
        }
        unlinkMonitorStack(owner, owner.getMainHandItem(), droneId);
        unlinkMonitorStack(owner, owner.getOffhandItem(), droneId);
        for (ItemStack stack : owner.getInventory().items) {
            unlinkMonitorStack(owner, stack, droneId);
        }
    }

    private void unlinkMonitorStack(ServerPlayer owner, ItemStack stack, UUID droneId) {
        if (owner == null || stack == null || stack.isEmpty() || droneId == null) {
            return;
        }
        if (!MONITOR_ITEM_CLASS.equals(stack.getItem().getClass().getName())) {
            return;
        }
        String linkedDrone = stack.getOrCreateTag().getString(MONITOR_TAG_LINKED_DRONE);
        if (!droneId.toString().equalsIgnoreCase(linkedDrone)) {
            return;
        }
        stack.getOrCreateTag().putBoolean(MONITOR_TAG_LINKED, false);
        stack.getOrCreateTag().putString(MONITOR_TAG_LINKED_DRONE, "none");
    }

    private void showFinalActionbar(ServerPlayer owner, boolean kamikaze) {
        if (owner == null) {
            return;
        }
        ServerActionBarService.showBuilder(
            owner,
            ACTIONBAR_SOURCE_DRONE_LIFECYCLE,
            ServerActionBarService.builder()
                .text(kamikaze ? "无人机已自毁" : "无人机已下线", kamikaze ? ChatFormatting.DARK_RED : ChatFormatting.RED),
            FINAL_ACTIONBAR_TICKS
        );
    }

    private DroneEntity resolveTrackedDrone(UUID droneId) {
        if (droneId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(droneId);
            if (entity instanceof DroneEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static boolean isDroneKamikaze(DroneEntity drone) {
        if (drone == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(drone.getEntityData().get(DroneEntity.IS_KAMIKAZE));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int ceilSeconds(long remainingTicks) {
        if (remainingTicks <= 0L) {
            return 0;
        }
        return (int) Math.ceil(remainingTicks / (double) TICKS_PER_SECOND);
    }

    private static ChatFormatting resolveLowBatteryColor(int remainingSeconds) {
        if (remainingSeconds > 10) {
            return ChatFormatting.AQUA;
        }
        if (remainingSeconds > 5) {
            return ChatFormatting.GREEN;
        }
        if (remainingSeconds >= 3) {
            return ChatFormatting.YELLOW;
        }
        if (remainingSeconds == 2) {
            return ChatFormatting.GOLD;
        }
        return ChatFormatting.RED;
    }

    private record TrackedDroneState(
        UUID droneId,
        UUID ownerId,
        String roomId,
        long spawnTick,
        long expireTick,
        int normalDurationSeconds,
        int kamikazeDurationSeconds,
        boolean enteredKamikazeMode,
        boolean lastObservedKamikaze,
        boolean halfBatteryWarned,
        int lastLowBatteryCountdownSecond,
        int lastKamikazeCountdownSecond
    ) {
        private TrackedDroneState enterKamikazeMode(long nextExpireTick) {
            return new TrackedDroneState(
                droneId,
                ownerId,
                roomId,
                spawnTick,
                nextExpireTick,
                normalDurationSeconds,
                kamikazeDurationSeconds,
                true,
                true,
                halfBatteryWarned,
                lastLowBatteryCountdownSecond,
                lastKamikazeCountdownSecond
            );
        }

        private TrackedDroneState withHalfBatteryWarned(boolean nextValue) {
            return new TrackedDroneState(
                droneId,
                ownerId,
                roomId,
                spawnTick,
                expireTick,
                normalDurationSeconds,
                kamikazeDurationSeconds,
                enteredKamikazeMode,
                lastObservedKamikaze,
                nextValue,
                lastLowBatteryCountdownSecond,
                lastKamikazeCountdownSecond
            );
        }

        private TrackedDroneState withLastLowBatteryCountdownSecond(int nextSecond) {
            return new TrackedDroneState(
                droneId,
                ownerId,
                roomId,
                spawnTick,
                expireTick,
                normalDurationSeconds,
                kamikazeDurationSeconds,
                enteredKamikazeMode,
                lastObservedKamikaze,
                halfBatteryWarned,
                nextSecond,
                lastKamikazeCountdownSecond
            );
        }

        private TrackedDroneState withLastKamikazeCountdownSecond(int nextSecond) {
            return new TrackedDroneState(
                droneId,
                ownerId,
                roomId,
                spawnTick,
                expireTick,
                normalDurationSeconds,
                kamikazeDurationSeconds,
                enteredKamikazeMode,
                lastObservedKamikaze,
                halfBatteryWarned,
                lastLowBatteryCountdownSecond,
                nextSecond
            );
        }

        private TrackedDroneState withLastObservedKamikaze(boolean nextValue) {
            return new TrackedDroneState(
                droneId,
                ownerId,
                roomId,
                spawnTick,
                expireTick,
                normalDurationSeconds,
                kamikazeDurationSeconds,
                enteredKamikazeMode,
                nextValue,
                halfBatteryWarned,
                lastLowBatteryCountdownSecond,
                lastKamikazeCountdownSecond
            );
        }
    }
}
