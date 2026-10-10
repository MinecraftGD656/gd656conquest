import org.mods.gd656conquest.server.room.runtime.ReviveTargetSelection;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Run with Java 17 source-file mode and build/classes/java/main on the classpath. */
public final class ReviveTargetSelectionCheck {
    public static void main(String[] args) {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID nearest = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Supplier<UUID> forbiddenFallback = () -> {
            throw new AssertionError("An explicit request must never query a replacement target");
        };

        // Multiple downed players: the selected player wins even when another is nearer.
        expect(requested, ReviveTargetSelection.select(requested, forbiddenFallback, requested::equals));
        // A failed authoritative check must not silently rescue a valid nearby player.
        expect(null, ReviveTargetSelection.select(requested, forbiddenFallback, nearest::equals));

        AtomicInteger nearestQueries = new AtomicInteger();
        expect(nearest, ReviveTargetSelection.select(null, () -> {
            nearestQueries.incrementAndGet();
            return nearest;
        }, nearest::equals));
        if (nearestQueries.get() != 1) {
            throw new AssertionError("Legacy requests must resolve the nearest target once");
        }
        // A nearest candidate still needs the same authoritative check.
        expect(null, ReviveTargetSelection.select(null, () -> nearest, id -> false));
        // No candidate means no authority lookup (and no null UUID player lookup).
        expect(null, ReviveTargetSelection.select(null, () -> null, id -> {
            throw new AssertionError("A missing target must not be validated");
        }));
        System.out.println("PASS: 5 rescue target selection scenarios");
    }

    private static void expect(UUID expected, UUID actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
