package org.mods.gd656conquest.server.room.config;

import org.mods.gd656conquest.server.data.roomdefinition.RoomConfigValidationResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class RoomValidationMessageFormatter {
    private RoomValidationMessageFormatter() {
    }

    public static String buildMissingConfigMessage(String prefix, RoomConfigValidationResult validation) {
        if (validation == null) {
            return prefix;
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>(labels(validation));
        if (labels.isEmpty()) {
            return prefix;
        }
        return prefix + "，缺失项: " + String.join(", ", labels);
    }

    public static List<String> labels(RoomConfigValidationResult validation) {
        if (validation == null) {
            return List.of();
        }
        return labels(validation.getMissingConfigIds(), validation.getMissingItems());
    }

    public static List<String> labels(List<String> missingConfigIds, List<String> missingItems) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (missingConfigIds != null) {
            for (String configId : missingConfigIds) {
                String label = labelForConfigId(configId);
                if (!label.isBlank()) {
                    labels.add(label);
                }
            }
        }
        if (missingItems != null) {
            for (String item : missingItems) {
                String label = labelForMissingItem(item);
                if (!label.isBlank()) {
                    labels.add(label);
                }
            }
        }
        return List.copyOf(labels);
    }

    public static String labelForConfigId(String configId) {
        if (configId == null || configId.isBlank()) {
            return "";
        }
        return RoomConfigDescriptorRegistry.find(configId.trim())
            .map(RoomConfigDescriptor::displayName)
            .orElse(configId.trim());
    }

    public static String labelForMissingItem(String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        String trimmed = item.trim();
        int firstDot = trimmed.indexOf('.');
        if (firstDot < 0) {
            return trimmed;
        }
        int secondDot = trimmed.indexOf('.', firstDot + 1);
        String baseKey = secondDot < 0 ? trimmed.substring(firstDot + 1) : trimmed.substring(firstDot + 1, secondDot);
        String suffix = secondDot < 0 ? "" : trimmed.substring(secondDot);
        return RoomConfigDescriptorRegistry.find(baseKey)
                .map(descriptor -> descriptor.displayName() + suffix)
                .orElse(trimmed);
    }
}
