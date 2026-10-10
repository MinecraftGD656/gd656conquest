package org.mods.gd656conquest.server.room.runtime;

import java.util.function.BooleanSupplier;

/** Command identity and pre-event fire metadata are scoped independently and restored on every exit. */
public final class ScopedDeathAttribution<E, F> {
    private final ThreadLocal<E> command = new ThreadLocal<>();
    private final ThreadLocal<F> fire = new ThreadLocal<>();

    public E commandTarget() { return command.get(); }
    public F fireSnapshot() { return fire.get(); }

    public void commandKill(E target, Runnable kill) {
        E previous = command.get();
        command.set(target);
        try { kill.run(); }
        finally {
            if (previous == null) command.remove(); else command.set(previous);
        }
    }

    public boolean withFireSnapshot(F snapshot, BooleanSupplier transition) {
        F previous = fire.get();
        fire.set(snapshot);
        try { return transition.getAsBoolean(); }
        finally {
            if (previous == null) fire.remove(); else fire.set(previous);
        }
    }
}
