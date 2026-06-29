package org.mods.gd656conquest.server.data;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.attachment.WeaponAttachmentDataStore;
import org.mods.gd656conquest.server.data.equipment.EquipmentDataStore;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepository;
import org.mods.gd656conquest.server.data.roomtemplate.RoomTemplateRepository;
import org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.vehicledata.VehicleConfigRepository;
import org.mods.gd656conquest.server.title.PlayerTitleRegistryStore;

import java.util.Map;
import java.util.WeakHashMap;

public final class ConquestDataManager {
    private static final Map<MinecraftServer, ConquestDataManager> INSTANCES = new WeakHashMap<>();

    private final ServerConfigStore serverConfigStore;
    private final PlayerDataStore playerDataStore;
    private final PlayerTitleRegistryStore playerTitleRegistryStore;
    private final RoomDefinitionRepository roomDefinitionRepository;
    private final RoomTemplateRepository roomTemplateRepository;
    private final EquipmentDataStore equipmentDataStore;
    private final WeaponAttachmentDataStore weaponAttachmentDataStore;
    private final VehicleConfigRepository vehicleConfigRepository;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private boolean initialized;

    private ConquestDataManager(MinecraftServer server) {
        this.delayedJsonWriteService = new DelayedJsonWriteService();
        this.serverConfigStore = new ServerConfigStore(server, delayedJsonWriteService);
        this.playerDataStore = new PlayerDataStore(server, delayedJsonWriteService);
        this.playerTitleRegistryStore = new PlayerTitleRegistryStore(server, delayedJsonWriteService);
        this.roomDefinitionRepository = new RoomDefinitionRepository(server, delayedJsonWriteService);
        this.roomTemplateRepository = new RoomTemplateRepository(server);
        this.equipmentDataStore = new EquipmentDataStore(server, delayedJsonWriteService);
        this.weaponAttachmentDataStore = new WeaponAttachmentDataStore(server, delayedJsonWriteService);
        this.vehicleConfigRepository = new VehicleConfigRepository(server, delayedJsonWriteService);
    }

    public static ConquestDataManager of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, ConquestDataManager::new);
        }
    }

    public static void forget(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        serverConfigStore.initialize();
        playerDataStore.initialize();
        playerTitleRegistryStore.initialize();
        roomDefinitionRepository.initialize();
        equipmentDataStore.initialize();
        weaponAttachmentDataStore.initialize();
        vehicleConfigRepository.initialize();
        initialized = true;
    }

    public synchronized void flushDelayedWrites(long gameTick) {
        delayedJsonWriteService.flushDue(gameTick);
    }

    public synchronized void flushAllWrites() {
        delayedJsonWriteService.flushAll();
    }

    public ServerConfigStore serverConfig() {
        initialize();
        return serverConfigStore;
    }

    public PlayerDataStore playerData() {
        initialize();
        return playerDataStore;
    }

    public PlayerTitleRegistryStore playerTitles() {
        initialize();
        return playerTitleRegistryStore;
    }

    public RoomDefinitionRepository roomDefinitions() {
        initialize();
        return roomDefinitionRepository;
    }

    public EquipmentDataStore equipmentData() {
        initialize();
        return equipmentDataStore;
    }

    public WeaponAttachmentDataStore weaponAttachments() {
        initialize();
        return weaponAttachmentDataStore;
    }

    public RoomTemplateRepository roomTemplates() {
        initialize();
        return roomTemplateRepository;
    }

    public VehicleConfigRepository vehicleConfigs() {
        initialize();
        return vehicleConfigRepository;
    }
}
