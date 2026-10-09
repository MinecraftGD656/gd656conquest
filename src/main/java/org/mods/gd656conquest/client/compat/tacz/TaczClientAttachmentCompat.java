package org.mods.gd656conquest.client.compat.tacz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
    private Method timelessGetCommonGunIndexMethod;
    private Method gunIndexGetGunDataMethod;
    private Method iGunClassGetIGunOrNullMethod;
    private Method iGunClassGetGunIdMethod;
    private Method timelessGetCommonAttachmentIndexMethod;
    private Method soundPlayManagerPlayerRefitSoundMethod;
    private String installSoundName;
    private String uninstallSoundName;

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

    public String resolveGunType(String itemSnbt) {
        return resolveGunType(parseItemStack(itemSnbt));
    }

    public String resolveGunType(ItemStack stack) {
        ensureResolved();
        if (!available || stack == null || stack.isEmpty() || iGunClassGetIGunOrNullMethod == null) {
            return "others";
        }
        try {
            Object iGun = iGunClassGetIGunOrNullMethod.invoke(null, stack);
            if (iGun == null) return "others";
            Object gunId = iGunClassGetGunIdMethod.invoke(iGun, stack);
            if (!(gunId instanceof ResourceLocation resourceLocation)) return "others";
            
            Object optionalIndex = timelessGetCommonGunIndexMethod.invoke(null, resourceLocation);
            Object gunIndex = unwrapOptional(optionalIndex);
            if (gunIndex == null) return "others";
            
            if (gunIndexGetGunDataMethod == null) {
                gunIndexGetGunDataMethod = gunIndex.getClass().getMethod("getGunData");
            }
            Object gunData = gunIndexGetGunDataMethod.invoke(gunIndex);
            
            // Assume we can get type via reflection if needed
            Method getTypeMethod = gunData.getClass().getMethod("getType");
            Object type = getTypeMethod.invoke(gunData);
            if (type == null) return "others";
            
            String typeName = type.toString().toLowerCase(Locale.ROOT);
            return typeName;
        } catch (Exception ignored) {
            return "others";
        }
    }

    private Object unwrapOptional(Object value) {
        if (!(value instanceof Optional<?> optional)) {
            return null;
        }
        return optional.orElse(null);
    }

    private ItemStack parseItemStack(String itemSnbt) {
        return org.mods.gd656conquest.client.loadout.ClientLoadoutUiState.parseAndCacheStack(itemSnbt);
    }

    public ItemStack installAttachment(ItemStack gunStack, ItemStack attachmentStack) {
        ensureResolved();
        if (!available || gunStack == null || gunStack.isEmpty() || attachmentStack == null || attachmentStack.isEmpty()) {
            return gunStack;
        }
        try {
            ItemStack modifiedGun = gunStack.copy();
            // Install attachment logic via TACZ API
            // IGun.getIGunOrNull(modifiedGun) -> getGunId
            Object iGun = iGunClassGetIGunOrNullMethod.invoke(null, modifiedGun);
            if (iGun == null) return modifiedGun;
            
            // IAttachment.getIAttachmentOrNull(attachmentStack) -> getType
            Object iAttachment = getIAttachmentOrNullMethod.invoke(null, attachmentStack);
            if (iAttachment == null) return modifiedGun;
            
            Object type = getTypeMethod.invoke(iAttachment, attachmentStack);
            if (type == null) return modifiedGun;
            
            // IGun API has an installAttachment method?
            // Actually com.tacz.guns.api.item.IGun has:
            // void installAttachment(ItemStack gunItem, ItemStack attachmentItem)
            Method installAttachmentMethod = iGun.getClass().getMethod("installAttachment", ItemStack.class, ItemStack.class);
            installAttachmentMethod.invoke(iGun, modifiedGun, attachmentStack);
            
            return modifiedGun;
        } catch (Exception ignored) {
            return gunStack;
        }
    }

    public void playAttachmentInstallSound(ItemStack attachmentStack) {
        playRefitSound(attachmentStack, installSoundName);
    }

    public void playAttachmentUninstallSound(ItemStack attachmentStack) {
        playRefitSound(attachmentStack, uninstallSoundName);
    }

    private void playRefitSound(ItemStack attachmentStack, String soundName) {
        ensureResolved();
        LocalPlayer player = Minecraft.getInstance() == null ? null : Minecraft.getInstance().player;
        if (!available || soundPlayManagerPlayerRefitSoundMethod == null || player == null || attachmentStack == null || attachmentStack.isEmpty() || soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            soundPlayManagerPlayerRefitSoundMethod.invoke(null, attachmentStack, player, soundName);
        } catch (Exception ignored) {
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
            timelessGetCommonAttachmentIndexMethod = timelessApiClass.getMethod("getCommonAttachmentIndex", ResourceLocation.class);
            Class<?> clientAttachmentIndexClass = Class.forName("com.tacz.guns.client.resource.index.ClientAttachmentIndex");
            getIAttachmentOrNullMethod = iAttachmentClass.getMethod("getIAttachmentOrNull", ItemStack.class);
            getTypeMethod = iAttachmentClass.getMethod("getType", ItemStack.class);
            getAttachmentIdMethod = iAttachmentClass.getMethod("getAttachmentId", ItemStack.class);
            timelessGetClientAttachmentIndexMethod = timelessApiClass.getMethod("getClientAttachmentIndex", ResourceLocation.class);
            clientAttachmentIndexGetZoomMethod = clientAttachmentIndexClass.getMethod("getZoom");

            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            iGunClassGetIGunOrNullMethod = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            iGunClassGetGunIdMethod = iGunClass.getMethod("getGunId", ItemStack.class);
            timelessGetCommonGunIndexMethod = timelessApiClass.getMethod("getCommonGunIndex", ResourceLocation.class);

            Class<?> soundPlayManagerClass = Class.forName("com.tacz.guns.client.sound.SoundPlayManager");
            soundPlayManagerPlayerRefitSoundMethod = soundPlayManagerClass.getMethod("playerRefitSound", ItemStack.class, LocalPlayer.class, String.class);
            Class<?> soundManagerClass = Class.forName("com.tacz.guns.sound.SoundManager");
            installSoundName = (String) soundManagerClass.getField("INSTALL_SOUND").get(null);
            uninstallSoundName = (String) soundManagerClass.getField("UNINSTALL_SOUND").get(null);
            
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }
}
