package org.mods.gd656conquest.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class DownedLoopSoundInstance extends AbstractTickableSoundInstance {
    private final ResourceLocation soundId;
    private boolean isFadingOut;
    private int fadeTicks;

    public DownedLoopSoundInstance(ResourceLocation soundId) {
        super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.PLAYERS, RandomSource.create());
        this.soundId = soundId;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f; // Fade in
        this.relative = true;
        this.pitch = 1.0f;
    }

    public ResourceLocation soundId() {
        return soundId;
    }

    public void fadeOut() {
        this.isFadingOut = true;
    }

    @Override
    public void tick() {
        if (this.isStopped()) {
            return;
        }
        if (this.isFadingOut) {
            this.volume -= 0.05f;
            if (this.volume <= 0.0f) {
                this.stop();
            }
        } else {
            if (this.volume < 1.0f) {
                this.volume += 0.05f;
                if (this.volume > 1.0f) {
                    this.volume = 1.0f;
                }
            }
        }
    }
}
