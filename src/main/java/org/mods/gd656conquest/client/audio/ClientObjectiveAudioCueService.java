package org.mods.gd656conquest.client.audio;

import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.common.audio.AudioManager;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClientObjectiveAudioCueService {
    private static final String SOUND_CAPTURE_IN = "gd656conquest:sfx.capture_in";
    private static final String SOUND_CAPTURE_TAKED = "gd656conquest:sfx.capture_taked";
    private static final Pattern CAPTURE_ALLIES_ATTACKING_PATTERN = Pattern.compile("我军(?:正在进攻|正在夺取|进攻|夺取)据点\\s*([A-Za-z][0-9]*)");
    private static final Pattern CAPTURE_HOSTILES_ATTACKING_PATTERN = Pattern.compile("敌军(?:正在进攻|正在夺取|进攻|夺取)据点\\s*([A-Za-z][0-9]*)");
    private static final Pattern CAPTURE_LOSING_PATTERN = Pattern.compile("我军(?:正在失去|正在丢失|失去|丢失)据点\\s*([A-Za-z][0-9]*)");
    private static final Pattern CAPTURE_LOST_PATTERN = Pattern.compile("敌军(?:已成功占领|已经占领了|占领了|占领|从我军手中夺取了)据点\\s*([A-Za-z][0-9]*)");
    private static final Pattern CAPTURE_TAKEN_PATTERN = Pattern.compile("我军(?:已成功占领|已经占领了|占领了|占领|已从敌军手中夺下)据点\\s*([A-Za-z][0-9]*)");

    private static String lastCaptureVoiceEventKey = "-";
    private static String lastCaptureTakingEventKey = "-";
    private static String lastCaptureFocusCode = "-";

    private ClientObjectiveAudioCueService() {
    }

    static void tick() {
        String roomId = normalize(ClientHudDebugState.getRoomId());
        String roomState = normalize(ClientHudDebugState.getRoomState());
        boolean inRoom = !roomId.isBlank() && !"-".equals(roomId) && !roomState.contains("未加入");
        triggerCapturePointSfxIfNeeded(roomId, inRoom);
        triggerCaptureVoiceIfNeeded(roomId);
    }

    static void reset() {
        lastCaptureVoiceEventKey = "-";
        lastCaptureTakingEventKey = "-";
        lastCaptureFocusCode = "-";
    }

    private static void triggerCaptureVoiceIfNeeded(String roomId) {
        String captureEvent = normalize(ClientHudDebugState.getCaptureEvent());
        if ("-".equals(captureEvent)) {
            return;
        }
        CaptureVoiceCue cue = resolveCaptureVoiceCue(captureEvent);
        if (cue == null || !isCaptureVoiceCueValid(cue)) {
            return;
        }
        String eventKey = roomId + "|" + cue.typeKey + "|" + cue.captureCode;
        if (eventKey.equals(lastCaptureVoiceEventKey)) {
            return;
        }
        lastCaptureVoiceEventKey = eventKey;
        AudioManager.getInstance().playSound(cue.soundId, 1.0F, 1.0F);
    }

    private static void triggerCapturePointSfxIfNeeded(String roomId, boolean inRoom) {
        if (!inRoom) {
            lastCaptureFocusCode = "-";
            return;
        }
        String captureFocus = normalize(ClientHudDebugState.getCaptureFocus());
        String focusCode = extractCaptureFocusCode(captureFocus);
        if (!"-".equals(focusCode) && !focusCode.equals(lastCaptureFocusCode)) {
            AudioManager.getInstance().playSound(SOUND_CAPTURE_IN, 1.0F, 1.0F);
        }
        lastCaptureFocusCode = focusCode;

        String captureEvent = normalize(ClientHudDebugState.getCaptureEvent());
        CaptureVoiceCue takenCue = resolveCaptureVoice(captureEvent, CAPTURE_TAKEN_PATTERN, "objective_taken");
        if (takenCue == null || !isCaptureVoiceCueValid(takenCue)) {
            return;
        }
        String takingKey = roomId + "|" + takenCue.captureCode;
        if (takingKey.equals(lastCaptureTakingEventKey)) {
            return;
        }
        lastCaptureTakingEventKey = takingKey;
        AudioManager.getInstance().playSound(SOUND_CAPTURE_TAKED, 1.0F, 1.0F);
    }

    private static CaptureVoiceCue resolveCaptureVoiceCue(String captureEvent) {
        CaptureVoiceCue hostilesAttacking = resolveCaptureVoice(captureEvent, CAPTURE_HOSTILES_ATTACKING_PATTERN, "objective_attacking_hostiles");
        if (hostilesAttacking != null) {
            return hostilesAttacking;
        }
        CaptureVoiceCue losing = resolveCaptureVoice(captureEvent, CAPTURE_LOSING_PATTERN, "objective_attacking_hostiles");
        if (losing != null) {
            return losing;
        }
        CaptureVoiceCue lost = resolveCaptureVoice(captureEvent, CAPTURE_LOST_PATTERN, "objective_lost");
        if (lost != null) {
            return lost;
        }
        return resolveCaptureVoice(captureEvent, CAPTURE_TAKEN_PATTERN, "objective_taken");
    }

    private static CaptureVoiceCue resolveCaptureVoice(String captureEvent, Pattern pattern, String keyPrefix) {
        Matcher matcher = pattern.matcher(captureEvent);
        if (!matcher.find()) {
            return null;
        }
        String code = normalizeCaptureCode(matcher.group(1));
        if (code.isBlank()) {
            return null;
        }
        String typeKey = keyPrefix + "_" + code;
        String soundId = "gd656conquest:voice.capture." + typeKey;
        return new CaptureVoiceCue(typeKey, code, soundId);
    }

    private static boolean isCaptureVoiceCueValid(CaptureVoiceCue cue) {
        if (cue == null || cue.typeKey == null) {
            return false;
        }
        if (cue.typeKey.startsWith("objective_taken") || cue.typeKey.startsWith("objective_lost")) {
            TeamToken self = TeamToken.fromClientDebug(ClientHudDebugState.getSelfTeam());
            return self != TeamToken.NEUTRAL;
        }
        return true;
    }

    private static String extractCaptureFocusCode(String captureFocusPayload) {
        if (captureFocusPayload == null || captureFocusPayload.isBlank() || "-".equals(captureFocusPayload)) {
            return "-";
        }
        int commaIndex = captureFocusPayload.indexOf(',');
        String code = commaIndex < 0 ? captureFocusPayload : captureFocusPayload.substring(0, commaIndex);
        String normalized = normalize(code).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][0-9]*")) {
            return "-";
        }
        return normalized;
    }

    private static String normalizeCaptureCode(String code) {
        String normalized = normalize(code).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][0-9]*")) {
            return "";
        }
        if (normalized.length() >= 2) {
            char last = normalized.charAt(normalized.length() - 1);
            if (last == '1') {
                return "a";
            }
            if (last == '2') {
                return "b";
            }
            if (last == '3') {
                return "c";
            }
        }
        return normalized.substring(0, 1);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    private record CaptureVoiceCue(String typeKey, String captureCode, String soundId) {
    }

    private enum TeamToken {
        NEUTRAL,
        CAMP_A,
        CAMP_B;

        static TeamToken fromClientDebug(String selfTeam) {
            String normalized = normalize(selfTeam).toLowerCase(Locale.ROOT);
            if (normalized.contains("campa") || normalized.contains("阵营a")) {
                return CAMP_A;
            }
            if (normalized.contains("campb") || normalized.contains("阵营b")) {
                return CAMP_B;
            }
            return NEUTRAL;
        }
    }
}
