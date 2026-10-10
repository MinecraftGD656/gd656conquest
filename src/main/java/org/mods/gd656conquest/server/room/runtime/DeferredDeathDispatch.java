package org.mods.gd656conquest.server.room.runtime;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Keeps room transitions after all death listeners, with scoped support for nested deaths. */
public final class DeferredDeathDispatch<E> {
    private final ThreadLocal<Dispatch<E>> active = new ThreadLocal<>();

    public boolean dispatch(Object victim, Object source, BooleanSupplier postEvent,
                            BooleanSupplier recovered, Predicate<E> canceled) {
        Dispatch<E> previous = active.get();
        for (Dispatch<E> ancestor = previous; ancestor != null; ancestor = ancestor.previous) {
            if (ancestor.victim == victim) {
                return true;
            }
        }
        Dispatch<E> current = new Dispatch<>(victim, source, previous);
        active.set(current);
        try {
            boolean stopped = postEvent.getAsBoolean();
            if (stopped || current.event == null) {
                return stopped;
            }
            if (canceled.test(current.event)) {
                return true;
            }
            if (recovered.getAsBoolean()) {
                return true;
            }
            current.processing = true;
            current.callback.run();
            return current.handled;
        } finally {
            if (previous == null) {
                active.remove();
            } else {
                active.set(previous);
            }
        }
    }

    /** Defers only the callback belonging to the exact victim and damage-source instances. */
    public boolean defer(Object victim, Object source, E event, Runnable callback) {
        Dispatch<E> current = active.get();
        if (current == null || current.processing || event == null
            || current.victim != victim || current.source != source) {
            return false;
        }
        if (current.event == null) {
            current.event = event;
            current.callback = callback;
        }
        return true;
    }

    /** Changes the outer vanilla-hook result, never the event's cancellation state. */
    public void handled(E event, boolean handled) {
        Dispatch<E> current = active.get();
        if (isProcessing(event) && handled) {
            current.handled = true;
        }
    }

    public boolean isProcessing(E event) {
        Dispatch<E> current = active.get();
        return current != null && current.processing && current.event == event;
    }

    public boolean isProcessingVictim(Object victim) {
        Dispatch<E> current = active.get();
        return current != null && current.processing && current.victim == victim;
    }

    private static final class Dispatch<E> {
        final Object victim;
        final Object source;
        final Dispatch<E> previous;
        E event;
        Runnable callback;
        boolean processing;
        boolean handled;

        Dispatch(Object victim, Object source, Dispatch<E> previous) {
            this.victim = victim;
            this.source = source;
            this.previous = previous;
        }
    }
}
