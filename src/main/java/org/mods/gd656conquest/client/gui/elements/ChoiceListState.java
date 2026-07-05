package org.mods.gd656conquest.client.gui.elements;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ChoiceListState {
    private final String stateId;
    private double scrollY;
    private final Map<String, Boolean> expandedGroups = new HashMap<>();

    public ChoiceListState(String stateId) {
        this.stateId = Objects.requireNonNull(stateId);
    }

    public String getStateId() {
        return stateId;
    }

    public double getScrollY() {
        return scrollY;
    }

    public void setScrollY(double scrollY) {
        this.scrollY = scrollY;
    }

    public boolean isExpanded(String groupId) {
        return expandedGroups.getOrDefault(groupId, false);
    }

    public void setExpanded(String groupId, boolean expanded) {
        expandedGroups.put(groupId, expanded);
    }
}
