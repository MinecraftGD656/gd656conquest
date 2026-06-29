package org.mods.gd656conquest.server.loadout;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Locale;

public final class TaczAttachmentCompatService {
    private static final TaczAttachmentCompatService INSTANCE = new TaczAttachmentCompatService();

    private boolean resolved;
    private boolean available;
    private Class<?> iGunClass;
    private Class<?> iAttachmentClass;
    private Class<? extends Enum<?>> attachmentTypeClass;
    private Method getIGunOrNullMethod;
    private Method getIAttachmentOrNullMethod;
    private Method getTypeMethod;
    private Method getAttachmentIdMethod;
    private Method allowAttachmentTypeMethod;
    private Method allowAttachmentMethod;
    private Method installAttachmentMethod;
    private Method unloadAttachmentMethod;
    private Method fillItemCategoryMethod;

    private TaczAttachmentCompatService() {
    }

    public static TaczAttachmentCompatService getInstance() {
        return INSTANCE;
    }

    public ItemStack deserializeStack(String itemSnbt) {
        if (itemSnbt == null || itemSnbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            CompoundTag tag = TagParser.parseTag(itemSnbt);
            return ItemStack.of(tag);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    public boolean isAvailable() {
        ensureResolved();
        return available;
    }

    public boolean isTaczGun(ItemStack stack) {
        ensureResolved();
        if (!available || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return getIGunOrNullMethod.invoke(null, stack) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isOfficialAttachment(ItemStack stack) {
        ensureResolved();
        if (!available || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return getIAttachmentOrNullMethod.invoke(null, stack) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isOfficialAttachmentSnbt(String itemSnbt) {
        return isOfficialAttachment(deserializeStack(itemSnbt));
    }

    public String resolveAttachmentType(ItemStack stack) {
        ensureResolved();
        if (!available || stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            Object iAttachment = getIAttachmentOrNullMethod.invoke(null, stack);
            if (iAttachment == null) {
                return "";
            }
            Object type = getTypeMethod.invoke(iAttachment, stack);
            return type instanceof Enum<?> enumValue ? enumValue.name().toLowerCase(Locale.ROOT) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    public ResourceLocation resolveAttachmentId(ItemStack stack) {
        ensureResolved();
        if (!available || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            Object iAttachment = getIAttachmentOrNullMethod.invoke(null, stack);
            if (iAttachment == null) {
                return null;
            }
            Object attachmentId = getAttachmentIdMethod.invoke(iAttachment, stack);
            return attachmentId instanceof ResourceLocation resourceLocation ? resourceLocation : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<ItemStack> listOfficialAttachmentsByType(String attachmentType) {
        ensureResolved();
        Enum<?> typeEnum = resolveTypeEnum(attachmentType);
        if (!available || fillItemCategoryMethod == null || typeEnum == null) {
            return List.of();
        }
        try {
            Object result = fillItemCategoryMethod.invoke(null, typeEnum);
            if (!(result instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (Object entry : iterable) {
                if (entry instanceof ItemStack stack && !stack.isEmpty() && isOfficialAttachment(stack)) {
                    stacks.add(stack.copy());
                }
            }
            return stacks;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public boolean allowAttachmentType(ItemStack gunStack, String attachmentType) {
        ensureResolved();
        if (!available || gunStack == null || gunStack.isEmpty()) {
            return false;
        }
        Object iGun = getGunOperator(gunStack);
        Enum<?> typeEnum = resolveTypeEnum(attachmentType);
        if (iGun == null || typeEnum == null) {
            return false;
        }
        try {
            Object result = allowAttachmentTypeMethod.invoke(iGun, gunStack, typeEnum);
            return result instanceof Boolean allowed && allowed;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean allowAttachment(ItemStack gunStack, ItemStack attachmentStack) {
        ensureResolved();
        if (!available || gunStack == null || gunStack.isEmpty() || attachmentStack == null || attachmentStack.isEmpty()) {
            return false;
        }
        Object iGun = getGunOperator(gunStack);
        if (iGun == null || !isOfficialAttachment(attachmentStack)) {
            return false;
        }
        try {
            Object result = allowAttachmentMethod.invoke(iGun, gunStack, attachmentStack);
            return result instanceof Boolean allowed && allowed;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean installAttachment(ItemStack gunStack, ItemStack attachmentStack) {
        ensureResolved();
        if (!allowAttachment(gunStack, attachmentStack)) {
            return false;
        }
        Object iGun = getGunOperator(gunStack);
        if (iGun == null) {
            return false;
        }
        try {
            installAttachmentMethod.invoke(iGun, gunStack, attachmentStack.copyWithCount(1));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean unloadAttachment(ItemStack gunStack, String attachmentType) {
        ensureResolved();
        if (!available || gunStack == null || gunStack.isEmpty()) {
            return false;
        }
        Object iGun = getGunOperator(gunStack);
        Enum<?> typeEnum = resolveTypeEnum(attachmentType);
        if (iGun == null || typeEnum == null) {
            return false;
        }
        try {
            unloadAttachmentMethod.invoke(iGun, gunStack, typeEnum);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object getGunOperator(ItemStack gunStack) {
        if (!available || gunStack == null || gunStack.isEmpty()) {
            return null;
        }
        try {
            return getIGunOrNullMethod.invoke(null, gunStack);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Enum<?> resolveTypeEnum(String attachmentType) {
        if (!available || attachmentTypeClass == null || attachmentType == null || attachmentType.isBlank()) {
            return null;
        }
        try {
            String normalizedType = attachmentType.trim().toUpperCase(Locale.ROOT);
            Object[] enumConstants = attachmentTypeClass.getEnumConstants();
            if (enumConstants == null) {
                return null;
            }
            for (Object enumConstant : enumConstants) {
                if (enumConstant instanceof Enum<?> enumValue && enumValue.name().equalsIgnoreCase(normalizedType)) {
                    return enumValue;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureResolved() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            iAttachmentClass = Class.forName("com.tacz.guns.api.item.IAttachment");
            Class<?> attachmentItemClass = Class.forName("com.tacz.guns.item.AttachmentItem");
            attachmentTypeClass = (Class<? extends Enum<?>>) Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            getIGunOrNullMethod = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            getIAttachmentOrNullMethod = iAttachmentClass.getMethod("getIAttachmentOrNull", ItemStack.class);
            getTypeMethod = iAttachmentClass.getMethod("getType", ItemStack.class);
            getAttachmentIdMethod = iAttachmentClass.getMethod("getAttachmentId", ItemStack.class);
            allowAttachmentTypeMethod = iGunClass.getMethod("allowAttachmentType", ItemStack.class, attachmentTypeClass);
            allowAttachmentMethod = iGunClass.getMethod("allowAttachment", ItemStack.class, ItemStack.class);
            installAttachmentMethod = iGunClass.getMethod("installAttachment", ItemStack.class, ItemStack.class);
            unloadAttachmentMethod = iGunClass.getMethod("unloadAttachment", ItemStack.class, attachmentTypeClass);
            fillItemCategoryMethod = attachmentItemClass.getMethod("fillItemCategory", attachmentTypeClass);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }
}
