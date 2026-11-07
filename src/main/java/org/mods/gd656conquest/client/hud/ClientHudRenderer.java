/*
 * Decompiled with CFR 0.152.
 */
package org.mods.gd656conquest.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.mods.gd656conquest.client.audio.ClientAudioManager;
import org.mods.gd656conquest.client.hud.ClientHudDebugPreviewState;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.hud.ClientHudStyledText;
import org.mods.gd656conquest.client.hud.ClientHudSubtitleManager;
import org.mods.gd656conquest.client.parkour.ClientParkourRuntimeState;
import org.mods.gd656conquest.client.room.ClientDownedState;

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
    private static final int GUNMASTER_FIRST_STAGE_COLOR = -15185261;
    private static final int GUNMASTER_UNLOCKED_STAGE_COLOR = -13666049;
    private static final int GUNMASTER_LOCKED_STAGE_COLOR = -3381648;
    private static final int GUNMASTER_LAST_STAGE_COLOR = -7264725;
    private static final int GUNMASTER_MARKER_COLOR = -1;
    private static final int GUNMASTER_SELF_MARKER_COLOR = -6569217;
    private static final int GUNMASTER_ENEMY_MARKER_COLOR = -23896;
    private static final int GUNMASTER_LAST_STAGE_ALERT_COLOR = -23896;

    public void render(RenderGuiOverlayEvent.Post event) {
        this.render(event.getGuiGraphics());
    }

    public void render(GuiGraphics gui) {
        String roomState;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (ClientHudDebugPreviewState.isActive()) {
            this.renderPreview(gui, minecraft.font);
            return;
        }
        if (ClientHudDebugState.isAdminDebugActive()) {
            this.renderAudioDebugOverlay(gui, minecraft.font);
        }
        if ("\u672a\u52a0\u5165\u623f\u95f4".equals(roomState = ClientHudDebugState.getRoomState()) || "-".equals(ClientHudDebugState.getRoomId())) {
            ClientParkourRuntimeState.render(gui, minecraft.font);
            // DownedClient renders the themed observation once in RenderGuiEvent.Post.
            return;
        }
        Font font = minecraft.font;
        int width = gui.guiWidth();
        int centerX = width / 2;
        int y1 = 8;
        // Shared BattlefieldPovOverlay draws the live score and objective composition.
        if (org.mods.gd656killicon.client.gui.theme.BattlefieldQueueLayout.visible(ClientHudDebugState.getRoomId(), ClientHudDebugState.getScoreMethod())) {
            this.renderMatching(gui, minecraft);
        }
        int subtitleY = 43;
        if ("GUNMASTER".equalsIgnoreCase(roomState) || "GUNMASTER".equalsIgnoreCase(ClientHudDebugState.getScoreMethod())) {
            subtitleY = 26;
        } else if ("DEATHMATCH".equalsIgnoreCase(roomState)
            || "DEATHMATCH".equalsIgnoreCase(ClientHudDebugState.getScoreMethod())
            || "DEATHMATCH_SOLO".equalsIgnoreCase(ClientHudDebugState.getScoreMethod())) {
            subtitleY = 25;
        }
        ClientHudSubtitleManager.render(gui, font, centerX, subtitleY);
        ClientParkourRuntimeState.render(gui, font);
        // The original downed input/tick owner is retained; the visual layer is independent.
    }

    private void renderMatching(GuiGraphics gui, Minecraft minecraft) {
        if (minecraft.options.hideGui || org.mods.gd656killicon.client.gui.theme.MenuWorldOverlay.menuActive()) return;
        var theme=org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme.current();
        boolean guide=minecraft.player.getInventory().getItem(0).is(org.mods.gd656conquest.common.item.ConquestItems.LOADOUT_GUIDE_ITEM.get());
        var data=new org.mods.gd656killicon.client.gui.theme.BattlefieldQueueLayout.Data(ClientHudDebugState.getRoomId(),ClientHudDebugState.getCaptureStatus(),
            ClientHudDebugState.getCampAScore(),ClientHudDebugState.getCampAGoal(),ClientHudDebugState.getCampBScore(),ClientHudDebugState.getCampBGoal(),ClientHudDebugState.getRemainSeconds(),guide);
        float scale=Math.min(gui.guiWidth()/1920f,gui.guiHeight()/1080f);gui.pose().pushPose();gui.pose().translate((gui.guiWidth()-1920*scale)/2,(gui.guiHeight()-1080*scale)/2,0);gui.pose().scale(scale,scale,1);
        org.mods.gd656killicon.client.hud.BattlefieldHud.begin(gui,theme,org.mods.gd656killicon.client.hud.BattlefieldHudConfig.Module.MATCH,org.mods.gd656killicon.client.gui.theme.BattlefieldQueueLayout.bounds(theme));
        org.mods.gd656killicon.client.gui.theme.BattlefieldQueueLayout.render(new QueueCanvas(gui,theme),theme,data);
        org.mods.gd656killicon.client.hud.BattlefieldHud.end(gui);gui.pose().popPose();
    }

    private static final class QueueCanvas extends org.mods.gd656killicon.client.gui.theme.BattlefieldPovOverlay.GameCanvas implements org.mods.gd656killicon.client.gui.theme.BattlefieldQueueLayout.Canvas {
        QueueCanvas(GuiGraphics graphics,org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme value){super(graphics,value);}
        public int background(int color){return org.mods.gd656killicon.client.hud.BattlefieldHud.background(theme,org.mods.gd656killicon.client.hud.BattlefieldHudConfig.Module.MATCH,color);}
        public int frame(int color){return org.mods.gd656killicon.client.hud.BattlefieldHud.frame(theme,org.mods.gd656killicon.client.hud.BattlefieldHudConfig.Module.MATCH,color);}
        public int content(int color){return org.mods.gd656killicon.client.hud.BattlefieldHud.content(theme,org.mods.gd656killicon.client.hud.BattlefieldHudConfig.Module.MATCH,color);}
    }

    private void renderAudioDebugOverlay(GuiGraphics gui, Font font) {
        List<String> lines = ClientAudioManager.getInstance().snapshotDebugLines();
        int right = gui.guiWidth() - 8;
        int y = 8;
        this.drawDebugLine(gui, font, "\u97f3\u9891\u8c03\u8bd5", right, y, -6237968);
        y += 10;
        if (lines.isEmpty()) {
            this.drawDebugLine(gui, font, "(\u5f53\u524d\u65e0\u97f3\u9891\u64ad\u653e)", right, y, -6645094);
            return;
        }
        int limit = Math.min(12, lines.size());
        for (int index = 0; index < limit; ++index) {
            this.drawDebugLine(gui, font, lines.get(index), right, y, -1513240);
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
            this.drawBreakthroughPreview(gui, font, centerX, y, ClientHudDebugPreviewState.mode());
        } else if (ClientHudDebugPreviewState.mode().startsWith("RUSH")) {
            this.drawBreakthroughPreview(gui, font, centerX, y, ClientHudDebugPreviewState.mode());
        } else if (ClientHudDebugPreviewState.mode().startsWith("GUNMASTER")) {
            this.drawGunmasterPreview(gui, font, centerX, y, ClientHudDebugPreviewState.mode());
        } else {
            this.drawConquestPreviewTopLine(gui, font, centerX, y);
            if (ClientHudDebugPreviewState.mode().startsWith("DEATHMATCH")) {
                ClientHudSubtitleManager.renderPreviewText(gui, font, centerX, 25, "\u6211\u519b\u7d2f\u8ba1\u5206\u6570\u5df2\u7ecf\u8fc7\u534a");
            }
        }
    }

    private void drawBreakthroughPreview(GuiGraphics gui, Font font, int centerX, int y, String previewMode) {
        this.drawBreakthroughPreviewTopLine(gui, font, centerX, y, previewMode);
        String subtitle = this.resolveBreakthroughPreviewSubtitle(previewMode);
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
        int allyGoal;
        int enemyValue;
        int allyValue;
        boolean selfCampB;
        String scoreMethod = ClientHudDebugState.getScoreMethod();
        if ("DEATHMATCH_SOLO".equalsIgnoreCase(scoreMethod)) {
            this.drawDeathmatchSoloTopLine(gui, font, centerX, y, this.parseDeathmatchSoloHudPayload(ClientHudDebugState.getExtraPayload()));
            return;
        }
        if ("GUNMASTER".equalsIgnoreCase(scoreMethod)) {
            this.drawGunmasterTopLine(gui, font, centerX, y, this.parseGunmasterHudPayload(ClientHudDebugState.getExtraPayload()));
            return;
        }
        if ("BREAKTHROUGH".equalsIgnoreCase(scoreMethod) || "RUSH".equalsIgnoreCase(scoreMethod)) {
            this.drawBreakthroughTopLine(gui, font, centerX, y);
            return;
        }
        int campA = ClientHudDebugState.getCampAScore();
        int campB = ClientHudDebugState.getCampBScore();
        int campAGoal = Math.max(1, ClientHudDebugState.getCampAGoal());
        int campBGoal = Math.max(1, ClientHudDebugState.getCampBGoal());
        String selfTeam = ClientHudDebugState.getSelfTeam();
        boolean selfCampA = selfTeam != null && (selfTeam.contains("CampA") || selfTeam.contains("\u9635\u8425A"));
        boolean bl = selfCampB = selfTeam != null && (selfTeam.contains("CampB") || selfTeam.contains("\u9635\u8425B"));
        allyValue = selfCampA ? campA : (selfCampB ? campB : campA);
        enemyValue = selfCampA ? campB : (selfCampB ? campA : campB);
        allyGoal = selfCampA ? campAGoal : (selfCampB ? campBGoal : campAGoal);
        int enemyGoal = selfCampA ? campBGoal : (selfCampB ? campAGoal : campBGoal);
        int displaySeconds = this.resolveTopCenterSeconds();
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
        float allyRatio = Math.min(1.0f, (float)allyValue / (float)allyGoal);
        float enemyRatio = Math.min(1.0f, (float)enemyValue / (float)enemyGoal);
        this.drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, allyRatio, -11888897, true, true);
        this.drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, enemyRatio, -46518, false, true);
        this.drawScaledBoldText(gui, font, allyText, leftScoreLeft, y + 1, -6569217, 1.1f);
        this.drawScaledBoldText(gui, font, enemyText, rightScoreLeft, y + 1, -25701, 1.1f);
        this.drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, -1, 1.1f, true);
    }

    private void drawDeathmatchSoloTopLine(GuiGraphics gui, Font font, int centerX, int y, DeathmatchSoloHudPayload payload) {
        if (payload == null) {
            return;
        }
        float textScale = 1.1f;
        String timeText = this.formatSeconds(this.resolveTopCenterSeconds());
        int timeWidth = this.scaledTextWidth(font, timeText, textScale);
        int timeHalf = Math.max(28, timeWidth / 2 + 6);
        int textGap = 5;
        int labelGap = 3;
        int leftRight = centerX - timeHalf - textGap;
        String selfKills = String.valueOf(payload.selfKills());
        String selfName = payload.selfName();
        int selfNameWidth = this.scaledTextWidth(font, selfName, textScale);
        int selfKillsWidth = this.scaledTextWidth(font, selfKills, textScale);
        int selfNameX = leftRight - selfNameWidth;
        int selfKillsX = selfNameX - labelGap - selfKillsWidth;
        this.drawScaledBoldText(gui, font, selfKills, selfKillsX, y + 1, -6569217, textScale);
        this.drawScaledBoldText(gui, font, selfName, selfNameX, y + 1, -11888897, textScale);
        int rightX = centerX + timeHalf + textGap;
        if (payload.selfLeading()) {
            this.drawScaledBoldText(gui, font, "\u4f60\u5df2\u9886\u5148", rightX, y + 1, this.mixColorByPulse(-11888897, -6569217), textScale);
        } else {
            String leaderName = payload.leaderName();
            String leaderKills = String.valueOf(payload.leaderKills());
            this.drawScaledBoldText(gui, font, leaderName, rightX, y + 1, -46518, textScale);
            this.drawScaledBoldText(gui, font, leaderKills, rightX + this.scaledTextWidth(font, leaderName, textScale) + labelGap, y + 1, -25701, textScale);
        }
        this.drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, -1, textScale, true);
    }

    private void drawBreakthroughTopLine(GuiGraphics gui, Font font, int centerX, int y) {
        int attackTroops = Math.max(0, ClientHudDebugState.getCampAScore());
        int stageIndex = Math.max(0, ClientHudDebugState.getCampBScore());
        int troopCap = Math.max(1, ClientHudDebugState.getCampAGoal());
        int stageCount = Math.max(1, ClientHudDebugState.getCampBGoal());
        boolean infiniteTroops = ClientHudDebugState.getCampAScore() < 0;
        String selfTeam = ClientHudDebugState.getSelfTeam();
        boolean selfCampA = selfTeam != null && (selfTeam.contains("CampA") || selfTeam.contains("\u9635\u8425A"));
        boolean selfCampB = selfTeam != null && (selfTeam.contains("CampB") || selfTeam.contains("\u9635\u8425B"));
        int themePrimary = selfCampB ? -46518 : -11888897;
        int themeText = selfCampB ? -25701 : -6569217;
        int displaySeconds = this.resolveTopCenterSeconds();
        int minute = displaySeconds / 60;
        int second = displaySeconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", minute, second);
        int timeColor = this.resolveBreakthroughTimerColor();
        String leftText = infiniteTroops ? "\u221e" : String.valueOf(attackTroops);
        String rightText = "\u9636\u6bb5" + this.stageLetter(stageIndex);
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
        float troopRatio = infiniteTroops ? 1.0f : Math.min(1.0f, (float)attackTroops / (float)troopCap);
        float stageRatio = Math.min(1.0f, (float)stageIndex / (float)stageCount);
        this.drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, troopRatio, themePrimary, true, true);
        this.drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, stageRatio, themePrimary, false, true);
        this.drawScaledBoldText(gui, font, leftText, leftScoreLeft, y + 1, themeText, 1.1f);
        this.drawScaledBoldText(gui, font, rightText, rightScoreLeft, y + 1, themeText, 1.0f);
        this.drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, timeColor, 1.1f, true);
    }

    private void drawGunmasterPreview(GuiGraphics gui, Font font, int centerX, int y, String previewMode) {
        this.drawGunmasterTopLine(gui, font, centerX, y, this.previewGunmasterPayload(previewMode));
        String subtitle = this.resolveGunmasterPreviewSubtitle(previewMode);
        if (!subtitle.isBlank()) {
            ClientHudSubtitleManager.renderPreviewText(gui, font, centerX, 26, subtitle);
        }
    }

    private void drawGunmasterTopLine(GuiGraphics gui, Font font, int centerX, int y, GunmasterHudPayload payload) {
        String nextWeaponText;
        if (payload == null) {
            return;
        }
        int displaySeconds = this.resolveTopCenterSeconds();
        String timeText = this.formatSeconds(displaySeconds);
        String string = nextWeaponText = payload.nextWeaponName == null || payload.nextWeaponName.isBlank() ? "-" : payload.nextWeaponName;
        if (payload.selfStageIndex >= payload.stageCount - 1) {
            nextWeaponText = "\u6700\u7ec8\u6b66\u5668";
        }
        int leftTextWidth = font.width(timeText);
        int rightTextWidth = font.width(nextWeaponText);
        ArrayList<Integer> widths = new ArrayList<Integer>();
        int totalWidth = 0;
        for (int index = 0; index < payload.stageCount; ++index) {
            int count = index < payload.stagePlayerCounts.length ? payload.stagePlayerCounts[index] : 0;
            int stageWidth = this.resolveGunmasterStageWidth(index, payload.stageCount, count);
            widths.add(stageWidth);
            totalWidth += stageWidth;
        }
        int startX = centerX - (totalWidth += Math.max(0, payload.stageCount - 1) * 3) / 2;
        float labelScale = 1.1f;
        Objects.requireNonNull(font);
        int scaledFontHeight = Math.round(9.0f * labelScale);
        int targetCenterY = y + 6;
        int textY = y + 3;
        int timeGap = 10;
        int nextWeaponGap = 10;
        int timeX = startX - timeGap - leftTextWidth;
        int nextWeaponX = startX + totalWidth + nextWeaponGap;
        this.drawScaledText(gui, font, timeText, timeX, textY, -1, labelScale, true);
        this.drawScaledText(gui, font, nextWeaponText, nextWeaponX, textY, -1, labelScale, true);
        for (int index = 0; index < widths.size(); ++index) {
            int width = (Integer)widths.get(index);
            int barHeight = this.resolveGunmasterStageHeight(index, payload.stageCount);
            int barY = targetCenterY - barHeight / 2;
            int fillColor = this.resolveGunmasterStageColor(index, payload.stageCount, payload.teamStageIndex, payload.finalEnemyAlert);
            gui.fill(startX, barY, startX + width, barY + barHeight, fillColor);
            if (index == payload.selfStageIndex) {
                org.mods.gd656killicon.client.gui.theme.BattlefieldRectStroke.hairline(gui, startX, barY, width, barHeight, -1);
            }
            this.drawGunmasterStageMarkers(gui, startX, barY, barHeight, width, payload.soloGrouping, index == payload.selfStageIndex, index < payload.stagePlayerCounts.length ? payload.stagePlayerCounts[index] : 0, index < payload.stageEnemyCounts.length ? payload.stageEnemyCounts[index] : 0);
            startX += width + 3;
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
        float allyRatio = Math.min(1.0f, (float)allyValue / (float)allyGoal);
        float enemyRatio = Math.min(1.0f, (float)enemyValue / (float)enemyGoal);
        this.drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, allyRatio, -11888897, true, true);
        this.drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, enemyRatio, -46518, false, true);
        this.drawScaledBoldText(gui, font, allyText, leftScoreLeft, y + 1, -6569217, 1.1f);
        this.drawScaledBoldText(gui, font, enemyText, rightScoreLeft, y + 1, -25701, 1.1f);
        this.drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, -1, 1.1f, true);
    }

    private void drawBreakthroughPreviewTopLine(GuiGraphics gui, Font font, int centerX, int y, String previewMode) {
        String timeText;
        int themeText;
        boolean backToWall = "BREAKTHROUGH_BACK_TO_WALL_ATTACK".equals(previewMode) || "BREAKTHROUGH_BACK_TO_WALL_DEFEND".equals(previewMode);
        boolean burnBoats = "BREAKTHROUGH_BURN_BOATS".equals(previewMode);
        int attackTroops = backToWall || burnBoats ? -1 : 148;
        int stageIndex = 3;
        int troopCap = 220;
        int stageCount = 6;
        boolean selfCampB = false;
        int themePrimary = selfCampB ? -46518 : -11888897;
        int n = themeText = selfCampB ? -25701 : -6569217;
        timeText = burnBoats ? "01:00" : (backToWall ? "03:00" : "07:28");
        int timeColor = burnBoats ? -43691 : (backToWall ? -6237968 : -1);
        String leftText = attackTroops < 0 ? "\u221e" : String.valueOf(attackTroops);
        String rightText = "\u9636\u6bb5" + this.stageLetter(stageIndex);
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
        float troopRatio = attackTroops < 0 ? 1.0f : Math.min(1.0f, (float)attackTroops / (float)troopCap);
        float stageRatio = Math.min(1.0f, (float)stageIndex / (float)stageCount);
        this.drawProgressBar(gui, leftBarLeft, y + 3, barWidth, barHeight, troopRatio, themePrimary, true, true);
        this.drawProgressBar(gui, rightBarLeft, y + 3, barWidth, barHeight, stageRatio, themePrimary, false, true);
        this.drawScaledBoldText(gui, font, leftText, leftScoreLeft, y + 1, themeText, 1.1f);
        this.drawScaledBoldText(gui, font, rightText, rightScoreLeft, y + 1, themeText, 1.0f);
        this.drawScaledText(gui, font, timeText, centerX - timeWidth / 2, y + 1, timeColor, 1.1f, true);
    }

    private void drawProgressBar(GuiGraphics gui, int x, int y, int w, int h, float ratio, int color, boolean reverse, boolean withBackground) {
        int fill;
        int actualHeight = Math.max(1, h - 1);
        if (withBackground) {
            gui.fill(x, y, x + w, y + actualHeight, 2047873040);
        }
        if ((fill = Math.max(0, Math.min(w, Math.round((float)w * ratio)))) <= 0) {
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
        int markerY = y + Math.max(2, (height - 3) / 2);
        int markerStartX = x + 3;
        int visibleSquares = Math.min(3, clampedTotal);
        int enemySquares = clampedTotal > 3 && clampedEnemy > 0 ? 1 : Math.min(clampedEnemy, visibleSquares);
        int friendlySquares = Math.max(0, visibleSquares - enemySquares);
        boolean selfMarkerPlaced = false;
        int enemyMarkerColor = soloGrouping ? -7264725 : -23896;
        for (int index = 0; index < visibleSquares; ++index) {
            int color;
            int dotX = markerStartX + index * 5;
            if (index < enemySquares) {
                color = enemyMarkerColor;
            } else if (selfStage && !selfMarkerPlaced && friendlySquares > 0) {
                color = -6569217;
                selfMarkerPlaced = true;
            } else {
                color = -1;
            }
            gui.fill(dotX, markerY, dotX + 3, markerY + 3, color);
        }
        if (clampedTotal <= 3) {
            return;
        }
        int overflowRight = x + width - 2;
        int overflowColor = soloGrouping ? -7264725 : -1;
        for (int index = 0; index < 3; ++index) {
            int dotRight = overflowRight - index * 2;
            int dotLeft = dotRight - 1;
            gui.fill(dotLeft, markerY + 1, dotRight, markerY + 2, overflowColor);
        }
    }

    private void drawScaledBoldText(GuiGraphics gui, Font font, String text, int x, int y, int color, float scale) {
        this.drawScaledText(gui, font, text, x, y, color, scale, true);
    }

    private int scaledTextWidth(Font font, String text, float scale) {
        return (int)Math.ceil(font.width(text == null ? "" : text) * Math.max(0.1f, scale));
    }

    private void drawScaledText(GuiGraphics gui, Font font, String text, int x, int y, int color, float scale, boolean shadow) {
        float usingScale = Math.max(0.1f, scale);
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0.0f);
        gui.pose().scale(usingScale, usingScale, 1.0f);
        gui.drawString(font, text, 0, 0, color, shadow);
        gui.pose().popPose();
    }

    private int resolveTopCenterSeconds() {
        String roomState = ClientHudDebugState.getRoomState();
        int remain = Math.max(0, ClientHudDebugState.getRemainSeconds());
        if (roomState != null && roomState.contains("\u5339\u914d")) {
            return remain;
        }
        if (roomState != null && (roomState.contains("\u8fdb\u884c") || roomState.contains("\u7ed3\u7b97"))) {
            return remain;
        }
        return Math.max(0, ClientHudDebugState.getRunningSeconds());
    }

    private String stageLetter(int stageIndex) {
        if (stageIndex <= 0) {
            return "-";
        }
        return String.valueOf((char)(65 + Math.max(0, Math.min(5, stageIndex - 1))));
    }

    private String resolveGunmasterPreviewSubtitle(String previewMode) {
        return switch (previewMode) {
            case "GUNMASTER_ENEMY_PUSH" -> "\u654c\u519b\u5df2\u6709\u73a9\u5bb6\u89e3\u9501\u81f3\u6700\u540e\u4e00\u628a\u6b66\u5668";
            default -> "\u51fb\u8d25\u4ed6\u4eec\u5e76\u4f7f\u7528\u6700\u540e\u4e00\u628a\u6b66\u5668\u51fb\u6740\u654c\u519b";
        };
    }

    private String resolveBreakthroughPreviewSubtitle(String previewMode) {
        return switch (previewMode) {
            case "BREAKTHROUGH_BACK_TO_WALL_ATTACK", "BREAKTHROUGH_BACK_TO_WALL_DEFEND" -> "<hud:BT_LAST_STAND_PERSISTENT>\u80cc\u6c34\u4e00\u6218";
            case "BREAKTHROUGH_BURN_BOATS" -> "<hud:BT_BURN_BOATS_PERSISTENT>\u7834\u91dc\u6c89\u821f";
            default -> "";
        };
    }

    private int resolveBreakthroughTimerColor() {
        ClientHudStyledText.Descriptor status = ClientHudStyledText.parse(ClientHudDebugState.getCaptureStatus(), -1);
        String text = status.text();
        if ("\u7834\u91dc\u6c89\u821f".equals(text)) {
            return -43691;
        }
        if ("\u80cc\u6c34\u4e00\u6218".equals(text)) {
            return -6237968;
        }
        return -1;
    }

    private int resolveGunmasterStageWidth(int playerCount) {
        return this.resolveGunmasterStageWidth(-1, 0, playerCount);
    }

    private int resolveGunmasterStageWidth(int stageIndex, int stageCount, int playerCount) {
        if (playerCount <= 0) {
            return 9;
        }
        int visibleSquares = Math.min(3, Math.max(0, playerCount));
        int visibleSquaresWidth = visibleSquares * 3 + Math.max(0, visibleSquares - 1) * 2;
        int width = 6 + visibleSquaresWidth;
        if (playerCount > 3) {
            width += 5;
        }
        return Math.max(9, width);
    }

    private int resolveGunmasterStageHeight(int stageIndex, int stageCount) {
        if (stageIndex <= 0 || stageIndex >= Math.max(0, stageCount - 1)) {
            return 9;
        }
        return 7;
    }

    private int resolveGunmasterStageColor(int stageIndex, int stageCount, int teamStageIndex, boolean finalEnemyAlert) {
        boolean teamReachedLast;
        boolean bl = teamReachedLast = teamStageIndex >= Math.max(0, stageCount - 1);
        if (stageIndex >= Math.max(0, stageCount - 1)) {
            if (teamReachedLast) {
                return this.mixColorByPulse(-15185261, -13666049);
            }
            if (finalEnemyAlert) {
                return this.mixColorByPulse(-7264725, -23896);
            }
            return -7264725;
        }
        if (stageIndex < teamStageIndex) {
            return -13666049;
        }
        if (stageIndex == teamStageIndex) {
            return -15185261;
        }
        return -3381648;
    }

    private int mixColorByPulse(int fromColor, int toColor) {
        long time = System.currentTimeMillis();
        float wave = (float)((Math.sin((double)time / 180.0) + 1.0) * 0.5);
        return this.mixArgb(fromColor, toColor, wave);
    }

    private int mixArgb(int fromColor, int toColor, float ratio) {
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        int a = this.mixChannel(fromColor >>> 24 & 0xFF, toColor >>> 24 & 0xFF, clamped);
        int r = this.mixChannel(fromColor >>> 16 & 0xFF, toColor >>> 16 & 0xFF, clamped);
        int g = this.mixChannel(fromColor >>> 8 & 0xFF, toColor >>> 8 & 0xFF, clamped);
        int b = this.mixChannel(fromColor & 0xFF, toColor & 0xFF, clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private int mixChannel(int from, int to, float ratio) {
        return Math.max(0, Math.min(255, Math.round((float)from + (float)(to - from) * ratio)));
    }

    private String formatSeconds(int totalSeconds) {
        int minute = Math.max(0, totalSeconds) / 60;
        int second = Math.max(0, totalSeconds) % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minute, second);
    }

    private GunmasterHudPayload previewGunmasterPayload(String previewMode) {
        if ("GUNMASTER_ENEMY_PUSH".equals(previewMode)) {
            return new GunmasterHudPayload(12, 2, 2, "M4A1", new int[]{2, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1}, new int[]{1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, false, true);
        }
        return new GunmasterHudPayload(12, 2, 4, "M4A1", new int[]{2, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0}, new int[]{1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, false, false);
    }

    private GunmasterHudPayload parseGunmasterHudPayload(String payload) {
        if (payload == null || payload.isBlank() || "-".equals(payload) || !payload.startsWith("GMHUD|")) {
            return this.previewGunmasterPayload("GUNMASTER");
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
        for (int index = 1; index < parts.length; ++index) {
            String part = parts[index];
            int split = part.indexOf(61);
            if (split <= 0) continue;
            String key = part.substring(0, split);
            String value = part.substring(split + 1);
            if ("next".equalsIgnoreCase(key)) {
                nextWeapon = value;
                continue;
            }
            if ("solo".equalsIgnoreCase(key)) {
                soloGrouping = "1".equals(value) || "true".equalsIgnoreCase(value);
                continue;
            }
            if ("finalalert".equalsIgnoreCase(key)) {
                finalEnemyAlert = "1".equals(value) || "true".equalsIgnoreCase(value);
                continue;
            }
            if ("sequence".equalsIgnoreCase(key)) {
                try {
                    stageCount = Math.max(1, Integer.parseInt(value));
                    counts = new int[stageCount];
                    enemy = new int[stageCount];
                }
                catch (NumberFormatException numberFormatException) {}
                continue;
            }
            if ("selfstage".equalsIgnoreCase(key)) {
                try {
                    selfStageIndex = Math.max(0, Integer.parseInt(value) - 1);
                }
                catch (NumberFormatException numberFormatException) {}
                continue;
            }
            if ("teamstage".equalsIgnoreCase(key)) {
                try {
                    teamStageIndex = Math.max(0, Integer.parseInt(value) - 1);
                }
                catch (NumberFormatException numberFormatException) {}
                continue;
            }
            if ("stage".equalsIgnoreCase(key)) {
                try {
                    selfStageIndex = Math.max(0, Integer.parseInt(value) - 1);
                    if (teamStageIndex != 0) continue;
                    teamStageIndex = selfStageIndex;
                }
                catch (NumberFormatException numberFormatException) {}
                continue;
            }
            if ("counts".equalsIgnoreCase(key)) {
                counts = this.parseIntArray(value, stageCount);
                continue;
            }
            if (!"enemy".equalsIgnoreCase(key)) continue;
            enemy = this.parseIntArray(value, stageCount);
        }
        return new GunmasterHudPayload(stageCount, selfStageIndex, teamStageIndex, nextWeapon, counts, enemy, soloGrouping, finalEnemyAlert);
    }

    private DeathmatchSoloHudPayload parseDeathmatchSoloHudPayload(String payload) {
        String selfName = "\u4f60";
        String leaderName = "-";
        int selfKills = 0;
        int leaderKills = 0;
        boolean selfLeading = false;
        if (payload == null || !payload.startsWith("DMFFA|")) {
            return new DeathmatchSoloHudPayload(selfName, selfKills, leaderName, leaderKills, false);
        }
        String[] parts = payload.split("\\|");
        for (int index = 1; index < parts.length; ++index) {
            int split = parts[index].indexOf(61);
            if (split <= 0) continue;
            String key = parts[index].substring(0, split);
            String value = parts[index].substring(split + 1);
            if ("self".equalsIgnoreCase(key)) {
                selfName = value;
                continue;
            }
            if ("selfkills".equalsIgnoreCase(key)) {
                selfKills = this.parseNonNegativeInt(value);
                continue;
            }
            if ("leader".equalsIgnoreCase(key)) {
                leaderName = value;
                continue;
            }
            if ("leaderkills".equalsIgnoreCase(key)) {
                leaderKills = this.parseNonNegativeInt(value);
                continue;
            }
            if ("selflead".equalsIgnoreCase(key)) {
                selfLeading = "1".equals(value) || "true".equalsIgnoreCase(value);
            }
        }
        return new DeathmatchSoloHudPayload(selfName, selfKills, leaderName, leaderKills, selfLeading);
    }

    private int parseNonNegativeInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        }
        catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int[] parseIntArray(String text, int expectedSize) {
        int[] values = new int[expectedSize];
        if (text == null || text.isBlank()) {
            return values;
        }
        String[] cols = text.split("\\.");
        for (int index = 0; index < cols.length && index < expectedSize; ++index) {
            try {
                values[index] = Math.max(0, Integer.parseInt(cols[index]));
                continue;
            }
            catch (NumberFormatException ignored) {
                values[index] = 0;
            }
        }
        return values;
    }

    private record GunmasterHudPayload(int stageCount, int selfStageIndex, int teamStageIndex, String nextWeaponName, int[] stagePlayerCounts, int[] stageEnemyCounts, boolean soloGrouping, boolean finalEnemyAlert) {
    }

    private record DeathmatchSoloHudPayload(String selfName, int selfKills, String leaderName, int leaderKills, boolean selfLeading) {
    }
}

