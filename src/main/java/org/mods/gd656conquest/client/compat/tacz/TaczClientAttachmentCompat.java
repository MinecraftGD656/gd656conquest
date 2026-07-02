package org.mods.gd656conquest.client.compat.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

public final class TaczClientAttachmentCompat {
    private static final TaczClientAttachmentCompat INSTANCE = new TaczClientAttachmentCompat();

    private boolean resolved;
    private boolean available;
    private Method getIAttachmentOrNullMethod;
    private Method getTypeMethod;
    private Method getAttachmentIdMethod;
    private Method timelessGetClientAttachmentIndexMethod;
    private Method clientAttachmentIndexGetZoomMethod;

    private TaczClientAttachmentCompat() {
    }

    public static TaczClientAttachmentCompat getInstance() {
        return INSTANCE;
    }

    public String resolveLocalizedAttachmentDisplayName(String itemSnbt) {
        ItemStack stack = parseItemStack(itemSnbt);
        return resolveLocalizedAttachmentDisplayName(stack);
    }

    public String resolveLocalizedAttachmentDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            String displayName = stack.getHoverName().getString();
            if (displayName == null) {
                return "";
            }
            String normalized = displayName.trim();
            if (normalized.isEmpty()) {
                return "";
            }
            String descriptionId = stack.getDescriptionId();
            return normalized.equals(descriptionId) ? "" : normalized;
        } catch (Exception ignored) {
            return "";
        }
    }

    public double resolveScopeMaxMagnification(String itemSnbt) {
        return resolveScopeMaxMagnification(parseItemStack(itemSnbt));
    }

    public double resolveScopeMaxMagnification(ItemStack stack) {
        ensureResolved();
        if (!available || stack == null || stack.isEmpty()) {
            return 0.0D;
        }
        try {
            Object iAttachment = getIAttachmentOrNullMethod.invoke(null, stack);
            if (iAttachment == null) {
                return 0.0D;
            }
            Object type = getTypeMethod.invoke(iAttachment, stack);
            String typeName = type == null ? "" : type.toString().trim().toLowerCase(Locale.ROOT);
            if (!"scope".equals(typeName)) {
                return 0.0D;
            }
            Object attachmentId = getAttachmentIdMethod.invoke(iAttachment, stack);
            if (!(attachmentId instanceof ResourceLocation resourceLocation)) {
                return 0.0D;
            }
            Object optionalIndex = timelessGetClientAttachmentIndexMethod.invoke(null, resourceLocation);
            Object clientIndex = unwrapOptional(optionalIndex);
            if (clientIndex == null) {
                return 0.0D;
            }
            Object zoomValues = clientAttachmentIndexGetZoomMethod.invoke(clientIndex);
            if (!(zoomValues instanceof float[] zooms) || zooms.length == 0) {
                return 0.0D;
            }
            double maxZoom = 0.0D;
            for (float zoom : zooms) {
                maxZoom = Math.max(maxZoom, zoom);
            }
            return maxZoom;
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    private Object unwrapOptional(Object value) {
        if (!(value instanceof Optional<?> optional)) {
            return null;
        }
        return optional.orElse(null);
    }

    private ItemStack parseItemStack(String itemSnbt) {
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

    private void ensureResolved() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> iAttachmentClass = Class.forName("com.tacz.guns.api.item.IAttachment");
            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            Class<?> clientAttachmentIndexClass = Class.forName("com.tacz.guns.client.resource.index.ClientAttachmentIndex");
            getIAttachmentOrNullMethod = iAttachmentClass.getMethod("getIAttachmentOrNull", ItemStack.class);
            getTypeMethod = iAttachmentClass.getMethod("getType", ItemStack.class);
            getAttachmentIdMethod = iAttachmentClass.getMethod("getAttachmentId", ItemStack.class);
            timelessGetClientAttachmentIndexMethod = timelessApiClass.getMethod("getClientAttachmentIndex", ResourceLocation.class);
            clientAttachmentIndexGetZoomMethod = clientAttachmentIndexClass.getMethod("getZoom");
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }
}
