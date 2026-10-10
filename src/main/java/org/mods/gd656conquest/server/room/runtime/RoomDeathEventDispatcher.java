package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.mods.gd656conquest.server.compat.ragdoll.DownedRagdollBridge;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollFatalContext;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomServerModule;
import org.mods.gd656conquest.server.room.entry.RoomDeathCauseService;
import org.mods.gd656conquest.server.room.model.RoomPosition;

/** Room death ordering, fatal attribution and scoped body promotion. */
public final class RoomDeathEventDispatcher {
    private static final DeferredDeathDispatch<LivingDeathEvent> DISPATCH = new DeferredDeathDispatch<>();

    private RoomDeathEventDispatcher() {
    }

    public static boolean dispatchPlayerDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || player.server == null
            || RoomCoreRuntimeManager.of(player.server).findPlayerRoomId(player.getUUID()).isEmpty()) {
            return ForgeHooks.onLivingDeath(entity, source);
        }
        return RoomDeathCauseService.withFireOwnership(player, source,
            () -> org.mods.gd656conquest.server.room.entry.KillerIntelService.withFatalSnapshot(player, source,
                () -> dispatchTrackedDeath(player, source)));
    }

    private static boolean dispatchTrackedDeath(ServerPlayer player, DamageSource source) {
        if (!DownedRagdollBridge.availableFor(player)) return ForgeHooks.onLivingDeath(player, source);
        return RagdollFatalContext.withDeathRecord(player,
            () -> DownedRagdollBridge.withFatalSource(player, source,
                () -> DISPATCH.dispatch(player, source,
                    () -> ForgeHooks.onLivingDeath(player, source),
                    () -> player.getHealth() > 0.0F, LivingDeathEvent::isCanceled)));
    }

    public static boolean isProcessingDeath(ServerPlayer player) {
        return DISPATCH.isProcessingVictim(player);
    }

    public static boolean handleRoomFatalDeath(RoomCoreRuntimeManager manager, ServerPlayer player,
                                              RoomPosition position, int killerId, long tick, DamageSource source) {
        final int resolvedKillerId = RoomDeathCauseService.killerEntityId(player, killerId);
        if (isProcessingDeath(player)) {
            return manager.handlePlayerDeathEvent(player, position, resolvedKillerId, tick);
        }
        return DownedRagdollBridge.withFatalSource(player, source,
            () -> manager.handlePlayerDeathEvent(player, position, resolvedKillerId, tick));
    }

    public static boolean deferRoomDeath(RoomServerModule module, LivingDeathEvent event) {
        return event != null && DISPATCH.defer(event.getEntity(), event.getSource(), event,
            () -> module.onLivingDeath(event));
    }

    public static void onRoomDeath(LivingDeathEvent event, boolean downedHandled) {
        if (!DISPATCH.isProcessing(event)) {
            return;
        }
        if (downedHandled) {
            DISPATCH.handled(event, true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(player.server);
        RunningRuntimeState state = findState(manager, player);
        if (isDeploymentState(state, player)) {
            DISPATCH.handled(event, manager.applyRespawnPostProcess(player));
        }
    }

    /** A healed deployment proxy has already completed forced death; do not kill it again. */
    public static boolean isStillAliveOutsideDeployment(ServerPlayer player) {
        if (!player.isAlive()) {
            return false;
        }
        if (player.server == null) {
            return true;
        }
        RunningRuntimeState state = findState(RoomCoreRuntimeManager.of(player.server), player);
        return !isDeploymentState(state, player);
    }

    private static RunningRuntimeState findState(RoomCoreRuntimeManager manager, ServerPlayer player) {
        return manager.findPlayerRoomId(player.getUUID()).flatMap(manager::findRunningState).orElse(null);
    }

    private static boolean isDeploymentState(RunningRuntimeState state, ServerPlayer player) {
        return state != null && state.isInRespawnFlow(player.getUUID())
            && !state.isPlayerDowned(player.getUUID()) && !state.isPlayerPendingDowned(player.getUUID());
    }
}
