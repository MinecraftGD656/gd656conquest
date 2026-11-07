package org.mods.gd656conquest.client.loadout.preview;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Read-only loadout properties using TACZ's own attachment evaluation and diagram semantics. */
public final class TaczWeaponStats {
    private static boolean reportedFailure;

    private TaczWeaponStats() {}

    /** Call after the configured stack changes; retain the immutable result for rendering. */
    public static Snapshot read(ItemStack configuredStack) {
        if (configuredStack == null || configuredStack.isEmpty()) return Snapshot.unavailable();
        Api api = Holder.API;
        if (api == null) return Snapshot.unavailable();
        ItemStack stack = configuredStack.copy();
        Object gun = invoke(api.getGun(), null, stack);
        if (gun == null) return Snapshot.unavailable();
        Object id = invoke(api.getGunId(), gun, stack);
        if (!(id instanceof ResourceLocation)) return Snapshot.unavailable();
        Object index = invoke(api.getIndex(), null, id);
        Object gunIndex = index instanceof Optional<?> optional ? optional.orElse(null) : null;
        Object data = gunIndex == null ? null : invoke(api.getData(), gunIndex);
        if (data == null) return Snapshot.unavailable();

        Object nativeModifiers = invoke(api.getModifiers(), null);
        Map<?, ?> modifiers = nativeModifiers instanceof Map<?, ?> map ? map : Map.of();
        Object cache = evaluate(api, stack, data);
        Double effectiveRange = number(cache(api, cache, modifiers, "effective_range"));
        List<DamagePoint> damagePoints = damagePoints(cache(api, cache, modifiers, "damage"), effectiveRange);
        Double damage = damagePoints.isEmpty() ? null : damagePoints.get(0).damage();
        Integer rpm = integer(cache(api, cache, modifiers, "rpm"));
        Boolean inventoryAmmo = bool(invoke(api.inventoryAmmo(), gun, stack));
        Integer magazine = Boolean.TRUE.equals(inventoryAmmo) ? null : integer(invoke(api.ammoCount(), null, stack, data));
        Object fireMode = invoke(api.fireMode(), gun, stack);
        List<ModifierStat> diagrams = new ArrayList<>();
        if (cache != null) {
            for (Map.Entry<?, ?> entry : modifiers.entrySet()) {
                Object result = invoke(api.diagrams(), entry.getValue(), stack, data, cache);
                if (!(result instanceof List<?> list)) continue;
                for (Object diagram : list) {
                    if (diagram != null) diagrams.add(diagram(String.valueOf(entry.getKey()), diagram));
                }
            }
        }
        return new Snapshot(true, id.toString(), damage, rpm, magazine, inventoryAmmo,
            fireMode == null ? null : fireMode.toString(), number(cache(api, cache, modifiers, "ammo_speed")),
            reloadSeconds(data, false), reloadSeconds(data, true), number(cache(api, cache, modifiers, "ads")),
            number(cache(api, cache, modifiers, "head_shot")), unlimited(effectiveRange), damagePoints, diagrams);
    }

    /** Values follow the TACZ attachment panel; null means the source did not supply that value. */
    public record Snapshot(boolean available, String gunId, Double damage, Integer rpm,
                           Integer magazine, Boolean inventoryAmmo, String fireMode,
                           Double muzzleVelocity, Double reloadTacticalSeconds, Double reloadEmptySeconds,
                           Double adsSeconds, Double headshotMultiplier, Double effectiveRange,
                           List<DamagePoint> damageByDistance, List<ModifierStat> modifiers) {
        public Snapshot {
            damageByDistance = List.copyOf(damageByDistance);
            modifiers = List.copyOf(modifiers);
        }

        public static Snapshot unavailable() {
            return new Snapshot(false, null, null, null, null, null, null, null, null, null,
                null, null, null, List.of(), List.of());
        }
    }

    /** Exclusive distance boundary in metres; positive infinity is TACZ's unbounded last segment. */
    public record DamagePoint(double distance, double damage) {}

    /** Raw native percentages/delta and formatted texts, without section codes; see effective-range note in r9-stats.md. */
    public record ModifierStat(String id, String titleKey, Double basePercent, Double modifierPercent,
                               Double modifier, Boolean positivelyBetter, String valueText, String modifiedText) {}

    private static Object evaluate(Api api, ItemStack stack, Object data) {
        try {
            Object cache = api.cacheConstructor().newInstance();
            api.evaluate().invoke(cache, stack, data);
            return cache;
        } catch (ReflectiveOperationException | LinkageError failure) {
            report(failure);
            return null;
        }
    }

    private static Object cache(Api api, Object cache, Map<?, ?> modifiers, String id) {
        return cache == null || !modifiers.containsKey(id) ? null : invoke(api.cachedValue(), cache, id);
    }

    static List<DamagePoint> damagePoints(Object nativePairs, Double effectiveRange) {
        if (!(nativePairs instanceof List<?> pairs) || pairs.isEmpty()) return List.of();
        Double firstDistance = number(get(pairs.get(0), "getDistance"));
        List<DamagePoint> points = new ArrayList<>();
        for (Object pair : pairs) {
            Double distance = number(get(pair, "getDistance"));
            Double damage = number(get(pair, "getDamage"));
            if (distance == null || damage == null) continue;
            // EntityKineticBullet replaces the first distance threshold with the evaluated range.
            if (distance.equals(firstDistance) && effectiveRange != null) distance = effectiveRange;
            points.add(new DamagePoint(unlimited(distance), damage));
        }
        return List.copyOf(points);
    }

    static ModifierStat diagram(String id, Object nativeDiagram) {
        Double modifier = number(get(nativeDiagram, "modifier"));
        String valueText = text(get(nativeDiagram, "defaultString"));
        String modifiedText = modifier == null || modifier == 0.0 ? valueText
            : text(get(nativeDiagram, modifier > 0.0 ? "positivelyString" : "negativeString"));
        return new ModifierStat(id, text(get(nativeDiagram, "titleKey")),
            number(get(nativeDiagram, "defaultPercent")), number(get(nativeDiagram, "modifierPercent")),
            modifier, bool(get(nativeDiagram, "positivelyBetter")), valueText, modifiedText);
    }

    static Double reloadSeconds(Object gunData, boolean empty) {
        // Scripted/manual reloads can select timings from attachment levels and runtime state.
        if (get(gunData, "getScript") != null) return null;
        Object reload = get(gunData, "getReloadData");
        Object type = get(reload, "getType");
        if (type == null || !"MAGAZINE".equals(type.toString())) return null;
        Object cooldown = get(reload, "getCooldown");
        return number(get(cooldown, empty ? "getEmptyTime" : "getTacticalTime"));
    }

    private static Object get(Object target, String method) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | LinkageError failure) {
            report(failure);
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | LinkageError failure) {
            report(failure);
            return null;
        }
    }

    private static Double number(Object value) {
        if (!(value instanceof Number number)) return null;
        double result = number.doubleValue();
        return Double.isFinite(result) ? result : null;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean flag ? flag : null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString().replaceAll("§.", "");
    }

    private static Double unlimited(Double distance) {
        return distance != null && distance >= (double)(float)Integer.MAX_VALUE ? Double.POSITIVE_INFINITY : distance;
    }

    private static void report(Throwable failure) {
        if (!reportedFailure) {
            reportedFailure = true;
            LogUtils.getLogger().warn("TACZ loadout properties are partially unavailable", failure);
        }
    }

    private static final class Holder {
        private static final Api API = resolve();
    }

    private static Api resolve() {
        try {
            Class<?> gun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> timeless = Class.forName("com.tacz.guns.api.TimelessAPI");
            Class<?> index = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            Class<?> data = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            Class<?> cache = Class.forName("com.tacz.guns.resource.modifier.AttachmentCacheProperty");
            Class<?> manager = Class.forName("com.tacz.guns.resource.modifier.AttachmentPropertyManager");
            Class<?> modifier = Class.forName("com.tacz.guns.api.modifier.IAttachmentModifier");
            Class<?> utils = Class.forName("com.tacz.guns.util.AttachmentDataUtils");
            return new Api(gun.getMethod("getIGunOrNull", ItemStack.class), gun.getMethod("getGunId", ItemStack.class),
                timeless.getMethod("getCommonGunIndex", ResourceLocation.class), index.getMethod("getGunData"),
                gun.getMethod("getFireMode", ItemStack.class), gun.getMethod("useInventoryAmmo", ItemStack.class),
                cache.getConstructor(), cache.getMethod("eval", ItemStack.class, data), cache.getMethod("getCache", String.class),
                manager.getMethod("getModifiers"), modifier.getMethod("getPropertyDiagramsData", ItemStack.class, data, cache),
                utils.getMethod("getAmmoCountWithAttachment", ItemStack.class, data));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            report(failure);
            return null;
        }
    }

    private record Api(Method getGun, Method getGunId, Method getIndex, Method getData, Method fireMode,
                       Method inventoryAmmo, Constructor<?> cacheConstructor, Method evaluate, Method cachedValue,
                       Method getModifiers, Method diagrams, Method ammoCount) {}
}
