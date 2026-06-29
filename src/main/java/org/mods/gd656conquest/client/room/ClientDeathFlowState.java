package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ClientDeathFlowState {
    private static final float HURT_FLASH_DURATION_SECONDS = 0.2F;
    private static final float VISUAL_SMOOTH_SHARPNESS = 4.5F;
    private static final float DEATH_DESATURATION_LEVEL = 0.92F;
    private static final float STANDARD_DEATH_VIEW_DARKNESS = 0.70F;
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_DEATH_FALL = 1;
    private static final int PHASE_DEATH_WAIT = 2;
    private static final int PHASE_DEATH_HOLD = 5;
    private static final int PHASE_DEPLOY_TRAVEL = 6;
    private static final int PHASE_DEPLOY_OPEN = 7;
    private static final int PHASE_RESPAWN_TRAVEL = 8;
    private static final int PHASE_RESPAWN_HOLD = 9;
    private static final int PHASE_DOWNED_HOLD = 10;
    private static final int PHASE_DEATH_RECOVER = 11;
    private static final int DEATH_LOOK_UP_TICKS = 14;
    private static final int DEATH_FALL_TICKS = 8;
    private static final int DEATH_WAIT_TICKS = 4;
    private static final int DEATH_RECOVER_TICKS = 14;
    private static final int DEPLOY_TRAVEL_TICKS = 40;
    private static boolean active;
    private static boolean matchEndViewLocked;
    private static boolean deathVisualsActive;
    private static int phase;
    private static long phaseTicks;
    private static int killerEntityId;
    private static int deployConfirmDelayTicks;
    private static int deployTicksSinceOpen;
    private static int travelTotalTicks;
    private static int travelElapsedTicks;
    private static double deathX;
    private static double deathY;
    private static double deathZ;
    private static double deathFallTargetX;
    private static double deathFallTargetY;
    private static double deathFallTargetZ;
    private static float deathStartYaw;
    private static float deathStartPitch;
    private static float trackBlendStartYaw;
    private static float trackBlendStartPitch;
    private static double trackedKillerX;
    private static double trackedKillerY;
    private static double trackedKillerZ;
    private static double travelFromX;
    private static double travelFromY;
    private static double travelFromZ;
    private static float travelFromYaw;
    private static float travelFromPitch;
    private static double transitionTargetX;
    private static double transitionTargetY;
    private static double transitionTargetZ;
    private static float transitionTargetYaw;
    private static float transitionTargetPitch;
    private static double previousCameraX;
    private static double previousCameraY;
    private static double previousCameraZ;
    private static float previousCameraYaw;
    private static float previousCameraPitch;
    private static double currentCameraX;
    private static double currentCameraY;
    private static double currentCameraZ;
    private static float currentCameraYaw;
    private static float currentCameraPitch;
    private static float currentDesaturation;
    private static float targetDesaturation;
    private static float deathDesaturationTarget;
    private static float currentDarkness;
    private static float targetDarkness;
    private static float deathDarknessTarget;
    private static float downedBlackoutTarget;
    private static float currentDeployIconAlpha;
    private static float targetDeployIconAlpha;
    private static boolean boundaryPenaltyActive;
    private static float hurtFlashRemainSeconds;
    private static int lastHurtTime = -1;
    private static long lastVisualUpdateNanos = -1L;
    private static boolean shaderLoaded;
    private static Method cameraSetPositionMethod;
    private static Method cameraSetPositionVecMethod;
    private static Method cameraSetRotationMethod;
    private static Field cameraPositionField;
    private static Field cameraBlockPositionField;

    private ClientDeathFlowState() {
    }

    public static void startDeathSequence(double deathXValue, double deathYValue, double deathZValue, int killerId) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        TaczClientInputCompat.suppressCombatInputUntilRelease(minecraft);
        TaczClientInputCompat.clearCrawlState(minecraft);
        active = true;
        deathVisualsActive = true;
        phase = PHASE_DEATH_FALL;
        phaseTicks = 0L;
        killerEntityId = killerId;
        deployConfirmDelayTicks = 0;
        deployTicksSinceOpen = 0;
        travelTotalTicks = 0;
        travelElapsedTicks = 0;
        deathX = deathXValue;
        deathY = deathYValue;
        deathZ = deathZValue;
        deathStartYaw = player.getYRot();
        deathStartPitch = player.getXRot();
        trackBlendStartYaw = deathStartYaw;
        trackBlendStartPitch = -70.0F;
        Vec3 startEye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 horizontalBack = new Vec3(-look.x, 0.0D, -look.z);
        if (horizontalBack.lengthSqr() < 1.0E-4D) {
            float yawRad = (deathStartYaw + 180.0F) * ((float) Math.PI / 180.0F);
            horizontalBack = new Vec3(Mth.sin(yawRad), 0.0D, -Mth.cos(yawRad));
        }
        horizontalBack = horizontalBack.normalize().scale(0.18D);
        deathFallTargetX = startEye.x + horizontalBack.x;
        deathFallTargetY = startEye.y - 1.0D;
        deathFallTargetZ = startEye.z + horizontalBack.z;
        trackedKillerX = deathXValue;
        trackedKillerY = deathYValue + player.getEyeHeight();
        trackedKillerZ = deathZValue;
        setCameraPose(startEye.x, startEye.y, startEye.z, deathStartYaw, deathStartPitch, true);
    }

    public static void openDeploySequence(double sourceX, double sourceY, double sourceZ, double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int deployDelayTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        TaczClientInputCompat.suppressCombatInputUntilRelease(minecraft);
        boolean hasSource = Double.isFinite(sourceX) && Double.isFinite(sourceY) && Double.isFinite(sourceZ);
        if (!active) {
            active = true;
            deathVisualsActive = false;
            phase = PHASE_IDLE;
            phaseTicks = 0L;
            killerEntityId = -1;
            travelTotalTicks = 0;
            travelElapsedTicks = 0;
            Vec3 currentPos = hasSource ? new Vec3(sourceX, sourceY, sourceZ) : player.getEyePosition();
            setCameraPose(currentPos.x, currentPos.y, currentPos.z, player.getYRot(), player.getXRot(), true);
            deathDesaturationTarget = 0.0F;
            deathDarknessTarget = 0.0F;
            lastVisualUpdateNanos = System.nanoTime();
        }
        deathVisualsActive = false;
        // Deploy travel should immediately exit death desaturation for a clean transition.
        currentDesaturation = 0.0F;
        targetDesaturation = 0.0F;
        deathDesaturationTarget = 0.0F;
        disableDesaturate(minecraft);
        phase = PHASE_DEPLOY_TRAVEL;
        phaseTicks = 0L;
        deployConfirmDelayTicks = Math.max(0, deployDelayTicks);
        deployTicksSinceOpen = 0;
        travelTotalTicks = DEPLOY_TRAVEL_TICKS;
        travelElapsedTicks = 0;
        travelFromX = currentCameraX;
        travelFromY = currentCameraY;
        travelFromZ = currentCameraZ;
        travelFromYaw = currentCameraYaw;
        travelFromPitch = currentCameraPitch;
        transitionTargetX = targetX;
        transitionTargetY = targetY;
        transitionTargetZ = targetZ;
        transitionTargetYaw = targetYaw;
        transitionTargetPitch = targetPitch;
    }

    public static void startRespawnTransition(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int transitionTicks) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        TaczClientInputCompat.suppressCombatInputUntilRelease(minecraft);
        phase = PHASE_RESPAWN_TRAVEL;
        phaseTicks = 0L;
        travelTotalTicks = Math.max(1, transitionTicks);
        travelElapsedTicks = 0;
        travelFromX = currentCameraX;
        travelFromY = currentCameraY;
        travelFromZ = currentCameraZ;
        travelFromYaw = currentCameraYaw;
        travelFromPitch = currentCameraPitch;
        transitionTargetX = targetX;
        transitionTargetY = targetY;
        transitionTargetZ = targetZ;
        transitionTargetYaw = targetYaw;
        transitionTargetPitch = targetPitch;
    }

    public static void openDeployInstant(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int deployDelayTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        TaczClientInputCompat.suppressCombatInputUntilRelease(minecraft);
        active = true;
        deathVisualsActive = false;
        phase = PHASE_DEPLOY_OPEN;
        phaseTicks = 0L;
        killerEntityId = -1;
        deployConfirmDelayTicks = Math.max(0, deployDelayTicks);
        deployTicksSinceOpen = 0;
        travelTotalTicks = 0;
        travelElapsedTicks = 0;
        currentDesaturation = 0.0F;
        targetDesaturation = 0.0F;
        deathDesaturationTarget = 0.0F;
        disableDesaturate(minecraft);
        transitionTargetX = targetX;
        transitionTargetY = targetY;
        transitionTargetZ = targetZ;
        transitionTargetYaw = targetYaw;
        transitionTargetPitch = targetPitch;
        setCameraPose(targetX, targetY, targetZ, targetYaw, targetPitch, true);
    }

    public static void tick() {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            clear();
            return;
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (phase == PHASE_DEPLOY_OPEN) {
            deployTicksSinceOpen++;
            setCameraPose(transitionTargetX, transitionTargetY, transitionTargetZ, transitionTargetYaw, transitionTargetPitch, false);
            return;
        }
        if (phase == PHASE_RESPAWN_HOLD) {
            setCameraPose(transitionTargetX, transitionTargetY, transitionTargetZ, transitionTargetYaw, transitionTargetPitch, false);
            return;
        }
        if (phase == PHASE_DOWNED_HOLD) {
            tickDownedHold(player);
            return;
        }
        switch (phase) {
            case PHASE_DEATH_FALL -> tickDeathFall(player);
            case PHASE_DEATH_WAIT -> tickDeathWait(player);
            case PHASE_DEATH_RECOVER -> tickDeathRecover(player);
            case PHASE_DEATH_HOLD -> tickDeathHold(player);
            case PHASE_DEPLOY_TRAVEL -> tickDeployTravel(player);
            case PHASE_RESPAWN_TRAVEL -> tickRespawnTravel(player);
            default -> {
            }
        }
    }

    public static void renderFrame(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            disableDesaturate(minecraft);
            currentDesaturation = 0.0F;
            targetDesaturation = 0.0F;
            deathDesaturationTarget = 0.0F;
            currentDarkness = 0.0F;
            targetDarkness = 0.0F;
            deathDarknessTarget = 0.0F;
            hurtFlashRemainSeconds = 0.0F;
            lastHurtTime = -1;
            lastVisualUpdateNanos = -1L;
            return;
        }
        updateVisualEffects(minecraft, player);
    }

    public static void applyCameraOverride(Camera camera, float partialTick) {
        if (!active || camera == null) {
            return;
        }
        if (phase == PHASE_DOWNED_HOLD) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            Vec3 position = new Vec3(currentCameraX, currentCameraY, currentCameraZ);
            float yaw = player == null ? currentCameraYaw : player.getYRot();
            float pitch = player == null ? currentCameraPitch : player.getXRot();
            invokeCameraPosition(camera, position);
            invokeCameraRotation(camera, yaw, pitch);
            return;
        }
        Vec3 position = getInterpolatedCameraPosition(partialTick);
        float yaw = getInterpolatedCameraYaw(partialTick);
        float pitch = getInterpolatedCameraPitch(partialTick);
        invokeCameraPosition(camera, position);
        invokeCameraRotation(camera, yaw, pitch);
    }

    public static boolean hasActiveCameraOverride() {
        return active;
    }

    public static boolean shouldRenderDeployBoundaries() {
        return active && (phase == PHASE_DEPLOY_TRAVEL || phase == PHASE_DEPLOY_OPEN);
    }

    public static boolean isDeployOpenPhase() {
        return active && phase == PHASE_DEPLOY_OPEN;
    }

    public static boolean isDeathObservationPhase() {
        if (!active) {
            return false;
        }
        return phase == PHASE_DEATH_FALL
            || phase == PHASE_DEATH_WAIT
            || phase == PHASE_DEATH_RECOVER
            || phase == PHASE_DEATH_HOLD;
    }

    public static boolean shouldHideFirstPersonHands() {
        return false;
    }

    public static void enterDownedView() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        active = true;
        deathVisualsActive = false;
        phase = PHASE_DOWNED_HOLD;
        phaseTicks = 0L;
        player.setYRot(currentCameraYaw);
        player.setYHeadRot(currentCameraYaw);
        player.setXRot(currentCameraPitch);
        setCameraPose(currentCameraX, currentCameraY, currentCameraZ, currentCameraYaw, currentCameraPitch, true);
    }

    public static boolean canConfirmDeploySelection() {
        return active && phase == PHASE_DEPLOY_OPEN && deployTicksSinceOpen >= deployConfirmDelayTicks;
    }

    public static int currentDeployCountdownRemainingTicks() {
        if (!active || (phase != PHASE_DEPLOY_TRAVEL && phase != PHASE_DEPLOY_OPEN)) {
            return -1;
        }
        return Math.max(0, deployConfirmDelayTicks - deployTicksSinceOpen);
    }

    public static float currentDarknessAlpha() {
        return Mth.clamp(currentDarkness, 0.0F, 0.985F);
    }

    public static float getStandardDeathViewDarkness() {
        return STANDARD_DEATH_VIEW_DARKNESS;
    }

    public static void setDownedBlackoutStrength(float darkness) {
        downedBlackoutTarget = Mth.clamp(darkness, 0.0F, 0.992F);
    }

    public static float currentHurtFlashAlpha() {
        if (hurtFlashRemainSeconds <= 0.0F) {
            return 0.0F;
        }
        float progress = 1.0F - (hurtFlashRemainSeconds / HURT_FLASH_DURATION_SECONDS);
        float alpha = progress <= 0.35F ? progress / 0.35F : 1.0F - ((progress - 0.35F) / 0.65F);
        return Mth.clamp(alpha * 0.65F, 0.0F, 0.65F);
    }

    public static float currentDeployIconAlpha() {
        return Mth.clamp(currentDeployIconAlpha, 0.0F, 1.0F);
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        TaczClientInputCompat.suppressCombatInputUntilRelease(minecraft);
        disableDesaturate(minecraft);
        active = false;
        matchEndViewLocked = false;
        deathVisualsActive = false;
        phase = PHASE_IDLE;
        phaseTicks = 0L;
        killerEntityId = -1;
        deployConfirmDelayTicks = 0;
        deployTicksSinceOpen = 0;
        travelTotalTicks = 0;
        travelElapsedTicks = 0;
        previousCameraX = 0.0D;
        previousCameraY = 0.0D;
        previousCameraZ = 0.0D;
        previousCameraYaw = 0.0F;
        previousCameraPitch = 0.0F;
        currentCameraX = 0.0D;
        currentCameraY = 0.0D;
        currentCameraZ = 0.0D;
        currentCameraYaw = 0.0F;
        currentCameraPitch = 0.0F;
        deathDesaturationTarget = 0.0F;
        deathDarknessTarget = 0.0F;
        downedBlackoutTarget = 0.0F;
        currentDeployIconAlpha = 0.0F;
        targetDeployIconAlpha = 0.0F;
        boundaryPenaltyActive = false;
        hurtFlashRemainSeconds = 0.0F;
        lastHurtTime = -1;
        lastVisualUpdateNanos = -1L;
    }

    public static void setMatchEndViewLocked(boolean locked) {
        matchEndViewLocked = locked;
        if (!locked) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        disableDesaturate(minecraft);
        active = false;
        deathVisualsActive = false;
        phase = PHASE_IDLE;
        currentDeployIconAlpha = 0.0F;
        targetDeployIconAlpha = 0.0F;
        downedBlackoutTarget = 0.0F;
    }

    public static void startMatchEndTransition(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, int transitionTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        disableDesaturate(minecraft);
        matchEndViewLocked = false;
        active = true;
        deathVisualsActive = false;
        phase = PHASE_RESPAWN_TRAVEL;
        phaseTicks = 0L;
        killerEntityId = -1;
        deployConfirmDelayTicks = 0;
        deployTicksSinceOpen = 0;
        travelTotalTicks = 0;
        travelElapsedTicks = 0;
        transitionTargetX = targetX;
        transitionTargetY = targetY;
        transitionTargetZ = targetZ;
        transitionTargetYaw = targetYaw;
        transitionTargetPitch = targetPitch;
        setCameraPose(targetX, targetY, targetZ, targetYaw, targetPitch, true);
        phase = PHASE_RESPAWN_HOLD;
        phaseTicks = 0L;
        currentDeployIconAlpha = 0.0F;
        targetDeployIconAlpha = 0.0F;
    }

    public static void setBoundaryPenaltyActive(boolean activeFlag) {
        boundaryPenaltyActive = activeFlag;
    }

    private static void updateVisualEffects(Minecraft minecraft, LocalPlayer player) {
        long now = System.nanoTime();
        float deltaSeconds = lastVisualUpdateNanos <= 0L ? (1.0F / 120.0F) : (float) ((now - lastVisualUpdateNanos) / 1_000_000_000.0D);
        deltaSeconds = Mth.clamp(deltaSeconds, 0.0F, 0.05F);
        lastVisualUpdateNanos = now;
        float lowHealthDarkness = computeLowHealthDarkness(player.getHealth());
        float sharedDeathDarkness = Math.max(STANDARD_DEATH_VIEW_DARKNESS, deathDarknessTarget);
        targetDesaturation = (deathVisualsActive || phase == PHASE_DOWNED_HOLD) ? deathDesaturationTarget : 0.0F;
        if (boundaryPenaltyActive) {
            targetDesaturation = Math.max(targetDesaturation, DEATH_DESATURATION_LEVEL);
        }
        boolean deployPhase = phase == PHASE_DEPLOY_TRAVEL || phase == PHASE_DEPLOY_OPEN;
        boolean respawnPhase = phase == PHASE_RESPAWN_TRAVEL || phase == PHASE_RESPAWN_HOLD;
        if (deployPhase || respawnPhase) {
            targetDarkness = 0.0F;
        } else if (phase == PHASE_DOWNED_HOLD) {
            targetDarkness = Math.max(sharedDeathDarkness, downedBlackoutTarget);
        } else if (deathVisualsActive) {
            targetDarkness = sharedDeathDarkness;
        } else {
            targetDarkness = Math.max(lowHealthDarkness, downedBlackoutTarget);
        }
        targetDeployIconAlpha = resolveDeployIconAlphaTarget();
        if (!active && lastHurtTime >= 0 && player.hurtTime > lastHurtTime) {
            hurtFlashRemainSeconds = HURT_FLASH_DURATION_SECONDS;
        }
        lastHurtTime = player.hurtTime;
        currentDesaturation = exponentialApproach(currentDesaturation, targetDesaturation, deltaSeconds, VISUAL_SMOOTH_SHARPNESS);
        currentDarkness = exponentialApproach(currentDarkness, targetDarkness, deltaSeconds, VISUAL_SMOOTH_SHARPNESS);
        currentDeployIconAlpha = exponentialApproach(currentDeployIconAlpha, targetDeployIconAlpha, deltaSeconds, 8.0F);
        hurtFlashRemainSeconds = Math.max(0.0F, hurtFlashRemainSeconds - deltaSeconds);
        applyDesaturation(minecraft, currentDesaturation);
    }

    private static float resolveDeployIconAlphaTarget() {
        if (matchEndViewLocked || !active) {
            return 0.0F;
        }
        if (phase == PHASE_DEPLOY_OPEN) {
            return 1.0F;
        }
        return 0.0F;
    }

    private static float computeLowHealthDarkness(float health) {
        float clamped = Mth.clamp(health, 0.0F, 20.0F);
        if (clamped >= 5.0F) {
            return 0.0F;
        }
        float ratio = (5.0F - clamped) / 5.0F;
        return Mth.clamp((float) Math.pow(ratio, 1.15D) * 0.90F, 0.0F, 0.70F);
    }

    private static float exponentialApproach(float current, float target, float deltaSeconds, float sharpness) {
        float t = 1.0F - (float) Math.exp(-sharpness * deltaSeconds);
        return Mth.lerp(t, current, target);
    }


    private static void tickDeathFall(LocalPlayer player) {
        phaseTicks++;
        double fallProgress = easeOutCubic(normalizedProgress(phaseTicks, DEATH_FALL_TICKS));
        double lookProgress = easeOutCubic(normalizedProgress(phaseTicks, DEATH_LOOK_UP_TICKS));
        double x = Mth.lerp(fallProgress, deathX, deathFallTargetX);
        double y = Mth.lerp(fallProgress, deathY, deathFallTargetY);
        double z = Mth.lerp(fallProgress, deathZ, deathFallTargetZ);
        float pitch = Mth.lerp((float) lookProgress, deathStartPitch, -70.0F);
        setCameraPose(x, y, z, deathStartYaw, pitch, false);
        if (phaseTicks >= DEATH_FALL_TICKS) {
            phase = PHASE_DEATH_WAIT;
            phaseTicks = 0L;
        }
    }

    private static void tickDeathWait(LocalPlayer player) {
        phaseTicks++;
        setCameraPose(deathFallTargetX, deathFallTargetY, deathFallTargetZ, deathStartYaw, -70.0F, false);
        if (phaseTicks < DEATH_WAIT_TICKS) {
            return;
        }
        trackBlendStartYaw = currentCameraYaw;
        trackBlendStartPitch = currentCameraPitch;
        phase = PHASE_DEATH_RECOVER;
        phaseTicks = 0L;
    }

    private static void tickDeathRecover(LocalPlayer player) {
        phaseTicks++;
        double progress = easeOutCubic(normalizedProgress(phaseTicks, DEATH_RECOVER_TICKS));
        float[] targetAngles = resolveTrackedKillerAngles(Minecraft.getInstance(), deathFallTargetX, deathFallTargetY, deathFallTargetZ);
        float yaw = Mth.rotLerp((float) progress, trackBlendStartYaw, targetAngles[0]);
        float pitch = Mth.lerp((float) progress, trackBlendStartPitch, targetAngles[1]);
        setCameraPose(deathFallTargetX, deathFallTargetY, deathFallTargetZ, yaw, pitch, false);
        if (phaseTicks >= DEATH_RECOVER_TICKS) {
            if (ClientDownedState.isActive()) {
                enterDownedView();
            } else {
                phase = PHASE_DEATH_HOLD;
                phaseTicks = 0L;
            }
        }
    }

    private static void tickDeathHold(LocalPlayer player) {
        setCameraPose(currentCameraX, currentCameraY, currentCameraZ, currentCameraYaw, currentCameraPitch, false);
    }

    private static void tickDeployTravel(LocalPlayer player) {
        phaseTicks++;
        deployTicksSinceOpen++;
        travelElapsedTicks++;
        double progress = easeOutCubic(normalizedProgress(travelElapsedTicks, travelTotalTicks));
        double x = Mth.lerp(progress, travelFromX, transitionTargetX);
        double y = Mth.lerp(progress, travelFromY, transitionTargetY);
        double z = Mth.lerp(progress, travelFromZ, transitionTargetZ);
        float yaw = Mth.rotLerp((float) progress, travelFromYaw, transitionTargetYaw);
        float pitch = Mth.lerp((float) progress, travelFromPitch, transitionTargetPitch);
        setCameraPose(x, y, z, yaw, pitch, false);
        if (progress >= 1.0D) {
            phase = PHASE_DEPLOY_OPEN;
            phaseTicks = 0L;
        }
    }

    private static void tickRespawnTravel(LocalPlayer player) {
        phaseTicks++;
        travelElapsedTicks++;
        double progress = easeOutCubic(normalizedProgress(travelElapsedTicks, travelTotalTicks));
        double x = Mth.lerp(progress, travelFromX, transitionTargetX);
        double y = Mth.lerp(progress, travelFromY, transitionTargetY);
        double z = Mth.lerp(progress, travelFromZ, transitionTargetZ);
        float yaw = Mth.rotLerp((float) progress, travelFromYaw, transitionTargetYaw);
        float pitch = Mth.lerp((float) progress, travelFromPitch, transitionTargetPitch);
        setCameraPose(x, y, z, yaw, pitch, false);
        if (progress >= 1.0D) {
            phase = PHASE_RESPAWN_HOLD;
            phaseTicks = 0L;
        }
    }

    private static void tickDownedHold(LocalPlayer player) {
        if (player == null) {
            return;
        }
        phaseTicks++;
    }

    public static Vec3 getInterpolatedCameraPosition(float partialTick) {
        if (phase == PHASE_DOWNED_HOLD) {
            return new Vec3(currentCameraX, currentCameraY, currentCameraZ);
        }
        float t = Mth.clamp(partialTick, 0.0F, 1.0F);
        return new Vec3(
                Mth.lerp(t, previousCameraX, currentCameraX),
                Mth.lerp(t, previousCameraY, currentCameraY),
                Mth.lerp(t, previousCameraZ, currentCameraZ)
        );
    }

    public static float getInterpolatedCameraYaw(float partialTick) {
        if (phase == PHASE_DOWNED_HOLD) {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player == null ? currentCameraYaw : minecraft.player.getYRot();
        }
        return Mth.rotLerp(Mth.clamp(partialTick, 0.0F, 1.0F), previousCameraYaw, currentCameraYaw);
    }

    public static float getInterpolatedCameraPitch(float partialTick) {
        if (phase == PHASE_DOWNED_HOLD) {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player == null ? currentCameraPitch : minecraft.player.getXRot();
        }
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), previousCameraPitch, currentCameraPitch);
    }

    private static void setCameraPose(double x, double y, double z, float yaw, float pitch, boolean initialize) {
        if (initialize) {
            previousCameraX = x;
            previousCameraY = y;
            previousCameraZ = z;
            previousCameraYaw = yaw;
            previousCameraPitch = pitch;
        } else {
            previousCameraX = currentCameraX;
            previousCameraY = currentCameraY;
            previousCameraZ = currentCameraZ;
            previousCameraYaw = currentCameraYaw;
            previousCameraPitch = currentCameraPitch;
        }
        currentCameraX = x;
        currentCameraY = y;
        currentCameraZ = z;
        currentCameraYaw = yaw;
        currentCameraPitch = pitch;
    }

    private static void invokeCameraPosition(Camera camera, Vec3 position) {
        try {
            Method method = cameraSetPositionMethod;
            if (method == null) {
                method = findCameraMethod(camera.getClass(), new String[]{"setPosition", "setPos"}, double.class, double.class, double.class);
                cameraSetPositionMethod = method;
            }
            if (method != null) {
                method.invoke(camera, position.x, position.y, position.z);
                return;
            }
            Method vecMethod = cameraSetPositionVecMethod;
            if (vecMethod == null) {
                vecMethod = findCameraMethod(camera.getClass(), new String[]{"setPosition", "setPos"}, Vec3.class);
                cameraSetPositionVecMethod = vecMethod;
            }
            if (vecMethod != null) {
                vecMethod.invoke(camera, position);
                return;
            }
        } catch (Exception ignored) {
        }
        applyCameraPositionByField(camera, position);
    }

    private static void invokeCameraRotation(Camera camera, float yaw, float pitch) {
        Method method = cameraSetRotationMethod;
        if (method == null) {
            method = findCameraMethod(camera.getClass(), "setRotation", float.class, float.class);
            cameraSetRotationMethod = method;
        }
        if (method == null) {
            return;
        }
        try {
            method.invoke(camera, yaw, pitch);
        } catch (Exception ignored) {
        }
    }

    private static Method findCameraMethod(Class<?> cameraClass, String primaryName, Class<?>... parameterTypes) {
        return findCameraMethod(cameraClass, new String[]{primaryName}, parameterTypes);
    }

    private static Method findCameraMethod(Class<?> cameraClass, String primaryName, String fallbackName, Class<?>... parameterTypes) {
        return findCameraMethod(cameraClass, new String[]{primaryName, fallbackName}, parameterTypes);
    }

    private static Method findCameraMethod(Class<?> cameraClass, String[] names, Class<?>... parameterTypes) {
        for (String name : names) {
            if (name == null) {
                continue;
            }
            try {
                Method method = cameraClass.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (Exception ignored) {
            }
            try {
                Method method = cameraClass.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void applyCameraPositionByField(Camera camera, Vec3 position) {
        try {
            Field positionField = cameraPositionField;
            if (positionField == null) {
                positionField = findCameraField(camera.getClass(), Vec3.class, "position");
                cameraPositionField = positionField;
            }
            if (positionField != null) {
                positionField.set(camera, position);
            }
            Field blockPosField = cameraBlockPositionField;
            if (blockPosField == null) {
                blockPosField = findCameraField(camera.getClass(), BlockPos.MutableBlockPos.class, "blockPosition");
                cameraBlockPositionField = blockPosField;
            }
            if (blockPosField != null) {
                Object current = blockPosField.get(camera);
                if (current instanceof BlockPos.MutableBlockPos mutable) {
                    mutable.set(position.x, position.y, position.z);
                } else {
                    blockPosField.set(camera, new BlockPos.MutableBlockPos(Mth.floor(position.x), Mth.floor(position.y), Mth.floor(position.z)));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Field findCameraField(Class<?> cameraClass, Class<?> fieldType, String preferredName) {
        try {
            Field field = cameraClass.getDeclaredField(preferredName);
            field.setAccessible(true);
            if (fieldType.isAssignableFrom(field.getType())) {
                return field;
            }
        } catch (Exception ignored) {
        }
        for (Field field : cameraClass.getDeclaredFields()) {
            if (!fieldType.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            return field;
        }
        return null;
    }

    private static float[] resolveTrackedKillerAngles(Minecraft minecraft, double fromX, double fromY, double fromZ) {
        if (minecraft.level != null && killerEntityId > 0) {
            Entity entity = minecraft.level.getEntity(killerEntityId);
            if (entity != null) {
                Vec3 eye = entity.getEyePosition();
                trackedKillerX = eye.x;
                trackedKillerY = eye.y;
                trackedKillerZ = eye.z;
            }
        }
        double dx = trackedKillerX - fromX;
        double dy = trackedKillerY - fromY;
        double dz = trackedKillerZ - fromZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));
        return new float[]{yaw, pitch};
    }

    private static void applyDesaturation(Minecraft minecraft, float desaturationLevel) {
        if (desaturationLevel <= 0.01F) {
            disableDesaturate(minecraft);
            return;
        }
        enableDesaturate(minecraft);
    }

    private static double normalizedProgress(long tick, int maxTicks) {
        return Math.min(1.0D, tick / (double) Math.max(1, maxTicks));
    }

    private static double easeOutCubic(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - clamped, 3.0D);
    }

    private static void enableDesaturate(Minecraft minecraft) {
        if (shaderLoaded) {
            return;
        }
        try {
            minecraft.gameRenderer.loadEffect(ResourceLocation.withDefaultNamespace("shaders/post/desaturate.json"));
            shaderLoaded = true;
        } catch (Exception exception) {
            shaderLoaded = false;
        }
    }

    private static void disableDesaturate(Minecraft minecraft) {
        if (!shaderLoaded) {
            return;
        }
        try {
            minecraft.gameRenderer.shutdownEffect();
        } catch (Exception ignored) {
        }
        shaderLoaded = false;
    }
}
