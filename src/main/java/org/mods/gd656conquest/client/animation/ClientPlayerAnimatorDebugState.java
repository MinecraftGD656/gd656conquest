package org.mods.gd656conquest.client.animation;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.InputEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.Gd656conquest;
import org.mods.gd656conquest.common.entity.PlayerAnimatorDebugDimensionsState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.PlayerAnimatorHelpRequestPacket;
import org.mods.gd656conquest.client.room.ClientDownedState;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class ClientPlayerAnimatorDebugState {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "debug_playeranimator");
    private static final int LAYER_PRIORITY = 2_500;
    private static final int MIN_DURATION_MS = 1_000;
    private static final int FADE_TICKS = 8;
    private static final int HELP_ANIMATION_DURATION_MS = 950;
    private static final int HELP_PEAK_RISE_MS = 220;
    private static final int HELP_INPUT_COOLDOWN_MS = 120;
    private static final Map<UUID, Long> ACTIVE_UNTIL_MS = new HashMap<>();
    private static final Map<UUID, Float> LOCKED_BODY_YAW_DEGREES = new HashMap<>();
    private static final Map<UUID, HelpWindow> HELP_WINDOWS = new HashMap<>();
    private static final Map<UUID, Long> HELP_INPUT_COOLDOWN_UNTIL_MS = new HashMap<>();
    private static boolean initialized;

    private ClientPlayerAnimatorDebugState() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, animationStack) -> {
            ModifierLayer<IAnimation> layer = new ModifierLayer<>();
            animationStack.addAnimLayer(LAYER_PRIORITY, layer);
            PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
        });
    }

    public static void startPreview(UUID playerId, int durationMs) {
        if (playerId == null) {
            return;
        }
        long expireAt = System.currentTimeMillis() + Math.max(MIN_DURATION_MS, durationMs);
        ACTIVE_UNTIL_MS.put(playerId, expireAt);
        PlayerAnimatorDebugDimensionsState.startPreview(playerId, durationMs);
    }

    public static void stopPreview(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ACTIVE_UNTIL_MS.remove(playerId);
        LOCKED_BODY_YAW_DEGREES.remove(playerId);
        HELP_WINDOWS.remove(playerId);
        HELP_INPUT_COOLDOWN_UNTIL_MS.remove(playerId);
        PlayerAnimatorDebugDimensionsState.stopPreview(playerId);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && playerId.equals(minecraft.player.getUUID()) && ClientDownedState.isDebugPreviewActive()) {
            ClientDownedState.clear();
        }
    }

    public static void startHelpPreview(UUID playerId, int durationMs) {
        if (playerId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long previewExpireAt = ACTIVE_UNTIL_MS.getOrDefault(playerId, 0L);
        if (previewExpireAt <= now) {
            return;
        }
        long totalDurationMs = Math.max(250, durationMs);
        long peakAtMs = Math.min(previewExpireAt, now + HELP_PEAK_RISE_MS);
        long helpExpireAt = Math.min(previewExpireAt, now + totalDurationMs);
        float currentStrength = resolveCurrentHelpStrength(playerId, now);
        float peakStrength = Mth.clamp(Math.max(currentStrength + 0.55F, 0.72F), 0.0F, 1.0F);
        Random random = new Random(playerId.hashCode() * 31L + now);
        HelpPoseProfile startPose = resolveCurrentHelpPose(playerId, now);
        HelpPoseProfile targetPose = new HelpPoseProfile(
            randomBetween(random, -4.0F, 5.0F),
            randomBetween(random, -3.0F, 5.0F),
            randomBetween(random, -4.0F, 4.0F),
            randomBetween(random, 0.0F, 0.48F),
            randomBetween(random, 1.5F, 6.0F),
            randomBetween(random, -4.0F, 4.0F),
            randomBetween(random, -2.0F, 2.5F),
            randomBetween(random, 0.82F, 1.02F)
        );
        HELP_WINDOWS.put(playerId, new HelpWindow(
            now,
            peakAtMs,
            helpExpireAt,
            currentStrength,
            peakStrength,
            startPose,
            targetPose
        ));
    }

    public static boolean handleMouseInput(InputEvent.MouseButton event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return false;
        }
        UUID playerId = minecraft.player.getUUID();
        long now = System.currentTimeMillis();
        if (!isPreviewActive(playerId, now)) {
            return false;
        }
        long cooldownExpireAt = HELP_INPUT_COOLDOWN_UNTIL_MS.getOrDefault(playerId, 0L);
        if (cooldownExpireAt > now) {
            return true;
        }
        HELP_INPUT_COOLDOWN_UNTIL_MS.put(playerId, now + HELP_INPUT_COOLDOWN_MS);
        startHelpPreview(playerId, HELP_ANIMATION_DURATION_MS);
        NetworkManager.getChannel().sendToServer(new PlayerAnimatorHelpRequestPacket());
        return true;
    }

    public static boolean isPreviewActive(UUID playerId) {
        return isPreviewActive(playerId, System.currentTimeMillis());
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearExpiredState(System.currentTimeMillis());
            return;
        }
        long now = System.currentTimeMillis();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            boolean bodyReady = org.mods.gd656conquest.client.room.ClientDownedRagdollState.isBodyReady(player.getUUID());
            boolean active = !bodyReady && isPreviewActive(player.getUUID(), now);
            if (!active) {
                LOCKED_BODY_YAW_DEGREES.remove(player.getUUID());
                HELP_WINDOWS.remove(player.getUUID());
            } else {
                maintainLockedBodyYaw(player);
            }
            if (bodyReady) {
                org.mods.gd656conquest.client.room.DownedRagdollClientHooks.clearForcedPose(player.getUUID());
            } else {
                PlayerAnimatorDebugDimensionsState.tickPlayer(player);
            }
            applyPreviewState(player, active);
        }
        clearExpiredState(now);
    }

    public static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            for (AbstractClientPlayer player : minecraft.level.players()) {
                applyPreviewState(player, false);
            }
        }
        ACTIVE_UNTIL_MS.clear();
        LOCKED_BODY_YAW_DEGREES.clear();
        HELP_WINDOWS.clear();
        HELP_INPUT_COOLDOWN_UNTIL_MS.clear();
        PlayerAnimatorDebugDimensionsState.resetAll();
    }

    private static boolean isPreviewActive(UUID playerId, long now) {
        return playerId != null && ACTIVE_UNTIL_MS.getOrDefault(playerId, 0L) > now;
    }

    private static void clearExpiredState(long now) {
        Minecraft minecraft = Minecraft.getInstance();
        UUID localPlayerId = minecraft.player == null ? null : minecraft.player.getUUID();
        Long localExpireAt = localPlayerId == null ? null : ACTIVE_UNTIL_MS.get(localPlayerId);
        if (localPlayerId != null && localExpireAt != null && localExpireAt <= now && ClientDownedState.isDebugPreviewActive()) {
            ClientDownedState.clear();
        }
        ACTIVE_UNTIL_MS.entrySet().removeIf(entry -> entry.getValue() <= now);
        LOCKED_BODY_YAW_DEGREES.entrySet().removeIf(entry -> !isPreviewActive(entry.getKey(), now));
        HELP_WINDOWS.entrySet().removeIf(entry -> entry.getValue().expireAtMs() <= now);
        HELP_INPUT_COOLDOWN_UNTIL_MS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static void maintainLockedBodyYaw(AbstractClientPlayer player) {
        if (player == null) {
            return;
        }
        float lockedYaw = LOCKED_BODY_YAW_DEGREES.computeIfAbsent(player.getUUID(), id -> player.yBodyRot);
        player.yBodyRot = lockedYaw;
        player.yBodyRotO = lockedYaw;
    }

    private static float resolveCurrentHelpStrength(UUID playerId, long now) {
        HelpWindow window = HELP_WINDOWS.get(playerId);
        return window == null ? 0.0F : window.strengthAt(now);
    }

    private static HelpPoseProfile resolveCurrentHelpPose(UUID playerId, long now) {
        HelpWindow window = HELP_WINDOWS.get(playerId);
        return window == null ? HelpPoseProfile.ZERO : window.poseAt(now);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> resolveLayer(AbstractClientPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            Object stored = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
            if (stored instanceof ModifierLayer<?> layer) {
                return (ModifierLayer<IAnimation>) layer;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static void applyPreviewState(AbstractClientPlayer player, boolean active) {
        ModifierLayer<IAnimation> layer = resolveLayer(player);
        if (layer == null) {
            return;
        }
        boolean alreadyActive = layer.getAnimation() instanceof DownedPoseAnimation;
        if (active) {
            if (!alreadyActive) {
                layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE),
                    new DownedPoseAnimation(player.getUUID()),
                    true
                );
            }
            return;
        }
        if (alreadyActive) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), null, true);
        }
    }

    private record HelpWindow(
        long startAtMs,
        long peakAtMs,
        long expireAtMs,
        float startStrength,
        float peakStrength,
        HelpPoseProfile startPose,
        HelpPoseProfile targetPose
    ) {
        private float strengthAt(long now) {
            if (now <= startAtMs) {
                return startStrength;
            }
            if (now < peakAtMs) {
                float progress = normalized(now, startAtMs, peakAtMs);
                return Mth.lerp(easeOut(progress), startStrength, peakStrength);
            }
            if (now < expireAtMs) {
                float progress = normalized(now, peakAtMs, expireAtMs);
                return Mth.lerp(easeInOut(progress), peakStrength, 0.0F);
            }
            return 0.0F;
        }

        private HelpPoseProfile poseAt(long now) {
            if (now <= startAtMs) {
                return startPose;
            }
            if (now < peakAtMs) {
                float progress = normalized(now, startAtMs, peakAtMs);
                return startPose.lerp(targetPose, easeOut(progress));
            }
            return targetPose;
        }

        private float normalized(long now, long start, long end) {
            return Mth.clamp((now - start) / (float) Math.max(1L, end - start), 0.0F, 1.0F);
        }

        private float easeOut(float progress) {
            float inverse = 1.0F - progress;
            return 1.0F - inverse * inverse * inverse;
        }

        private float easeInOut(float progress) {
            return progress * progress * (3.0F - 2.0F * progress);
        }
    }

    private record HelpPoseProfile(
        float rightArmPitchJitterDeg,
        float rightArmYawJitterDeg,
        float rightArmRollJitterDeg,
        float leftArmLiftFactor,
        float headLiftDeg,
        float headYawBiasDeg,
        float torsoRollBiasDeg,
        float struggleAmplitude
    ) {
        private static final HelpPoseProfile ZERO = new HelpPoseProfile(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.9F);

        private HelpPoseProfile lerp(HelpPoseProfile target, float delta) {
            return new HelpPoseProfile(
                Mth.lerp(delta, rightArmPitchJitterDeg, target.rightArmPitchJitterDeg),
                Mth.lerp(delta, rightArmYawJitterDeg, target.rightArmYawJitterDeg),
                Mth.lerp(delta, rightArmRollJitterDeg, target.rightArmRollJitterDeg),
                Mth.lerp(delta, leftArmLiftFactor, target.leftArmLiftFactor),
                Mth.lerp(delta, headLiftDeg, target.headLiftDeg),
                Mth.lerp(delta, headYawBiasDeg, target.headYawBiasDeg),
                Mth.lerp(delta, torsoRollBiasDeg, target.torsoRollBiasDeg),
                Mth.lerp(delta, struggleAmplitude, target.struggleAmplitude)
            );
        }
    }

    private record DownedPoseAnimation(UUID playerId) implements IAnimation {
            private static final float BODY_PITCH = degrees(89.0F);
            private static final float BODY_ROLL = degrees(2.0F);
            private static final float TORSO_PITCH = degrees(6.0F);
            private static final float HEAD_PITCH = degrees(84.0F);
            private static final float HEAD_HORIZONTAL_TURN_LIMIT_DEGREES = 85.0F;
            private static final float HEAD_VERTICAL_TURN_LIMIT_DEGREES = 70.0F;
            private static final float RIGHT_ARM_BASE_PITCH = degrees(-18.0F);
            private static final float RIGHT_ARM_BASE_YAW = degrees(12.0F);
            private static final float RIGHT_ARM_BASE_ROLL = degrees(14.0F);
            private static final float LEFT_ARM_PITCH = degrees(-12.0F);
            private static final float LEFT_ARM_YAW = degrees(-12.0F);
            private static final float LEFT_ARM_ROLL = degrees(-14.0F);
            private static final float RIGHT_ARM_HELP_PITCH = degrees(-72.0F);
            private static final float RIGHT_ARM_HELP_YAW = degrees(26.0F);
            private static final float RIGHT_ARM_HELP_ROLL = degrees(18.0F);
            private static final float RIGHT_LEG_PITCH = degrees(18.0F);
            private static final float RIGHT_LEG_ROLL = degrees(4.0F);
            private static final float LEFT_LEG_PITCH = degrees(10.0F);
            private static final float LEFT_LEG_YAW = degrees(-3.0F);
            private static final Vec3f BODY_POSITION = new Vec3f(0.0F, -0.6F, 0.0F);
            private static final Vec3f HEAD_POSITION = new Vec3f(0.0F, -0.25F, 0.0F);
            private static final Vec3f RIGHT_ARM_POSITION = new Vec3f(0.0F, -0.35F, 0.0F);
            private static final Vec3f LEFT_ARM_POSITION = new Vec3f(0.0F, -0.35F, 0.0F);
            private static final Vec3f RIGHT_LEG_POSITION = new Vec3f(0.0F, -0.45F, 0.0F);
            private static final Vec3f LEFT_LEG_POSITION = new Vec3f(0.0F, -0.45F, 0.0F);

        private DownedPoseAnimation(UUID playerId) {
                this.playerId = playerId == null ? new UUID(0L, 0L) : playerId;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
                return switch (type) {
                    case POSITION -> resolvePosition(modelName, value0);
                    case ROTATION -> resolveRotation(modelName, value0);
                    case BEND -> value0;
                };
            }

            @Override
            public void setupAnim(float tickDelta) {
            }

            private Vec3f resolvePosition(String modelName, Vec3f original) {
                return switch (modelName) {
                    case "body" -> original.add(BODY_POSITION);
                    case "head" -> original.add(HEAD_POSITION);
                    case "rightArm" -> original.add(RIGHT_ARM_POSITION);
                    case "leftArm" -> original.add(LEFT_ARM_POSITION);
                    case "rightLeg" -> original.add(RIGHT_LEG_POSITION);
                    case "leftLeg" -> original.add(LEFT_LEG_POSITION);
                    default -> original;
                };
            }

            private Vec3f resolveRotation(String modelName, Vec3f original) {
                float helpStrength = resolveHelpStrength();
                float struggleStrength = resolveStruggleStrength(helpStrength);
                HelpWindow window = HELP_WINDOWS.get(playerId);
                HelpPoseProfile poseProfile = window == null ? HelpPoseProfile.ZERO : window.poseAt(System.currentTimeMillis());
                float rightArmPitchJitter = degrees(poseProfile.rightArmPitchJitterDeg());
                float rightArmYawJitter = degrees(poseProfile.rightArmYawJitterDeg());
                float rightArmRollJitter = degrees(poseProfile.rightArmRollJitterDeg());
                float leftArmLift = degrees(poseProfile.leftArmLiftFactor() * 22.0F * helpStrength);
                float headLift = degrees(poseProfile.headLiftDeg() * helpStrength);
                float headHorizontalTurn = resolveHeadHorizontalTurn();
                float headVerticalTurn = resolveHeadVerticalTurn();
                float torsoRollBias = degrees(poseProfile.torsoRollBiasDeg() * helpStrength);
                float bodyPitch = BODY_PITCH + degrees(2.4F * helpStrength);
                float bodyRoll = BODY_ROLL - degrees(3.4F * struggleStrength);
                float torsoPitch = TORSO_PITCH + degrees(3.0F * helpStrength);
                float torsoYaw = degrees(1.5F * struggleStrength);
                float torsoRoll = degrees(-2.8F * struggleStrength) + torsoRollBias;
                return switch (modelName) {
                    case "body" -> original.add(new Vec3f(
                            bodyPitch,
                            0.0F,
                            bodyRoll
                    ));
                    case "torso" -> original.add(new Vec3f(
                            torsoPitch,
                            torsoYaw,
                            torsoRoll
                    ));
                    case "head" -> new Vec3f(
                            HEAD_PITCH + headVerticalTurn - headLift,
                            0.0F,
                            headHorizontalTurn
                    );
                    case "rightArm" -> original.add(new Vec3f(
                            lerp(RIGHT_ARM_BASE_PITCH, RIGHT_ARM_HELP_PITCH, helpStrength) + rightArmPitchJitter - degrees(6.0F * struggleStrength),
                            lerp(RIGHT_ARM_BASE_YAW, RIGHT_ARM_HELP_YAW, helpStrength) + rightArmYawJitter + degrees(4.5F * struggleStrength),
                            lerp(RIGHT_ARM_BASE_ROLL, RIGHT_ARM_HELP_ROLL, helpStrength) + rightArmRollJitter + degrees(4.0F * struggleStrength)
                    ));
                    case "leftArm" -> original.add(new Vec3f(
                            LEFT_ARM_PITCH - leftArmLift + degrees(1.6F * struggleStrength),
                            LEFT_ARM_YAW - degrees(1.2F * struggleStrength) - degrees(2.0F * helpStrength),
                            LEFT_ARM_ROLL - degrees(2.2F * struggleStrength) - degrees(6.0F * helpStrength * poseProfile.leftArmLiftFactor())
                    ));
                    case "rightLeg" -> original.add(new Vec3f(
                            RIGHT_LEG_PITCH + degrees(1.8F * struggleStrength),
                            degrees(struggleStrength),
                            RIGHT_LEG_ROLL + degrees(2.0F * struggleStrength)
                    ));
                    case "leftLeg" -> original.add(new Vec3f(
                            LEFT_LEG_PITCH - degrees(1.4F * struggleStrength),
                            LEFT_LEG_YAW - degrees(0.8F * struggleStrength),
                            degrees(-1.6F * struggleStrength)
                    ));
                    default -> original;
                };
            }

            private float resolveHelpStrength() {
                return resolveCurrentHelpStrength(playerId, System.currentTimeMillis());
            }

            private float resolveStruggleStrength(float helpStrength) {
                if (helpStrength <= 0.0F) {
                    return 0.0F;
                }
                HelpWindow window = HELP_WINDOWS.get(playerId);
                if (window == null) {
                    return 0.0F;
                }
                long now = System.currentTimeMillis();
                float progress = Mth.clamp((now - window.startAtMs()) / (float) Math.max(1L, window.expireAtMs() - window.startAtMs()), 0.0F, 1.0F);
                HelpPoseProfile poseProfile = window.poseAt(now);
                float struggleWave = Mth.sin(progress * Mth.PI * 4.5F);
                float secondaryWave = Mth.sin(progress * Mth.PI * 7.0F + 0.45F);
                return helpStrength * (struggleWave * 0.22F + secondaryWave * 0.08F) * poseProfile.struggleAmplitude();
            }

            private AbstractClientPlayer resolveAnimatedPlayer() {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null) {
                    return null;
                }
                return (AbstractClientPlayer) minecraft.level.getPlayerByUUID(playerId);
            }

            private float resolveHeadHorizontalTurn() {
                AbstractClientPlayer player = resolveAnimatedPlayer();
                if (player == null) {
                    return 0.0F;
                }
                float deltaDegrees = Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot);
                float clamped = Mth.clamp(deltaDegrees, -HEAD_HORIZONTAL_TURN_LIMIT_DEGREES, HEAD_HORIZONTAL_TURN_LIMIT_DEGREES);
                return degrees(clamped);
            }

            private float resolveHeadVerticalTurn() {
                AbstractClientPlayer player = resolveAnimatedPlayer();
                if (player == null) {
                    return 0.0F;
                }
                float clamped = Mth.clamp(player.getXRot(), -HEAD_VERTICAL_TURN_LIMIT_DEGREES, HEAD_VERTICAL_TURN_LIMIT_DEGREES);
                return degrees(clamped);
            }

            private static float degrees(float degrees) {
                return (float) Math.toRadians(degrees);
            }

            private static float lerp(float start, float end, float delta) {
                return start + (end - start) * delta;
            }
        }

    private static float randomBetween(Random random, float min, float max) {
        return Mth.lerp(random.nextFloat(), min, max);
    }
}
