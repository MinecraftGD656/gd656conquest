package org.mods.gd656conquest.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** Force streaming, skip decoded PCM on the IO worker, and bound playback to the authored clip. */
public final class TimelineSound extends AbstractTickableSoundInstance {
    private final double offsetSeconds, totalSeconds;
    private final long requestedAt = System.nanoTime();
    private int ticksLeft;
    private volatile boolean canceled;
    public TimelineSound(String id, float volume, double offsetSeconds, double durationSeconds) {
        super(SoundEvent.createVariableRangeEvent(new ResourceLocation(id)), SoundSource.VOICE, RandomSource.create(0));
        this.volume = volume; this.pitch = 1; this.relative = true; this.attenuation = Attenuation.NONE;
        this.offsetSeconds = Math.max(0,offsetSeconds); this.totalSeconds = Math.max(0,durationSeconds);
        ticksLeft = (int)Math.ceil(Math.max(0,durationSeconds-offsetSeconds)*20);
    }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        var events = super.resolve(manager);
        if (sound != null && sound != SoundManager.EMPTY_SOUND) sound = new Sound(sound.getLocation().toString(),
            sound.getVolume(),sound.getPitch(),sound.getWeight(),sound.getType(),true,false,sound.getAttenuationDistance());
        return events;
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        return library.getStream(sound.getPath(),false).thenApply(stream -> {
            try {
                double offset = Math.min(totalSeconds,offsetSeconds+Math.max(0,(System.nanoTime()-requestedAt)/1e9));
                var slice = new TimelineAudioSlice(stream,offset,totalSeconds);
                if (canceled) { slice.close(); throw new IOException("Timeline clip canceled"); }
                return slice;
            } catch (IOException error) {
                try { stream.close(); } catch (IOException ignored) {}
                throw new java.util.concurrent.CompletionException(error);
            }
        });
    }
    @Override public void tick() { if (--ticksLeft <= 0) { canceled=true;stop(); } }
    public void cancel() { canceled=true;stop(); Minecraft.getInstance().getSoundManager().stop(this); }
}
