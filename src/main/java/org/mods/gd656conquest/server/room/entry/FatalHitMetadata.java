package org.mods.gd656conquest.server.room.entry;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Generic hit-event metadata. A headshot is associated with the actual victim, projectile and damage tick. */
@Mod.EventBusSubscriber(modid = "gd656conquest")
public final class FatalHitMetadata {
    private record Hit(ServerPlayer victim, Entity projectile, int tick, boolean headshot) {}
    private record Access(Method victim, Method projectile, Method headshot) {}
    private static final Map<UUID, Hit> HITS = new HashMap<>();
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        protected Access computeValue(Class<?> type) {
            try {
                Method flag;
                try { flag = type.getMethod("isHeadShot"); }
                catch (NoSuchMethodException ignored) { flag = type.getMethod("isHeadshot"); }
                return new Access(type.getMethod("getHurtEntity"), type.getMethod("getBullet"), flag);
            } catch (ReflectiveOperationException ignored) { return new Access(null, null, null); }
        }
    };
    private FatalHitMetadata() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHitEvent(Event event) {
        Access access = ACCESS.get(event.getClass());
        if (access.victim() == null) return;
        try {
            if (access.victim().invoke(event) instanceof ServerPlayer victim
                && access.projectile().invoke(event) instanceof Entity projectile
                && access.headshot().invoke(event) instanceof Boolean headshot)
                remember(victim, projectile, headshot);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    /** Other providers may report a confirmed hit without depending on a gun mod. */
    public static void remember(ServerPlayer victim, Entity projectile, boolean headshot) {
        if (victim == null || projectile == null || victim.level().isClientSide) return;
        HITS.values().removeIf(hit -> hit.victim().server == victim.server && hit.tick() != hit.victim().tickCount);
        HITS.put(victim.getUUID(), new Hit(victim, projectile, victim.tickCount, headshot));
    }
    public static boolean headshot(ServerPlayer victim, DamageSource source) {
        Hit hit = HITS.get(victim.getUUID());
        return hit != null && hit.victim() == victim && hit.tick() == victim.tickCount
            && source != null && source.getDirectEntity() == hit.projectile() && hit.headshot();
    }
    public static void forget(ServerPlayer victim) { HITS.remove(victim.getUUID()); }
    public static void clear(net.minecraft.server.MinecraftServer server) { HITS.values().removeIf(hit -> hit.victim().server == server); }
}
