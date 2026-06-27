package org.mods.gd656conquest.mixin.server;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import org.mods.gd656conquest.server.room.runtime.RoomDimensionReloadBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements RoomDimensionReloadBridge {
    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow
    @Final
    private LayeredRegistryAccess<RegistryLayer> registries;

    @Shadow
    @Final
    private WorldData worldData;

    @Shadow
    private Executor executor;

    @Shadow
    @Final
    private LevelStorageSource.LevelStorageAccess storageSource;

    @Override
    public boolean unloadRoomDimension(ResourceKey<Level> levelKey) {
        if (levelKey == null || Level.OVERWORLD.equals(levelKey)) {
            return false;
        }
        ServerLevel existing = levels.remove(levelKey);
        markWorldsDirtyCompat();
        if (existing == null) {
            return true;
        }
        try {
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(existing));
        } catch (Exception ignored) {
        }
        try {
            gd656conquest$roomLevelDataCache.put(levelKey, existing.getLevelData());
        } catch (Exception ignored) {
        }
        try {
            existing.save(null, true, false);
        } catch (Exception ignored) {
        }
        try {
            existing.close();
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    public boolean reloadRoomDimension(ResourceKey<Level> levelKey) {
        if (levelKey == null || Level.OVERWORLD.equals(levelKey)) {
            return false;
        }
        if (levels.containsKey(levelKey)) {
            return true;
        }
        ServerLevel overworld = levels.get(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }
        if (registries == null || worldData == null || executor == null || storageSource == null) {
            return false;
        }
        Registry<LevelStem> levelStemRegistry = registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
        LevelStem stem = levelStemRegistry.get(stemKey);
        if (stem == null) {
            return false;
        }
        try {
            long obfuscatedSeed = BiomeManager.obfuscateSeed(worldData.worldGenOptions().seed());
            net.minecraft.world.level.storage.LevelData cached = gd656conquest$roomLevelDataCache.remove(levelKey);
            net.minecraft.world.level.storage.ServerLevelData baseData = cached instanceof net.minecraft.world.level.storage.ServerLevelData serverLevelData
                    ? serverLevelData
                    : worldData.overworldData();
            if (baseData == null) {
                return false;
            }
            DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, baseData);
            RandomSequences randomSequences = overworld.getRandomSequences();
            ServerLevel reloaded = new ServerLevel((MinecraftServer) (Object) this, executor, storageSource, derivedLevelData, levelKey, stem, new NoopChunkProgressListener(), worldData.isDebugWorld(), obfuscatedSeed, ImmutableList.of(), false, randomSequences);
            overworld.getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(reloaded.getWorldBorder()));
            levels.put(levelKey, reloaded);
            markWorldsDirtyCompat();
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(reloaded));
            return true;
        } catch (Exception exception) {
            levels.remove(levelKey);
            markWorldsDirtyCompat();
            return false;
        }
    }

    private void markWorldsDirtyCompat() {
        try {
            Method method = MinecraftServer.class.getMethod("markWorldsDirty");
            method.invoke((MinecraftServer) (Object) this);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private final java.util.Map<ResourceKey<Level>, net.minecraft.world.level.storage.LevelData> gd656conquest$roomLevelDataCache = new java.util.HashMap<>();

    private static final class NoopChunkProgressListener implements ChunkProgressListener {
        @Override
        public void updateSpawnPos(net.minecraft.world.level.ChunkPos chunkPos) {
        }

        @Override
        public void onStatusChange(net.minecraft.world.level.ChunkPos chunkPos, net.minecraft.world.level.chunk.ChunkStatus chunkStatus) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }
}
