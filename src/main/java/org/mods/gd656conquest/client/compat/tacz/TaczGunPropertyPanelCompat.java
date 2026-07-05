package org.mods.gd656conquest.client.compat.tacz;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TaczGunPropertyPanelCompat {
    private static final TaczGunPropertyPanelCompat INSTANCE = new TaczGunPropertyPanelCompat();
    private static final int PANEL_WIDTH = 288;
    private static final int FALLBACK_PANEL_HEIGHT = 198;

    private boolean resolved;
    private boolean available;

    private Method getIGunOrNullMethod;
    private Method getGunIdMethod;
    private Method getFireModeMethod;
    private Method hasBulletInBarrelMethod;
    private Method useInventoryAmmoMethod;
    private Method timelessGetCommonGunIndexMethod;
    private Method getGunDataMethod;
    private Method getHidePropertyButtonYOffsetMethod;
    private Method getModifiersMethod;
    private Method getAmmoCountWithAttachmentMethod;
    private Constructor<?> attachmentCachePropertyConstructor;
    private Method attachmentCachePropertyEvalMethod;
    private Class<?> gunDataClass;

    private TaczGunPropertyPanelCompat() {
    }

    public static TaczGunPropertyPanelCompat getInstance() {
        return INSTANCE;
    }

    public int getPanelWidth() {
        return PANEL_WIDTH;
    }

    public int getPanelHeight() {
        ensureResolved();
        if (!available || getHidePropertyButtonYOffsetMethod == null) {
            return FALLBACK_PANEL_HEIGHT;
        }
        try {
            Object value = getHidePropertyButtonYOffsetMethod.invoke(null);
            if (value instanceof Number number) {
                return Math.max(80, number.intValue() - 11);
            }
        } catch (Exception ignored) {
        }
        return FALLBACK_PANEL_HEIGHT;
    }

    public boolean canRender(ItemStack gunStack) {
        return resolveContext(gunStack) != null;
    }

    public void render(GuiGraphics guiGraphics, Font font, int x, int y, ItemStack gunStack) {
        GunContext context = resolveContext(gunStack);
        if (context == null) {
            return;
        }
        Object cacheProperty = createCacheProperty(gunStack, context.gunData());
        if (cacheProperty == null) {
            return;
        }

        int panelHeight = getPanelHeight();
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, 0xAF222222);

        int barStartX = x + 83;
        int barMaxWidth = 120;
        int barEndX = barStartX + barMaxWidth;

        int barBackgroundColor = 0xFF000000;
        int barBaseColor = 0xFFFFFFFF;
        int barPositiveColor = 0xFF55FF55;
        int barNegativeColor = 0xFFFF5555;
        int fontColor = 0x00CCCCCC;
        int nameTextStartX = x + 5;
        int valueTextStartX = x + 210;

        int currentY = y + 5;

        Component fireModeText = Component.translatable("gui.tacz.gun_refit.property_diagrams.fire_mode")
            .append(resolveFireModeSuffix(context.iGun(), gunStack));
        guiGraphics.drawString(font, fireModeText, nameTextStartX + 12, currentY, fontColor, false);
        currentY += 10;

        if (invokeBoolean(context.iGun(), useInventoryAmmoMethod, gunStack)) {
            guiGraphics.drawString(font, Component.translatable("gui.tacz.gun_refit.property_diagrams.ammo_capacity"), nameTextStartX, currentY, fontColor, false);
            guiGraphics.fill(barStartX, currentY + 2, barEndX, currentY + 6, barBackgroundColor);
            guiGraphics.fill(barStartX, currentY + 2, barStartX + barMaxWidth, currentY + 6, barBaseColor);
            guiGraphics.drawString(font, Component.literal("INV"), valueTextStartX, currentY, fontColor, false);
        } else {
            int barrelBulletAmount = isOpenBolt(context.gunData()) || !invokeBoolean(context.iGun(), hasBulletInBarrelMethod, gunStack) ? 0 : 1;
            int ammoAmount = invokeInt(context.gunData(), "getAmmoAmount", 0) + barrelBulletAmount;
            double ammoAmountPercent = Math.min(ammoAmount / 100.0D, 1.0D);
            int ammoLength = (int) (barStartX + barMaxWidth * ammoAmountPercent);
            int maxAmmoCount = resolveAmmoCountWithAttachment(gunStack, context.gunData()) + barrelBulletAmount;
            int addAmmoCount = Math.max(maxAmmoCount - ammoAmount, 0);
            int addAmmoCountLength = (int) (barMaxWidth * addAmmoCount / 100.0D);

            guiGraphics.drawString(font, Component.translatable("gui.tacz.gun_refit.property_diagrams.ammo_capacity"), nameTextStartX, currentY, fontColor, false);
            guiGraphics.fill(barStartX, currentY + 2, barEndX, currentY + 6, barBackgroundColor);
            guiGraphics.fill(barStartX, currentY + 2, ammoLength, currentY + 6, barBaseColor);
            if (addAmmoCount > 0) {
                int barRight = Math.min(ammoLength + addAmmoCountLength, barEndX);
                guiGraphics.fill(ammoLength, currentY + 2, barRight, currentY + 6, barPositiveColor);
                guiGraphics.drawString(font, Component.literal(ammoAmount + " (+" + addAmmoCount + ")"), valueTextStartX, currentY, fontColor, false);
            } else {
                guiGraphics.drawString(font, Component.literal(Integer.toString(ammoAmount)), valueTextStartX, currentY, fontColor, false);
            }
        }
        currentY += 10;

        float sprintTime = invokeFloat(context.gunData(), "getSprintTime", 0.0F);
        double sprintTimePercent = Mth.clamp(sprintTime / 0.5D, 0.0D, 1.0D);
        int sprintLength = (int) (barStartX + barMaxWidth * sprintTimePercent);
        guiGraphics.drawString(font, Component.translatable("gui.tacz.gun_refit.property_diagrams.sprint_time"), nameTextStartX, currentY, fontColor, false);
        guiGraphics.fill(barStartX, currentY + 2, barEndX, currentY + 6, barBackgroundColor);
        guiGraphics.fill(barStartX, currentY + 2, sprintLength, currentY + 6, barBaseColor);
        guiGraphics.drawString(font, Component.literal(String.format("%.2fs", sprintTime)), valueTextStartX, currentY, fontColor, false);
        currentY += 10;

        for (Object modifier : getRegisteredModifiers().values()) {
            List<?> diagrams = invokeDiagrams(modifier, gunStack, context.gunData(), cacheProperty);
            for (Object diagram : diagrams) {
                double defaultPercent = Mth.clamp(invokeDouble(diagram, "defaultPercent", 0.0D), 0.0D, 1.0D);
                double modifierPercent = invokeDouble(diagram, "modifierPercent", 0.0D);
                double modifierValue = invokeNumber(diagram, "modifier", 0.0D).doubleValue();
                String titleKey = invokeString(diagram, "titleKey", "");
                String positiveString = sanitizeValueText(invokeString(diagram, "positivelyString", ""));
                String negativeString = sanitizeValueText(invokeString(diagram, "negativeString", ""));
                String defaultString = sanitizeValueText(invokeString(diagram, "defaultString", ""));
                boolean positivelyBetter = invokeBoolean(diagram, "positivelyBetter", true);

                int defaultLength = (int) (barStartX + barMaxWidth * defaultPercent);
                int modifierLength = Mth.clamp(defaultLength + (int) (barMaxWidth * modifierPercent), barStartX, barEndX);

                guiGraphics.drawString(font, Component.translatable(titleKey), nameTextStartX, currentY, fontColor, false);
                guiGraphics.fill(barStartX, currentY + 2, barEndX, currentY + 6, barBackgroundColor);
                guiGraphics.fill(barStartX, currentY + 2, defaultLength, currentY + 6, barBaseColor);
                if (modifierValue > 0.0D) {
                    int barColor = positivelyBetter ? barPositiveColor : barNegativeColor;
                    guiGraphics.fill(defaultLength, currentY + 2, modifierLength, currentY + 6, barColor);
                    guiGraphics.drawString(font, Component.literal(positiveString), valueTextStartX, currentY, fontColor, false);
                } else if (modifierValue < 0.0D) {
                    int barColor = positivelyBetter ? barNegativeColor : barPositiveColor;
                    guiGraphics.fill(modifierLength, currentY + 2, defaultLength, currentY + 6, barColor);
                    guiGraphics.drawString(font, Component.literal(negativeString), valueTextStartX, currentY, fontColor, false);
                } else {
                    guiGraphics.drawString(font, Component.literal(defaultString), valueTextStartX, currentY, fontColor, false);
                }
                currentY += 10;
            }
        }
    }

    private GunContext resolveContext(ItemStack gunStack) {
        ensureResolved();
        if (!available || gunStack == null || gunStack.isEmpty()) {
            return null;
        }
        try {
            Object iGun = getIGunOrNullMethod.invoke(null, gunStack);
            if (iGun == null) {
                return null;
            }
            Object gunIdValue = getGunIdMethod.invoke(iGun, gunStack);
            if (!(gunIdValue instanceof ResourceLocation gunId)) {
                return null;
            }
            Object optionalIndex = timelessGetCommonGunIndexMethod.invoke(null, gunId);
            Object gunIndex = unwrapOptional(optionalIndex);
            if (gunIndex == null) {
                return null;
            }
            Object gunData = getGunDataMethod.invoke(gunIndex);
            if (gunData == null) {
                return null;
            }
            return new GunContext(iGun, gunData);
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
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            getIGunOrNullMethod = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            getGunIdMethod = iGunClass.getMethod("getGunId", ItemStack.class);
            getFireModeMethod = iGunClass.getMethod("getFireMode", ItemStack.class);
            hasBulletInBarrelMethod = iGunClass.getMethod("hasBulletInBarrel", ItemStack.class);
            useInventoryAmmoMethod = iGunClass.getMethod("useInventoryAmmo", ItemStack.class);

            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            timelessGetCommonGunIndexMethod = timelessApiClass.getMethod("getCommonGunIndex", ResourceLocation.class);

            Class<?> commonGunIndexClass = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            getGunDataMethod = commonGunIndexClass.getMethod("getGunData");

            gunDataClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            Class<?> gunPropertyDiagramsClass = Class.forName("com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams");
            getHidePropertyButtonYOffsetMethod = gunPropertyDiagramsClass.getMethod("getHidePropertyButtonYOffset");

            Class<?> attachmentPropertyManagerClass = Class.forName("com.tacz.guns.resource.modifier.AttachmentPropertyManager");
            getModifiersMethod = attachmentPropertyManagerClass.getMethod("getModifiers");

            Class<?> attachmentDataUtilsClass = Class.forName("com.tacz.guns.util.AttachmentDataUtils");
            getAmmoCountWithAttachmentMethod = attachmentDataUtilsClass.getMethod("getAmmoCountWithAttachment", ItemStack.class, gunDataClass);

            Class<?> attachmentCachePropertyClass = Class.forName("com.tacz.guns.resource.modifier.AttachmentCacheProperty");
            attachmentCachePropertyConstructor = attachmentCachePropertyClass.getDeclaredConstructor();
            attachmentCachePropertyEvalMethod = attachmentCachePropertyClass.getMethod("eval", ItemStack.class, gunDataClass);

            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    private Object createCacheProperty(ItemStack gunStack, Object gunData) {
        if (attachmentCachePropertyConstructor == null || attachmentCachePropertyEvalMethod == null || gunData == null) {
            return null;
        }
        try {
            Object cacheProperty = attachmentCachePropertyConstructor.newInstance();
            attachmentCachePropertyEvalMethod.invoke(cacheProperty, gunStack, gunData);
            return cacheProperty;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> getRegisteredModifiers() {
        if (getModifiersMethod == null) {
            return Collections.emptyMap();
        }
        try {
            Object value = getModifiersMethod.invoke(null);
            if (value instanceof Map<?, ?> modifiers) {
                return (Map<Object, Object>) modifiers;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    private List<?> invokeDiagrams(Object modifier, ItemStack gunStack, Object gunData, Object cacheProperty) {
        if (modifier == null || gunData == null || cacheProperty == null) {
            return Collections.emptyList();
        }
        try {
            Method method = modifier.getClass().getMethod("getPropertyDiagramsData", ItemStack.class, gunDataClass, cacheProperty.getClass());
            Object value = method.invoke(modifier, gunStack, gunData, cacheProperty);
            if (value instanceof List<?> list) {
                return list;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private Component resolveFireModeSuffix(Object iGun, ItemStack gunStack) {
        if (iGun == null || getFireModeMethod == null) {
            return Component.translatable("gui.tacz.gun_refit.property_diagrams.unknown");
        }
        try {
            Object fireMode = getFireModeMethod.invoke(iGun, gunStack);
            String modeName = fireMode == null ? "" : fireMode.toString();
            return switch (modeName) {
                case "AUTO" -> Component.translatable("gui.tacz.gun_refit.property_diagrams.auto");
                case "SEMI" -> Component.translatable("gui.tacz.gun_refit.property_diagrams.semi");
                case "BURST" -> Component.translatable("gui.tacz.gun_refit.property_diagrams.burst");
                default -> Component.translatable("gui.tacz.gun_refit.property_diagrams.unknown");
            };
        } catch (Exception ignored) {
            return Component.translatable("gui.tacz.gun_refit.property_diagrams.unknown");
        }
    }

    private int resolveAmmoCountWithAttachment(ItemStack gunStack, Object gunData) {
        if (getAmmoCountWithAttachmentMethod == null) {
            return invokeInt(gunData, "getAmmoAmount", 0);
        }
        try {
            Object value = getAmmoCountWithAttachmentMethod.invoke(null, gunStack, gunData);
            if (value instanceof Number number) {
                return number.intValue();
            }
        } catch (Exception ignored) {
        }
        return invokeInt(gunData, "getAmmoAmount", 0);
    }

    private boolean isOpenBolt(Object gunData) {
        try {
            Method method = gunData.getClass().getMethod("getBolt");
            Object bolt = method.invoke(gunData);
            return bolt != null && "OPEN_BOLT".equals(String.valueOf(bolt));
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return null;
    }

    private boolean invokeBoolean(Object target, Method method, Object... args) {
        if (target == null || method == null) {
            return false;
        }
        try {
            Object value = method.invoke(target, args);
            return value instanceof Boolean bool && bool;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean invokeBoolean(Object target, String methodName, boolean fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int invokeInt(Object target, String methodName, int fallback) {
        Number value = invokeNumber(target, methodName, fallback);
        return value.intValue();
    }

    private float invokeFloat(Object target, String methodName, float fallback) {
        Number value = invokeNumber(target, methodName, fallback);
        return value.floatValue();
    }

    private double invokeDouble(Object target, String methodName, double fallback) {
        Number value = invokeNumber(target, methodName, fallback);
        return value.doubleValue();
    }

    private Number invokeNumber(Object target, String methodName, Number fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number number ? number : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String invokeString(Object target, String methodName, String fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? fallback : String.valueOf(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String sanitizeValueText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\u00A7.", "");
    }

    private record GunContext(Object iGun, Object gunData) {
    }
}
