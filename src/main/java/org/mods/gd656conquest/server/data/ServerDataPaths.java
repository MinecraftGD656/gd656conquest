package org.mods.gd656conquest.server.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.mods.gd656conquest.server.room.model.RoomVehicleBackend;

import java.nio.file.Path;

public final class ServerDataPaths {
    private ServerDataPaths() {
    }

    public static Path getModRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("gd656conquest");
    }

    public static Path getServerConfigFile(MinecraftServer server) {
        return getModRoot(server).resolve("serverconfig.json");
    }

    public static Path getPackWhitelistFile(MinecraftServer server) {
        return getModRoot(server).resolve("packwhitelist.json");
    }

    public static Path getPackLogFile(MinecraftServer server) {
        return getModRoot(server).resolve("packlog.json");
    }

    public static Path getPlayerDataDirectory(MinecraftServer server) {
        return getModRoot(server).resolve("playerdata");
    }

    public static Path getPlayerDataFile(MinecraftServer server, String uuid) {
        return getPlayerDataDirectory(server).resolve(uuid + ".json");
    }

    public static Path getTitleRegistryFile(MinecraftServer server) {
        return getModRoot(server).resolve("titles.json");
    }

    public static Path getFilesDirectory(MinecraftServer server) {
        return getModRoot(server).resolve("files");
    }

    public static Path getEquipmentDataFile(MinecraftServer server) {
        return getModRoot(server).resolve("equipmentData.json");
    }

    public static Path getEquipmentSetDataFile(MinecraftServer server) {
        return getModRoot(server).resolve("equipmentSets.json");
    }

    public static Path getHeroKitDataFile(MinecraftServer server) {
        return getModRoot(server).resolve("herokits.json");
    }

    public static Path getShellingDataFile(MinecraftServer server) {
        return getModRoot(server).resolve("shellings.json");
    }

    public static Path getWeaponAttachmentDataFile(MinecraftServer server) {
        return getModRoot(server).resolve("weaponAttachmentData.json");
    }

    public static Path getParkourDataFile(MinecraftServer server) {
        return getModRoot(server).resolve("parkourData.json");
    }

    public static Path getRoomDefinitionDirectory(MinecraftServer server) {
        return getModRoot(server).resolve("roomdefinitions");
    }

    public static Path getLegacyRoomDefinitionDirectory(MinecraftServer server) {
        return getModRoot(server).resolve("roomdefinitions_old");
    }

    public static Path getRoomDefinitionFile(MinecraftServer server, String roomId) {
        return getRoomDataDirectory(server).resolve(roomId + ".json");
    }

    public static Path getRoomDataDirectory(MinecraftServer server) {
        return getModRoot(server).resolve("roomdata");
    }

    public static Path getRoomTemplateDirectory(MinecraftServer server) {
        return getModRoot(server).resolve("roomtemplates");
    }

    public static Path getVehicleDataDirectory(MinecraftServer server) {
        return getModRoot(server).resolve("vehicledata");
    }

    public static Path getVehicleDataRoomDirectory(MinecraftServer server, String roomId) {
        return getVehicleDataDirectory(server).resolve(roomId);
    }

    public static Path getVehicleDataFile(MinecraftServer server, String roomId, String gameplayId) {
        return getVehicleDataRoomDirectory(server, roomId).resolve(gameplayId + ".json");
    }

    public static Path getVehicleDataFile(MinecraftServer server, String roomId, String gameplayId, RoomVehicleBackend backend) {
        String backendKey = backend == null ? RoomVehicleBackend.defaultValue().value().toLowerCase(java.util.Locale.ROOT) : backend.value().toLowerCase(java.util.Locale.ROOT);
        return getVehicleDataRoomDirectory(server, roomId).resolve(gameplayId + "." + backendKey + ".json");
    }

    public static Path getRoomTemplatePath(MinecraftServer server, String roomId) {
        return getRoomTemplateDirectory(server).resolve(roomId);
    }

    public static Path getRoomResetRecoveryFile(MinecraftServer server) {
        return getModRoot(server).resolve("room-reset-recovery.json");
    }
}
