package org.mods.gd656conquest.network.packet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import org.mods.gd656conquest.client.room.ClientSuppressionState;
public record SuppressionPacket(boolean active,float opacity) {
    public SuppressionPacket {opacity=Float.isFinite(opacity)?Math.max(0,Math.min(.6F,opacity)):.25F;}
    public static void encode(SuppressionPacket p,FriendlyByteBuf b){b.writeBoolean(p.active);b.writeFloat(p.opacity);}
    public static SuppressionPacket decode(FriendlyByteBuf b){return new SuppressionPacket(b.readBoolean(),b.readFloat());}
    public static void handle(SuppressionPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();
        if(ctx.getDirection()==NetworkDirection.PLAY_TO_CLIENT)
            ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientSuppressionState.accept(p)));
        ctx.setPacketHandled(true);
    }
}
