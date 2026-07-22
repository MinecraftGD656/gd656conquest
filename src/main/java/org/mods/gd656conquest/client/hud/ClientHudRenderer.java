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
import java.util.ArrayList;

public final class ClientHudRenderer {
    private static final int GUNMASTER_BAR_HEIGHT = 7;
    private static final int GUNMASTER_EDGE_BAR_HEIGHT = 9;
    private static final int GUNMASTER_BAR_GAP = 3;
    private static final int GUNMASTER_DOT_SIZE = 3;
    private static final int GUNMASTER_SQUARE_EDGE_PADDING = 3;
    private static final int GUNMASTER_OVERFLOW_RIGHT_PADDING = 2;
    private static final int GUNMASTER_DOT_GAP = 2;
    private static final int GUNMASTER_OVERFLOW_DOT_SIZE = 1;
    private static final int GUNMASTER_OVERFLOW_DOT_GAP = 1;
    private static final int GUNMASTER_STAGE_BASE_WIDTH = 4;
    private static final int GUNMASTER_STAGE_PADDING_WIDTH = 4;
    private static final int GUNMASTER_FIRST_STAGE_COLOR = 0xFF184A93;
    private static final int GUNMASTER_UNLOCKED_STAGE_COLOR = 0xFF2F78FF;
    private static final int GUNMASTER_LOCKED_STAGE_COLOR = 0xFFCC6670;
    private static final int GUNMASTER_LAST_STAGE_COLOR = 0xFF91262B;
    private static final int GUNMASTER_MARKER_COLOR = 0xFFFFFFFF;
    private static final int GUNMASTER_SELF_MARKER_COLOR = 0xFF9BC2FF;
    private static final int GUNMASTER_ENEMY_MARKER_COLOR = 0xFFFFA2A8;
    private static final int GUNMASTER_LAST_STAGE_ALERT_COLOR = 0xFFFFA2A8;

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
        int subtitleY = 43;
        if ("GUNMASTER".equalsIgnoreCase(roomState) || "GUNMASTER".equalsIgnoreCase(ClientHudDebugState.getScoreMethod())) {
            subtitleY = 26;
        } else if ("DEATHMATCH".equalsIgnoreCase(roomState) || "DEATHMATCH".equalsIgnoreCase(ClientHudDebugState.getScoreMethod())) {
            subtitleY = 25;
        }
        ClientHudSubtitleManager.render(gui, font, centerX, subtitleY);
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
        if (ClientHudDebugPreviewState.mode().startsWith("BREAKTHROUGH")) {
            drawBreakthroughPreview(gui, font, centerX, y, ClientHudDebugPreviewState.mode());
        } else if (ClientHudDebugPreviewState.mode().startsWith("GUNMASTER")) {
            drawGunmasterPreview(gui, font, centerX, y, ClientHudDebugPreviewState.mode());
        } else {
            drawConquestPreviewTopLine(gui, font, centerX, y);
            if (ClientHudDebugPreviewState.mode().startsWith("DEATHMATCH")) {
                ClientHudSubtitleManager.renderPreviewText(gui, font, centerX, 25, "我军累计分数已经过半");
            }
        }
    }

    private void drawBreakthroughPreview(GuiGraphics gui, Font font, int centerX, int y, String previewMode) {
        drawBreakthroughPreviewTopLine(gui, font, centerX, y, previewMode);
        String subtitle = resolveBreakthroughPreviewSubtitle(previewMode);
        if (!subtitle.isBlank()) {
            int subtitleY = 43;
            if (previewMode.startsWith("GUNMASTER")) {
                subtitleY = 26;
            } else if (previewMode.startsWith("DEATHMATCH")) {
                subtitleY = 25;
            }
            ClientHudSubtitleManager.renderPreviewText(gui, font, centerX, subtitleY, subtitle);
        }
    }

    private void drawTopScoreLine(GuiGraphics gui, Font font, int centerX, int y) {
        String scoreMethod = ClientHudDebugState.getScoreMethod();
        if ("GUNMASTER".equalsIgnoreCase(scoreMethod)) {
            drawGunmasterTopLine(gui, font, centerX, y, parseGunmasterHudPayload(ClientHudDebugState.getExtraPayload()));
            return;
        }
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
        boolean infiniteTroops = ClientHudDebugState.getCampAScore() < 0;
        String selfTeam = ClientHudDebugState.getSelfTeam();
        boolean selfCampA = selfTeam != null && (selfTeam.contains("CampA") || selfTeam.contains("阵营A"));
        boolean selfCampB = selfTeam != null && (selfTeam.contains("CampB") || selfTeam.contains("阵营B"));
        int themePrimary = selfCampB ? 0xFFFF4A4A : 0xFF4A96FF;
        int themeText = selfCampB ? 0xFFFF9B9B : 0xFF9BC2FF;
        int displaySeconds = resolveTopCenterSeconds();
        int minute = displaySeconds / 60;
        int second = displaySeconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", minute, second);
        int timeColor = resolveBreakthroughTimerColor();
        String leftText = infiniteTroops ? "∞" : String.valueOf(attackTroops);
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
        float troopRatio = infiniteTroops ? 1.0F : Math.min(1.0F, attackTroops / (float) troopCap);
        float stageRatio = Math.min(1.0F, stageIndex / (float) stageCount);
        drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, troopRatio, themePrimary, true, true);
        drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, stageRatio, themePrimary, false, true);
        drawScaledBoldText(gui, font, leftText, leftScoreLeft, y + 1, themeText, 1.1F);
        drawScaledBoldText(gui, font, rightText, rightScoreLeft, y + 1, themeText, 1.0F);
        drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, timeColor, 1.1F, true);
    }

    private void drawGunmasterPreview(GuiGraphics gui, Font font, int centerX, int y, String previewMode) {
        drawGunmasterTopLine(gui, font, centerX, y, previewGunmasterPayload(previewMode));
        String subtitle = resolveGunmasterPreviewSubtitle(previewMode);
        if (!subtitle.isBlank()) {
            ClientHudSubtitleManager.renderPreviewText(gui, font, centerX, 26, subtitle);
        }
    }

    private void drawGunmasterTopLine(GuiGraphics gui, Font font, int centerX, int y, GunmasterHudPayload payload) {
        if (payload == null) {
            return;
        }
        int displaySeconds = resolveTopCenterSeconds();
        String timeText = formatSeconds(displaySeconds);
        String nextWeaponText = payload.nextWeaponName == null || payload.nextWeaponName.isBlank() ? "-" : payload.nextWeaponName;
        if (payload.selfStageIndex >= payload.stageCount - 1) {
            nextWeaponText = "最终武器";
        }
        int leftTextWidth = font.width(timeText);
        int rightTextWidth = font.width(nextWeaponText);

        List<Integer> widths = new ArrayList<>();
        int totalWidth = 0;
        for (int index = 0; index < payload.stageCount; index++) {
            int count = index < payload.stagePlayerCounts.length ? payload.stagePlayerCounts[index] : 0;
            int stageWidth = resolveGunmasterStageWidth(index, payload.stageCount, count);
            widths.add(stageWidth);
            totalWidth += stageWidth;
        }
        totalWidth += Math.max(0, payload.stageCount - 1) * GUNMASTER_BAR_GAP;
        int startX = centerX - totalWidth / 2;
        float labelScale = 1.1F;
        int scaledFontHeight = Math.round(font.lineHeight * labelScale);
        int targetCenterY = y + 6; // Keep the bars exactly where they were
        int textY = y + 3; // Shift text down by 2 pixels to perfectly align visually with bars
        int timeGap = 10;
        int nextWeaponGap = 10;
        int timeX = startX - timeGap - leftTextWidth;
        int nextWeaponX = startX + totalWidth + nextWeaponGap;
        drawScaledText(gui, font, timeText, timeX, textY, 0xFFFFFFFF, labelScale, true);
        drawScaledText(gui, font, nextWeaponText, nextWeaponX, textY, 0xFFFFFFFF, labelScale, true);
        for (int index = 0; index < widths.size(); index++) {
            int width = widths.get(index);
            int barHeight = resolveGunmasterStageHeight(index, payload.stageCount);
            int barY = targetCenterY - barHeight / 2;
            int fillColor = resolveGunmasterStageColor(index, payload.stageCount, payload.teamStageIndex, payload.finalEnemyAlert);
            gui.fill(startX, barY, startX + width, barY + barHeight, fillColor);
            
            if (index == payload.selfStageIndex) {
                gui.fill(startX, barY, startX + width, barY + 1, 0xFFFFFFFF);
                gui.fill(startX, barY + barHeight - 1, startX + width, barY + barHeight, 0xFFFFFFFF);
                gui.fill(startX, barY + 1, startX + 1, barY + barHeight - 1, 0xFFFFFFFF);
                gui.fill(startX + width - 1, barY + 1, startX + width, barY + barHeight - 1, 0xFFFFFFFF);
            }
            
            drawGunmasterStageMarkers(
                gui,
                startX,
                barY,
                barHeight,
                width,
                payload.soloGrouping,
                index == payload.selfStageIndex,
                index < payload.stagePlayerCounts.length ? payload.stagePlayerCounts[index] : 0,
                index < payload.stageEnemyCounts.length ? payload.stageEnemyCounts[index] : 0
            );
            startX += width + GUNMASTER_BAR_GAP;
        }
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

    private void drawBreakthroughPreviewTopLine(GuiGraphics gui, Font font, int centerX, int y, String previewMode) {
        boolean backToWall = "BREAKTHROUGH_BACK_TO_WALL_ATTACK".equals(previewMode) || "BREAKTHROUGH_BACK_TO_WALL_DEFEND".equals(previewMode);
        boolean burnBoats = "BREAKTHROUGH_BURN_BOATS".equals(previewMode);
        int attackTroops = backToWall || burnBoats ? -1 : 148;
        int stageIndex = 3;
        int troopCap = 220;
        int stageCount = 6;
        boolean selfCampB = false;
        int themePrimary = selfCampB ? 0xFFFF4A4A : 0xFF4A96FF;
        int themeText = selfCampB ? 0xFFFF9B9B : 0xFF9BC2FF;
        String timeText = burnBoats ? "01:00" : backToWall ? "03:00" : "07:28";
        int timeColor = burnBoats ? 0xFFFF5555 : backToWall ? 0xFFFFD85A : 0xFFFFFFFF;
        String leftText = attackTroops < 0 ? "∞" : String.valueOf(attackTroops);
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
        float troopRatio = attackTroops < 0 ? 1.0F : Math.min(1.0F, attackTroops / (float) troopCap);
        float stageRatio = Math.min(1.0F, stageIndex / (float) stageCount);
        drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, troopRatio, themePrimary, true, true);
        drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, stageRatio, themePrimary, false, true);
        drawScaledBoldText(gui, font, leftText, leftScoreLeft, y + 1, themeText, 1.1F);
        drawScaledBoldText(gui, font, rightText, rightScoreLeft, y + 1, themeText, 1.0F);
        drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, timeColor, 1.1F, true);
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

    private void drawGunmasterStageMarkers(GuiGraphics gui, int x, int y, int height, int width, boolean soloGrouping, boolean selfStage, int totalCount, int enemyCount) {
        int clampedTotal = Math.max(0, totalCount);
        int clampedEnemy = Math.max(0, Math.min(enemyCount, clampedTotal));
        int markerY = y + Math.max(2, (height - GUNMASTER_DOT_SIZE) / 2);
        int markerStartX = x + GUNMASTER_SQUARE_EDGE_PADDING;
        int visibleSquares = Math.min(3, clampedTotal);
        int enemySquares = clampedTotal > 3 && clampedEnemy > 0 ? 1 : Math.min(clampedEnemy, visibleSquares);
        int friendlySquares = Math.max(0, visibleSquares - enemySquares);
        boolean selfMarkerPlaced = false;
        int enemyMarkerColor = soloGrouping ? GUNMASTER_LAST_STAGE_COLOR : GUNMASTER_ENEMY_MARKER_COLOR;
        for (int index = 0; index < visibleSquares; index++) {
            int dotX = markerStartX + index * (GUNMASTER_DOT_SIZE + GUNMASTER_DOT_GAP);
            int color;
            if (index < enemySquares) {
                color = enemyMarkerColor;
            } else if (selfStage && !selfMarkerPlaced && friendlySquares > 0) {
                color = GUNMASTER_SELF_MARKER_COLOR;
                selfMarkerPlaced = true;
            } else {
                color = GUNMASTER_MARKER_COLOR;
            }
            gui.fill(dotX, markerY, dotX + GUNMASTER_DOT_SIZE, markerY + GUNMASTER_DOT_SIZE, color);
        }
        if (clampedTotal <= 3) {
            return;
        }
        int overflowRight = x + width - GUNMASTER_OVERFLOW_RIGHT_PADDING;
        int overflowColor = soloGrouping ? GUNMASTER_LAST_STAGE_COLOR : GUNMASTER_MARKER_COLOR;
        for (int index = 0; index < 3; index++) {
            int dotRight = overflowRight - index * (GUNMASTER_OVERFLOW_DOT_SIZE + GUNMASTER_OVERFLOW_DOT_GAP);
            int dotLeft = dotRight - GUNMASTER_OVERFLOW_DOT_SIZE;
            gui.fill(dotLeft, markerY + 1, dotRight, markerY + 2, overflowColor);
        }
    }

    private void drawScaledBoldText(GuiGraphics gui, Font font, String text, int x, int y, int color, float scale) {
        drawScaledText(gui, font, text, x, y, color, scale, true);
    }

    private void drawScaledText(GuiGraphics gui, Font font, String text, int x, int y, int color, float scale, boolean shadow) {
        float usingScale = Math.max(0.1F, scale);
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(usingScale, usingScale, 1.0F);
        gui.drawString(font, text, 0, 0, color, shadow);
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

    private String resolveGunmasterPreviewSubtitle(String previewMode) {
        return switch (previewMode) {
            case "GUNMASTER_ENEMY_PUSH" -> "敌军已有玩家解锁至最后一把武器";
            default -> "击败他们并使用最后一把武器击杀敌军";
        };
    }

    private String resolveBreakthroughPreviewSubtitle(String previewMode) {
        return switch (previewMode) {
            case "BREAKTHROUGH_BACK_TO_WALL_ATTACK", "BREAKTHROUGH_BACK_TO_WALL_DEFEND" -> "<hud:BT_LAST_STAND_PERSISTENT>背水一战";
            case "BREAKTHROUGH_BURN_BOATS" -> "<hud:BT_BURN_BOATS_PERSISTENT>破釜沉舟";
            default -> "";
        };
    }

    private int resolveBreakthroughTimerColor() {
        ClientHudStyledText.Descriptor status = ClientHudStyledText.parse(ClientHudDebugState.getCaptureStatus(), 0xFFFFFFFF);
        String text = status.text();
        if ("破釜沉舟".equals(text)) {
            return 0xFFFF5555;
        }
        if ("背水一战".equals(text)) {
            return 0xFFFFD85A;
        }
        return 0xFFFFFFFF;
    }

    private int resolveGunmasterStageWidth(int playerCount) {
        return resolveGunmasterStageWidth(-1, 0, playerCount);
    }

    private int resolveGunmasterStageWidth(int stageIndex, int stageCount, int playerCount) {
        if (playerCount <= 0) {
            return 9;
        }
        int visibleSquares = Math.min(3, Math.max(0, playerCount));
        int visibleSquaresWidth = visibleSquares * GUNMASTER_DOT_SIZE + Math.max(0, visibleSquares - 1) * GUNMASTER_DOT_GAP;
        int width = GUNMASTER_SQUARE_EDGE_PADDING * 2 + visibleSquaresWidth;
        if (playerCount > 3) {
            width += 5;
        }
        return Math.max(9, width);
    }

    private int resolveGunmasterStageHeight(int stageIndex, int stageCount) {
        if (stageIndex <= 0 || stageIndex >= Math.max(0, stageCount - 1)) {
            return GUNMASTER_EDGE_BAR_HEIGHT;
        }
        return GUNMASTER_BAR_HEIGHT;
    }

    private int resolveGunmasterStageColor(int stageIndex, int stageCount, int teamStageIndex, boolean finalEnemyAlert) {
        boolean teamReachedLast = (teamStageIndex >= Math.max(0, stageCount - 1));
        if (stageIndex >= Math.max(0, stageCount - 1)) {
            if (teamReachedLast) {
                return mixColorByPulse(GUNMASTER_FIRST_STAGE_COLOR, GUNMASTER_UNLOCKED_STAGE_COLOR);
            } else if (finalEnemyAlert) {
                return mixColorByPulse(GUNMASTER_LAST_STAGE_COLOR, GUNMASTER_LAST_STAGE_ALERT_COLOR);
            }
            return GUNMASTER_LAST_STAGE_COLOR;
        }
        if (stageIndex < teamStageIndex) {
            return GUNMASTER_UNLOCKED_STAGE_COLOR;
        } else if (stageIndex == teamStageIndex) {
            return GUNMASTER_FIRST_STAGE_COLOR;
        } else {
            return GUNMASTER_LOCKED_STAGE_COLOR;
        }
    }

    private int mixColorByPulse(int fromColor, int toColor) {
        long time = System.currentTimeMillis();
        float wave = (float) ((Math.sin(time / 180.0D) + 1.0D) * 0.5D);
        return mixArgb(fromColor, toColor, wave);
    }

    private int mixArgb(int fromColor, int toColor, float ratio) {
        float clamped = Math.max(0.0F, Math.min(1.0F, ratio));
        int a = mixChannel((fromColor >>> 24) & 0xFF, (toColor >>> 24) & 0xFF, clamped);
        int r = mixChannel((fromColor >>> 16) & 0xFF, (toColor >>> 16) & 0xFF, clamped);
        int g = mixChannel((fromColor >>> 8) & 0xFF, (toColor >>> 8) & 0xFF, clamped);
        int b = mixChannel(fromColor & 0xFF, toColor & 0xFF, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int mixChannel(int from, int to, float ratio) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * ratio)));
    }

    private String formatSeconds(int totalSeconds) {
        int minute = Math.max(0, totalSeconds) / 60;
        int second = Math.max(0, totalSeconds) % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minute, second);
    }

    private GunmasterHudPayload previewGunmasterPayload(String previewMode) {
        if ("GUNMASTER_ENEMY_PUSH".equals(previewMode)) {
            return new GunmasterHudPayload(
                12, 2, 2, "M4A1", new int[]{2, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1},
                new int[]{1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, false, true
            );
        }
        return new GunmasterHudPayload(
            12, 2, 4, "M4A1", new int[]{2, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0},
            new int[]{1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, false, false
        );
    }

    private GunmasterHudPayload parseGunmasterHudPayload(String payload) {
        if (payload == null || payload.isBlank() || "-".equals(payload) || !payload.startsWith("GMHUD|")) {
            return previewGunmasterPayload("GUNMASTER");
        }
        int stageCount = 10;
        int selfStageIndex = 0;
        int teamStageIndex = 0;
        boolean soloGrouping = false;
        boolean finalEnemyAlert = false;
        String nextWeapon = "-";
        int[] counts = new int[stageCount];
        int[] enemy = new int[stageCount];
        String[] parts = payload.split("\\|");
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index];
            int split = part.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = part.substring(0, split);
            String value = part.substring(split + 1);
            if ("next".equalsIgnoreCase(key)) {
                nextWeapon = value;
            } else if ("solo".equalsIgnoreCase(key)) {
                soloGrouping = "1".equals(value) || "true".equalsIgnoreCase(value);
            } else if ("finalalert".equalsIgnoreCase(key)) {
                finalEnemyAlert = "1".equals(value) || "true".equalsIgnoreCase(value);
            } else if ("sequence".equalsIgnoreCase(key)) {
                try {
                    stageCount = Math.max(1, Integer.parseInt(value));
                    counts = new int[stageCount];
                    enemy = new int[stageCount];
                } catch (NumberFormatException ignored) {
                }
            } else if ("selfstage".equalsIgnoreCase(key)) {
                try {
                    selfStageIndex = Math.max(0, Integer.parseInt(value) - 1);
                } catch (NumberFormatException ignored) {
                }
            } else if ("teamstage".equalsIgnoreCase(key)) {
                try {
                    teamStageIndex = Math.max(0, Integer.parseInt(value) - 1);
                } catch (NumberFormatException ignored) {
                }
            } else if ("stage".equalsIgnoreCase(key)) {
                try {
                    selfStageIndex = Math.max(0, Integer.parseInt(value) - 1);
                    if (teamStageIndex == 0) {
                        teamStageIndex = selfStageIndex;
                    }
                } catch (NumberFormatException ignored) {
                }
            } else if ("counts".equalsIgnoreCase(key)) {
                counts = parseIntArray(value, stageCount);
            } else if ("enemy".equalsIgnoreCase(key)) {
                enemy = parseIntArray(value, stageCount);
            }
        }
        return new GunmasterHudPayload(stageCount, selfStageIndex, teamStageIndex, nextWeapon, counts, enemy, soloGrouping, finalEnemyAlert);
    }

    private int[] parseIntArray(String text, int expectedSize) {
        int[] values = new int[expectedSize];
        if (text == null || text.isBlank()) {
            return values;
        }
        String[] cols = text.split("\\.");
        for (int index = 0; index < cols.length && index < expectedSize; index++) {
            try {
                values[index] = Math.max(0, Integer.parseInt(cols[index]));
            } catch (NumberFormatException ignored) {
                values[index] = 0;
            }
        }
        return values;
    }

    private record GunmasterHudPayload(
        int stageCount,
        int selfStageIndex,
        int teamStageIndex,
        String nextWeaponName,
        int[] stagePlayerCounts,
        int[] stageEnemyCounts,
        boolean soloGrouping,
        boolean finalEnemyAlert
    ) {
    }
}
