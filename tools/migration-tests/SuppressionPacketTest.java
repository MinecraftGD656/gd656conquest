package tests;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.mods.gd656conquest.network.packet.SuppressionPacket;

/** Real native buffers and shipped wire codec, including malformed float inputs. */
public final class SuppressionPacketTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        for(boolean active:new boolean[]{false,true})for(float opacity:new float[]{-.5F,0,.125F,.25F,.6F,1,Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY}){
            var packet=new SuppressionPacket(active,opacity);var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{
                SuppressionPacket.encode(packet,buffer);check(buffer.readableBytes()==5,"boolean plus float payload");
                check(packet.equals(SuppressionPacket.decode(buffer)),"actual packet roundtrip");check(buffer.readableBytes()==0,"payload fully consumed");
                check(packet.opacity()==(Float.isFinite(opacity)?Math.max(0,Math.min(.6F,opacity)):.25F),"bounded or fallback opacity");
            }finally{buffer.release();}
        }
        var buffer=new FriendlyByteBuf(Unpooled.buffer());try{buffer.writeBoolean(true);buffer.writeFloat(Float.NaN);check(SuppressionPacket.decode(buffer).opacity()==.25F,"malformed wire opacity normalized");}finally{buffer.release();}
        System.out.println("PASS actual suppression S2C packet codec: "+checks);
    }
}
