package org.mods.gd656conquest.server.loadout;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerItemNameUploadService {
    private static final Map<UUID, UploadedMainHandName> MAIN_HAND_NAMES = new ConcurrentHashMap<>();

    private PlayerItemNameUploadService() {
    }

    public static void updateUploadedMainHandName(ServerPlayer player, String itemId, String displayName) {
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        ResourceLocation heldKey = held == null || held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem());
        String heldId = heldKey == null ? "" : heldKey.toString();
        String normalizedItemId = normalize(itemId);
        if (!heldId.equalsIgnoreCase(normalizedItemId)) {
            return;
        }
        MAIN_HAND_NAMES.put(player.getUUID(), new UploadedMainHandName(heldId, normalize(displayName)));
    }

    public static String resolveUploadedMainHandName(ServerPlayer player, ResourceLocation expectedItemKey) {
        if (player == null || expectedItemKey == null) {
            return "";
        }
        UploadedMainHandName uploaded = MAIN_HAND_NAMES.get(player.getUUID());
        if (uploaded == null) {
            return "";
        }
        String expectedId = expectedItemKey.toString();
        if (!expectedId.equalsIgnoreCase(uploaded.itemId())) {
            return "";
        }
        return uploaded.displayName();
    }

    public static void clearPlayerCache(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MAIN_HAND_NAMES.remove(player.getUUID());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record UploadedMainHandName(String itemId, String displayName) {
    }
}
