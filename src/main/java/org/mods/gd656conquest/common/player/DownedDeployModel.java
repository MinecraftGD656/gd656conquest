package org.mods.gd656conquest.common.player;

/** Server-confirmed readiness; local interpolation cannot unlock the single-press action. */
public final class DownedDeployModel {
    private long deathId, receivedTick;
    private int deploymentTicks, deathTicks;
    private boolean enabled, accepted, closed = true, jumpWasDown;
    public void begin(long id) {
        if (id <= deathId) return;
        deathId = id; accepted = enabled = false; closed = false;
        deploymentTicks = deathTicks = 0; jumpWasDown = true;
    }
    public boolean accept(long id, boolean enabled, int deploymentTicks, int deathTicks, long now, boolean jumpDown) {
        if (closed || id != deathId || deploymentTicks < 0 || deathTicks < 0) return false;
        boolean wasReady = ready();
        this.enabled = enabled; this.deploymentTicks = deploymentTicks; this.deathTicks = deathTicks;
        receivedTick = now; accepted = true;
        if (!wasReady && ready()) jumpWasDown = jumpDown;
        return true;
    }
    public boolean enabled() { return accepted && !closed && enabled; }
    public boolean known() { return accepted && !closed; }
    public boolean ready() { return enabled() && deploymentTicks == 0; }
    public int deploymentSeconds(long now) {
        int seconds = seconds(deploymentTicks, now);
        return ready() ? 0 : Math.max(1, seconds);
    }
    public int deathSeconds(long now) { return seconds(deathTicks, now); }
    private int seconds(int ticks, long now) { return (int)Math.ceil(Math.max(0L, ticks - Math.max(0L, now - receivedTick)) / 20D); }
    public boolean press(boolean down, boolean allowed) {
        boolean edge = down && !jumpWasDown; jumpWasDown = down;
        return edge && allowed && ready();
    }
    public void close() { closed = true; accepted = enabled = false; jumpWasDown = true; }
    public void reset() { close(); deathId = 0; }
}
