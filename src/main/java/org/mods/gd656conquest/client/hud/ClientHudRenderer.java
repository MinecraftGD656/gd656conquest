package org.mods.gd656conquest.client.hud;

import org.mods.gd656conquest.client.audio.ClientAudioManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import java.util.List;
import java.util.Locale;

public final class ClientHudRenderer {
    public void render(RenderGuiOverlayEvent.Post event) {
        render(event.getGuiGraphics());
    }

    public void render(GuiGraphics gui) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (ClientHudDebugPreviewState.isActive()) {
            renderPreview(gui, minecraft.font);
            return;
        }
        if (ClientHudDebugState.isAdminDebugActive()) {
            renderAudioDebugOverlay(gui, minecraft.font);
        }
        String roomState = ClientHudDebugState.getRoomState();
        if ("未加入房间".equals(roomState) || "-".equals(ClientHudDebugState.getRoomId())) {
            ClientParkourRuntimeState.render(gui, minecraft.font);
            ClientDownedState.render(gui, minecraft.font);
            return;
        }
        Font font = minecraft.font;
        int width = gui.guiWidth();
        int centerX = width / 2;
        int y1 = 8;
        drawTopScoreLine(gui, font, centerX, y1);
        ClientHudSubtitleManager.render(gui, font, centerX, 43);
        ClientParkourRuntimeState.render(gui, font);
        ClientDownedState.render(gui, font);
    }

    private void renderAudioDebugOverlay(GuiGraphics gui, Font font) {
        List<String> lines = ClientAudioManager.getInstance().snapshotDebugLines();
        int right = gui.guiWidth() - 8;
        int y = 8;
        drawDebugLine(gui, font, "音频调试", right, y, 0xFFE7C75A);
        y += 10;
        if (lines.isEmpty()) {
            drawDebugLine(gui, font, "(当前无音频播放)", right, y, 0xFF9A9A9A);
            return;
        }
        int limit = Math.min(12, lines.size());
        for (int index = 0; index < limit; index++) {
            drawDebugLine(gui, font, lines.get(index), right, y, 0xFFE8E8E8);
            y += 10;
        }
    }

    private void drawDebugLine(GuiGraphics gui, Font font, String text, int right, int y, int color) {
        int x = right - font.width(text);
        gui.drawString(font, text, x, y, color, true);
    }

    private void renderPreview(GuiGraphics gui, Font font) {
        int centerX = gui.guiWidth() / 2;
        int y = 8;
        if ("BREAKTHROUGH".equalsIgnoreCase(ClientHudDebugPreviewState.mode())) {
            drawBreakthroughPreviewTopLine(gui, font, centerX, y);
        } else {
            drawConquestPreviewTopLine(gui, font, centerX, y);
        }
    }

    private void drawTopScoreLine(GuiGraphics gui, Font font, int centerX, int y) {
        String scoreMethod = ClientHudDebugState.getScoreMethod();
        if ("BREAKTHROUGH".equalsIgnoreCase(scoreMethod)) {
            drawBreakthroughTopLine(gui, font, centerX, y);
            return;
        }
        int campA = ClientHudDebugState.getCampAScore();
        int campB = ClientHudDebugState.getCampBScore();
        int campAGoal = Math.max(1, ClientHudDebugState.getCampAGoal());
        int campBGoal = Math.max(1, ClientHudDebugState.getCampBGoal());
        String selfTeam = ClientHudDebugState.getSelfTeam();
        boolean selfCampA = selfTeam != null && (selfTeam.contains("CampA") || selfTeam.contains("阵营A"));
        boolean selfCampB = selfTeam != null && (selfTeam.contains("CampB") || selfTeam.contains("阵营B"));
        int allyValue = selfCampA ? campA : selfCampB ? campB : campA;
        int enemyValue = selfCampA ? campB : selfCampB ? campA : campB;
        int allyGoal = selfCampA ? campAGoal : selfCampB ? campBGoal : campAGoal;
        int enemyGoal = selfCampA ? campBGoal : selfCampB ? campAGoal : campBGoal;
        int displaySeconds = resolveTopCenterSeconds();
        int minute = displaySeconds / 60;
        int second = displaySeconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", minute, second);
        int timeWidth = font.width(timeText);
        int timeHalf = Math.max(28, timeWidth / 2 + 6);
        int textNearGap = 4;
        int textToBarGap = 7;
        int barWidth = 96;
        int barHeight = 6;
        String allyText = String.valueOf(allyValue);
        String enemyText = String.valueOf(enemyValue);
        int leftScoreRight = centerX - timeHalf - textNearGap;
        int leftScoreLeft = leftScoreRight - font.width(allyText);
        int rightScoreLeft = centerX + timeHalf + textNearGap;
        int rightScoreRight = rightScoreLeft + font.width(enemyText);
        int leftBarRight = leftScoreLeft - textToBarGap;
        int leftBarLeft = leftBarRight - barWidth;
        int rightBarLeft = rightScoreRight + textToBarGap;
        float allyRatio = Math.min(1.0F, allyValue / (float) allyGoal);
        float enemyRatio = Math.min(1.0F, enemyValue / (float) enemyGoal);
        drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, allyRatio, 0xFF4A96FF, true, true);
        drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, enemyRatio, 0xFFFF4A4A, false, true);
        drawScaledBoldText(gui, font, allyText, leftScoreLeft, y + 1, 0xFF9BC2FF, 1.1F);
        drawScaledBoldText(gui, font, enemyText, rightScoreLeft, y + 1, 0xFFFF9B9B, 1.1F);
        drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, 0xFFFFFFFF, 1.1F, true);
    }

    private void drawBreakthroughTopLine(GuiGraphics gui, Font font, int centerX, int y) {
        int attackTroops = Math.max(0, ClientHudDebugState.getCampAScore());
        int stageIndex = Math.max(0, ClientHudDebugState.getCampBScore());
        int troopCap = Math.max(1, ClientHudDebugState.getCampAGoal());
        int stageCount = Math.max(1, ClientHudDebugState.getCampBGoal());
        String selfTeam = ClientHudDebugState.getSelfTeam();
        boolean selfCampA = selfTeam != null && (selfTeam.contains("CampA") || selfTeam.contains("阵营A"));
        boolean selfCampB = selfTeam != null && (selfTeam.contains("CampB") || selfTeam.contains("阵营B"));
        int themePrimary = selfCampB ? 0xFFFF4A4A : 0xFF4A96FF;
        int themeText = selfCampB ? 0xFFFF9B9B : 0xFF9BC2FF;
        int displaySeconds = resolveTopCenterSeconds();
        int minute = displaySeconds / 60;
        int second = displaySeconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", minute, second);
        String leftText = String.valueOf(attackTroops);
        String rightText = "阶段" + stageLetter(stageIndex);
        int timeWidth = font.width(timeText);
        int timeHalf = Math.max(28, timeWidth / 2 + 6);
        int textNearGap = 4;
        int textToBarGap = 7;
        int barWidth = 96;
        int barHeight = 6;
        int leftScoreRight = centerX - timeHalf - textNearGap;
        int leftScoreLeft = leftScoreRight - font.width(leftText);
        int rightScoreLeft = centerX + timeHalf + textNearGap;
        int rightScoreRight = rightScoreLeft + font.width(rightText);
        int leftBarRight = leftScoreLeft - textToBarGap;
        int leftBarLeft = leftBarRight - barWidth;
        int rightBarLeft = rightScoreRight + textToBarGap;
        float troopRatio = Math.min(1.0F, attackTroops / (float) troopCap);
        float stageRatio = Math.min(1.0F, stageIndex / (float) stageCount);
        drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, troopRatio, themePrimary, true, true);
        drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, stageRatio, themePrimary, false, true);
        drawScaledBoldText(gui, font, leftText, leftScoreLeft, y + 1, themeText, 1.1F);
        drawScaledBoldText(gui, font, rightText, rightScoreLeft, y + 1, themeText, 1.0F);
        drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, 0xFFFFFFFF, 1.1F, true);
    }

    private void drawConquestPreviewTopLine(GuiGraphics gui, Font font, int centerX, int y) {
        int allyValue = 680;
        int enemyValue = 420;
        int allyGoal = 1000;
        int enemyGoal = 1000;
        String timeText = "12:34";
        int timeWidth = font.width(timeText);
        int timeHalf = Math.max(28, timeWidth / 2 + 6);
        int textNearGap = 4;
        int textToBarGap = 7;
        int barWidth = 96;
        int barHeight = 6;
        String allyText = String.valueOf(allyValue);
        String enemyText = String.valueOf(enemyValue);
        int leftScoreRight = centerX - timeHalf - textNearGap;
        int leftScoreLeft = leftScoreRight - font.width(allyText);
        int rightScoreLeft = centerX + timeHalf + textNearGap;
        int rightScoreRight = rightScoreLeft + font.width(enemyText);
        int leftBarRight = leftScoreLeft - textToBarGap;
        int leftBarLeft = leftBarRight - barWidth;
        int rightBarLeft = rightScoreRight + textToBarGap;
        float allyRatio = Math.min(1.0F, allyValue / (float) allyGoal);
        float enemyRatio = Math.min(1.0F, enemyValue / (float) enemyGoal);
        drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, allyRatio, 0xFF4A96FF, true, true);
        drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, enemyRatio, 0xFFFF4A4A, false, true);
        drawScaledBoldText(gui, font, allyText, leftScoreLeft, y + 1, 0xFF9BC2FF, 1.1F);
        drawScaledBoldText(gui, font, enemyText, rightScoreLeft, y + 1, 0xFFFF9B9B, 1.1F);
        drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, 0xFFFFFFFF, 1.1F, true);
    }

    private void drawBreakthroughPreviewTopLine(GuiGraphics gui, Font font, int centerX, int y) {
        int attackTroops = 148;
        int stageIndex = 3;
        int troopCap = 220;
        int stageCount = 6;
        boolean selfCampB = false;
        int themePrimary = selfCampB ? 0xFFFF4A4A : 0xFF4A96FF;
        int themeText = selfCampB ? 0xFFFF9B9B : 0xFF9BC2FF;
        String timeText = "07:28";
        String leftText = String.valueOf(attackTroops);
        String rightText = "阶段" + stageLetter(stageIndex);
        int timeWidth = font.width(timeText);
        int timeHalf = Math.max(28, timeWidth / 2 + 6);
        int textNearGap = 4;
        int textToBarGap = 7;
        int barWidth = 96;
        int barHeight = 6;
        int leftScoreRight = centerX - timeHalf - textNearGap;
        int leftScoreLeft = leftScoreRight - font.width(leftText);
        int rightScoreLeft = centerX + timeHalf + textNearGap;
        int rightScoreRight = rightScoreLeft + font.width(rightText);
        int leftBarRight = leftScoreLeft - textToBarGap;
        int leftBarLeft = leftBarRight - barWidth;
        int rightBarLeft = rightScoreRight + textToBarGap;
        float troopRatio = Math.min(1.0F, attackTroops / (float) troopCap);
        float stageRatio = Math.min(1.0F, stageIndex / (float) stageCount);
        drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, troopRatio, themePrimary, true, true);
        drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, stageRatio, themePrimary, false, true);
        drawScaledBoldText(gui, font, leftText, leftScoreLeft, y + 1, themeText, 1.1F);
        drawScaledBoldText(gui, font, rightText, rightScoreLeft, y + 1, themeText, 1.0F);
        drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, 0xFFFFFFFF, 1.1F, true);
    }

    private void drawProgressBar(GuiGraphics gui, int x, int y, int w, int h, float ratio, int color, boolean reverse, boolean withBackground) {
        int actualHeight = Math.max(1, h - 1);
        if (withBackground) {
            gui.fill(x, y, x + w, y + actualHeight, 0x7A101010);
        }
        int fill = Math.max(0, Math.min(w, Math.round(w * ratio)));
        if (fill <= 0) {
            return;
        }
        if (reverse) {
            int fillLeft = x + w - fill;
            gui.fill(fillLeft, y, x + w, y + actualHeight, color);
        } else {
            int fillRight = x + fill;
            gui.fill(x, y, fillRight, y + actualHeight, color);
        }
    }

    private void drawScaledBoldText(GuiGraphics gui, Font font, String text, int x, int y, int color, float scale) {
        drawScaledText(gui, font, text, x, y, color, scale, true);
    }

    private void drawScaledText(GuiGraphics gui, Font font, String text, int x, int y, int color, float scale, boolean shadow) {
        float usingScale = Math.max(0.1F, scale);
        gui.pose().pushPose();
        gui.pose().scale(usingScale, usingScale, 1.0F);
        int sx = (int) (x / usingScale);
        int sy = (int) (y / usingScale);
        gui.drawString(font, text, sx, sy, color, shadow);
        gui.pose().popPose();
    }

    private int resolveTopCenterSeconds() {
        String roomState = ClientHudDebugState.getRoomState();
        int remain = Math.max(0, ClientHudDebugState.getRemainSeconds());
        if (roomState != null && roomState.contains("匹配")) {
            return remain;
        }
        if (roomState != null && (roomState.contains("进行") || roomState.contains("结算"))) {
            return remain;
        }
        return Math.max(0, ClientHudDebugState.getRunningSeconds());
    }

    private String stageLetter(int stageIndex) {
        if (stageIndex <= 0) {
            return "-";
        }
        return String.valueOf((char) ('A' + Math.max(0, Math.min(5, stageIndex - 1))));
    }

}
