package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.config.ClientPacketBypassConfig;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class LoadoutUiOpenPacket {
    private final int playerLevel;
    private final String playerTitleText;
    private final String currentClassKey;
    private final boolean openPanel;
    private final boolean admin;
    private final boolean vehiclePageAvailable;
    private final List<ClassPayload> classes;
    private final List<AdminEquipmentPayload> adminEquipment;
    private final List<AdminAttachmentPayload> adminAttachments;
    private final List<WeaponAttachmentPanelPayload> attachmentPanels;
    private final List<VehicleEntryPayload> vehicleEntries;

    public LoadoutUiOpenPacket(
        int playerLevel,
        String playerTitleText,
        String currentClassKey,
        boolean openPanel,
        boolean admin,
        boolean vehiclePageAvailable,
        List<ClassPayload> classes,
        List<AdminEquipmentPayload> adminEquipment,
        List<AdminAttachmentPayload> adminAttachments,
        List<WeaponAttachmentPanelPayload> attachmentPanels,
        List<VehicleEntryPayload> vehicleEntries
    ) {
        this.playerLevel = playerLevel;
        this.playerTitleText = playerTitleText == null ? "" : playerTitleText;
        this.currentClassKey = currentClassKey == null ? "" : currentClassKey;
        this.openPanel = openPanel;
        this.admin = admin;
        this.vehiclePageAvailable = vehiclePageAvailable;
        this.classes = classes == null ? List.of() : List.copyOf(classes);
        this.adminEquipment = adminEquipment == null ? List.of() : List.copyOf(adminEquipment);
        this.adminAttachments = adminAttachments == null ? List.of() : List.copyOf(adminAttachments);
        this.attachmentPanels = attachmentPanels == null ? List.of() : List.copyOf(attachmentPanels);
        this.vehicleEntries = vehicleEntries == null ? List.of() : List.copyOf(vehicleEntries);
    }

    public int getPlayerLevel() {
        return playerLevel;
    }

    public String getPlayerTitleText() {
        return playerTitleText;
    }

    public String getCurrentClassKey() {
        return currentClassKey;
    }

    public boolean isOpenPanel() {
        return openPanel;
    }

    public boolean isAdmin() {
        return admin;
    }

    public boolean isVehiclePageAvailable() {
        return vehiclePageAvailable;
    }

    public List<ClassPayload> getClasses() {
        return classes;
    }

    public List<AdminEquipmentPayload> getAdminEquipment() {
        return adminEquipment;
    }

    public List<AdminAttachmentPayload> getAdminAttachments() {
        return adminAttachments;
    }

    public List<WeaponAttachmentPanelPayload> getAttachmentPanels() {
        return attachmentPanels;
    }

    public List<VehicleEntryPayload> getVehicleEntries() {
        return vehicleEntries;
    }

    public static void encode(LoadoutUiOpenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.playerLevel);
        buffer.writeUtf(packet.playerTitleText);
        buffer.writeUtf(packet.currentClassKey);
        buffer.writeBoolean(packet.openPanel);
        buffer.writeBoolean(packet.admin);
        buffer.writeBoolean(packet.vehiclePageAvailable);
        buffer.writeInt(packet.classes.size());
        for (ClassPayload classPayload : packet.classes) {
            buffer.writeUtf(classPayload.classKey());
            buffer.writeUtf(classPayload.className());
            buffer.writeInt(classPayload.slots().size());
            for (SlotPayload slotPayload : classPayload.slots()) {
                buffer.writeInt(slotPayload.slotIndex());
                buffer.writeUtf(slotPayload.slotKey());
                buffer.writeUtf(slotPayload.slotLabel());
                buffer.writeUtf(slotPayload.selectedId());
                buffer.writeInt(slotPayload.entries().size());
                for (EntryPayload entry : slotPayload.entries()) {
                    buffer.writeUtf(entry.id());
                    buffer.writeUtf(entry.displayName());
                    buffer.writeInt(entry.unlockLevel());
                    buffer.writeBoolean(entry.unlocked());
                    buffer.writeUtf(entry.itemSnbt());
                }
            }
        }
        buffer.writeInt(packet.adminEquipment.size());
        for (AdminEquipmentPayload equipment : packet.adminEquipment) {
            buffer.writeUtf(equipment.id());
            buffer.writeInt(equipment.slot());
            buffer.writeUtf(equipment.slotName());
            buffer.writeUtf(equipment.displayName());
            buffer.writeInt(equipment.extraData());
            buffer.writeInt(equipment.unlockLevel());
            buffer.writeInt(equipment.availableClasses());
            buffer.writeUtf(equipment.itemSnbt());
            buffer.writeInt(equipment.replenishSeconds());
            buffer.writeBoolean(equipment.ammoBoxRefillEnabled());
        }
        buffer.writeInt(packet.adminAttachments.size());
        for (AdminAttachmentPayload attachment : packet.adminAttachments) {
            buffer.writeUtf(attachment.id());
            buffer.writeUtf(attachment.displayName());
            buffer.writeUtf(attachment.attachmentType());
            buffer.writeInt(attachment.unlockLevel());
            buffer.writeUtf(attachment.itemSnbt());
            buffer.writeUtf(attachment.futureRequirement());
        }
        buffer.writeInt(packet.attachmentPanels.size());
        for (WeaponAttachmentPanelPayload panel : packet.attachmentPanels) {
            buffer.writeInt(panel.slotIndex());
            buffer.writeUtf(panel.slotKey());
            buffer.writeUtf(panel.slotLabel());
            buffer.writeUtf(panel.loadoutEntryId());
            buffer.writeUtf(panel.weaponDisplayName());
            buffer.writeUtf(panel.weaponItemSnbt());
            buffer.writeBoolean(panel.available());
            buffer.writeBoolean(panel.taczGun());
            buffer.writeInt(panel.attachmentTypes().size());
            for (AttachmentTypePayload typePayload : panel.attachmentTypes()) {
                buffer.writeUtf(typePayload.attachmentType());
                buffer.writeUtf(typePayload.attachmentLabel());
                buffer.writeBoolean(typePayload.available());
                buffer.writeBoolean(typePayload.hasStoredSelection());
                buffer.writeUtf(typePayload.selectedAttachmentId());
                buffer.writeUtf(typePayload.selectedDisplayName());
                buffer.writeDouble(typePayload.maxScopeMagnification());
                buffer.writeInt(typePayload.choices().size());
                for (AttachmentChoicePayload choice : typePayload.choices()) {
                    buffer.writeUtf(choice.id());
                    buffer.writeUtf(choice.displayName());
                    buffer.writeInt(choice.unlockLevel());
                    buffer.writeBoolean(choice.unlocked());
                    buffer.writeUtf(choice.itemSnbt());
                }
            }
        }
        buffer.writeInt(packet.vehicleEntries.size());
        for (VehicleEntryPayload entry : packet.vehicleEntries) {
            buffer.writeUtf(entry.entryId());
            buffer.writeUtf(entry.displayName());
            buffer.writeUtf(entry.stateToken());
            buffer.writeUtf(entry.statusText());
            buffer.writeUtf(entry.actionText());
            buffer.writeBoolean(entry.actionable());
            buffer.writeBoolean(entry.selected());
            buffer.writeBoolean(entry.waitingForReady());
            buffer.writeBoolean(entry.immediateDeploy());
            buffer.writeBoolean(entry.lowHealth());
            buffer.writeInt(entry.readyRemainSeconds());
            buffer.writeInt(entry.healthPercent());
            buffer.writeInt(entry.healthCurrent());
            buffer.writeInt(entry.healthMax());
            buffer.writeInt(entry.occupiedSeatCount());
            buffer.writeInt(entry.seatCount());
            buffer.writeUtf(entry.detailText());
        }
    }

    public static LoadoutUiOpenPacket decode(FriendlyByteBuf buffer) {
        int playerLevel = buffer.readInt();
        String playerTitleText = buffer.readUtf();
        String currentClassKey = buffer.readUtf();
        boolean openPanel = buffer.readBoolean();
        boolean admin = buffer.readBoolean();
        boolean vehiclePageAvailable = buffer.readBoolean();
        int classCount = Math.max(0, buffer.readInt());
        List<ClassPayload> classes = new ArrayList<>();
        for (int classIndex = 0; classIndex < classCount; classIndex++) {
            String classKey = buffer.readUtf();
            String className = buffer.readUtf();
            int slotCount = Math.max(0, buffer.readInt());
            List<SlotPayload> slots = new ArrayList<>();
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                int index = buffer.readInt();
                String slotKey = buffer.readUtf();
                String slotLabel = buffer.readUtf();
                String selectedId = buffer.readUtf();
                int entryCount = Math.max(0, buffer.readInt());
                List<EntryPayload> entries = new ArrayList<>();
                for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                    entries.add(new EntryPayload(
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readInt(),
                        buffer.readBoolean(),
                        buffer.readUtf()
                    ));
                }
                slots.add(new SlotPayload(index, slotKey, slotLabel, selectedId, entries));
            }
            classes.add(new ClassPayload(classKey, className, slots));
        }
        int adminEquipmentCount = Math.max(0, buffer.readInt());
        List<AdminEquipmentPayload> adminEquipment = new ArrayList<>();
        for (int i = 0; i < adminEquipmentCount; i++) {
            adminEquipment.add(new AdminEquipmentPayload(
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readUtf(),
                Math.max(0, buffer.readInt()),
                buffer.readBoolean()
            ));
        }
        int adminAttachmentCount = Math.max(0, buffer.readInt());
        List<AdminAttachmentPayload> adminAttachments = new ArrayList<>();
        for (int i = 0; i < adminAttachmentCount; i++) {
            adminAttachments.add(new AdminAttachmentPayload(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readUtf()
            ));
        }
        int attachmentPanelCount = Math.max(0, buffer.readInt());
        List<WeaponAttachmentPanelPayload> attachmentPanels = new ArrayList<>();
        for (int panelIndex = 0; panelIndex < attachmentPanelCount; panelIndex++) {
            int slotIndex = buffer.readInt();
            String slotKey = buffer.readUtf();
            String slotLabel = buffer.readUtf();
            String loadoutEntryId = buffer.readUtf();
            String weaponDisplayName = buffer.readUtf();
            String weaponItemSnbt = buffer.readUtf();
            boolean available = buffer.readBoolean();
            boolean taczGun = buffer.readBoolean();
            int typeCount = Math.max(0, buffer.readInt());
            List<AttachmentTypePayload> attachmentTypes = new ArrayList<>();
            for (int typeIndex = 0; typeIndex < typeCount; typeIndex++) {
                String attachmentType = buffer.readUtf();
                String attachmentLabel = buffer.readUtf();
                boolean typeAvailable = buffer.readBoolean();
                boolean hasStoredSelection = buffer.readBoolean();
                String selectedAttachmentId = buffer.readUtf();
                String selectedDisplayName = buffer.readUtf();
                double maxScopeMagnification = buffer.readDouble();
                int choiceCount = Math.max(0, buffer.readInt());
                List<AttachmentChoicePayload> choices = new ArrayList<>();
                for (int choiceIndex = 0; choiceIndex < choiceCount; choiceIndex++) {
                    choices.add(new AttachmentChoicePayload(
                        buffer.readUtf(),
                        buffer.readUtf(),
                        buffer.readInt(),
                        buffer.readBoolean(),
                        buffer.readUtf()
                    ));
                }
                attachmentTypes.add(new AttachmentTypePayload(
                    attachmentType,
                    attachmentLabel,
                    typeAvailable,
                    hasStoredSelection,
                    selectedAttachmentId,
                    selectedDisplayName,
                    maxScopeMagnification,
                    choices
                ));
            }
            attachmentPanels.add(new WeaponAttachmentPanelPayload(
                slotIndex,
                slotKey,
                slotLabel,
                loadoutEntryId,
                weaponDisplayName,
                weaponItemSnbt,
                available,
                taczGun,
                attachmentTypes
            ));
        }
        int vehicleEntryCount = Math.max(0, buffer.readInt());
        List<VehicleEntryPayload> vehicleEntries = new ArrayList<>();
        for (int i = 0; i < vehicleEntryCount; i++) {
            vehicleEntries.add(new VehicleEntryPayload(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readUtf()
            ));
        }
        return new LoadoutUiOpenPacket(
            playerLevel,
            playerTitleText,
            currentClassKey,
            openPanel,
            admin,
            vehiclePageAvailable,
            classes,
            adminEquipment,
            adminAttachments,
            attachmentPanels,
            vehicleEntries
        );
    }

    public static void handle(LoadoutUiOpenPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (ClientPacketBypassConfig.bypassLoadoutUiPackets()) {
                ClientLoadoutUiState.clear();
                return;
            }
            ClientLoadoutUiState.apply(message);
        }));
        context.setPacketHandled(true);
    }

    public record ClassPayload(String classKey, String className, List<SlotPayload> slots) {
    }

    public record SlotPayload(int slotIndex, String slotKey, String slotLabel, String selectedId, List<EntryPayload> entries) {
    }

    public record EntryPayload(String id, String displayName, int unlockLevel, boolean unlocked, String itemSnbt) {
    }

    public record AdminEquipmentPayload(
        String id,
        int slot,
        String slotName,
        String displayName,
        int extraData,
        int unlockLevel,
        int availableClasses,
        String itemSnbt,
        int replenishSeconds,
        boolean ammoBoxRefillEnabled
    ) {
    }

    public record AdminAttachmentPayload(
        String id,
        String displayName,
        String attachmentType,
        int unlockLevel,
        String itemSnbt,
        String futureRequirement
    ) {
    }

    public record WeaponAttachmentPanelPayload(
        int slotIndex,
        String slotKey,
        String slotLabel,
        String loadoutEntryId,
        String weaponDisplayName,
        String weaponItemSnbt,
        boolean available,
        boolean taczGun,
        List<AttachmentTypePayload> attachmentTypes
    ) {
    }

    public record AttachmentTypePayload(
        String attachmentType,
        String attachmentLabel,
        boolean available,
        boolean hasStoredSelection,
        String selectedAttachmentId,
        String selectedDisplayName,
        double maxScopeMagnification,
        List<AttachmentChoicePayload> choices
    ) {
    }

    public record AttachmentChoicePayload(
        String id,
        String displayName,
        int unlockLevel,
        boolean unlocked,
        String itemSnbt
    ) {
    }

    public record VehicleEntryPayload(
        String entryId,
        String displayName,
        String stateToken,
        String statusText,
        String actionText,
        boolean actionable,
        boolean selected,
        boolean waitingForReady,
        boolean immediateDeploy,
        boolean lowHealth,
        int readyRemainSeconds,
        int healthPercent,
        int healthCurrent,
        int healthMax,
        int occupiedSeatCount,
        int seatCount,
        String detailText
    ) {
    }
}
