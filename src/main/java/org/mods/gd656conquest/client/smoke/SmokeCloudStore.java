package org.mods.gd656conquest.client.smoke;

import org.mods.gd656conquest.common.smoke.SmokeCloudInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client copy of server-published smoke clouds, partitioned by dimension.
 */
public final class SmokeCloudStore {
    private static final Map<String, Map<UUID, SmokeCloudInstance>> CLOUDS_BY_DIMENSION = new LinkedHashMap<>();

    private SmokeCloudStore() {
    }

    public static void put(SmokeCloudInstance instance) {
        if (instance == null || instance.dimensionId() == null || instance.dimensionId().isBlank()) {
            return;
        }
        CLOUDS_BY_DIMENSION
            .computeIfAbsent(instance.dimensionId(), ignored -> new LinkedHashMap<>())
            .put(instance.id(), instance);
    }

    public static void remove(UUID id, String dimensionId) {
        Map<UUID, SmokeCloudInstance> clouds = CLOUDS_BY_DIMENSION.get(dimensionId);
        if (clouds == null) {
            return;
        }
        clouds.remove(id);
        if (clouds.isEmpty()) {
            CLOUDS_BY_DIMENSION.remove(dimensionId);
        }
    }

    public static List<SmokeCloudInstance> get(String dimensionId) {
        Map<UUID, SmokeCloudInstance> clouds = CLOUDS_BY_DIMENSION.get(dimensionId);
        return clouds == null || clouds.isEmpty() ? List.of() : new ArrayList<>(clouds.values());
    }

    public static void reset() {
        CLOUDS_BY_DIMENSION.clear();
    }
}
