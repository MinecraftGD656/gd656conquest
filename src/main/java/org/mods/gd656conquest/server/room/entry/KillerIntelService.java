package org.mods.gd656conquest.server.room.entry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.common.player.KillerCardInfo;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.KillerCardPacket;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;

/** Observes presentation metadata only. Does not award kills, cancel damage, or change respawn state. */
@Mod.EventBusSubscriber(modid = "gd656conquest")
public final class KillerIntelService {
    private record Attack(ServerPlayer victim, DamageSource source, ItemStack stack, long tick) {}
    private record Swing(ServerPlayer attacker, ServerPlayer victim, ItemStack stack, long tick) {}
    private record Launch(Entity projectile, UUID owner, KillWeaponResolver.Weapon weapon, long tick) {}
    private record Fatal(ServerPlayer victim, ServerPlayer killer, String name, int level, float health,
                         float maxHealth, KillWeaponResolver.Weapon weapon, boolean headshot) {}
    private record Saved(ServerPlayer victim, RunningRuntimeState room, long match, long deathId, KillerCardInfo info) {}
    private static final Map<UUID, Attack> ATTACKS = new HashMap<>();
    private static final Map<UUID, Swing> SWINGS = new HashMap<>();
    private static final Map<UUID, Launch> LAUNCHES = new HashMap<>();
    private static final Map<UUID, Saved> SAVED = new HashMap<>();
    private static final Map<MinecraftServer, Map<RunningRuntimeState, MatchDuelStats>> DUELS = new WeakHashMap<>();
    private static final ThreadLocal<Fatal> FATAL = new ThreadLocal<>();
    private KillerIntelService() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMelee(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer attacker && event.getTarget() instanceof ServerPlayer victim && attacker.server != null)
            SWINGS.put(victim.getUUID(), new Swing(attacker, victim, attacker.getMainHandItem().copy(), attacker.server.getTickCount()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.server == null) return;
        ServerPlayer killer = RoomDamageSourceResolver.resolveAttackingPlayer(event.getSource());
        Swing swing = SWINGS.get(victim.getUUID());
        if (KillWeaponResolver.isMelee(event.getSource(), killer) && swing != null && swing.attacker() == killer && swing.victim() == victim
            && swing.tick() == victim.server.getTickCount()) {
            SWINGS.remove(victim.getUUID());
            ATTACKS.put(victim.getUUID(), new Attack(victim, event.getSource(), swing.stack().copy(), swing.tick()));
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectile(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile projectile)
            || !(projectile.getOwner() instanceof ServerPlayer owner) || owner.server == null) return;
        KillWeaponResolver.Weapon weapon = KillWeaponResolver.extract(projectile, owner);
        if (weapon == null && projectile instanceof AbstractArrow && !(projectile instanceof ThrownTrident)) {
            ItemStack used = owner.getUseItem();
            if (used.isEmpty()) used = projectile instanceof AbstractArrow arrow && arrow.shotFromCrossbow()
                ? findHeld(owner, item("minecraft:crossbow")) : findHeld(owner, item("minecraft:bow"));
            if (!used.isEmpty() && (used.getItem() == item("minecraft:bow") || used.getItem() == item("minecraft:crossbow")))
                weapon = KillWeaponResolver.fromStack(used.copy(), owner);
        }
        if (weapon != null) LAUNCHES.put(projectile.getUUID(), new Launch(projectile, owner.getUUID(), weapon, owner.server.getTickCount()));
    }
    private static ItemStack findHeld(ServerPlayer owner, net.minecraft.world.item.Item item) {
        if (owner.getMainHandItem().getItem() == item) return owner.getMainHandItem();
        return owner.getOffhandItem().getItem() == item ? owner.getOffhandItem() : ItemStack.EMPTY;
    }
    private static net.minecraft.world.item.Item item(String id) { return BuiltInRegistries.ITEM.get(new ResourceLocation(id)); }

    /** Runs inside fire ownership scope, before other death listeners can clear equipment or heal proxies. */
    public static boolean withFatalSnapshot(ServerPlayer victim, DamageSource source, BooleanSupplier transition) {
        Fatal previous = FATAL.get();
        ServerPlayer killer = RoomDeathCauseService.attributedKiller(source, victim);
        FATAL.set(killer == null ? null : snapshot(victim, killer, source));
        try { return transition.getAsBoolean(); }
        finally { if (previous == null) FATAL.remove(); else FATAL.set(previous); }
    }
    private static Fatal snapshot(ServerPlayer victim, ServerPlayer killer, DamageSource source) {
        Attack attack = ATTACKS.get(victim.getUUID());
        ItemStack melee = attack != null && attack.victim() == victim && attack.source() == source
            && attack.tick() == victim.server.getTickCount() ? attack.stack().copy() : null;
        Entity direct = source == null ? null : source.getDirectEntity();
        Launch launch = direct == null ? null : LAUNCHES.get(direct.getUUID());
        KillWeaponResolver.Weapon launched = launch != null && launch.projectile() == direct && launch.owner().equals(killer.getUUID())
            && victim.server.getTickCount() - launch.tick() <= 1200 ? launch.weapon() : null;
        KillWeaponResolver.Weapon weapon = KillWeaponResolver.resolve(new KillWeaponResolver.Context(victim, killer, source, melee), launched);
        float maximum = killer.getMaxHealth(), health = killer.getHealth();
        if (!Float.isFinite(maximum) || maximum <= 0) maximum = 20;
        if (!Float.isFinite(health)) health = 0;
        RoomCoreRuntimeManager manager = RoomCoreRuntimeManager.of(killer.server);
        RunningRuntimeState room = manager.findPlayerRoomId(killer.getUUID()).flatMap(manager::findRunningState).orElse(null);
        if (!killer.isAlive() || room != null && room.isInRespawnFlow(killer.getUUID())) health = 0;
        String name = killer.getDisplayName().getString();
        if (name == null || name.isBlank()) name = killer.getScoreboardName();
        return new Fatal(victim, killer, name.substring(0, Math.min(128, name.length())),
            PlayerLevelService.getEffectiveLevel(killer), Math.max(0, Math.min(maximum, health)), maximum, weapon, FatalHitMetadata.headshot(victim, source));
    }
    public static void capture(ServerPlayer victim, RunningRuntimeState room, long deathId, DeathCause cause,
                               ServerPlayer killer, DamageSource source) {
        SAVED.remove(victim.getUUID());
        if (cause == null || !cause.allowsKillerCard() || killer == null || killer == victim) return;
        Fatal fatal = FATAL.get();
        if (fatal == null || fatal.victim() != victim || fatal.killer() != killer) fatal = snapshot(victim, killer, source);
        MatchDuelStats.Score score = DUELS.computeIfAbsent(victim.server, unused -> new WeakHashMap<>())
            .computeIfAbsent(room, unused -> new MatchDuelStats())
            .record(room.getActiveMatchInstanceId(), deathId, killer.getUUID(), victim.getUUID());
        KillWeaponResolver.Weapon weapon = fatal.weapon();
        KillerCardInfo info = new KillerCardInfo(killer.getUUID(), fatal.name(), fatal.level(), fatal.health(), fatal.maxHealth(),
            score.playerKills(), score.enemyKills(), weapon.name(), weapon.id(), weapon.displayId(), fatal.headshot());
        SAVED.put(victim.getUUID(), new Saved(victim, room, room.getActiveMatchInstanceId(), deathId, info));
    }
    public static void publish(ServerPlayer victim, long deathId) {
        Saved saved = SAVED.get(victim.getUUID());
        if (saved != null && saved.victim() == victim && saved.deathId() == deathId && saved.match() == saved.room().getActiveMatchInstanceId())
            NetworkManager.sendToPlayer(victim, new KillerCardPacket(deathId, victim.getId(),
                victim.level().dimension().location().toString(), saved.info()));
    }
    public static void forget(ServerPlayer victim) { FatalHitMetadata.forget(victim); SAVED.remove(victim.getUUID()); ATTACKS.remove(victim.getUUID()); SWINGS.remove(victim.getUUID()); }
    public static void clear(MinecraftServer server) {
        FatalHitMetadata.clear(server);
        SAVED.values().removeIf(s -> s.victim().server == server);
        ATTACKS.values().removeIf(a -> a.victim().server == server);
        SWINGS.values().removeIf(s -> s.attacker().server == server);
        LAUNCHES.values().removeIf(l -> l.projectile().level().getServer() == server);
        DUELS.remove(server);
    }
    public static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        ATTACKS.values().removeIf(a -> a.victim().server == server && tick - a.tick() > 1);
        SWINGS.values().removeIf(s -> s.attacker().server == server && tick - s.tick() > 1);
        LAUNCHES.values().removeIf(l -> l.projectile().level().getServer() == server && (tick - l.tick() > 1200 || l.projectile().isRemoved()));
        SAVED.values().removeIf(s -> s.victim().server == server && (s.match() != s.room().getActiveMatchInstanceId()
            || server.getPlayerList().getPlayer(s.victim().getUUID()) != s.victim() || !s.room().isInRespawnFlow(s.victim().getUUID())
            || s.room().getDeployViewActivePlayers().contains(s.victim().getUUID()) || s.room().getRespawnFinalizeTicks().containsKey(s.victim().getUUID())));
    }
}
