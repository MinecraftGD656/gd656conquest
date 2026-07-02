package org.mods.gd656conquest.client.parkour;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ParkourActionPacket;
import org.mods.gd656conquest.network.packet.ParkourSessionSyncPacket;
import org.mods.gd656conquest.network.packet.ServerboundParkourFinishPacket;

import java.util.Locale;

public final class ClientParkourRuntimeState {
    private static boolean inParkour;
    private static boolean challengeActive;
    private static boolean fallLeaveRequested;
    private static int runNonce;
    private static int startX;
    private static double startY;
    private static int startZ;
    private static int endX;
    private static double endY;
    private static int endZ;
    private static long timerStartedAtNanos;
    private static boolean finishSubmitted;

    private ClientParkourRuntimeState() {
    }

    public static synchronized void apply(ParkourSessionSyncPacket packet) {
        if (packet == null) {
            return;
        }
        boolean wasActive = challengeActive;
        inParkour = packet.inParkour();
        challengeActive = packet.challengeActive();
        runNonce = packet.runNonce();
        startX = packet.startX();
        startY = packet.startY();
        startZ = packet.startZ();
        endX = packet.endX();
        endY = packet.endY();
        endZ = packet.endZ();
        if (challengeActive && (!wasActive || timerStartedAtNanos <= 0L)) {
            timerStartedAtNanos = System.nanoTime();
            finishSubmitted = false;
        }
        if (!challengeActive) {
            timerStartedAtNanos = 0L;
            finishSubmitted = false;
        }
        if (!inParkour) {
            clear();
        }
    }

    public static synchronized void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        if (inParkour && !fallLeaveRequested && minecraft.player.getY() < startY) {
            fallLeaveRequested = true;
            NetworkManager.getChannel().sendToServer(ParkourActionPacket.fellBelowStart());
        }
        if (!inParkour || !challengeActive || finishSubmitted || timerStartedAtNanos <= 0L) {
            return;
        }
        if (!isWithinArea(minecraft.player.getBlockX(), minecraft.player.getY(), minecraft.player.getBlockZ(), endX, endY, endZ)) {
            return;
        }
        long elapsedMillis = Math.max(1L, (System.nanoTime() - timerStartedAtNanos) / 1_000_000L);
        finishSubmitted = true;
        NetworkManager.getChannel().sendToServer(new ServerboundParkourFinishPacket(elapsedMillis, runNonce));
    }

    public static synchronized void render(GuiGraphics guiGraphics, Font font) {
        if (!challengeActive || timerStartedAtNanos <= 0L || guiGraphics == null || font == null) {
            return;
        }
        long elapsedMillis = Math.max(0L, (System.nanoTime() - timerStartedAtNanos) / 1_000_000L);
        String text = formatElapsed(elapsedMillis);
        int x = (guiGraphics.guiWidth() - font.width(text)) / 2;
        int y = guiGraphics.guiHeight() - 88 - font.lineHeight;
        guiGraphics.drawString(font, text, x, y, 0xFFFFD78A, true);
    }

    public static synchronized void clear() {
        inParkour = false;
        challengeActive = false;
        fallLeaveRequested = false;
        runNonce = 0;
        startX = 0;
        startY = 0.0D;
        startZ = 0;
        endX = 0;
        endY = 0.0D;
        endZ = 0;
        timerStartedAtNanos = 0L;
        finishSubmitted = false;
    }

    private static boolean isWithinArea(int playerX, double playerY, int playerZ, int centerX, double centerY, int centerZ) {
        return Math.abs(playerX - centerX) <= 1
            && Math.abs(playerZ - centerZ) <= 1
            && Math.abs(playerY - centerY) <= 2.5D;
    }

    public static String formatElapsed(long elapsedMillis) {
        long totalSeconds = elapsedMillis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long millis = elapsedMillis % 1000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }
}
