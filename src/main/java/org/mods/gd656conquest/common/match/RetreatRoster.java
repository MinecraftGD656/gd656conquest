package org.mods.gd656conquest.common.match;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One sector's retreat. Completing or leaving it is irreversible until the next sector. */
public final class RetreatRoster {
    private final Set<UUID> pending = new LinkedHashSet<>();
    public void begin(Collection<UUID> defenders) {
        pending.clear();
        if (defenders != null) defenders.stream().filter(java.util.Objects::nonNull).forEach(pending::add);
    }
    public boolean isRetreating(UUID id) { return pending.contains(id); }
    public boolean complete(UUID id) { return pending.remove(id); }
    /** Removes the participant before awarding troops, preventing duplicate fatal callbacks. */
    public boolean fail(UUID id) { return pending.remove(id); }
    public Set<UUID> pending() { return Set.copyOf(pending); }
    public int size() { return pending.size(); }
    public void clear() { pending.clear(); }
}
