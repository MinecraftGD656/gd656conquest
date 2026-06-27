package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.mods.gd656conquest.client.compat.tacz.TaczClientInputCompat;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.PlayerAnimatorHelpRequestPacket;
import org.mods.gd656conquest.network.packet.RoomDownedActionPacket;

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
        ClientDeathFlowState.clear();
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
        ClientDeathFlowState.clear();
        ClientDeathFlowState.setDownedBlackoutStrength(0.0F);
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isDebugPreviewActive() {
        return active && debugPreview;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
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
            && minecraft.options.keyJump.isDown();
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
        if (!active || gui == null || font == null) {
            return;
        }
        int height = gui.guiHeight();
        int right = gui.guiWidth() - 16;
        int listBaseY = height - 38 - Math.max(1, rescuers.size()) * 10;
        int titleY = listBaseY - 48;
        int surrenderHintY = listBaseY - 36;
        int helpHintY = listBaseY - 24;
        drawRightAligned(gui, font, "倒地状态", right, titleY, TEXT_PRIMARY);
        drawRightAligned(gui, font, resolveSurrenderHintText(), right, surrenderHintY, resolveSurrenderHintColor());
        drawRightAligned(gui, font, resolveHelpHintText(), right, helpHintY, resolveHelpHintColor());
        drawRightAligned(gui, font, "附近救援者", right, listBaseY, TEXT_PRIMARY);
        if (rescuers.isEmpty()) {
            drawRightAligned(gui, font, "30m 内暂无队友", right, listBaseY + 12, TEXT_MUTED);
            return;
        }
        int rowY = listBaseY + 12;
        for (RescuerEntry entry : rescuers) {
            int baseColor = entry.squadMate() ? TEXT_SQUAD : TEXT_TEAM;
            drawRightAlignedParts(gui, font, right, rowY, entry.name() + "  ", baseColor, entry.distanceMeters() + "米", TEXT_DISTANCE);
            rowY += 10;
        }
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
