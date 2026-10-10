package tests;

import org.mods.gd656conquest.common.player.ManualDeathDeployModel;

/** Runs the actual packaged input model; does not substitute Minecraft/server entities. */
public final class ManualDeathDeployModelCheck {
    static int checks;
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        var model = new ManualDeathDeployModel();
        model.begin(1);
        check(!model.accept(0, 200, 100, true), "Unrelated death rejected");
        check(!model.accept(1, -1, 100, true), "Invalid remaining time rejected");
        check(model.accept(1, 200, 100, true), "Authoritative waiting snapshot accepted");
        check(!model.press(true, true, 101), "Jump held before death is not confirmation");
        check(!model.press(false, true, 102) && model.press(true, true, 103), "Fresh press may open view before timer completion");
        check(!model.press(true, true, 104) && !model.accept(1, 190, 110, true), "Pending press suppresses duplicates and immediate heartbeat");
        check(model.accept(1, 150, 150, true), "Continued server waiting permits retry after failed view");
        check(!model.press(true, true, 151), "Held retry is not automatically submitted");
        model.press(false, true, 152);
        check(model.press(true, true, 153), "Released and newly pressed retry works");
        model.begin(2); model.accept(2, 20, 200, false);
        check(model.seconds(225) == 0 && model.active(), "Zero time waits indefinitely");
        check(!model.press(true, false, 226) && !model.press(true, true, 227), "Chat press cannot confirm on chat close");
        model.press(false, true, 228);
        check(model.press(true, true, 229), "New gameplay press confirms");
        model.close();
        check(!model.accept(2, 0, 500, false), "Closed session rejects late heartbeat");
        model.begin(3);
        check(!model.accept(2, 0, 600, false), "Previous death cannot update next session");
        check(ManualDeathDeployModel.remaining(100, 101) == 0, "Elapsed deadline clamps to zero");
        check(ManualDeathDeployModel.remaining(Long.MAX_VALUE, 0) == Integer.MAX_VALUE, "Remaining time clamps to packet range");
        model.reset(); model.begin(1); model.accept(1, 40, 100, false);
        check(model.seconds(99) == 2 && model.seconds(120) == 1, "Clock rollback and seconds rounding");
        check(model.seconds(1000000) == 0 && model.active(), "Long wait does not automatically deploy");
        System.out.println("PASS packaged manual deployment input/deadline model: " + checks);
    }
}
