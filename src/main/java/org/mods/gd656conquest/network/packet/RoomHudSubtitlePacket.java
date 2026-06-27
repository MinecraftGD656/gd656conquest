package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.hud.ClientHudSubtitleManager;

import java.util.function.Supplier;

public final class RoomHudSubtitlePacket {
    private static final int ACTION_SHOW_ONCE = 0;
    private static final int ACTION_SET_PERSISTENT = 1;
    private static final int ACTION_CLEAR_PERSISTENT = 2;
    private static final int ACTION_RESET = 3;

    private final int action;
    private final String key;
    private final String text;

    public RoomHudSubtitlePacket(int action, String key, String text) {
        this.action = action;
        this.key = key == null ? "" : key;
        this.text = text == null ? "" : text;
    }

    public static RoomHudSubtitlePacket showOnce(String key, String text) {
        return new RoomHudSubtitlePacket(ACTION_SHOW_ONCE, key, text);
    }

    public static RoomHudSubtitlePacket setPersistent(String key, String text) {
        return new RoomHudSubtitlePacket(ACTION_SET_PERSISTENT, key, text);
    }

    public static RoomHudSubtitlePacket clearPersistent(String key) {
        return new RoomHudSubtitlePacket(ACTION_CLEAR_PERSISTENT, key, "");
    }

    public static RoomHudSubtitlePacket reset() {
        return new RoomHudSubtitlePacket(ACTION_RESET, "", "");
    }

    public static void encode(RoomHudSubtitlePacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.action);
        buffer.writeUtf(message.key);
        buffer.writeUtf(message.text);
    }

    public static RoomHudSubtitlePacket decode(FriendlyByteBuf buffer) {
        return new RoomHudSubtitlePacket(
            buffer.readInt(),
            buffer.readUtf(),
            buffer.readUtf()
        );
    }

    public static void handle(RoomHudSubtitlePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (message.action == ACTION_SHOW_ONCE) {
                ClientHudSubtitleManager.showOnce(message.key, message.text);
                return;
            }
            if (message.action == ACTION_SET_PERSISTENT) {
                ClientHudSubtitleManager.setPersistent(message.key, message.text);
                return;
            }
            if (message.action == ACTION_CLEAR_PERSISTENT) {
                ClientHudSubtitleManager.clearPersistent(message.key);
                return;
            }
            ClientHudSubtitleManager.reset();
        }));
        context.setPacketHandled(true);
    }
}
