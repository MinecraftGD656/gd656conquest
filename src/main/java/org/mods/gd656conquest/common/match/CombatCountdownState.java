package org.mods.gd656conquest.common.match;

/** Presentation only: expiry never unlocks movement or advances a server stage. */
public final class CombatCountdownState {
    public enum Kind { INITIAL, ADVANCE }

    private Kind kind;
    private String text = "";
    private int remainingTicks;

    public void update(Kind next, String label, int ticks) {
        if (ticks <= 0 || label == null || label.isBlank()) {
            clear(next);
            return;
        }
        kind = next;
        text = label;
        remainingTicks = ticks;
    }

    public void clear(Kind source) { if (kind == source) reset(); }
    public void tick() { if (remainingTicks > 0 && --remainingTicks == 0) reset(); }
    public boolean active() { return remainingTicks > 0; }
    public String text() { return text; }
    public int remainingTicks() { return remainingTicks; }
    public void reset() { kind = null; text = ""; remainingTicks = 0; }
}
