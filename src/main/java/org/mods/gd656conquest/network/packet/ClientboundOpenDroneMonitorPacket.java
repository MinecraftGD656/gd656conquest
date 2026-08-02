package org.mods.gd656conquest.network.packet;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class ClientboundOpenDroneMonitorPacket {

    public ClientboundOpenDroneMonitorPacket() {
    }

    public static void encode(ClientboundOpenDroneMonitorPacket packet, FriendlyByteBuf buffer) {
    }

    public static ClientboundOpenDroneMonitorPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundOpenDroneMonitorPacket();
    }

    public static void handle(ClientboundOpenDroneMonitorPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options != null) {
                    ClientEventHandler.lastCameraType = mc.options.getCameraType();
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
