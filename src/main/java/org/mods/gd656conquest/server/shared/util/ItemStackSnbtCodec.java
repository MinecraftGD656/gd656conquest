package org.mods.gd656conquest.server.shared.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

public final class ItemStackSnbtCodec {
    private ItemStackSnbtCodec() {
    }

    public static String serialize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        return tag.toString();
    }

    public static ItemStack deserialize(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
