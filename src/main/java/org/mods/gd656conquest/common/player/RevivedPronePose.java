package org.mods.gd656conquest.common.player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** Uses entity identity, so integrated-server and client poses cannot clear each other. */
public final class RevivedPronePose {
    private static final Map<Player, Long> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Player> RELEASING = new ThreadLocal<>();
    private RevivedPronePose() {}
    public static void begin(Player player, long session) {
        ACTIVE.put(player, session);
        setNativeCrawl(player, true);
        apply(player);
    }
    public static boolean active(Player player) { return ACTIVE.containsKey(player); }
    public static void apply(Player player) {
        player.setForcedPose(Pose.SWIMMING);
        player.setPose(Pose.SWIMMING);
        player.refreshDimensions();
    }
    public static void end(Player player) {
        if (ACTIVE.remove(player) == null) return;
        Player previous = RELEASING.get();
        RELEASING.set(player);
        try { setNativeCrawl(player, false); }
        finally { if (previous == null) RELEASING.remove(); else RELEASING.set(previous); }
        if (player.getForcedPose() == Pose.SWIMMING) player.setForcedPose(null);
        player.refreshDimensions();
    }
    /** Only our own synchronous revive release may bypass TACZ's item/cooldown checks. */
    public static boolean releasingNativeCrawl(Player player) { return player != null && RELEASING.get() == player; }
    private static void setNativeCrawl(Player player, boolean value) {
        // Server operator accepts any held item. Local operator may reject a syringe/non-gun;
        // our independent forced pose and compatibility mixin cover that case.
        try {
            Class<?> api = Class.forName(player.level().isClientSide
                ? "com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator" : "com.tacz.guns.api.entity.IGunOperator");
            Method from = null;
            for (Method method : api.getMethods()) if (method.getName().equals(player.level().isClientSide
                ? "fromLocalPlayer" : "fromLivingEntity")) { from = method; break; }
            if (from != null) api.getMethod("crawl", boolean.class).invoke(from.invoke(null, player), value);
        } catch (ReflectiveOperationException | LinkageError ignored) {}
    }
    private static final ClassValue<Field> PLAYER_FIELD = new ClassValue<>() {
        protected Field computeValue(Class<?> type) {
            for (String name : new String[]{"player", "shooter"}) try {
                Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
            } catch (ReflectiveOperationException ignored) {}
            throw new IllegalArgumentException("Unknown crawl owner");
        }
    };
    public static boolean preserveNativePose(Object crawl) {
        try {
            Object owner = PLAYER_FIELD.get(crawl.getClass()).get(crawl);
            if (owner instanceof Player player && active(player)) { apply(player); return true; }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
        return false;
    }
}
