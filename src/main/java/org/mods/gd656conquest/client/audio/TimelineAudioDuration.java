package org.mods.gd656conquest.client.audio;

import com.mojang.blaze3d.audio.OggAudioStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundManager;

/** Decode in bounded chunks off the render thread; use the same event variant/pitch as playback. */
public final class TimelineAudioDuration {
    private TimelineAudioDuration() {}

    public static CompletableFuture<Integer> readTicks(String eventId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            var sound = new TimelineSound(eventId, 1, 0, 1);
            sound.resolve(mc.getSoundManager());
            if (sound.getSound() == SoundManager.EMPTY_SOUND || sound.getSound() == SoundManager.INTENTIONALLY_EMPTY_SOUND)
                throw new IOException("声音事件没有可播放的音频");
            var resource = mc.getResourceManager().getResource(sound.getSound().getPath())
                .orElseThrow(() -> new IOException("声音文件不存在"));
            float pitch = sound.getPitch();
            return CompletableFuture.supplyAsync(() -> {
                try (var input = resource.open(); var stream = new OggAudioStream(input)) {
                    return measureTicks(stream, pitch);
                } catch (IOException error) { throw new CompletionException(error); }
            }, Util.backgroundExecutor());
        } catch (Exception error) { return CompletableFuture.failedFuture(error); }
    }

    public static int measureTicks(AudioStream stream, float pitch) throws IOException {
        var format = stream.getFormat();
        int frameSize = format.getFrameSize();
        float rate = format.getFrameRate();
        if (frameSize <= 0 || !Float.isFinite(rate) || rate <= 0 || !Float.isFinite(pitch) || pitch <= 0)
            throw new IOException("声音格式或播放速度无效");
        long bytes = 0;
        while (true) {
            var buffer = stream.read(65536);
            if (!buffer.hasRemaining()) break;
            bytes = Math.addExact(bytes, buffer.remaining());
        }
        if (bytes == 0 || bytes % frameSize != 0) throw new IOException("声音为空或数据不完整");
        double ticks = (bytes / frameSize) * 20.0 / rate / pitch;
        if (!Double.isFinite(ticks) || ticks > Integer.MAX_VALUE) throw new IOException("声音时长超限");
        return Math.max(1, (int)Math.ceil(ticks));
    }
}
