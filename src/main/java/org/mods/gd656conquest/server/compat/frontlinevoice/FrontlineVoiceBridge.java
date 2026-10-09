package org.mods.gd656conquest.server.compat.frontlinevoice;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class FrontlineVoiceBridge {
    public static final String MOD_ID = "gd656frontvoice";

    private static Method playCueMethod;
    private static Method playCueAtMethod;
    private static Method playThirdPersonSoundMethod;
    private static Method playConfirmedDeathMethod;
    private static boolean initialized;

    private FrontlineVoiceBridge() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean playCue(ServerPlayer sourcePlayer, String cueId) {
        if (!ensureInitialized() || sourcePlayer == null || cueId == null || cueId.isBlank()) {
            return false;
        }
        try {
            Object result = playCueMethod.invoke(null, sourcePlayer, cueId);
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean playThirdPersonSound(ServerPlayer sourcePlayer, String soundId, float volume, float pitch, double maxDistance) {
        if (!ensureInitialized() || sourcePlayer == null || soundId == null || soundId.isBlank()) {
            return false;
        }
        try {
            Object result = playThirdPersonSoundMethod.invoke(null, sourcePlayer, soundId, volume, pitch, maxDistance);
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean playCueAt(ServerPlayer sourcePlayer, Vec3 position, String cueId) {
        if (!ensureInitialized() || sourcePlayer == null || position == null || cueId == null || cueId.isBlank()) {
            return false;
        }
        try {
            Object result = playCueAtMethod.invoke(null, sourcePlayer, position, cueId);
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean playConfirmedDeath(ServerPlayer sourcePlayer) {
        if (!ensureInitialized() || sourcePlayer == null) {
            return false;
        }
        try {
            Object result = playConfirmedDeathMethod.invoke(null, sourcePlayer);
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean ensureInitialized() {
        if (initialized) {
            return playCueMethod != null
                && playCueAtMethod != null
                && playThirdPersonSoundMethod != null
                && playConfirmedDeathMethod != null;
        }
        initialized = true;
        if (!isLoaded()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.mods.gd656frontvoice.api.FrontVoiceApi");
            playCueMethod = apiClass.getMethod("playCue", ServerPlayer.class, String.class);
            try {
                playCueAtMethod = apiClass.getMethod("playCueAt", ServerPlayer.class, Vec3.class, String.class);
                playThirdPersonSoundMethod = apiClass.getMethod(
                    "playThirdPersonSound",
                    ServerPlayer.class,
                    String.class,
                    float.class,
                    float.class,
                    double.class
                );
            } catch (NoSuchMethodException e) {
                // Ignore missing methods in the new API for now, we don't use them currently
                playCueAtMethod = null;
                playThirdPersonSoundMethod = null;
            }
            playConfirmedDeathMethod = apiClass.getMethod("playConfirmedDeath", ServerPlayer.class);
            return true;
        } catch (ReflectiveOperationException ignored) {
            playCueMethod = null;
            playCueAtMethod = null;
            playThirdPersonSoundMethod = null;
            playConfirmedDeathMethod = null;
            return false;
        }
    }

}
