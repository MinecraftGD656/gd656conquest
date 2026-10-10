import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.mods.gd656conquest.server.room.runtime.DeferredDeathDispatch;

import java.util.ArrayList;
import java.util.List;

/** Uses the real Forge EventBus and a test payload, without Minecraft entities or API stubs. */
public final class DeferredDeathEventBusCheck {
    @Cancelable
    public static final class DeathProbe extends Event {
        public DeathProbe() {
        }
    }

    public static void main(String[] args) {
        IEventBus bus = BusBuilder.builder().build();
        DeferredDeathDispatch<DeathProbe> dispatch = new DeferredDeathDispatch<>();
        Object victim = new Object();
        Object source = new Object();
        List<String> order = new ArrayList<>();
        boolean[] earlyCancel = {false}, lateCancel = {false}, recovered = {false}, recoverInListener = {false};
        int[] statistics = {0}, transitions = {0};

        bus.addListener(EventPriority.HIGHEST, false, DeathProbe.class, event -> {
            order.add("highest");
            if (earlyCancel[0]) {
                event.setCanceled(true);
            }
        });
        bus.addListener(EventPriority.NORMAL, false, DeathProbe.class, event -> {
            order.add("room-listener");
            expect(dispatch.defer(victim, source, event, () -> {
                order.add("room-transition");
                transitions[0]++;
                dispatch.handled(event, true);
            }), "room callback must defer during real EventBus post");
        });
        bus.addListener(EventPriority.LOWEST, false, DeathProbe.class, event -> {
            order.add("statistics");
            statistics[0]++;
            expect(!event.isCanceled(), "statistics must see uncanceled event");
        });
        bus.addListener(EventPriority.LOWEST, false, DeathProbe.class, event -> {
            order.add("last");
            if (lateCancel[0]) {
                event.setCanceled(true);
            }
            if (recoverInListener[0]) {
                recovered[0] = true;
            }
        });

        java.util.function.BooleanSupplier attempt = () -> dispatch.dispatch(victim, source, () -> {
            boolean canceled = bus.post(new DeathProbe());
            order.add("bus-return");
            return canceled;
        }, () -> recovered[0], DeathProbe::isCanceled);

        expect(attempt.getAsBoolean(), "successful takeover stops vanilla");
        expect(order.equals(List.of("highest", "room-listener", "statistics", "last", "bus-return", "room-transition")),
            "all real Forge priorities must finish before transition");
        expect(statistics[0] == 1 && transitions[0] == 1, "one statistic and one transition");

        order.clear();
        earlyCancel[0] = true;
        expect(attempt.getAsBoolean(), "early external cancellation stops vanilla");
        expect(order.equals(List.of("highest", "bus-return")), "real cancellation skips room and statistics");
        expect(statistics[0] == 1 && transitions[0] == 1, "early cancellation creates no room effects");

        order.clear();
        earlyCancel[0] = false;
        lateCancel[0] = true;
        expect(attempt.getAsBoolean(), "late external cancellation stops vanilla");
        expect(statistics[0] == 2 && transitions[0] == 1, "late cancellation prevents deferred transition");

        order.clear();
        lateCancel[0] = false;
        recoverInListener[0] = true;
        expect(attempt.getAsBoolean(), "uncanceled external recovery stops vanilla");
        expect(statistics[0] == 3 && transitions[0] == 1, "external recovery prevents room takeover");
        expect(!order.contains("room-transition"), "recovered victim never enters deferred transition");
        System.out.println("PASS: 4 real Forge EventBus priority / cancellation / recovery scenarios");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
