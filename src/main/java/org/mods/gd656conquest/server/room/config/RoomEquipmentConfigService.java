package org.mods.gd656conquest.server.room.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class RoomEquipmentConfigService {
    public static final int EQUIPMENT_SLOT_COUNT = 4;
    private static final EquipmentSlot[] EQUIPMENT_SLOT_ORDER = new EquipmentSlot[] {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    private RoomEquipmentConfigService() {
    }

    public static List<String> captureCurrentEquipmentSlots(ServerPlayer player) {
        List<String> slots = new ArrayList<>(EQUIPMENT_SLOT_COUNT);
        if (player == null) {
            return slots;
        }
        for (EquipmentSlot slot : EQUIPMENT_SLOT_ORDER) {
            slots.add(serializeToSnbt(player.getItemBySlot(slot)));
        }
        return normalizeEquipmentSlots(slots);
    }

    public static List<String> normalizeEquipmentSlots(List<String> slots) {
        List<String> normalized = new ArrayList<>(EQUIPMENT_SLOT_COUNT);
        if (slots != null) {
            for (String slot : slots) {
                if (normalized.size() >= EQUIPMENT_SLOT_COUNT) {
                    break;
                }
                normalized.add(slot == null ? "" : slot.trim());
            }
        }
        while (normalized.size() < EQUIPMENT_SLOT_COUNT) {
            normalized.add("");
        }
        for (String value : normalized) {
            if (value != null && !value.isBlank()) {
                return List.copyOf(normalized);
            }
        }
        return List.of();
    }

    private static String serializeToSnbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        return tag.toString();
    }
}
