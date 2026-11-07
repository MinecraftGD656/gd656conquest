package org.mods.gd656conquest.client.event.handler;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.mods.gd656conquest.client.log.ClientLogger;
import org.mods.gd656conquest.common.capture.CaptureBoundaryBakeFile;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.CaptureBoundaryBakeRequestPacket;
import org.mods.gd656conquest.server.capture.CaptureBoundaryBakeService;
import org.mods.gd656conquest.server.shared.text.ServerText;

/** Native protocol, with atomic map-version changes and bounded out-of-order assembly. */
public final class ClientCaptureBakeState {
    private static String activeRoomId="",failedRequestKey="";
    private static final CaptureBakeTransfer transfer=new CaptureBakeTransfer();
    private static CaptureBakeTransfer.Key applied;
    private ClientCaptureBakeState() {}
    public static void handleMeta(String roomId,int version,String hash,boolean available){
        String room=CaptureBoundaryBakeService.canonicalRoomId(roomId);
        if(!room.matches("[a-z0-9_-]{1,80}"))return;
        if(!room.equals(activeRoomId)){reset();activeRoomId=room;ClientEventHandler.getInstance().applyCaptureBake(room,Map.of());}
        if(!available){transfer.clear();applied=null;CaptureRegionOverlay.clear();ClientEventHandler.getInstance().applyCaptureBake(room,Map.of());return;}
        if(version<1||hash==null||!hash.matches("(?i)[a-f0-9]{64}")){ClientLogger.warning("invalid capture bake metadata: room=%s version=%d",room,version);return;}
        var key=new CaptureBakeTransfer.Key(room,version,hash.toLowerCase(Locale.ROOT));
        if(key.equals(applied)||key.equals(transfer.key()))return;
        transfer.clear();applied=null;CaptureRegionOverlay.clear();ClientEventHandler.getInstance().applyCaptureBake(room,Map.of());
        Path path=resolveLocalBakeFile(room);
        try{
            if(Files.exists(path)&&Files.size(path)<=CaptureBakeTransfer.MAX_BYTES){
                var file=readBounded(Files.readAllBytes(path));
                if(matches(file,key)){apply(file,key);return;}
            }
        }catch(IOException|IllegalArgumentException|IllegalStateException e){ClientLogger.warning("capture bake cache invalid: room=%s reason=%s",room,e.getMessage());}
        if(key.toString().equals(failedRequestKey))return;
        transfer.begin(key);NetworkManager.getChannel().sendToServer(new CaptureBoundaryBakeRequestPacket(room));
    }
    public static void handleChunk(String roomId,int version,String hash,int index,int count,byte[] payload){
        String room=CaptureBoundaryBakeService.canonicalRoomId(roomId);
        var key=new CaptureBakeTransfer.Key(room,version,hash==null?"":hash.toLowerCase(Locale.ROOT));
        if(!room.equals(activeRoomId)||!key.equals(transfer.key()))return;
        try{
            byte[] bytes=transfer.accept(key,index,count,payload);if(bytes==null)return;
            var file=readBounded(bytes);
            if(!matches(file,key))throw new IllegalArgumentException("Bake payload identity differs from server metadata");
            CaptureBoundaryBakeFile.writeAtomically(resolveLocalBakeFile(room),file);apply(file,key);failedRequestKey="";
        }catch(IOException|IllegalArgumentException|IllegalStateException e){
            transfer.clear();failedRequestKey=key.toString();ClientLogger.failure("capture bake rejected: room=%s version=%d reason=%s",room,version,e.getMessage());
            notifyFailure("据点范围数据校验失败，请重新进入房间获取。");
        }
    }
    private static boolean matches(CaptureBoundaryBakeFile file,CaptureBakeTransfer.Key key){return file.roomId().equals(key.room())&&file.dataVersion()==key.version()&&file.payloadHash().equals(key.hash());}
    private static void apply(CaptureBoundaryBakeFile file,CaptureBakeTransfer.Key key){
        var blocks=file.toBlockMap();CaptureRegionOverlay.replace(blocks);ClientEventHandler.getInstance().applyCaptureBake(key.room(),blocks);applied=key;transfer.clear();
    }
    /** Validate allocation sizes and geometry before delegating hashing and decoding to the original codec. */
    static CaptureBoundaryBakeFile readBounded(byte[] bytes)throws IOException{
        if(bytes.length>CaptureBakeTransfer.MAX_BYTES)throw new IllegalArgumentException("Capture bake too large");
        var input=new DataInputStream(new ByteArrayInputStream(bytes));
        if(input.readInt()!=0x47444342||input.readInt()!=1)throw new IllegalArgumentException("Unsupported capture bake format");
        input.readUTF();input.readInt();input.readUTF();int total=input.readInt(),counts=input.readInt();
        if(total<0||total>1_000_000||counts<0||counts>64)throw new IllegalArgumentException("Invalid capture bake block/count limit");
        for(int i=0;i<counts;i++){input.readUTF();if(input.readInt()<0)throw new IllegalArgumentException("Negative capture count");}
        int chunks=input.readInt();if(chunks<0||chunks>Math.min(200_000,input.available()/12))throw new IllegalArgumentException("Invalid capture chunk count");
        long actual=0;
        for(int i=0;i<chunks;i++){
            input.readInt();input.readInt();int blocks=input.readInt();
            if(blocks<0||blocks>input.available()/5||(actual+=blocks)>total)throw new IllegalArgumentException("Invalid capture block count");
            for(int n=0;n<blocks;n++){int x=input.readUnsignedByte(),y=input.readShort(),z=input.readUnsignedByte(),code=input.readUnsignedByte();
                if(x>15||z>15||CaptureBoundaryBakeFile.captureCodeFromId(code).isEmpty())throw new IllegalArgumentException("Invalid capture block coordinates/code");}
        }
        if(actual!=total||input.available()!=0)throw new IllegalArgumentException("Capture bake payload length differs");
        return CaptureBoundaryBakeFile.read(bytes);
    }
    public static void handleFailure(String roomId,String message){
        String room=CaptureBoundaryBakeService.canonicalRoomId(roomId);if(!room.equals(activeRoomId))return;
        var key=transfer.key();failedRequestKey=key==null?"":key.toString();transfer.clear();notifyFailure(message);
    }
    private static void notifyFailure(String message){var mc=Minecraft.getInstance();if(mc.player!=null&&message!=null&&!message.isBlank())mc.player.sendSystemMessage(ServerText.prefixed(null,message,ChatFormatting.RED));}
    public static void setDebugScanEnabled(boolean enabled){ClientEventHandler.getInstance().setCaptureDebugScanEnabled(enabled);}
    public static void reset(){activeRoomId="";failedRequestKey="";transfer.clear();applied=null;CaptureRegionOverlay.clear();}
    private static Path resolveLocalBakeFile(String room){return FMLPaths.GAMEDIR.get().resolve("gd656conquest/bake").resolve(org.mods.gd656conquest.server.data.ServerDataPaths.toBakeFileName(room));}
}
