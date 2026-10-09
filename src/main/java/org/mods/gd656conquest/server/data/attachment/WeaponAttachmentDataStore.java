package org.mods.gd656conquest.server.data.attachment;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;

import java.util.Objects;

public final class WeaponAttachmentDataStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private WeaponAttachmentDataModel cache;

    public WeaponAttachmentDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        WeaponAttachmentDataModel data = getData();
        write(ServerDataPaths.getWeaponAttachmentDataFile(server), data);
    }

    public synchronized WeaponAttachmentDataModel getData() {
        if (cache != null) {
            return cache;
        }
        WeaponAttachmentDataModel data = read(
            ServerDataPaths.getWeaponAttachmentDataFile(server),
            WeaponAttachmentDataModel.class,
            WeaponAttachmentDataModel::new
        );
        data.getAttachmentById();
        cache = data;
        return data;
    }

    public synchronized void saveData(WeaponAttachmentDataModel data) {
        WeaponAttachmentDataModel resolved = data == null ? new WeaponAttachmentDataModel() : data;
        resolved.getAttachmentById();
        cache = resolved;
        delayedJsonWriteService.schedule(ServerDataPaths.getWeaponAttachmentDataFile(server), resolved, server.getTickCount());
    }
}
