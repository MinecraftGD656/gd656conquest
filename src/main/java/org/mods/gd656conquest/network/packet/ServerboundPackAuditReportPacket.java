package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.pack.PackAuditEntry;
import org.mods.gd656conquest.server.pack.PackValidationService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ServerboundPackAuditReportPacket {
    private final String requestId;
    private final String reason;
    private final boolean success;
    private final String message;
    private final List<PackAuditEntry> entries;

    public ServerboundPackAuditReportPacket(String requestId, String reason, boolean success, String message, List<PackAuditEntry> entries) {
        this.requestId = requestId == null ? "" : requestId;
        this.reason = reason == null ? "" : reason;
        this.success = success;
        this.message = message == null ? "" : message;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void encode(ServerboundPackAuditReportPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.requestId);
        buffer.writeUtf(packet.reason);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message);
        buffer.writeInt(packet.entries.size());
        for (PackAuditEntry entry : packet.entries) {
            buffer.writeUtf(entry == null ? "" : entry.getType());
            buffer.writeUtf(entry == null ? "" : entry.getFileName());
            buffer.writeUtf(entry == null ? "" : entry.getSha256());
            buffer.writeLong(entry == null ? 0L : entry.getSize());
            buffer.writeUtf(entry == null ? "" : entry.getSourceKind());
        }
    }

    public static ServerboundPackAuditReportPacket decode(FriendlyByteBuf buffer) {
        String requestId = buffer.readUtf();
        String reason = buffer.readUtf();
        boolean success = buffer.readBoolean();
        String message = buffer.readUtf();
        int size = Math.max(0, buffer.readInt());
        List<PackAuditEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new PackAuditEntry(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readUtf()
            ));
        }
        return new ServerboundPackAuditReportPacket(requestId, reason, success, message, entries);
    }

    public static void handle(ServerboundPackAuditReportPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && sender.server != null) {
            context.enqueueWork(() -> PackValidationService.of(sender.server).handleClientAuditReport(
                sender,
                packet.requestId,
                packet.reason,
                packet.success,
                packet.message,
                packet.entries
            ));
        }
        context.setPacketHandled(true);
    }
}
