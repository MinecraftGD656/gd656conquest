package org.mods.gd656conquest.server.room.runtime;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

public final class RoomDimensionControlService {
    private RoomDimensionControlService() {
    }

    public static boolean unloadRoomDimension(MinecraftServer server, RoomDefinition definition) {
        return unloadRoomDimension(server, resolveRoomDimensionKey(definition));
    }

    public static boolean reloadRoomDimension(MinecraftServer server, RoomDefinition definition) {
        return reloadRoomDimension(server, resolveRoomDimensionKey(definition));
    }

    public static boolean unloadRoomDimension(MinecraftServer server, ResourceKey<Level> levelKey) {
        if (server == null || levelKey == null) {
            return false;
        }
        if (server instanceof RoomDimensionReloadBridge bridge) {
            return bridge.unloadRoomDimension(levelKey);
        }
        return false;
    }

    public static boolean reloadRoomDimension(MinecraftServer server, ResourceKey<Level> levelKey) {
        if (server == null || levelKey == null) {
            return false;
        }
        if (server instanceof RoomDimensionReloadBridge bridge) {
            return bridge.reloadRoomDimension(levelKey);
        }
        return false;
    }

    public static ResourceKey<Level> resolveRoomDimensionKey(RoomDefinition definition) {
        if (definition == null || definition.commonConfig() == null) {
            return null;
        }
        String dimensionId = definition.commonConfig().dimensionId();
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(dimensionId.trim());
        if (location == null) {
            return null;
        }
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location);
    }
}
