package org.mods.gd656conquest.common.audio;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.mods.gd656conquest.client.audio.ClientAudioManager;

public final class AudioManager {
    private static final AudioManager INSTANCE = new AudioManager();

    private AudioManager() {
    }

    public static AudioManager getInstance() {
        return INSTANCE;
    }

    public void playMusic(String soundId, float volume, boolean loop) {
        playMusic(soundId, volume, loop, 0);
    }

    public void playMusic(String soundId, float volume, boolean loop, int fadeInTicks) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAudioManager.getInstance().enqueuePlayMusic(soundId, volume, loop, fadeInTicks));
    }

    public void stopMusic(String soundId, int fadeOutTicks) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAudioManager.getInstance().enqueueStopMusic(soundId, fadeOutTicks));
    }

    public void setMusicVolume(String soundId, float volume) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAudioManager.getInstance().enqueueSetMusicVolume(soundId, volume));
    }

    public void stopAllMusic(int fadeOutTicks) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAudioManager.getInstance().enqueueStopAllMusic(fadeOutTicks));
    }

    public void playSound(String soundId, float volume, float pitch) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAudioManager.getInstance().enqueuePlaySound(soundId, volume, pitch));
    }

    public void tickClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAudioManager.getInstance().tick());
    }
}
