package org.mods.gd656conquest.server.data.roomdefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RoomConfigValidationResult {
    private final List<String> missingItems = new ArrayList<>();
    private final Set<String> missingConfigIds = new LinkedHashSet<>();

    public void addMissing(String value) {
        addMissingItem(value);
        String inferredConfigId = inferConfigId(value);
        if (inferredConfigId != null) {
            missingConfigIds.add(inferredConfigId);
        }
    }

    public void addMissing(String value, String configId) {
        addMissingItem(value);
        if (configId != null && !configId.isBlank()) {
            missingConfigIds.add(configId);
        }
    }

    public boolean isComplete() {
        return missingItems.isEmpty();
    }

    public List<String> getMissingItems() {
        return missingItems;
    }

    public List<String> getMissingConfigIds() {
        return List.copyOf(missingConfigIds);
    }

    private void addMissingItem(String value) {
        missingItems.add(value);
    }

    private static String inferConfigId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("capture.captureSpawnGroups.")) {
            String code = trimmed.substring("capture.captureSpawnGroups.".length()).trim();
            return code.isEmpty() ? null : "captureInnerSpawnGroup_" + code.toUpperCase(Locale.ROOT);
        }
        if (trimmed.startsWith("capture.captureOuterSpawnGroups.")) {
            String code = trimmed.substring("capture.captureOuterSpawnGroups.".length()).trim();
            return code.isEmpty() ? null : "captureOuterSpawnGroup_" + code.toUpperCase(Locale.ROOT);
        }
        return null;
    }
}
