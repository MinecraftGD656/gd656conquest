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
    List<HeroKitPayload> heroKits
) {
    public SupportUiSnapshotPacket {
        roomId = roomId == null ? "" : roomId;
        roomDisplayName = roomDisplayName == null ? "" : roomDisplayName;
        teamDisplayName = teamDisplayName == null ? "" : teamDisplayName;
        statusText = statusText == null ? "" : statusText;
        heroKits = heroKits == null ? List.of() : List.copyOf(heroKits);
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
        buffer.writeInt(packet.heroKits.size());
        for (HeroKitPayload heroKit : packet.heroKits) {
            buffer.writeUtf(heroKit.id());
            buffer.writeUtf(heroKit.displayName());
            buffer.writeUtf(heroKit.type());
            buffer.writeInt(heroKit.cost());
            buffer.writeBoolean(heroKit.unlocked());
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
        List<HeroKitPayload> heroKits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            heroKits.add(new HeroKitPayload(
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
            heroKits
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

    public record HeroKitPayload(
        String id,
        String displayName,
        String type,
        int cost,
        boolean unlocked
    ) {
    }
}
