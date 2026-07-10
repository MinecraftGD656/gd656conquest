package org.mods.gd656conquest.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.support.ClientSupportUiState;
import org.mods.gd656conquest.client.support.SupportScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SupportUiSnapshotPacket(
    String roomId,
    String roomDisplayName,
    String teamDisplayName,
    int currentScore,
    int spentScore,
    int remainingScore,
    String statusText,
    List<SupportEntryPayload> entries
) {
    public SupportUiSnapshotPacket {
        roomId = roomId == null ? "" : roomId;
        roomDisplayName = roomDisplayName == null ? "" : roomDisplayName;
        teamDisplayName = teamDisplayName == null ? "" : teamDisplayName;
        statusText = statusText == null ? "" : statusText;
        entries = entries == null ? List.of() : List.copyOf(entries);
        currentScore = Math.max(0, currentScore);
        spentScore = Math.max(0, spentScore);
        remainingScore = Math.max(0, remainingScore);
    }

    public static void encode(SupportUiSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.roomId);
        buffer.writeUtf(packet.roomDisplayName);
        buffer.writeUtf(packet.teamDisplayName);
        buffer.writeInt(packet.currentScore);
        buffer.writeInt(packet.spentScore);
        buffer.writeInt(packet.remainingScore);
        buffer.writeUtf(packet.statusText);
        buffer.writeInt(packet.entries.size());
        for (SupportEntryPayload entry : packet.entries) {
            buffer.writeUtf(entry.kind());
            buffer.writeUtf(entry.id());
            buffer.writeUtf(entry.displayName());
            buffer.writeUtf(entry.typeKey());
            buffer.writeUtf(entry.subtitleText());
            buffer.writeUtf(entry.detailText());
            buffer.writeUtf(entry.buttonText());
            buffer.writeInt(entry.cost());
            buffer.writeBoolean(entry.available());
        }
    }

    public static SupportUiSnapshotPacket decode(FriendlyByteBuf buffer) {
        String roomId = buffer.readUtf();
        String roomDisplayName = buffer.readUtf();
        String teamDisplayName = buffer.readUtf();
        int currentScore = buffer.readInt();
        int spentScore = buffer.readInt();
        int remainingScore = buffer.readInt();
        String statusText = buffer.readUtf();
        int count = Math.max(0, buffer.readInt());
        List<SupportEntryPayload> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new SupportEntryPayload(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readBoolean()
            ));
        }
        return new SupportUiSnapshotPacket(
            roomId,
            roomDisplayName,
            teamDisplayName,
            currentScore,
            spentScore,
            remainingScore,
            statusText,
            entries
        );
    }

    public static void handle(SupportUiSnapshotPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientSupportUiState.apply(message);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen instanceof SupportScreen supportScreen) {
                supportScreen.refreshFromState();
            }
        }));
        context.setPacketHandled(true);
    }

    public record SupportEntryPayload(
        String kind,
        String id,
        String displayName,
        String typeKey,
        String subtitleText,
        String detailText,
        String buttonText,
        int cost,
        boolean available
    ) {
        public SupportEntryPayload {
            kind = kind == null ? "" : kind;
            id = id == null ? "" : id;
            displayName = displayName == null ? "" : displayName;
            typeKey = typeKey == null ? "" : typeKey;
            subtitleText = subtitleText == null ? "" : subtitleText;
            detailText = detailText == null ? "" : detailText;
            buttonText = buttonText == null ? "" : buttonText;
            cost = Math.max(0, cost);
        }
    }
}
