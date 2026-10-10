package org.mods.gd656conquest.server.compat.ragdoll;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Correlates optional Ragdollified hit records with the attack that created them. */
public final class RagdollHitCacheGuard {
    private static final long MAX_AGE_MS = 10_000L;
    private static final Map<Integer, Hit> SERVER = new ConcurrentHashMap<>();
    private static final Map<Integer, Hit> CLIENT = new ConcurrentHashMap<>();

    private RagdollHitCacheGuard() {}

    private record Hit(LivingEntity victim, DamageSource source, Entity projectile,
                       Object value, int tick, long capturedAt) {
        boolean current(LivingEntity entity, Object candidate) {
            return victim == entity && value == candidate && fresh(candidate)
                && System.currentTimeMillis() - capturedAt <= MAX_AGE_MS;
        }
    }

    public static boolean fresh(Object hit) {
        if (hit == null) return false;
        try {
            long age = System.currentTimeMillis() - hit.getClass().getField("captureTimeMs").getLong(hit);
            return age >= 0 && age <= MAX_AGE_MS;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Ragdollified hit cache API changed", error);
        }
    }

    /** Keep TACZ's richer Pre record only for the same bullet, victim and damage tick. */
    public static void beforeServerHurt(LivingHurtEvent event, Map<Integer, ?> cache) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        int id = victim.getId();
        Hit hit = SERVER.get(id);
        DamageSource source = event.getSource();
        if (hit != null && hit.projectile != null && source != null
            && hit.projectile == source.getDirectEntity() && hit.tick == victim.tickCount
            && hit.current(victim, cache.get(id))) return;
        cache.remove(id);
        SERVER.remove(id);
    }

    public static void afterServerHurt(LivingHurtEvent event, Map<Integer, ?> cache) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        int id = victim.getId();
        Object value = cache.get(id);
        if (value == null) { SERVER.remove(id); return; }
        remember(SERVER, victim, event.getSource(),
            event.getSource() == null ? null : event.getSource().getDirectEntity(), value);
    }

    /** Called after the native TACZ recorder, preserving its headshot and damage fields. */
    public static void afterGunHit(Object event, Map<Integer, ?> cache, boolean client) {
        try {
            Object target = event.getClass().getMethod("getHurtEntity").invoke(event);
            Object bullet = event.getClass().getMethod("getBullet").invoke(event);
            if (!(target instanceof LivingEntity victim) || !(bullet instanceof Entity projectile)
                || victim.level().isClientSide != client) return;
            Object value = cache.get(victim.getId());
            if (fresh(value)) remember(client ? CLIENT : SERVER, victim, null, projectile, value);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Ragdollified TACZ hit event API changed", error);
        }
    }

    private static void remember(Map<Integer, Hit> records, LivingEntity victim,
                                 DamageSource source, Entity projectile, Object value) {
        long now = System.currentTimeMillis();
        records.entrySet().removeIf(entry -> now - entry.getValue().capturedAt > MAX_AGE_MS);
        records.put(victim.getId(), new Hit(victim, source, projectile, value, victim.tickCount, now));
    }

    /** The native consume has already removed the value. An unrelated death cannot reuse it. */
    public static Object forServerDeath(LivingEntity victim, DamageSource source, Object value) {
        Hit hit = SERVER.remove(victim.getId());
        if (hit == null || source == null || !hit.current(victim, value)
            || hit.tick != victim.tickCount) return null;
        if (hit.source != null) return hit.source == source ? value : null;
        return hit.projectile != null && hit.projectile == source.getDirectEntity() ? value : null;
    }

    /** Client-only deaths consume once, and must identify the actual projectile. */
    public static Object consumeClient(Map<Integer, ?> cache, int id,
                                       DamageSource source, LivingEntity victim) {
        Object value = cache.remove(id);
        Hit hit = CLIENT.remove(id);
        return hit != null && source != null && hit.current(victim, value)
            && hit.projectile != null && hit.projectile == source.getDirectEntity() ? value : null;
    }

    public static void clearServerMetadata() { SERVER.clear(); }
    public static void clearClientMetadata() { CLIENT.clear(); }

    public static void clearServer(int id) {
        SERVER.remove(id);
        clearNative("com.raiiiden.ragdollified.ServerRagdollHitTracker", "HIT_INFO", id);
        // The native pre-hit movement sample also belongs to the previous player state.
        clearNative("com.raiiiden.ragdollified.RagdollSpawnState", "PRE_HIT_VELOCITIES", (id & 0xffffffffL) << 1);
    }

    public static void clearClient(int id) {
        CLIENT.remove(id);
        clearNative("com.raiiiden.ragdollified.client.RagdollHitTracker", "HIT_INFO", id);
        clearNative("com.raiiiden.ragdollified.RagdollSpawnState", "PRE_HIT_VELOCITIES", ((id & 0xffffffffL) << 1) | 1L);
    }

    private static void clearNative(String name, String fieldName, Object key) {
        try {
            Field field = Class.forName(name).getDeclaredField(fieldName);
            field.setAccessible(true);
            ((Map<?, ?>)field.get(null)).remove(key);
        } catch (ClassNotFoundException ignored) {
            // Ragdollified is an optional dependency.
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Ragdollified hit cache API changed", error);
        }
    }
}
