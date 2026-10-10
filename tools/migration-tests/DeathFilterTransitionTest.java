package tests;

import org.mods.gd656conquest.client.room.DeathFilterTransition;

public final class DeathFilterTransitionTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static float run(int fps, boolean dying, float seconds, DeathFilterTransition transition) {
        float value = transition.strength();
        for (int frame = 0; frame < Math.ceil(fps * seconds); frame++) {
            float delta = Math.min(1F / fps, seconds - (float) frame / fps);
            float next = transition.advance(dying, delta);
            check(dying ? next >= value : next <= value, "Monotonic transition at " + fps + " FPS");
            check(next >= 0 && next <= DeathFilterTransition.MAX_DESATURATION, "Bounded strength");
            value = next;
        }
        return value;
    }
    public static void main(String[] args) {
        for (int fps : new int[]{20,30,60,144,240}) {
            DeathFilterTransition t = new DeathFilterTransition();
            check(t.advance(false, .5F) == 0, "Alive unchanged");
            check(t.advance(true, 0) == 0, "No first-frame color jump");
            float impact = run(fps, true, .32F, t);
            check(impact > .05F && impact < .2F, "Impact retains color while entry begins");
            float halfway = run(fps, true, .39F, t);
            check(Math.abs(halfway - .48F) < .0001F, "Halfway through full camera move, halfway desaturated");
            check(run(fps, true, .69F, t) < .96F, "Filter still entering before camera reveal ends");
            check(Math.abs(run(fps, true, .025F, t) - .96F) < .00001, "Entry completes at 1.42-second camera reveal");
            check(t.advance(true, 1) == .96F, "Killcam holds filter");
            check(t.advance(false, 0) == .96F, "Exit starts continuously");
            check(Math.abs(run(fps, false, .1F, t) - .48F) < .0001F, "Halfway through return, halfway restored");
            check(run(fps, false, .105F, t) == 0, "Revive/deployment recovers at 0.20-second camera return");
            run(fps,true,.1F,t);float interrupted=t.strength();
            check(t.advance(false,0)==interrupted,"Early revival continuity");
            run(fps,false,.1F,t);float redown=t.strength();
            check(t.advance(true,0)==redown,"Repeated death continuity");
            check(Float.isFinite(t.advance(true,Float.NaN)),"Invalid delta ignored");
            t.reset();check(t.strength()==0,"Disconnect reset");
        }
        System.out.println("PASS death filter timing and continuity: " + checks + " checks");
    }
}
