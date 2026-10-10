package org.mods.gd656conquest.server.room.entry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;

/** Read-only adapter to the already installed Killicon's fire ownership and expiry rules. */
final class KilliconFireOwnership {
    private static boolean discovered;
    private static Field history, timeout;
    private static Method attackerId, timestamp, cloudOwner;
    private KilliconFireOwnership() {}

    static ServerPlayer resolve(ServerPlayer victim, DamageSource source) {
        if (source == null || !source.is(DamageTypeTags.IS_FIRE)) return null;
        String type = source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
        if (type.equals("minecraft:lava")) return null; // Same exclusion as Killicon.
        discover();
        if (history == null) return null;
        try {
            Object value = ((Map<?, ?>) history.get(null)).get(victim.getUUID());
            if (value != null && System.currentTimeMillis() - (long) timestamp.invoke(value) <= timeout.getLong(null)) {
                ServerPlayer owner = victim.server.getPlayerList().getPlayer((java.util.UUID) attackerId.invoke(value));
                if (owner != null) return owner;
            }
            return (ServerPlayer) cloudOwner.invoke(null, victim);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private static void discover() {
        if (discovered) return;
        discovered = true;
        try {
            Class<?> engine = Class.forName("org.mods.gd656killicon.server.event.ServerCombatEngine");
            Class<?> entry = Class.forName("org.mods.gd656killicon.server.event.FireAttribution");
            Class<?> resolver = Class.forName("org.mods.gd656killicon.server.event.ServerCombatAttribution");
            Field records = engine.getDeclaredField("fireAttribution");
            timeout = engine.getDeclaredField("FIRE_ATTRIBUTION_TIMEOUT_MS");
            attackerId = entry.getDeclaredMethod("attackerId"); timestamp = entry.getDeclaredMethod("timestamp");
            cloudOwner = resolver.getDeclaredMethod("resolveMolotovOwner", net.minecraft.world.entity.LivingEntity.class);
            records.setAccessible(true); timeout.setAccessible(true); attackerId.setAccessible(true);
            timestamp.setAccessible(true); cloudOwner.setAccessible(true);
            history = records;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { history = null; }
    }
}
