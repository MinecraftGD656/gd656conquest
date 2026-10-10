package org.mods.gd656conquest.server.room.entry;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.loadout.TaczDisplayNameResolver;

/** One generic pipeline, with TACZ ID/display-ID support built in. Never guesses from a later held item. */
public final class KillWeaponResolver {
    public record Weapon(String name, String id, String displayId) {
        public Weapon {
            if (name == null || name.isBlank() || name.length() > 256 || id == null || id.length() > 256
                || displayId == null || displayId.length() > 256) throw new IllegalArgumentException("Invalid weapon metadata");
        }
        public static Weapon unknown() { return new Weapon("无法确认", "", ""); }
        public static Weapon unarmed() { return new Weapon("空手", "minecraft:air", ""); }
    }
    /** attackStack is a defensive copy from this exact melee hit, or null when it is not melee. */
    public record Context(ServerPlayer victim, ServerPlayer killer, DamageSource source, ItemStack attackStack) {}
    @FunctionalInterface public interface Extension { Optional<Weapon> resolve(Context context); }
    private static final List<Extension> EXTENSIONS = new CopyOnWriteArrayList<>();
    private KillWeaponResolver() {}

    /** Register once at server/mod setup; closing the handle removes this extension. Runs on the server thread. */
    public static AutoCloseable register(Extension extension) {
        java.util.Objects.requireNonNull(extension); EXTENSIONS.add(extension);
        return () -> EXTENSIONS.remove(extension);
    }
    public static Weapon resolve(Context context, Weapon launched) {
        for (Extension extension : EXTENSIONS) {
            try { Optional<Weapon> result = extension.resolve(context); if (result != null && result.isPresent()) return result.get(); }
            catch (RuntimeException | LinkageError ignored) { /* An optional adapter must not break the death flow. */ }
        }
        DamageSource source = context.source();
        if (source == null) return Weapon.unknown();
        Weapon direct = extract(source.getDirectEntity(), context.killer());
        if (direct == null) direct = extract(source, context.killer());
        if (direct != null) return direct;
        if (launched != null) return launched;
        if (isMelee(source, context.killer()) && context.attackStack() != null)
            return fromStack(context.attackStack(), context.killer());
        return Weapon.unknown();
    }
    public static boolean isMelee(DamageSource source, ServerPlayer attacker) {
        return source != null && attacker != null && source.getDirectEntity() == attacker && source.getEntity() == attacker
            && "minecraft:player_attack".equals(typeId(source));
    }
    public static String typeId(DamageSource source) {
        return source == null ? "" : source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
    }
    /** Public metadata getters are optional capabilities, rather than a list of mod-specific entity types. */
    public static Weapon extract(Object source, ServerPlayer attacker) {
        if (source == null) return null;
        Object stack = first(source, "getWeaponStack", "getWeaponItemStack", "getGunItemStack", "getItemStack");
        if (stack instanceof ItemStack item && !item.isEmpty()) return fromStack(item, attacker);
        String gun = identifier(first(source, "getGunId"));
        String display = identifier(first(source, "getGunDisplayId"));
        if (!gun.isEmpty()) {
            // TACZ bullets retain these IDs from firing, even after switching weapons or mutual kills.
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation("tacz", "modern_kinetic_gun"));
            if (!isAir(item)) {
                ItemStack reconstructed = new ItemStack(item);
                reconstructed.getOrCreateTag().putString("GunId", gun);
                if (!display.isEmpty()) reconstructed.getOrCreateTag().putString("GunDisplayId", display);
                Weapon named = fromStack(reconstructed, attacker);
                return new Weapon(named.name(), gun, display);
            }
            return new Weapon(readableId(display.isEmpty() ? gun : display), gun, display);
        }
        String id = identifier(first(source, "getWeaponId"));
        Object name = first(source, "getWeaponName", "getWeaponDisplayName");
        if (name instanceof net.minecraft.network.chat.Component text) name = text.getString();
        if (name instanceof String text && !text.isBlank()) return new Weapon(limit(text, 256), id, "");
        if (!id.isEmpty()) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            return !isAir(item) ? fromStack(new ItemStack(item), attacker) : new Weapon(readableId(id), id, "");
        }
        if (source instanceof ThrownTrident) return fromStack(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:trident"))), attacker);
        // ThrowableItemProjectile's public SRG getter also covers vanilla potions and throwable mod items.
        Object thrown = first(source, "getItem", "m_7846_");
        if (!(source instanceof AbstractArrow) && thrown instanceof ItemStack item && !item.isEmpty())
            return fromStack(item, attacker);
        return null;
    }
    public static Weapon fromStack(ItemStack stack, ServerPlayer attacker) {
        if (stack.isEmpty()) return Weapon.unarmed();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String display = "";
        String name = stack.getHoverName().getString();
        if (TaczDisplayNameResolver.isTaczGun(stack)) {
            id = stack.getTag().getString("GunId");
            display = stack.getTag().getString("GunDisplayId");
            if (attacker != null && attacker.server != null) name = TaczDisplayNameResolver.resolve(attacker.server, stack);
        }
        return new Weapon(limit(name == null || name.isBlank() ? readableId(id) : name, 256), id, display);
    }
    private static Object first(Object object, String... names) {
        for (String name : names) try {
            Method method = object.getClass().getMethod(name);
            if (method.getParameterCount() == 0) { Object value = method.invoke(object); if (value != null) return value; }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return null;
    }
    private static String identifier(Object value) {
        if (value instanceof ResourceLocation id && id.toString().length() <= 256) return id.toString();
        if (value instanceof String s && s.length() <= 256 && ResourceLocation.tryParse(s) != null) return s;
        return "";
    }
    private static String readableId(String id) { return id.substring(id.indexOf(':') + 1).replace('_', ' '); }
    private static boolean isAir(Item item) { return "minecraft:air".equals(BuiltInRegistries.ITEM.getKey(item).toString()); }
    private static String limit(String text, int size) { return text.length() <= size ? text : text.substring(0, size); }
}
