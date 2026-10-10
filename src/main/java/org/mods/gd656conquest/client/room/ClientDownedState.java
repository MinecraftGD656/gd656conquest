package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.PlayerAnimatorHelpRequestPacket;
import org.mods.gd656conquest.network.packet.RoomDownedActionPacket;
import org.mods.gd656conquest.client.audio.DownedLoopSoundInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ClientDownedState {
    private static final int MAX_RESCUERS = 4;
    private static final double MAX_RESCUER_DISTANCE_SQR = 30.0D * 30.0D;
    private static final int DOWNED_DURATION_TICKS = 30 * 20;
    private static final int SURRENDER_INPUT_ARM_DELAY_TICKS = 3;
    private static final int SURRENDER_HOLD_TICKS = 30;
    private static final int REDEPLOY_REQUEST_RETRY_TICKS = 20;
    private static final float SURRENDER_RELEASE_RECOVER_PER_TICK = 2.6F;
    private static final int HELP_STATUS_TICKS = 40;
    private static final long HELP_REQUEST_COOLDOWN_MS = 1_250L;
    private static final int DEBUG_FINISH_BLACK_TICKS = 8;
    private static final float DOWNED_BASE_BLACKOUT = ClientDeathFlowState.getStandardDeathViewDarkness();
    private static final float DOWNED_FINAL_COUNTDOWN_BLACKOUT = 0.90F;
    private static final float DOWNED_FULL_SURRENDER_BLACKOUT = 0.992F;
    private static final int TEXT_PRIMARY = 0xFFE7E1DB;
    private static final int TEXT_MUTED = 0xFFA8A0A0;
    private static final int TEXT_HOT = 0xFFB63A3A;
    private static final int TEXT_HOT_BRIGHT = 0xFFFF5A5A;
    private static final int TEXT_HELP_ACTIVE = 0xFF53D16A;
    private static final int TEXT_DISTANCE = 0xFF1F7A38;
    private static final int TEXT_SQUAD = 0xFF6BFF88;
    private static final int TEXT_TEAM = 0xFF4FE7FF;
    private static final List<ResourceLocation> DOWNED_LOOP_SOUND_IDS_SPLIT = List.of(
        new ResourceLocation("gd656frontvoice", "downed.loop.bleedout_loop_01_join"),
        new ResourceLocation("gd656frontvoice", "downed.loop.heartbeats_02")
    );
    private static final List<ResourceLocation> DOWNED_LOOP_SOUND_IDS_LEGACY = List.of(
        new ResourceLocation("gd656frontvoice", "downed.loop")
    );

    private static boolean active;
    private static boolean debugPreview;
    private static boolean surrenderRequested;
    private static int debugFinishTicksRemaining;
    private static int remainTicks;
    private static int helpStatusTicksRemaining;
    private static int surrenderInputArmTicksRemaining;
    private static int redeployRequestRetryTicksRemaining;
    private static long debugExpireAtMs;
    private static long helpCooldownUntilMs;
    private static float surrenderHoldTicks;
    private static List<RescuerEntry> rescuers = List.of();
    private static List<DownedLoopSoundInstance> loopSounds = List.of();

    private ClientDownedState() {
    }

    public static void startOrUpdate(boolean startCameraSequence, double deathX, double deathY, double deathZ, int killerEntityId, int remainTicksValue, List<RescuerEntry> rescuerEntries) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        boolean enteringFreshDowned = startCameraSequence || !active;
        if (enteringFreshDowned) {
            TaczClientInputCompat.clearCrawlState(minecraft);
            TaczClientInputCompat.suppressCombatInputUntilRelease(minecraft);
            if (!ClientDeathFlowState.isDeathObservationPhase() && !ClientDeathFlowState.hasActiveCameraOverride()) {
                ClientDeathFlowState.clear();
            }
        }
        active = true;
        debugPreview = false;
        if (enteringFreshDowned) {
            surrenderRequested = false;
            debugFinishTicksRemaining = 0;
            surrenderHoldTicks = 0.0F;
            helpStatusTicksRemaining = 0;
            helpCooldownUntilMs = 0L;
            surrenderInputArmTicksRemaining = SURRENDER_INPUT_ARM_DELAY_TICKS;
            redeployRequestRetryTicksRemaining = 0;
        }
        remainTicks = Math.max(0, remainTicksValue);
        debugExpireAtMs = 0L;
        rescuers = sanitizeRescuers(rescuerEntries);
        
        if (enteringFreshDowned) ClientKillerCardState.downedStarted();
        ensureLoopSoundsPlaying(minecraft);
    }

    public static void startDebugPreview(int durationMs) {
        if (active && !debugPreview) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        active = true;
        debugPreview = true;
        surrenderRequested = false;
        debugFinishTicksRemaining = 0;
        remainTicks = Math.max(20, Math.round(durationMs / 50.0F));
        debugExpireAtMs = System.currentTimeMillis() + Math.max(1000L, durationMs);
        helpStatusTicksRemaining = 0;
        helpCooldownUntilMs = 0L;
        redeployRequestRetryTicksRemaining = 0;
        rescuers = buildLocalPreviewRescuers(player);
        ensureLoopSoundsPlaying(minecraft);
        ClientDeathFlowState.clear();
        ClientKillerCardState.downedStarted();
    }

    public static void clear() {
        TaczClientInputCompat.clearTransientCombatState(Minecraft.getInstance());
        active = false;
        debugPreview = false;
        surrenderRequested = false;
        debugFinishTicksRemaining = 0;
        remainTicks = 0;
        debugExpireAtMs = 0L;
        helpStatusTicksRemaining = 0;
        helpCooldownUntilMs = 0L;
        surrenderInputArmTicksRemaining = 0;
        redeployRequestRetryTicksRemaining = 0;
        surrenderHoldTicks = 0.0F;
        rescuers = List.of();
        if (!loopSounds.isEmpty()) {
            for (DownedLoopSoundInstance loopSound : loopSounds) {
                if (loopSound != null) {
                    loopSound.fadeOut();
                }
            }
            loopSounds = List.of();
        }
        ClientDeathFlowState.clear();
        ClientDeathFlowState.setDownedBlackoutStrength(0.0F);
        DownedRagdollClientHooks.onDownedCleared();
    }

    private static void ensureLoopSoundsPlaying(Minecraft minecraft) {
        if (minecraft == null || minecraft.getSoundManager() == null) {
            return;
        }
        List<ResourceLocation> targetLoopSoundIds = resolveDownedLoopSoundIds(minecraft.getSoundManager());
        List<DownedLoopSoundInstance> activeLoopSounds = new ArrayList<>();
        for (DownedLoopSoundInstance loopSound : loopSounds) {
            if (loopSound != null && !loopSound.isStopped()) {
                activeLoopSounds.add(loopSound);
            }
        }
        if (matchesTargetLoopSoundIds(activeLoopSounds, targetLoopSoundIds)) {
            loopSounds = activeLoopSounds;
            return;
        }
        for (DownedLoopSoundInstance loopSound : activeLoopSounds) {
            loopSound.fadeOut();
        }
        List<DownedLoopSoundInstance> newLoopSounds = new ArrayList<>(targetLoopSoundIds.size());
        for (ResourceLocation soundId : targetLoopSoundIds) {
            DownedLoopSoundInstance loopSound = new DownedLoopSoundInstance(soundId);
            minecraft.getSoundManager().play(loopSound);
            newLoopSounds.add(loopSound);
        }
        loopSounds = List.copyOf(newLoopSounds);
    }

    private static boolean matchesTargetLoopSoundIds(List<DownedLoopSoundInstance> activeLoopSounds, List<ResourceLocation> targetLoopSoundIds) {
        if (activeLoopSounds.size() != targetLoopSoundIds.size()) {
            return false;
        }
        for (int i = 0; i < targetLoopSoundIds.size(); i++) {
            if (!targetLoopSoundIds.get(i).equals(activeLoopSounds.get(i).soundId())) {
                return false;
            }
        }
        return true;
    }

    private static List<ResourceLocation> resolveDownedLoopSoundIds(SoundManager soundManager) {
        if (allLoopSoundsResolvable(soundManager, DOWNED_LOOP_SOUND_IDS_SPLIT)) {
            return DOWNED_LOOP_SOUND_IDS_SPLIT;
        }
        return DOWNED_LOOP_SOUND_IDS_LEGACY;
    }

    private static boolean allLoopSoundsResolvable(SoundManager soundManager, List<ResourceLocation> soundIds) {
        for (ResourceLocation soundId : soundIds) {
            if (!isLoopSoundResolvable(soundManager, soundId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLoopSoundResolvable(SoundManager soundManager, ResourceLocation soundId) {
        DownedLoopSoundInstance probe = new DownedLoopSoundInstance(soundId);
        WeighedSoundEvents soundEvents = probe.resolve(soundManager);
        return soundEvents != null && soundEvents.getWeight() > 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isDebugPreviewActive() {
        return active && debugPreview;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDownedDeployState.tick();
        boolean beingRevived = isBeingRevivedLocked();
        if (!active || minecraft.player == null) {
            ClientDeathFlowState.setDownedBlackoutStrength(0.0F);
            return;
        }
        if (debugPreview) {
            long remainMs = Math.max(0L, debugExpireAtMs - System.currentTimeMillis());
            remainTicks = (int) Math.ceil(remainMs / 50.0D);
            if (!surrenderRequested && remainTicks <= 0) {
                clear();
                return;
            }
            if (minecraft.level != null && minecraft.level.getGameTime() % 10L == 0L) {
                rescuers = buildLocalPreviewRescuers(minecraft.player);
            }
        }
        if (beingRevived) {
            surrenderRequested = false;
            redeployRequestRetryTicksRemaining = 0;
            surrenderHoldTicks = 0.0F;
        }
        if (!surrenderRequested && surrenderInputArmTicksRemaining > 0) {
            surrenderInputArmTicksRemaining--;
            surrenderHoldTicks = 0.0F;
        }
        if (surrenderRequested && !debugPreview && redeployRequestRetryTicksRemaining > 0) {
            redeployRequestRetryTicksRemaining--;
            if (redeployRequestRetryTicksRemaining <= 0) {
                surrenderRequested = false;
                surrenderInputArmTicksRemaining = SURRENDER_INPUT_ARM_DELAY_TICKS;
                surrenderHoldTicks = Math.min(surrenderHoldTicks, SURRENDER_HOLD_TICKS * 0.85F);
            }
        }
        if (helpStatusTicksRemaining > 0) {
            helpStatusTicksRemaining--;
        }
        boolean inputArmed = surrenderInputArmTicksRemaining <= 0;
        boolean holdingJump = inputArmed
            && !beingRevived
            && !surrenderRequested
            && minecraft.screen == null
            && minecraft.options != null
            && ClientDownedDeployState.allowLongPress(minecraft.options.keyJump);
        if (holdingJump) {
            surrenderHoldTicks = Math.min(SURRENDER_HOLD_TICKS, surrenderHoldTicks + 1.0F);
        } else if (!surrenderRequested) {
            surrenderHoldTicks = Math.max(0.0F, surrenderHoldTicks - SURRENDER_RELEASE_RECOVER_PER_TICK);
        }
        if (!surrenderRequested && surrenderHoldTicks >= SURRENDER_HOLD_TICKS - 0.001F) {
            surrenderRequested = true;
            surrenderHoldTicks = SURRENDER_HOLD_TICKS;
            if (debugPreview) {
                debugFinishTicksRemaining = DEBUG_FINISH_BLACK_TICKS;
            } else {
                redeployRequestRetryTicksRemaining = REDEPLOY_REQUEST_RETRY_TICKS;
                NetworkManager.getChannel().sendToServer(RoomDownedActionPacket.surrender());
            }
        }
        if (debugPreview && surrenderRequested && debugFinishTicksRemaining > 0) {
            debugFinishTicksRemaining--;
            if (debugFinishTicksRemaining <= 0) {
                clear();
                return;
            }
        }
        float deathFadeProgress = 1.0F - Mth.clamp(remainTicks / (float) DOWNED_DURATION_TICKS, 0.0F, 1.0F);
        float imminentDeathBlackout = Mth.lerp(
            (float) Math.pow(deathFadeProgress, 1.35D),
            DOWNED_BASE_BLACKOUT,
            DOWNED_FINAL_COUNTDOWN_BLACKOUT
        );
        float surrenderProgress = Mth.clamp(surrenderHoldTicks / SURRENDER_HOLD_TICKS, 0.0F, 1.0F);
        float surrenderBlackout = surrenderRequested
            ? DOWNED_FULL_SURRENDER_BLACKOUT
            : Mth.lerp(surrenderProgress, DOWNED_BASE_BLACKOUT, DOWNED_FULL_SURRENDER_BLACKOUT);
        float blackoutStrength = Math.max(imminentDeathBlackout, surrenderBlackout);
        ClientDeathFlowState.setDownedBlackoutStrength(blackoutStrength);
    }

    public static void render(GuiGraphics gui, Font font) {
        ClientKillerCardState.render(gui, font, resolveSurrenderHintText(), resolveSurrenderHintColor(),
            resolveHelpHintText(), resolveHelpHintColor(), rescuers);
    }

    public static int killerCardRemainingTicks() { return remainTicks; }

    public static void requestReadyDeployment() {
        if (!active || surrenderRequested || isBeingRevivedLocked()) return;
        surrenderRequested = true;
        surrenderHoldTicks = SURRENDER_HOLD_TICKS;
        redeployRequestRetryTicksRemaining = REDEPLOY_REQUEST_RETRY_TICKS;
        NetworkManager.getChannel().sendToServer(RoomDownedActionPacket.surrender());
    }

    public static boolean handleLeftClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || surrenderRequested || minecraft == null || minecraft.player == null || minecraft.screen != null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (helpCooldownUntilMs > now) {
            return true;
        }
        helpCooldownUntilMs = now + HELP_REQUEST_COOLDOWN_MS;
        helpStatusTicksRemaining = HELP_STATUS_TICKS;
        minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        NetworkManager.getChannel().sendToServer(new PlayerAnimatorHelpRequestPacket());
        return true;
    }

    private static boolean isBeingRevivedLocked() {
        return "being_revived".equals(ClientRoomLockState.snapshot().reason());
    }

    private static String resolveSurrenderHintText() {
        if (isBeingRevivedLocked()) {
            return "正在被急救";
        }
        if (surrenderRequested) {
            return "已放弃救援";
        }
        int percent = Math.round(Mth.clamp(surrenderHoldTicks / SURRENDER_HOLD_TICKS, 0.0F, 1.0F) * 100.0F);
        return "长按空格放弃 " + percent + "%";
    }

    private static int resolveSurrenderHintColor() {
        return surrenderRequested ? TEXT_HOT_BRIGHT : TEXT_HOT;
    }

    private static String resolveHelpHintText() {
        if (helpStatusTicksRemaining > 0) {
            return "正在呼救";
        }
        return "点按左键呼救";
    }

    private static int resolveHelpHintColor() {
        return helpStatusTicksRemaining > 0 ? TEXT_HELP_ACTIVE : TEXT_SQUAD;
    }

    private static List<RescuerEntry> sanitizeRescuers(List<RescuerEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<RescuerEntry> result = new ArrayList<>();
        for (RescuerEntry entry : entries) {
            if (entry == null || entry.name() == null || entry.name().isBlank()) {
                continue;
            }
            result.add(new RescuerEntry(entry.name().trim(), Math.max(1, entry.distanceMeters()), entry.squadMate()));
            if (result.size() >= MAX_RESCUERS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static List<RescuerEntry> buildLocalPreviewRescuers(LocalPlayer self) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || self == null) {
            return List.of();
        }
        List<Player> candidates = new ArrayList<>();
        for (Player other : minecraft.level.players()) {
            if (other == null || other == self || !other.isAlive()) {
                continue;
            }
            if (!ClientRoomTeamMirror.isTeammate(other)) {
                continue;
            }
            double distanceSqr = other.distanceToSqr(self);
            if (distanceSqr > MAX_RESCUER_DISTANCE_SQR) {
                continue;
            }
            candidates.add(other);
        }
        candidates.sort(
            Comparator.<Player>comparingInt(other -> ClientRoomTeamMirror.isSquadMate(other) ? 0 : 1)
                .thenComparingDouble(other -> other.distanceToSqr(self))
        );
        List<RescuerEntry> result = new ArrayList<>();
        for (Player other : candidates) {
            result.add(new RescuerEntry(
                other.getGameProfile().getName(),
                Math.max(1, Math.round(other.distanceTo(self))),
                ClientRoomTeamMirror.isSquadMate(other)
            ));
            if (result.size() >= MAX_RESCUERS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static void drawRightAligned(GuiGraphics gui, Font font, String text, int right, int y, int color) {
        gui.drawString(font, text, right - font.width(text), y, color, true);
    }

    private static void drawRightAlignedParts(
        GuiGraphics gui,
        Font font,
        int right,
        int y,
        String leftText,
        int leftColor,
        String rightText,
        int rightColor
    ) {
        int totalWidth = font.width(leftText) + font.width(rightText);
        int startX = right - totalWidth;
        gui.drawString(font, leftText, startX, y, leftColor, true);
        gui.drawString(font, rightText, startX + font.width(leftText), y, rightColor, true);
    }

    public record RescuerEntry(String name, int distanceMeters, boolean squadMate) {
    }
}
