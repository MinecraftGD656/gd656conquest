package org.mods.gd656conquest.client.event.handler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** One requested map version owns its incoming chunks until complete or reset. */
final class CaptureBakeTransfer {
    record Key(String room,int version,String hash) {}
    static final int MAX_BYTES=64*1024*1024,MAX_BATCHES=4096,MAX_CHUNK_BYTES=65536;
    private Key key;
    private int batchCount,totalBytes;
    private final Map<Integer,byte[]> chunks=new HashMap<>();
    void begin(Key next){clear();key=next;}
    Key key(){return key;}
    void clear(){key=null;batchCount=0;totalBytes=0;chunks.clear();}
    byte[] accept(Key incoming,int index,int count,byte[] bytes){
        if(key==null||!key.equals(incoming))return null;
        if(count<1||count>MAX_BATCHES||index<0||index>=count||bytes==null||bytes.length>MAX_CHUNK_BYTES)throw new IllegalArgumentException("Invalid capture bake chunk bounds");
        if(batchCount!=0&&batchCount!=count)throw new IllegalArgumentException("Capture bake batch count changed");
        batchCount=count;
        byte[] previous=chunks.get(index);
        if(previous!=null){if(!Arrays.equals(previous,bytes))throw new IllegalArgumentException("Capture bake duplicate chunk differs");return null;}
        if((long)totalBytes+bytes.length>MAX_BYTES)throw new IllegalArgumentException("Capture bake exceeds 64 MiB");
        chunks.put(index,bytes.clone());totalBytes+=bytes.length;
        if(chunks.size()!=batchCount)return null;
        byte[] result=new byte[totalBytes];int cursor=0;
        for(int i=0;i<batchCount;i++){byte[] chunk=chunks.get(i);System.arraycopy(chunk,0,result,cursor,chunk.length);cursor+=chunk.length;}
        clear();return result;
    }
}
