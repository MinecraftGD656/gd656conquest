package tests;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.client.room.DeathCauseBanner;
import org.mods.gd656conquest.server.room.entry.DeathCauseClassifier;
import org.mods.gd656conquest.server.room.entry.DeathCauseClassifier.*;
import org.mods.gd656conquest.server.room.runtime.ScopedDeathAttribution;

/** Checks actual production classifiers, presentation state and nested fatal scopes. */
public final class DeathCauseRulesCheck {
    private static int checks;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    private static DeathCause classify(SystemDeath system, Ownership owner, String type, boolean fire) {
        return DeathCauseClassifier.classify(new Context(system, owner, type, fire));
    }
    public static void main(String[] args) throws Exception {
        for (String type : new String[]{null, "", "minecraft:fall", "minecraft:drown", "minecraft:lava", "future:unknown"}) {
            for (boolean fire : new boolean[]{false, true}) {
                check(classify(SystemDeath.COMMAND, Ownership.ENEMY, type, fire) == DeathCause.COMMAND_KILL, "Command overrides ownership/type");
                check(classify(SystemDeath.BOUNDARY, Ownership.ENEMY, type, fire) == DeathCause.DESERTION, "Boundary overrides ownership/type");
                check(classify(SystemDeath.REDEPLOY, Ownership.ENEMY, type, fire) == DeathCause.REDEPLOY, "Redeploy overrides ownership/type");
                check(classify(SystemDeath.NONE, Ownership.ENEMY, type, fire) == DeathCause.PLAYER_KILL, "Enemy credit includes fall/fire fallback");
                check(classify(SystemDeath.NONE, Ownership.SELF, type, fire) == DeathCause.ACCIDENT, "Self credit is accident");
            }
        }
        Map<String, DeathCause> types = Map.of("minecraft:fall", DeathCause.FALL, "minecraft:drown", DeathCause.DROWNED,
            "minecraft:falling_block", DeathCause.CRUSHED, "minecraft:falling_anvil", DeathCause.CRUSHED,
            "minecraft:falling_stalactite", DeathCause.CRUSHED, "minecraft:cramming", DeathCause.CRUSHED,
            "ywzj_vehicle:vehicle_collision", DeathCause.CRUSHED);
        for (var entry : types.entrySet()) {
            check(classify(SystemDeath.NONE, Ownership.NONE, entry.getKey(), false) == entry.getValue(), "Environment family");
            check(classify(SystemDeath.NONE, Ownership.OTHER, entry.getKey(), false) == entry.getValue(), "Non-enemy credit retains environment label");
        }
        check(classify(SystemDeath.NONE, Ownership.NONE, "future:fire", true) == DeathCause.BURNED, "Fire tag covers unknown types");
        check(classify(SystemDeath.NONE, Ownership.NONE, "future:unknown", false) == DeathCause.KILLED_IN_ACTION, "Unknown damage fallback");
        check(DeathCauseClassifier.classify(null) == DeathCause.KILLED_IN_ACTION, "Null context fallback");
        Set<Integer> ids = new HashSet<>();
        for (DeathCause cause : DeathCause.values()) {
            check(ids.add(cause.wireId()), "Wire IDs unique");
            check(DeathCause.fromWireId(cause.wireId()) == cause, "Wire IDs stable");
        }
        check(DeathCause.fromWireId(255) == DeathCause.KILLED_IN_ACTION, "Unknown wire ID fallback");

        var scope = new ScopedDeathAttribution<Object, String>();
        Object a = new Object(), b = new Object();
        check(scope.commandTarget() == null && scope.fireSnapshot() == null, "Initial attribution empty");
        scope.commandKill(a, () -> {
            check(scope.commandTarget() == a && scope.commandTarget() != b, "Command marks exact target identity");
            scope.commandKill(b, () -> check(scope.commandTarget() == b, "Nested command target"));
            check(scope.commandTarget() == a, "Nested command restores parent");
            check(scope.withFireSnapshot("pre-event owner", () -> {
                check(scope.commandTarget() == a && scope.fireSnapshot().equals("pre-event owner"), "Independent fire scope preserves command");
                scope.withFireSnapshot("nested owner", () -> {
                    check(scope.fireSnapshot().equals("nested owner"), "Nested fire snapshot");
                    return false;
                });
                check(scope.fireSnapshot().equals("pre-event owner"), "Fire scope restores parent");
                return true;
            }), "Fire scope preserves true result");
            check(scope.fireSnapshot() == null, "Fire scope cleans normal exit");
        });
        check(scope.commandTarget() == null, "Command scope cleans normal exit");
        try { scope.commandKill(a, () -> { throw new IllegalStateException("test"); }); }
        catch (IllegalStateException expected) { check(scope.commandTarget() == null, "Command exception cleanup"); }
        try { scope.withFireSnapshot("old owner", () -> { throw new IllegalStateException("test"); }); }
        catch (IllegalStateException expected) { check(scope.fireSnapshot() == null, "Fire exception cleanup"); }
        check(!scope.withFireSnapshot(null, () -> false), "Null fire snapshot and false result preserved");
        scope.commandKill(a, () -> {
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread other = new Thread(() -> {
                if (scope.commandTarget() != null || scope.fireSnapshot() != null) throw new AssertionError("Scope leaked across threads");
                scope.commandKill(b, () -> { if (scope.commandTarget() != b) throw new AssertionError("Other thread command scope"); });
            });
            other.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
            other.start();
            try { other.join(); } catch (InterruptedException error) { throw new AssertionError(error); }
            check(failure.get() == null, "Attribution scopes isolated between threads");
            check(scope.commandTarget() == a, "Other thread leaves parent command untouched");
        });

        var banner = new DeathCauseBanner();
        check(banner.begin(1, DeathCause.FALL), "Fresh death accepted");
        banner.advance(.1f, true);
        check(Math.abs(banner.alpha() - .5f) < .001, "200 ms fade-in");
        check(!banner.begin(1, DeathCause.BURNED) && !banner.begin(0, DeathCause.BURNED), "Duplicate/stale death ignored");
        check(banner.cause() == DeathCause.FALL, "Accepted metadata stays stable");
        banner.advance(.1f, true);
        check(banner.alpha() == 1, "Fade completes");
        banner.hide(); banner.advance(.1f, true);
        check(Math.abs(banner.alpha() - .5f) < .001, "200 ms fade-out");
        banner.begin(2, DeathCause.PLAYER_KILL); banner.advance(1, true);
        check(banner.alpha() == 0, "Player kill has no environment banner");
        banner.begin(3, null); banner.advance(Float.NaN, true); banner.advance(-1, true);
        check(banner.cause() == DeathCause.KILLED_IN_ACTION && banner.alpha() == 0, "Null cause and invalid frame fallback");
        banner.advance(.2f, false);
        check(banner.alpha() == 0, "Presentation waits for observation phase");
        for (int width : List.of(160, 320, 960, 1920)) for (String title : List.of("摔死", "重新部署中", "被管理员杀死", "KILLED BY ADMINISTRATOR")) {
            var box = DeathCauseBanner.layout(width, 180, title.length() * 9, title.length());
            check(box.left() >= 0 && box.left() + box.width() <= width && box.top() + box.height() <= 180, "Labels fit GUI sizes");
            check(Math.abs(box.left() + box.width() / 2 - width / 2f) < .001, "Banner centered");
        }
        banner.reset();
        check(banner.begin(1, DeathCause.FALL) && banner.alpha() == 0, "New connection resets IDs");
        System.out.println("PASS death classification / attribution scopes / banner: " + checks);
    }
}
