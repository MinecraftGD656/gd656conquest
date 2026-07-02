package org.mods.gd656conquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mods.gd656conquest.Gd656conquest;
import org.mods.gd656conquest.network.packet.AttachmentEditorUpdatePacket;
import org.mods.gd656conquest.network.packet.MainHandItemNameUploadPacket;
import org.mods.gd656conquest.network.packet.EquipmentEditorUpdatePacket;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiOpenPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.network.packet.PlayerAnimatorDebugPacket;
import org.mods.gd656conquest.network.packet.PlayerAnimatorHelpRequestPacket;
import org.mods.gd656conquest.network.packet.PlayerAnimatorHelpSyncPacket;
import org.mods.gd656conquest.network.packet.PlayerLockStatePacket;
import org.mods.gd656conquest.network.packet.RoomDownedActionPacket;
import org.mods.gd656conquest.network.packet.RoomDownedStatePacket;
import org.mods.gd656conquest.network.packet.RoomEditorRequestPacket;
import org.mods.gd656conquest.network.packet.RoomEditorSnapshotPacket;
import org.mods.gd656conquest.network.packet.RoomEditorUpdatePacket;
import org.mods.gd656conquest.network.packet.VehicleAdminEditorRequestPacket;
import org.mods.gd656conquest.network.packet.VehicleAdminEditorSnapshotPacket;
import org.mods.gd656conquest.network.packet.VehicleAdminEditorUpdatePacket;
import org.mods.gd656conquest.network.packet.RoomDeathFlowPacket;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;
import org.mods.gd656conquest.network.packet.RoomHudSubtitlePacket;
import org.mods.gd656conquest.network.packet.RoomHudSyncPacket;
import org.mods.gd656conquest.network.packet.RoomMarkerActionPacket;
import org.mods.gd656conquest.network.packet.RoomMarkerSyncPacket;
import org.mods.gd656conquest.network.packet.RoomMatchResultPacket;
import org.mods.gd656conquest.network.packet.RoomTeamMirrorSyncPacket;
import org.mods.gd656conquest.network.packet.AdminDebugOverlayPacket;
import org.mods.gd656conquest.network.packet.HudDebugPreviewPacket;
import org.mods.gd656conquest.network.packet.MarkerDebugPreviewPacket;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.network.packet.ClientboundScreenshotRequestPacket;
import org.mods.gd656conquest.network.packet.ServerboundScreenshotChunkPacket;
import org.mods.gd656conquest.network.packet.ServerboundScreenshotStatusPacket;
import org.mods.gd656conquest.network.packet.ServerboundAmmoBoxInteractPacket;

public final class NetworkManager {
    // Bump this whenever packet layout/order changes to avoid decode mismatch crashes.
    private static final String PROTOCOL_VERSION = "065";
    private static int packetId;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private NetworkManager() {
    }

    public static void init() {
        CHANNEL.registerMessage(
                packetId++,
                RoomHudSyncPacket.class,
                RoomHudSyncPacket::encode,
                RoomHudSyncPacket::decode,
                RoomHudSyncPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                PlayerLockStatePacket.class,
                PlayerLockStatePacket::encode,
                PlayerLockStatePacket::decode,
                PlayerLockStatePacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomHudSubtitlePacket.class,
                RoomHudSubtitlePacket::encode,
                RoomHudSubtitlePacket::decode,
                RoomHudSubtitlePacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomDeathFlowPacket.class,
                RoomDeathFlowPacket::encode,
                RoomDeathFlowPacket::decode,
                RoomDeathFlowPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomDownedStatePacket.class,
                RoomDownedStatePacket::encode,
                RoomDownedStatePacket::decode,
                RoomDownedStatePacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomDownedActionPacket.class,
                RoomDownedActionPacket::encode,
                RoomDownedActionPacket::decode,
                RoomDownedActionPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomDeploySelectPacket.class,
                RoomDeploySelectPacket::encode,
                RoomDeploySelectPacket::decode,
                RoomDeploySelectPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomMarkerSyncPacket.class,
                RoomMarkerSyncPacket::encode,
                RoomMarkerSyncPacket::decode,
                RoomMarkerSyncPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomMarkerActionPacket.class,
                RoomMarkerActionPacket::encode,
                RoomMarkerActionPacket::decode,
                RoomMarkerActionPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomMatchResultPacket.class,
                RoomMatchResultPacket::encode,
                RoomMatchResultPacket::decode,
                RoomMatchResultPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomTeamMirrorSyncPacket.class,
                RoomTeamMirrorSyncPacket::encode,
                RoomTeamMirrorSyncPacket::decode,
                RoomTeamMirrorSyncPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                AdminDebugOverlayPacket.class,
                AdminDebugOverlayPacket::encode,
                AdminDebugOverlayPacket::decode,
                AdminDebugOverlayPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                MarkerDebugPreviewPacket.class,
                MarkerDebugPreviewPacket::encode,
                MarkerDebugPreviewPacket::decode,
                MarkerDebugPreviewPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                HudDebugPreviewPacket.class,
                HudDebugPreviewPacket::encode,
                HudDebugPreviewPacket::decode,
                HudDebugPreviewPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                PlayerAnimatorDebugPacket.class,
                PlayerAnimatorDebugPacket::encode,
                PlayerAnimatorDebugPacket::decode,
                PlayerAnimatorDebugPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                PlayerAnimatorHelpSyncPacket.class,
                PlayerAnimatorHelpSyncPacket::encode,
                PlayerAnimatorHelpSyncPacket::decode,
                PlayerAnimatorHelpSyncPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                PlayerAnimatorHelpRequestPacket.class,
                PlayerAnimatorHelpRequestPacket::encode,
                PlayerAnimatorHelpRequestPacket::decode,
                PlayerAnimatorHelpRequestPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                ClientShortSoundPacket.class,
                ClientShortSoundPacket::encode,
                ClientShortSoundPacket::decode,
                ClientShortSoundPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                MainHandItemNameUploadPacket.class,
                MainHandItemNameUploadPacket::encode,
                MainHandItemNameUploadPacket::decode,
                MainHandItemNameUploadPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                LoadoutUiRequestPacket.class,
                LoadoutUiRequestPacket::encode,
                LoadoutUiRequestPacket::decode,
                LoadoutUiRequestPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                LoadoutUiOpenPacket.class,
                LoadoutUiOpenPacket::encode,
                LoadoutUiOpenPacket::decode,
                LoadoutUiOpenPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                LoadoutUiActionPacket.class,
                LoadoutUiActionPacket::encode,
                LoadoutUiActionPacket::decode,
                LoadoutUiActionPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                EquipmentEditorUpdatePacket.class,
                EquipmentEditorUpdatePacket::encode,
                EquipmentEditorUpdatePacket::decode,
                EquipmentEditorUpdatePacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                AttachmentEditorUpdatePacket.class,
                AttachmentEditorUpdatePacket::encode,
                AttachmentEditorUpdatePacket::decode,
                AttachmentEditorUpdatePacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomEditorRequestPacket.class,
                RoomEditorRequestPacket::encode,
                RoomEditorRequestPacket::decode,
                RoomEditorRequestPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomEditorSnapshotPacket.class,
                RoomEditorSnapshotPacket::encode,
                RoomEditorSnapshotPacket::decode,
                RoomEditorSnapshotPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                RoomEditorUpdatePacket.class,
                RoomEditorUpdatePacket::encode,
                RoomEditorUpdatePacket::decode,
                RoomEditorUpdatePacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                VehicleAdminEditorRequestPacket.class,
                VehicleAdminEditorRequestPacket::encode,
                VehicleAdminEditorRequestPacket::decode,
                VehicleAdminEditorRequestPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                VehicleAdminEditorSnapshotPacket.class,
                VehicleAdminEditorSnapshotPacket::encode,
                VehicleAdminEditorSnapshotPacket::decode,
                VehicleAdminEditorSnapshotPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                VehicleAdminEditorUpdatePacket.class,
                VehicleAdminEditorUpdatePacket::encode,
                VehicleAdminEditorUpdatePacket::decode,
                VehicleAdminEditorUpdatePacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                ClientboundScreenshotRequestPacket.class,
                ClientboundScreenshotRequestPacket::encode,
                ClientboundScreenshotRequestPacket::decode,
                ClientboundScreenshotRequestPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                ServerboundScreenshotChunkPacket.class,
                ServerboundScreenshotChunkPacket::encode,
                ServerboundScreenshotChunkPacket::decode,
                ServerboundScreenshotChunkPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                ServerboundScreenshotStatusPacket.class,
                ServerboundScreenshotStatusPacket::encode,
                ServerboundScreenshotStatusPacket::decode,
                ServerboundScreenshotStatusPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                ServerboundAmmoBoxInteractPacket.class,
                ServerboundAmmoBoxInteractPacket::encode,
                ServerboundAmmoBoxInteractPacket::decode,
                ServerboundAmmoBoxInteractPacket::handle
        );
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static SimpleChannel getChannel() {
        return CHANNEL;
    }
}
