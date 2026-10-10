import org.mods.gd656conquest.server.room.runtime.DeferredDeathDispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Tests the production coordinator without loading Minecraft or replacing production classes. */
public final class DeferredDeathDispatchCheck {
    private static int checks;
    private static final DeferredDeathDispatch<Death> DISPATCH = new DeferredDeathDispatch<>();

    private static final class Victim {
        float health;
    }

    private static final class Death {
        final Victim victim;
        final Object source;
        boolean canceled;

        Death(Victim victim, Object source) {
            this.victim = victim;
            this.source = source;
        }
    }

    private static final class Scenario {
        final Victim victim = new Victim();
        final Object source = new Object();
        int posts;
        int transitions;
        int statistics;
        boolean handled = true;
        final List<String> order = new ArrayList<>();
        Death event;

        boolean die(Consumer<Death> observers) {
            return DISPATCH.dispatch(victim, source, () -> {
                posts++;
                event = new Death(victim, source);
                observers.accept(event);
                order.add("bus-return");
                return event.canceled;
            }, () -> victim.health > 0, e -> e.canceled);
        }

        void room(Death death) {
            if (DISPATCH.defer(death.victim, death.source, death, () -> room(death))) {
                return;
            }
            order.add("room-transition");
            transitions++;
            if (handled) {
                victim.health = 1;
            }
            DISPATCH.handled(death, handled);
        }

        void observers(Death death) {
            order.add("early");
            check(victim.health == 0, "early observer sees fatal state");
            room(death);
            check(transitions == 0 && victim.health == 0, "room callback leaves fatal state intact");
            order.add("statistics");
            statistics++;
            check(!death.canceled && victim.health == 0, "statistics sees uncanceled original death");
            order.add("late");
        }
    }

    public static void main(String[] args) throws Exception {
        Scenario ordinary = new Scenario();
        check(ordinary.die(ordinary::observers), "successful takeover stops vanilla continuation");
        check(ordinary.order.equals(List.of("early", "statistics", "late", "bus-return", "room-transition")),
            "every observer precedes room transition");
        check(ordinary.posts == 1 && ordinary.statistics == 1 && ordinary.transitions == 1,
            "event, statistics and transition each occur once");
        check(!ordinary.event.canceled, "room takeover never cancels event");

        Scenario earlyCancel = new Scenario();
        check(earlyCancel.die(e -> e.canceled = true), "early cancellation stops vanilla");
        check(earlyCancel.transitions == 0, "early cancellation prevents room transition");

        Scenario lateCancel = new Scenario();
        check(lateCancel.die(e -> {
            lateCancel.observers(e);
            e.canceled = true;
        }), "late cancellation stops vanilla");
        check(lateCancel.transitions == 0 && lateCancel.victim.health == 0,
            "late cancellation prevents deferred transition");

        Scenario recovered = new Scenario();
        check(recovered.die(e -> {
            recovered.room(e);
            recovered.victim.health = 20;
        }), "uncanceled external recovery stops vanilla");
        check(recovered.transitions == 0 && !recovered.event.canceled, "external recovery wins over takeover");

        Scenario failed = new Scenario();
        failed.handled = false;
        check(!failed.die(failed::observers), "failed takeover permits vanilla death");
        check(failed.transitions == 1 && failed.victim.health == 0, "failed takeover does not heal victim");

        Scenario missing = new Scenario();
        check(!missing.die(e -> missing.statistics++), "no matching room callback retains hook result");
        check(missing.statistics == 1, "untracked death still reaches observers once");

        Scenario mismatched = new Scenario();
        check(!mismatched.die(e -> {
            check(!DISPATCH.defer(e.victim, new Object(), e, () -> {
                throw new AssertionError("wrong source callback");
            }), "source instance must match");
            check(!DISPATCH.defer(new Victim(), e.source, e, () -> {
                throw new AssertionError("wrong victim callback");
            }), "victim instance must match");
        }), "mismatched callbacks cannot take over");

        Scenario recursive = new Scenario();
        check(recursive.die(e -> {
            recursive.observers(e);
            check(recursive.die(ignored -> {
                throw new AssertionError("recursive dispatch duplicated");
            }), "recursive same-victim death is stopped");
        }), "outer recursive attempt completes");
        check(recursive.posts == 1 && recursive.transitions == 1, "recursive death is recorded once");

        Scenario outer = new Scenario();
        Scenario inner = new Scenario();
        check(outer.die(e -> {
            outer.observers(e);
            check(inner.die(nested -> {
                inner.observers(nested);
                check(outer.die(ignored -> {
                    throw new AssertionError("ancestor victim dispatched again");
                }), "A to B to A recursion is stopped");
            }), "nested different-victim death completes");
            check(outer.transitions == 0, "nested completion preserves pending outer transition");
        }), "outer nested attempt completes");
        check(outer.posts == 1 && inner.posts == 1 && outer.transitions == 1 && inner.transitions == 1,
            "nested contexts restore and each victim transitions once");

        Scenario exception = new Scenario();
        try {
            exception.die(e -> {
                exception.room(e);
                throw new IllegalStateException("observer failure");
            });
            throw new AssertionError("observer exception swallowed");
        } catch (IllegalStateException expected) {
            check(exception.transitions == 0, "throwing observer prevents deferred transition");
        }
        check(!DISPATCH.defer(exception.victim, exception.source, exception.event, () -> {}),
            "observer exception clears thread context");
        check(exception.die(exception::observers), "subsequent death works after exception");

        DeferredDeathDispatch<Death> callbackFailure = new DeferredDeathDispatch<>();
        Victim failureVictim = new Victim();
        Object failureSource = new Object();
        Death failureEvent = new Death(failureVictim, failureSource);
        try {
            callbackFailure.dispatch(failureVictim, failureSource, () -> {
                callbackFailure.defer(failureVictim, failureSource, failureEvent, () -> {
                    throw new IllegalArgumentException("transition failure");
                });
                return false;
            }, () -> false, e -> false);
            throw new AssertionError("transition exception swallowed");
        } catch (IllegalArgumentException expected) {
            check(!callbackFailure.isProcessing(failureEvent), "transition exception clears processing context");
        }

        Scenario repeat = new Scenario();
        check(repeat.die(repeat::observers), "first life completes");
        repeat.victim.health = 0;
        repeat.transitions = 0;
        check(repeat.die(repeat::observers), "revived victim may dispatch a new death");
        check(repeat.posts == 2 && repeat.statistics == 2, "new life is credited once again");

        // A separate server/test thread must never inherit the calling thread's pending death.
        Scenario threaded = new Scenario();
        check(threaded.die(e -> {
            threaded.room(e);
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    if (DISPATCH.defer(e.victim, e.source, e, () -> {})) {
                        throw new AssertionError("thread context leaked");
                    }
                } catch (Throwable failure) {
                    error.set(failure);
                }
            });
            worker.start();
            try {
                worker.join(5000);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            check(!worker.isAlive() && error.get() == null, "pending death is isolated to its thread");
        }), "thread-isolated outer transition completes");

        System.out.println("PASS: " + checks + " death dispatch ordering / cancellation / recovery / recursion / cleanup checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
