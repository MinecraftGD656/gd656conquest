package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.server.facility.support.SupportActionService;

import java.util.function.Supplier;

public final class SupportUiActionPacket {
    private final String action;
    private final String targetId;

    public SupportUiActionPacket(String action, String targetId) {
        this.action = action == null ? "" : action;
        this.targetId = targetId == null ? "" : targetId;
    }

    public static SupportUiActionPacket deployHeroKit(String heroKitId) {
        return new SupportUiActionPacket("deploy_hero_kit", heroKitId);
    }

    public static SupportUiActionPacket startShelling(String shellingId) {
        return new SupportUiActionPacket("start_shelling", shellingId);
    }

    public static void encode(SupportUiActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.action);
        buffer.writeUtf(packet.targetId);
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
            SupportActionService.handleAction(sender, message.action, message.targetId);
        });
        context.setPacketHandled(true);
    }
}
