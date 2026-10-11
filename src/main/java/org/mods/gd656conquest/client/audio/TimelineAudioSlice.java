package org.mods.gd656conquest.client.audio;
import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/** PCM slicing retains decoder overshoot, so seeking never loses the first resumed frame. */
public final class TimelineAudioSlice implements AudioStream {
    private final AudioStream source;
    private long remaining;
    private ByteBuffer pending;
    public TimelineAudioSlice(AudioStream source, double offset, double duration) throws IOException {
        this.source=source; AudioFormat format=source.getFormat(); int frameSize=format.getFrameSize();
        if(frameSize<=0||frameSize>65536||!Float.isFinite(format.getFrameRate())||format.getFrameRate()<=0
            || !Double.isFinite(offset)||!Double.isFinite(duration)||offset<0||duration<0)throw new IOException("Invalid PCM slice");
        long skip=(long)(offset*format.getFrameRate())*frameSize;
        remaining=Math.max(0,(long)(duration*format.getFrameRate())*frameSize-skip);
        while(skip>0){
            ByteBuffer data=source.read((int)Math.min(skip,65536/frameSize*frameSize));
            if(!data.hasRemaining()){remaining=0;break;}
            if(data.remaining()>skip){data.position(data.position()+(int)skip);pending=data;skip=0;}
            else skip-=data.remaining();
        }
    }
    @Override public AudioFormat getFormat(){return source.getFormat();}
    @Override public ByteBuffer read(int bytes) throws IOException {
        if(remaining<=0||bytes<=0)return ByteBuffer.allocate(0);
        if(pending==null||!pending.hasRemaining())pending=source.read((int)Math.min(remaining,bytes));
        int size=(int)Math.min(Math.min(remaining,bytes),pending.remaining());
        ByteBuffer result=pending.slice();result.limit(size);pending.position(pending.position()+size);remaining-=size;
        return result;
    }
    @Override public void close() throws IOException {pending=null;source.close();}
}
