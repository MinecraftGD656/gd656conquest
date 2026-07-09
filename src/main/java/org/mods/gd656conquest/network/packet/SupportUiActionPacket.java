package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.support.HeroKitSupportService;

import java.util.function.Supplier;

public final class SupportUiActionPacket {
    private final String action;
    private final String heroKitId;

    public SupportUiActionPacket(String action, String heroKitId) {
        this.action = action == null ? "" : action;
        this.heroKitId = heroKitId == null ? "" : heroKitId;
    }

    public static SupportUiActionPacket deployHeroKit(String heroKitId) {
        return new SupportUiActionPacket("deploy_hero_kit", heroKitId);
    }

    public static void encode(SupportUiActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.heroKitId);
    }

    public static SupportUiActionPacket decode(FriendlyByteBuf buffer) {
        return new SupportUiActionPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(SupportUiActionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            HeroKitSupportService.handleAction(sender, message.action, message.heroKitId);
        });
        context.setPacketHandled(true);
    }
}
