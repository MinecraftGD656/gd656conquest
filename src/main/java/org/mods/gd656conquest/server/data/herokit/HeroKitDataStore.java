package org.mods.gd656conquest.server.data.herokit;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class HeroKitDataStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private HeroKitDataModel cache;

    public HeroKitDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        cache = read(ServerDataPaths.getHeroKitDataFile(server), HeroKitDataModel.class, HeroKitDataModel::new);
        normalize(cache);
        save(cache);
    }

    public synchronized HeroKitDataModel get() {
        if (cache == null) {
            initialize();
        }
        return cache;
    }

    public synchronized void save(HeroKitDataModel model) {
        normalize(model);
        cache = model;
        delayedJsonWriteService.schedule(ServerDataPaths.getHeroKitDataFile(server), model, server.getTickCount());
    }

    public synchronized HeroKitDataModel reloadFromDisk() {
        cache = read(ServerDataPaths.getHeroKitDataFile(server), HeroKitDataModel.class, HeroKitDataModel::new);
        normalize(cache);
        return cache;
    }

    private void normalize(HeroKitDataModel model) {
        if (model == null) {
            return;
        }
        model.setVersion(model.getVersion());
        Map<String, HeroKitDataModel.HeroKitEntry> source = model.getHeroKitsById();
        Map<String, HeroKitDataModel.HeroKitEntry> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, HeroKitDataModel.HeroKitEntry> entry : source.entrySet()) {
            String rawId = entry.getKey();
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String normalizedId = rawId.trim();
            HeroKitDataModel.HeroKitEntry resolved = entry.getValue();
            if (resolved == null) {
                resolved = new HeroKitDataModel.HeroKitEntry();
            }
            resolved.setId(resolved.getId().isBlank() ? normalizedId : resolved.getId());
            resolved.setDisplayName(resolved.getDisplayName());
            resolved.setCost(resolved.getCost());
            resolved.setType(resolved.getType());
            resolved.setHotbarBySlot(normalizeSlotMap(resolved.getHotbarBySlot()));
            resolved.setInventoryBySlot(normalizeSlotMap(resolved.getInventoryBySlot()));
            resolved.setOffhandItemSnbt(resolved.getOffhandItemSnbt());
            HeroKitDataModel.ArmorLoadout armor = resolved.getArmor();
            armor.setHeadItemSnbt(armor.getHeadItemSnbt());
            armor.setChestItemSnbt(armor.getChestItemSnbt());
            armor.setLegsItemSnbt(armor.getLegsItemSnbt());
            armor.setFeetItemSnbt(armor.getFeetItemSnbt());
            resolved.setArmor(armor);
            normalized.put(normalizedId, resolved);
        }
        model.setHeroKitsById(normalized);
    }

    private Map<String, String> normalizeSlotMap(Map<String, String> rawMap) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (rawMap == null || rawMap.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : rawMap.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = entry.getValue();
            normalized.put(key.trim(), value == null ? "" : value.trim());
        }
        return normalized;
    }
}
