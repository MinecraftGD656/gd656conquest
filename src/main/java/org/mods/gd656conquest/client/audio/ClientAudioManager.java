package org.mods.gd656conquest.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.Gd656conquest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;

public final class ClientAudioManager {
    private static final ClientAudioManager INSTANCE = new ClientAudioManager();
    private static final float DEFAULT_PITCH = 1.0F;
    private static final float MAX_MANAGED_VOLUME = 1.5F;
    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private final Map<ResourceLocation, ManagedMusicSound> activeMusic = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, ManagedLoopSound> activeLoops = new ConcurrentHashMap<>();
    private ResourceKey<Level> lastDimension;
    private boolean wasConnected;

    private ClientAudioManager() {
    }

    public static ClientAudioManager getInstance() {
        return INSTANCE;
    }

    public void enqueuePlayMusic(String soundId, float volume, boolean loop) {
        enqueuePlayMusic(soundId, volume, loop, 0);
    }

    public void enqueuePlayMusic(String soundId, float volume, boolean loop, int fadeInTicks) {
        mainThreadTasks.offer(() -> playMusicInternal(soundId, volume, loop, fadeInTicks));
    }

    public void enqueueStopMusic(String soundId, int fadeOutTicks) {
        mainThreadTasks.offer(() -> stopMusicInternal(soundId, fadeOutTicks));
    }

    public void enqueueStopAllMusic(int fadeOutTicks) {
        mainThreadTasks.offer(() -> stopAllMusicInternal(fadeOutTicks));
    }

    public void enqueuePlayLoopSound(String soundId, float volume, int fadeInTicks) {
        mainThreadTasks.offer(() -> playLoopSoundInternal(soundId, volume, fadeInTicks));
    }

    public void enqueueStopLoopSound(String soundId, int fadeOutTicks) {
        mainThreadTasks.offer(() -> stopLoopSoundInternal(soundId, fadeOutTicks));
    }

    public void enqueueStopShellingPresentation() {
        mainThreadTasks.offer(() -> {
            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            activeLoops.entrySet().removeIf(entry -> {
                ResourceLocation id = entry.getKey();
                if (!Gd656conquest.MODID.equals(id.getNamespace()) || !id.getPath().startsWith("sfx.shelling.")) return false;
                if (soundManager != null) soundManager.stop(entry.getValue());
                return true;
            });
            ClientAudioDebugState.stopShellingEffects(soundManager);
        });
    }

    public void enqueuePlaySound(String soundId, float volume, float pitch) {
        mainThreadTasks.offer(() -> playSoundInternal(soundId, volume, pitch));
    }

    public void enqueuePlaySoundAt(String soundId, float volume, float pitch, double x, double y, double z) {
        mainThreadTasks.offer(() -> playSoundAtInternal(soundId, volume, pitch, x, y, z));
    }

    public boolean isMusicActive(String soundId) {
        ResourceLocation key = parseSoundId(soundId);
        if (key == null) {
            return false;
        }
        ManagedMusicSound sound = activeMusic.get(key);
        if (sound == null) {
            return false;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        return soundManager != null && soundManager.isActive(sound);
    }

    public List<String> snapshotDebugLines() {
        return ClientAudioDebugState.snapshotLines(Minecraft.getInstance().getSoundManager(), activeMusic);
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        drainMainThreadTasks();
        SoundManager soundManager = minecraft.getSoundManager();
        if (soundManager == null) {
            return;
        }
        // Force vanilla background music manager to stay muted; mod music remains fully managed here.
        minecraft.getMusicManager().stopPlaying();
        for (Map.Entry<ResourceLocation, ManagedMusicSound> entry : activeMusic.entrySet()) {
            ManagedMusicSound sound = entry.getValue();
            if (sound == null) {
                continue;
            }
            if (sound.shouldStop(soundManager)) {
                soundManager.stop(sound);
                activeMusic.remove(entry.getKey(), sound);
            }
        }
        for (Map.Entry<ResourceLocation, ManagedLoopSound> entry : activeLoops.entrySet()) {
            ManagedLoopSound sound = entry.getValue();
            if (sound == null) {
                continue;
            }
            if (sound.shouldStop(soundManager)) {
                soundManager.stop(sound);
                activeLoops.remove(entry.getKey(), sound);
            }
        }
        boolean hasConnection = minecraft.getConnection() != null;
        if (!hasConnection) {
            if (wasConnected) {
                stopAllManagedAudioImmediate(soundManager);
            }
            wasConnected = false;
            lastDimension = null;
            return;
        }
        wasConnected = true;
        if (minecraft.level == null) {
            // Keep current music while the client is still connected but world/player is in a transient
            // clone/loading state (e.g. death-respawn pipeline).
            return;
        }
        ResourceKey<Level> currentDimension = minecraft.level.dimension();
        if (lastDimension != null && !lastDimension.equals(currentDimension)) {
            stopAllManagedAudioImmediate(soundManager);
        }
        lastDimension = currentDimension;
    }

    private void playMusicInternal(String soundId, float volume, boolean loop, int fadeInTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager soundManager = minecraft.getSoundManager();
        if (soundManager == null) {
            return;
        }
        ResourceLocation key = parseSoundId(soundId);
        if (key == null) {
            return;
        }
        float targetVolume = clampVolume(volume);
        ManagedMusicSound existing = activeMusic.get(key);
        if (existing != null) {
            // Keep continuity for the same track: refresh settings instead of restart.
            existing.refreshPlayback(targetVolume, loop, fadeInTicks);
            return;
        }
        ManagedMusicSound sound = new ManagedMusicSound(key, targetVolume, loop, fadeInTicks);
        activeMusic.put(key, sound);
        soundManager.play(sound);
    }

    private void stopMusicInternal(String soundId, int fadeOutTicks) {
        ResourceLocation key = parseSoundId(soundId);
        if (key == null) {
            return;
        }
        ManagedMusicSound sound = activeMusic.get(key);
        if (sound == null) {
            return;
        }
        if (fadeOutTicks <= 0) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            activeMusic.remove(key);
            return;
        }
        sound.beginFadeOut(fadeOutTicks);
    }

    private void stopAllMusicInternal(int fadeOutTicks) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (fadeOutTicks <= 0) {
            stopAllMusicImmediate(soundManager);
            return;
        }
        for (ManagedMusicSound sound : activeMusic.values()) {
            if (sound != null) {
                sound.beginFadeOut(fadeOutTicks);
            }
        }
    }

    private void playLoopSoundInternal(String soundId, float volume, int fadeInTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager soundManager = minecraft.getSoundManager();
        if (soundManager == null) {
            return;
        }
        ResourceLocation key = parseSoundId(soundId);
        if (key == null) {
            return;
        }
        float targetVolume = clampVolume(volume);
        ManagedLoopSound existing = activeLoops.get(key);
        if (existing != null) {
            existing.refreshPlayback(targetVolume, fadeInTicks);
            return;
        }
        ManagedLoopSound sound = new ManagedLoopSound(key, targetVolume, fadeInTicks);
        activeLoops.put(key, sound);
        soundManager.play(sound);
    }

    private void stopLoopSoundInternal(String soundId, int fadeOutTicks) {
        ResourceLocation key = parseSoundId(soundId);
        if (key == null) {
            return;
        }
        ManagedLoopSound sound = activeLoops.get(key);
        if (sound == null) {
            return;
        }
        if (fadeOutTicks <= 0) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            activeLoops.remove(key);
            return;
        }
        sound.beginFadeOut(fadeOutTicks);
    }

    private void playSoundInternal(String soundId, float volume, float pitch) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        ResourceLocation key = parseSoundId(soundId);
        if (soundManager == null || key == null) {
            return;
        }
        OneShotSound sound = new OneShotSound(key, clampVolume(volume), clampPitch(pitch));
        ClientAudioDebugState.registerEffect(ClientAudioDebugState.DebugSoundKind.SOUND, key.toString(), sound);
        soundManager.play(sound);
    }

    private void playSoundAtInternal(String soundId, float volume, float pitch, double x, double y, double z) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        ResourceLocation key = parseSoundId(soundId);
        if (soundManager == null || key == null) {
            return;
        }
        PositionalOneShotSound sound = new PositionalOneShotSound(key, clampVolume(volume), clampPitch(pitch), x, y, z);
        ClientAudioDebugState.registerEffect(ClientAudioDebugState.DebugSoundKind.SOUND, key.toString(), sound);
        soundManager.play(sound);
    }

    private void stopAllMusicImmediate(SoundManager soundManager) {
        for (ManagedMusicSound sound : activeMusic.values()) {
            if (sound != null) {
                soundManager.stop(sound);
            }
        }
        activeMusic.clear();
        ClientAudioDebugState.clear();
    }

    private void stopAllLoopSoundsImmediate(SoundManager soundManager) {
        for (ManagedLoopSound sound : activeLoops.values()) {
            if (sound != null) {
                soundManager.stop(sound);
            }
        }
        activeLoops.clear();
    }

    private void stopAllManagedAudioImmediate(SoundManager soundManager) {
        stopAllMusicImmediate(soundManager);
        stopAllLoopSoundsImmediate(soundManager);
    }

    private void drainMainThreadTasks() {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            task.run();
        }
    }

    private static float clampVolume(float volume) {
        if (Float.isNaN(volume)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(MAX_MANAGED_VOLUME, volume));
    }

    private static float clampPitch(float pitch) {
        if (Float.isNaN(pitch)) {
            return DEFAULT_PITCH;
        }
        return Math.max(0.01F, Math.min(2.0F, pitch));
    }

    private static ResourceLocation parseSoundId(String soundId) {
        String raw = soundId == null ? "" : soundId.trim();
        if (raw.isBlank()) {
            return null;
        }
        ResourceLocation direct = ResourceLocation.tryParse(raw);
        if (direct != null) {
            return direct;
        }
        return ResourceLocation.tryBuild(Gd656conquest.MODID, raw);
    }

    static final class ManagedMusicSound extends AbstractTickableSoundInstance {
        private static final int STARTUP_ACTIVE_CHECK_GRACE_TICKS = 20;
        private int fadeTicksTotal;
        private int fadeTicksLeft;
        private float fadeStartVolume;
        private int fadeInTicksTotal;
        private int fadeInTicksLeft;
        private float fadeInTargetVolume;
        private int activeCheckGraceTicks = STARTUP_ACTIVE_CHECK_GRACE_TICKS;

        private ManagedMusicSound(ResourceLocation soundId, float volume, boolean loop, int fadeInTicks) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.MUSIC, RandomSource.create());
            this.volume = fadeInTicks > 0 ? 0.0F : volume;
            this.pitch = DEFAULT_PITCH;
            this.looping = loop;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.delay = 0;
            this.fadeInTargetVolume = volume;
            if (fadeInTicks > 0) {
                this.fadeInTicksTotal = fadeInTicks;
                this.fadeInTicksLeft = fadeInTicks;
            }
        }

        @Override
        public void tick() {
        }

        private synchronized void beginFadeOut(int ticks) {
            int safeTicks = Math.max(1, ticks);
            this.fadeTicksTotal = safeTicks;
            this.fadeTicksLeft = safeTicks;
            this.fadeStartVolume = this.volume;
        }

        String debugSoundId() {
            return this.getLocation().toString().replace("gd656conquest:", "");
        }

        boolean isFadingOut() {
            return fadeTicksLeft > 0;
        }

        private synchronized boolean tickFadeOut() {
            if (fadeTicksLeft <= 0) {
                return false;
            }
            fadeTicksLeft--;
            float ratio = fadeTicksTotal <= 0 ? 0.0F : (float) fadeTicksLeft / (float) fadeTicksTotal;
            this.volume = Math.max(0.0F, fadeStartVolume * ratio);
            if (fadeTicksLeft <= 0 || this.volume <= 0.0001F) {
                this.volume = 0.0F;
                this.stop();
                return true;
            }
            return false;
        }

        private synchronized void tickFadeIn() {
            if (fadeInTicksLeft <= 0) {
                return;
            }
            fadeInTicksLeft--;
            float progress = fadeInTicksTotal <= 0 ? 1.0F : 1.0F - (fadeInTicksLeft / (float) fadeInTicksTotal);
            this.volume = Math.max(this.volume, fadeInTargetVolume * progress);
            if (fadeInTicksLeft <= 0) {
                this.volume = fadeInTargetVolume;
            }
        }

        private synchronized boolean shouldStop(SoundManager soundManager) {
            if (tickFadeOut()) {
                return true;
            }
            tickFadeIn();
            if (activeCheckGraceTicks > 0) {
                activeCheckGraceTicks--;
                return false;
            }
            return !soundManager.isActive(this);
        }

        private synchronized void refreshPlayback(float newVolume, boolean newLooping, int fadeInTicks) {
            this.fadeInTargetVolume = newVolume;
            if (fadeInTicks > 0 && this.volume < newVolume) {
                this.fadeInTicksTotal = fadeInTicks;
                this.fadeInTicksLeft = fadeInTicks;
            } else {
                this.volume = newVolume;
                this.fadeInTicksTotal = 0;
                this.fadeInTicksLeft = 0;
            }
            this.looping = newLooping;
            this.fadeTicksTotal = 0;
            this.fadeTicksLeft = 0;
            this.fadeStartVolume = 0.0F;
            if (fadeInTicks <= 0) {
                this.volume = newVolume;
            }
        }
    }

    static final class ManagedLoopSound extends AbstractTickableSoundInstance {
        private static final int STARTUP_ACTIVE_CHECK_GRACE_TICKS = 20;
        private int fadeTicksTotal;
        private int fadeTicksLeft;
        private float fadeStartVolume;
        private int fadeInTicksTotal;
        private int fadeInTicksLeft;
        private float fadeInTargetVolume;
        private int activeCheckGraceTicks = STARTUP_ACTIVE_CHECK_GRACE_TICKS;

        private ManagedLoopSound(ResourceLocation soundId, float volume, int fadeInTicks) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.MASTER, RandomSource.create());
            this.volume = fadeInTicks > 0 ? 0.0F : volume;
            this.pitch = DEFAULT_PITCH;
            this.looping = true;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.delay = 0;
            this.fadeInTargetVolume = volume;
            if (fadeInTicks > 0) {
                this.fadeInTicksTotal = fadeInTicks;
                this.fadeInTicksLeft = fadeInTicks;
            }
        }

        @Override
        public void tick() {
        }

        private synchronized void beginFadeOut(int ticks) {
            int safeTicks = Math.max(1, ticks);
            this.fadeTicksTotal = safeTicks;
            this.fadeTicksLeft = safeTicks;
            this.fadeStartVolume = this.volume;
        }

        private synchronized boolean tickFadeOut() {
            if (fadeTicksLeft <= 0) {
                return false;
            }
            fadeTicksLeft--;
            float ratio = fadeTicksTotal <= 0 ? 0.0F : (float) fadeTicksLeft / (float) fadeTicksTotal;
            this.volume = Math.max(0.0F, fadeStartVolume * ratio);
            if (fadeTicksLeft <= 0 || this.volume <= 0.0001F) {
                this.volume = 0.0F;
                this.stop();
                return true;
            }
            return false;
        }

        private synchronized void tickFadeIn() {
            if (fadeInTicksLeft <= 0) {
                return;
            }
            fadeInTicksLeft--;
            float progress = fadeInTicksTotal <= 0 ? 1.0F : 1.0F - (fadeInTicksLeft / (float) fadeInTicksTotal);
            this.volume = Math.max(this.volume, fadeInTargetVolume * progress);
            if (fadeInTicksLeft <= 0) {
                this.volume = fadeInTargetVolume;
            }
        }

        private synchronized boolean shouldStop(SoundManager soundManager) {
            if (tickFadeOut()) {
                return true;
            }
            tickFadeIn();
            if (activeCheckGraceTicks > 0) {
                activeCheckGraceTicks--;
                return false;
            }
            return !soundManager.isActive(this);
        }

        private synchronized void refreshPlayback(float newVolume, int fadeInTicks) {
            this.fadeInTargetVolume = newVolume;
            if (fadeInTicks > 0 && this.volume < newVolume) {
                this.fadeInTicksTotal = fadeInTicks;
                this.fadeInTicksLeft = fadeInTicks;
            } else {
                this.volume = newVolume;
                this.fadeInTicksTotal = 0;
                this.fadeInTicksLeft = 0;
            }
            this.fadeTicksTotal = 0;
            this.fadeTicksLeft = 0;
            this.fadeStartVolume = 0.0F;
            if (fadeInTicks <= 0) {
                this.volume = newVolume;
            }
        }
    }

    private static class OneShotSound extends AbstractTickableSoundInstance {
        private OneShotSound(ResourceLocation soundId, float volume, float pitch) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.MASTER, RandomSource.create());
            this.volume = volume;
            this.pitch = pitch;
            this.looping = false;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.delay = 0;
        }

        @Override
        public void tick() {
        }
    }

    private static class PositionalOneShotSound extends AbstractTickableSoundInstance {
        private PositionalOneShotSound(ResourceLocation soundId, float volume, float pitch, double x, double y, double z) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.MASTER, RandomSource.create());
            this.volume = volume;
            this.pitch = pitch;
            this.looping = false;
            this.relative = false;
            this.attenuation = Attenuation.NONE;
            this.delay = 0;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void tick() {
        }
    }

}
