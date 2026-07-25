package org.mods.gd656conquest.server.player.state;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.Gd656conquest;

public final class HubDimensionService {
    public static final ResourceKey<Level> HUB_DIMENSION = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "hub")
    );

    private HubDimensionService() {
    }

    public static ResourceKey<Level> hubDimension() {
        return HUB_DIMENSION;
    }

    public static ServerLevel resolveHubLevel(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        ServerLevel hub = server.getLevel(HUB_DIMENSION);
        return hub != null ? hub : server.overworld();
    }

    public static boolean isHubDimension(Level level) {
        if (level == null) {
            return false;
        }
        if (HUB_DIMENSION.equals(level.dimension())) {
            return true;
        }
        // When the custom hub dimension does not exist, resolveHubLevel falls back to overworld.
        // Treat overworld as an effective hub to avoid a teleport loop in enforceStandaloneWorldPolicy.
        return Level.OVERWORLD.equals(level.dimension());
    }

    public static boolean isConquestDimension(Level level) {
        if (level == null || level.dimension() == null || level.dimension().location() == null) {
            return false;
        }
        return Gd656conquest.MODID.equalsIgnoreCase(level.dimension().location().getNamespace());
    }

    public static boolean isVanillaPrimaryDimension(Level level) {
        if (level == null) {
            return false;
        }
        ResourceKey<Level> dimension = level.dimension();
        return Level.OVERWORLD.equals(dimension)
            || Level.NETHER.equals(dimension)
            || Level.END.equals(dimension);
    }

    public static boolean isScoreRestrictedDimension(Level level) {
        return isHubDimension(level) || isVanillaPrimaryDimension(level);
    }
}
