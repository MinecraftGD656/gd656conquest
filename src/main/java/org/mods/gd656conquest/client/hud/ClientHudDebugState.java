package org.mods.gd656conquest.client.hud;

public final class ClientHudDebugState {
    private static String roomId = "-";
    private static String roomState = "-";
    private static String audioState = "-";
    private static String selfTeam = "-";
    private static String selfClass = "-";
    private static int campAScore;
    private static int campBScore;
    private static int runningSeconds;
    private static String scoreMethod = "SCORE_TARGET";
    private static int campAGoal;
    private static int campBGoal;
    private static int remainSeconds;
    private static int playerMaxY;
    private static int aircraftPassengerMaxY;
    private static boolean allowPlayerCampSwitch = true;
    private static String captureStatus = "-";
    private static String captureIcons = "-";
    private static String captureEvent = "-";
    private static String captureFocus = "-";
    private static String extraPayload = "-";
    private static String adminDebugOverlay = "-";

    private ClientHudDebugState() {
    }

    public static void update(String roomIdValue, String roomStateValue, String audioStateValue, String selfTeamValue, String selfClassValue, int campA, int campB, int seconds, String scoreMethodValue, int campAGoalValue, int campBGoalValue, int remainSecondsValue, int playerMaxYValue, int aircraftPassengerMaxYValue, boolean allowPlayerCampSwitchValue, String captureStatusValue, String captureIconsValue, String captureEventValue, String captureFocusValue, String extraPayloadValue) {
        roomId = roomIdValue;
        roomState = roomStateValue;
        audioState = audioStateValue == null || audioStateValue.isBlank() ? "-" : audioStateValue;
        selfTeam = selfTeamValue;
        selfClass = selfClassValue == null || selfClassValue.isBlank() ? "-" : selfClassValue;
        campAScore = campA;
        campBScore = campB;
        runningSeconds = seconds;
        scoreMethod = scoreMethodValue == null || scoreMethodValue.isBlank() ? "SCORE_TARGET" : scoreMethodValue;
        campAGoal = campAGoalValue;
        campBGoal = campBGoalValue;
        remainSeconds = remainSecondsValue;
        playerMaxY = playerMaxYValue;
        aircraftPassengerMaxY = aircraftPassengerMaxYValue;
        allowPlayerCampSwitch = allowPlayerCampSwitchValue;
        captureStatus = captureStatusValue;
        captureIcons = captureIconsValue;
        captureEvent = captureEventValue;
        captureFocus = captureFocusValue;
        extraPayload = extraPayloadValue == null || extraPayloadValue.isBlank() ? "-" : extraPayloadValue;
    }

    public static String getRoomId() {
        return roomId;
    }

    public static String getRoomState() {
        return roomState;
    }

    public static String getAudioState() {
        return audioState;
    }

    public static int getCampAScore() {
        return campAScore;
    }

    public static int getCampBScore() {
        return campBScore;
    }

    public static int getRunningSeconds() {
        return runningSeconds;
    }

    public static String getSelfTeam() {
        return selfTeam;
    }

    public static String getSelfClass() {
        return selfClass;
    }

    public static String getScoreMethod() {
        return scoreMethod;
    }

    public static int getCampAGoal() {
        return campAGoal;
    }

    public static int getCampBGoal() {
        return campBGoal;
    }

    public static int getRemainSeconds() {
        return remainSeconds;
    }

    public static int getPlayerMaxY() {
        return playerMaxY;
    }

    public static int getAircraftPassengerMaxY() {
        return aircraftPassengerMaxY;
    }

    public static boolean isPlayerCampSwitchAllowed() {
        return allowPlayerCampSwitch;
    }

    public static String getCaptureStatus() {
        return captureStatus;
    }

    public static String getCaptureIcons() {
        return captureIcons;
    }

    public static String getCaptureEvent() {
        return captureEvent;
    }

    public static String getCaptureFocus() {
        return captureFocus;
    }

    public static String getExtraPayload() {
        return extraPayload;
    }

    public static String getAdminDebugOverlay() {
        return adminDebugOverlay;
    }

    public static void setAdminDebugOverlay(String payload) {
        adminDebugOverlay = payload == null || payload.isBlank() ? "-" : payload;
    }

    public static boolean isAdminDebugActive() {
        return adminDebugOverlay != null && !adminDebugOverlay.isBlank() && !"-".equals(adminDebugOverlay);
    }

    public static void reset() {
        update("-", "未加入房间", "-", "-", "-", 0, 0, 0, "SCORE_TARGET", 0, 0, 0, 0, 0, true, "-", "-", "-", "-", "-");
        adminDebugOverlay = "-";
    }
}
