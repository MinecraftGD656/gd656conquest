package org.mods.gd656conquest.server.room.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record RoomConfigDescriptor(
    String configId,
    RoomConfigScope scope,
    RoomConfigCategory category,
    String displayName,
    String newPath,
    RoomConfigValueType valueType,
    List<String> suggestionValues,
    List<String> legacyAliases,
    RoomConfigGroupOperationMode groupOperationMode,
    String specialHandlerId,
    boolean enabled
) {
    public RoomConfigDescriptor {
        configId = requireNonBlank(configId, "configId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(category, "category");
        displayName = requireNonBlank(displayName, "displayName");
        newPath = requireNonBlank(newPath, "newPath");
        Objects.requireNonNull(valueType, "valueType");
        suggestionValues = List.copyOf(normalizeValues(suggestionValues, true));
        legacyAliases = List.copyOf(normalizeValues(legacyAliases, false));
        Objects.requireNonNull(groupOperationMode, "groupOperationMode");
        specialHandlerId = normalizeNullable(specialHandlerId);
    }

    public boolean supportsQuotedValue() {
        return valueType == RoomConfigValueType.STRING;
    }

    public boolean matches(String idOrAlias) {
        String normalized = normalizeKey(idOrAlias);
        if (normalized == null) {
            return false;
        }
        if (normalizeKey(configId).equals(normalized)) {
            return true;
        }
        for (String alias : legacyAliases) {
            if (normalizeKey(alias).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean supportsSuggestions() {
        return !suggestionValues.isEmpty();
    }

    public boolean supportsGroupOperations() {
        return groupOperationMode.supportsList();
    }

    public String preferredLegacyId() {
        if (legacyAliases.isEmpty()) {
            return configId;
        }
        String firstAlias = legacyAliases.get(0);
        if (normalizeKey(firstAlias).equals(normalizeKey(configId))) {
            return configId;
        }
        return firstAlias;
    }

    public boolean isSpecialCase() {
        return specialHandlerId != null;
    }

    static String normalizeKey(String raw) {
        String normalized = normalizeNullable(raw);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String requireNonBlank(String value, String name) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> normalizeValues(List<String> values, boolean keepCase) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String trimmed = normalizeNullable(value);
            if (trimmed == null) {
                continue;
            }
            String key = keepCase ? trimmed.toLowerCase(Locale.ROOT) : trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }
}
