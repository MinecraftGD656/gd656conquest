package org.mods.gd656conquest.server.compat.ragdoll;

import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.runtime.RoomDeathEventDispatcher;

/** A pre-dispatch record identity distinguishes this death's corpse from a previous life. */
public final class RagdollFatalContext {
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private RagdollFatalContext() {
    }

    public static boolean withDeathRecord(ServerPlayer player, BooleanSupplier dispatch) {
        Snapshot previous = CURRENT.get();
        CURRENT.set(new Snapshot(player, DownedRagdollBridge.retainedRecord(player)));
        try {
            return dispatch.getAsBoolean();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static boolean isFreshDeathRecord(ServerPlayer player, Object record) {
        Snapshot current = CURRENT.get();
        return current != null && current.player == player && record != null
            && record != current.previousRecord && RoomDeathEventDispatcher.isProcessingDeath(player);
    }

    private record Snapshot(ServerPlayer player, Object previousRecord) {
    }
}
