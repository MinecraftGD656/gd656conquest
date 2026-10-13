package org.mods.gd656conquest.client.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Resumable streaming BGM. Fade and playback clocks start after decoding the initial offset. */
final class OperationMusicSound extends AbstractTickableSoundInstance {
    private final double offset, duration;
    private volatile boolean ready, canceled;
    private long playedTicks;
    private int fadeInTicks, fadeInElapsed, fadeOutTicks, fadeOutElapsed;
    private float fadeOutStart, duck = 1, resolvedPitch = 1;
    private OperationMusicSound incoming;

    OperationMusicSound(String id, boolean loop, double offset, double duration, int fadeTicks) {
        super(SoundEvent.createVariableRangeEvent(ResourceLocation.parse(id)), SoundSource.MUSIC, RandomSource.create(0));
        this.offset = Math.max(0, offset); this.duration = duration;
        looping = loop; relative = true; attenuation = Attenuation.NONE; pitch = 1;
        fadeInTicks = Math.max(0, fadeTicks); volume = fadeInTicks > 0 ? 0 : 1;
    }
    @Override public boolean canStartSilent() { return true; }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        random.setSeed(0);
        var events = super.resolve(manager);
        if (sound != null && sound != SoundManager.EMPTY_SOUND && sound != SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            sound = new Sound(sound.getLocation().toString(), sound.getVolume(), sound.getPitch(), sound.getWeight(),
                sound.getType(), true, false, sound.getAttenuationDistance());
        }
        resolvedPitch = net.minecraft.util.Mth.clamp(super.getPitch(), .5F, 2F);
        return events;
    }
    @Override public float getPitch() { return resolvedPitch; }
    @Override public float getVolume() { return super.getVolume() * duck; }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound definition, boolean ignored) {
        return library.getStream(definition.getPath(), looping).thenApplyAsync(stream -> {
            try {
                var sliced = new OffsetStream(stream, offset * resolvedPitch);
                if (canceled) { sliced.close(); throw new IOException("Operation music canceled"); }
                ready = true;
                return sliced;
            } catch (IOException error) {
                try { stream.close(); } catch (IOException ignoredClose) {}
                throw new java.util.concurrent.CompletionException(error);
            }
        }, Util.backgroundExecutor());
    }
    @Override public void tick() {
        if (!ready) return;
        playedTicks++;
        if (fadeOutTicks > 0) {
            if (incoming != null && !incoming.ready && !incoming.canceled) return;
            volume = fadeOutStart * Math.max(0, 1 - ++fadeOutElapsed / (float) fadeOutTicks);
            if (fadeOutElapsed >= fadeOutTicks) cancel();
        } else if (fadeInElapsed < fadeInTicks) volume = ++fadeInElapsed / (float) fadeInTicks;
    }
    void fadeOut(int ticks) {
        if (ticks <= 0 || !ready) { cancel(); return; }
        fadeOutStart = volume; fadeOutTicks = ticks; fadeOutElapsed = 0;
    }
    void duck(float multiplier) { duck = multiplier; }
    void waitFor(OperationMusicSound next) { incoming = next; }
    void cancel() { canceled = true; stop(); }
    double position() {
        double seconds = offset + playedTicks / 20.0;
        return looping && duration > 0 ? seconds % duration : seconds;
    }

    /** Skip decoded PCM only on an IO worker; retain overshoot and let the upstream stream loop normally. */
    private static final class OffsetStream implements AudioStream {
        private final AudioStream source;
        private ByteBuffer pending;
        OffsetStream(AudioStream source, double seconds) throws IOException {
            this.source = source;
            var format = source.getFormat();
            int frame = format.getFrameSize();
            if (frame <= 0 || frame > 65536 || !Float.isFinite(format.getFrameRate()) || format.getFrameRate() <= 0)
                throw new IOException("Invalid operation music format");
            long skip = (long)(seconds * format.getFrameRate()) * frame;
            while (skip > 0) {
                var data = source.read((int)Math.min(skip, 65536 / frame * frame));
                if (!data.hasRemaining()) break;
                if (data.remaining() > skip) { data.position(data.position() + (int)skip); pending = data; break; }
                skip -= data.remaining();
            }
        }
        @Override public javax.sound.sampled.AudioFormat getFormat() { return source.getFormat(); }
        @Override public ByteBuffer read(int bytes) throws IOException {
            if (pending != null && pending.hasRemaining()) {
                int count = Math.min(bytes, pending.remaining());
                var result = pending.slice(); result.limit(count); pending.position(pending.position() + count);
                return result;
            }
            return source.read(bytes);
        }
        @Override public void close() throws IOException { source.close(); }
    }
}
