package org.mods.gd656conquest.client.audio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class ClientAudioDebugState {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final Map<Long, DebugSoundEntry> ACTIVE_EFFECTS = new ConcurrentHashMap<>();

    private ClientAudioDebugState() {
    }

    static long registerEffect(DebugSoundKind kind, String soundId, SoundInstance instance) {
        long id = NEXT_ID.getAndIncrement();
        ACTIVE_EFFECTS.put(id, new DebugSoundEntry(id, kind, normalizeSoundId(soundId), instance));
        return id;
    }

    static void stopShellingEffects(SoundManager soundManager) {
        ACTIVE_EFFECTS.entrySet().removeIf(entry -> {
            DebugSoundEntry effect = entry.getValue();
            if (!effect.soundId().startsWith("sfx.shelling.")) return false;
            if (soundManager != null && effect.instance() != null) soundManager.stop(effect.instance());
            return true;
        });
    }

    static void clear() {
        ACTIVE_EFFECTS.clear();
    }

    static List<String> snapshotLines(SoundManager soundManager, Map<?, ClientAudioManager.ManagedMusicSound> activeMusic) {
        cleanupInactive(soundManager);
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (activeMusic != null) {
            List<ClientAudioManager.ManagedMusicSound> musics = new ArrayList<>(activeMusic.values());
            musics.sort(Comparator.comparing(ClientAudioManager.ManagedMusicSound::debugSoundId));
            for (ClientAudioManager.ManagedMusicSound music : musics) {
                if (music == null || music.debugSoundId() == null) {
                    continue;
                }
                if (soundManager != null && !music.isFadingOut() && !soundManager.isActive(music)) {
                    continue;
                }
                addCount(counts, prefix(DebugSoundKind.MUSIC) + music.debugSoundId());
            }
        }
        List<DebugSoundEntry> effects = new ArrayList<>(ACTIVE_EFFECTS.values());
        effects.sort(Comparator.comparing(DebugSoundEntry::displayKey));
        for (DebugSoundEntry entry : effects) {
            addCount(counts, prefix(entry.kind()) + entry.soundId());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                lines.add(entry.getKey() + " x" + entry.getValue());
            } else {
                lines.add(entry.getKey());
            }
        }
        return lines;
    }

    private static void cleanupInactive(SoundManager soundManager) {
        if (soundManager == null) {
            ACTIVE_EFFECTS.clear();
            return;
        }
        ACTIVE_EFFECTS.entrySet().removeIf(entry -> {
            DebugSoundEntry value = entry.getValue();
            return value == null || value.instance() == null || !soundManager.isActive(value.instance());
        });
    }

    private static void addCount(Map<String, Integer> counts, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        counts.merge(key, 1, Integer::sum);
    }

    private static String prefix(DebugSoundKind kind) {
        return switch (kind) {
            case MUSIC -> "[音乐] ";
            case SOUND -> "[音效] ";
            case VOICE -> "[语音] ";
        };
    }

    private static String normalizeSoundId(String soundId) {
        String value = soundId == null ? "" : soundId.trim();
        if (value.startsWith("gd656conquest:")) {
            value = value.substring("gd656conquest:".length());
        }
        return value.toLowerCase(Locale.ROOT);
    }

    enum DebugSoundKind {
        MUSIC,
        SOUND,
        VOICE
    }

    private record DebugSoundEntry(long id, DebugSoundKind kind, String soundId, SoundInstance instance) {
        String displayKey() {
            return prefix(kind) + soundId;
        }
    }
}
