package org.mods.gd656conquest.server.compat.tacz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import org.mods.gd656conquest.common.match.ShellingScopeLedger;
import org.mods.gd656conquest.common.match.ShellingScopeLedger.Scope;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

/** Tags actual shells and lingering effects, so owner changes cannot revive their damage. */
public final class ShellingPresentationProtection {
    private static final Map<MinecraftServer, ShellingScopeLedger> LEDGERS = new WeakHashMap<>();
    private static final String ROOM = "gdcShellingRoom";
    private static final String GENERATION = "gdcShellingGeneration";

    private ShellingPresentationProtection() {}

    private static ShellingScopeLedger ledger(MinecraftServer server) {
        return LEDGERS.computeIfAbsent(server, ignored -> new ShellingScopeLedger());
    }

    public static Scope scope(ServerPlayer owner) {
        return RoomCoreRuntimeManager.of(owner.server).findPlayerRoomId(owner.getUUID())
            .map(id -> ledger(owner.server).scope(id)).orElse(null);
    }

    public static void begin(MinecraftServer server, String room) {
        ledger(server).begin(room);
    }

    public static void terminate(MinecraftServer server, String room) {
        ledger(server).terminate(room);
        for (var level : server.getAllLevels()) {
            List<Entity> entities = new ArrayList<>();
            level.getAllEntities().forEach(entities::add);
            for (Entity entity : entities) {
                Scope scope = scope(entity);
                if (scope != null && room.equals(scope.roomId())) entity.discard();
            }
        }
    }

    public static void forget(MinecraftServer server) {
        LEDGERS.remove(server);
    }

    public static void tag(Entity entity, Scope scope) {
        if (entity == null || scope == null) return;
        entity.getPersistentData().putString(ROOM, scope.roomId());
        entity.getPersistentData().putLong(GENERATION, scope.generation());
    }

    public static Scope scope(Entity entity) {
        if (entity == null) return null;
        String room = entity.getPersistentData().getString(ROOM);
        return room.isEmpty() ? null : new Scope(room, entity.getPersistentData().getLong(GENERATION));
    }

    public static boolean harmless(Entity entity) {
        Scope scope = scope(entity);
        return scope != null && entity.getServer() != null && ledger(entity.getServer()).harmless(scope);
    }

    public static boolean expired(Entity entity) {
        Scope scope = scope(entity);
        return scope != null && entity.getServer() != null && ledger(entity.getServer()).expired(scope);
    }

    public static boolean harmless(MinecraftServer server, Scope scope) {
        return scope != null && ledger(server).harmless(scope);
    }

    public static boolean expired(MinecraftServer server, Scope scope) {
        return scope != null && ledger(server).expired(scope);
    }

    public static boolean blocksDamage(DamageSource source) {
        return source != null && (harmless(source.getDirectEntity()) || harmless(source.getEntity()));
    }
}
