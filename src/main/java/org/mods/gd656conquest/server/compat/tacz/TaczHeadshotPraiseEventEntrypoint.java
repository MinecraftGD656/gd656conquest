package org.mods.gd656conquest.server.compat.tacz;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

import java.lang.reflect.Method;

public final class TaczHeadshotPraiseEventEntrypoint {
    private static final String TACZ_KILL_EVENT_CLASS = "com.tacz.guns.api.event.common.EntityKillByGunEvent";
    private static final String LOGICAL_SIDE_SERVER = "SERVER";
    private static volatile Class<?> killEventClass;
    private static volatile Method getLogicalSideMethod;
    private static volatile Method isHeadShotMethod;
    private static volatile Method getAttackerMethod;
    private static volatile Method getKilledEntityMethod;
    private static volatile boolean methodsResolved;

    @SubscribeEvent
    public void onForgeEvent(Event event) {
        if (event == null || !TACZ_KILL_EVENT_CLASS.equals(event.getClass().getName())) {
            return;
        }
        if (!ensureMethodsResolved(event.getClass())) {
            return;
        }
        if (!isServerSide(event)) {
            return;
        }
        ServerPlayer attacker = resolveServerPlayer(invoke(event, getAttackerMethod));
        ServerPlayer killedPlayer = resolveServerPlayer(invoke(event, getKilledEntityMethod));
        if (attacker == null || attacker.server == null || attacker.isSpectator() || killedPlayer == null || killedPlayer == attacker) {
            return;
        }
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(attacker.server);
        boolean playedNiceShot = false;
        if (isHeadshot(event)) {
            playedNiceShot = roomManager.handleNearbyHeadshotPraise(attacker);
        }
        if (!playedNiceShot) {
            roomManager.emitPlayerThirdPersonAudioCue(attacker.getUUID(), RoomThirdPersonAudioCueRegistry.PLAYER_KILL_ENEMY);
        }
    }

    private static boolean ensureMethodsResolved(Class<?> runtimeClass) {
        if (methodsResolved) {
            return killEventClass != null
                && getLogicalSideMethod != null
                && isHeadShotMethod != null
                && getAttackerMethod != null
                && getKilledEntityMethod != null;
        }
        synchronized (TaczHeadshotPraiseEventEntrypoint.class) {
            if (methodsResolved) {
                return killEventClass != null
                    && getLogicalSideMethod != null
                    && isHeadShotMethod != null
                    && getAttackerMethod != null
                    && getKilledEntityMethod != null;
            }
            methodsResolved = true;
            try {
                killEventClass = runtimeClass;
                getLogicalSideMethod = runtimeClass.getMethod("getLogicalSide");
                isHeadShotMethod = runtimeClass.getMethod("isHeadShot");
                getAttackerMethod = runtimeClass.getMethod("getAttacker");
                getKilledEntityMethod = runtimeClass.getMethod("getKilledEntity");
            } catch (Exception ignored) {
                killEventClass = null;
                getLogicalSideMethod = null;
                isHeadShotMethod = null;
                getAttackerMethod = null;
                getKilledEntityMethod = null;
            }
        }
        return killEventClass != null
            && getLogicalSideMethod != null
            && isHeadShotMethod != null
            && getAttackerMethod != null
            && getKilledEntityMethod != null;
    }

    private static boolean isServerSide(Object event) {
        Object logicalSide = invoke(event, getLogicalSideMethod);
        return logicalSide != null && LOGICAL_SIDE_SERVER.equalsIgnoreCase(String.valueOf(logicalSide));
    }

    private static boolean isHeadshot(Object event) {
        Object result = invoke(event, isHeadShotMethod);
        return result instanceof Boolean value && value;
    }

    private static Object invoke(Object target, Method method) {
        if (target == null || method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ServerPlayer resolveServerPlayer(Object entity) {
        return entity instanceof ServerPlayer player ? player : null;
    }
}
