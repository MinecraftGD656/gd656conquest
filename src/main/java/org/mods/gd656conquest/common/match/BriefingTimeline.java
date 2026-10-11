package org.mods.gd656conquest.common.match;

import java.util.*;
import org.mods.gd656conquest.common.match.BattalionSequence.Phase;

/** Shared authoring and playback rules. Offsets are anchored to stages; durations never stretch. */
public final class BriefingTimeline {
    private BriefingTimeline() {}
    public static final List<Phase> STAGES = List.of(Phase.RESULT, Phase.TRAVEL_TO_BRIEFING, Phase.BRIEFING, Phase.BRIEFING_FADE_OUT, Phase.NEXT_ASSAULT);
    public record Clip(Phase phase, int offsetTicks, int durationTicks, String value, float volume) {
        public Clip {
            if (!STAGES.contains(phase) || offsetTicks < 0 || durationTicks <= 0 || durationTicks > 12320
                || value == null || value.isBlank() || value.length() > 1024 || !Float.isFinite(volume) || volume < 0 || volume > 1)
                throw new IllegalArgumentException("素材的阶段、时间、内容或音量无效");
        }
        public int start(int briefingSeconds) { return stageStart(phase, briefingSeconds) + offsetTicks; }
        public int end(int briefingSeconds) { return start(briefingSeconds) + durationTicks; }
        public boolean active(double ticks, int briefingSeconds) { return ticks >= start(briefingSeconds) && ticks < end(briefingSeconds); }
    }
    public record Track(List<Clip> subtitles, List<Clip> sounds) {
        public Track {
            subtitles = subtitles == null ? List.of() : List.copyOf(subtitles);
            sounds = sounds == null ? List.of() : List.copyOf(sounds);
            if (subtitles.size() > 32 || sounds.size() > 32) throw new IllegalArgumentException("每条轨道最多 32 个素材");
        }
        public static Track empty() { return new Track(List.of(), List.of()); }
        public Track resolve(Map<String,String> variables) {
            return new Track(subtitles.stream().map(c -> {
                String text = c.value();
                for (var entry : variables.entrySet()) text = text.replace("{" + entry.getKey() + "}", entry.getValue());
                return new Clip(c.phase(), c.offsetTicks(), c.durationTicks(), text.substring(0,Math.min(1024,text.length())), c.volume());
            }).toList(), sounds);
        }
        public Clip subtitleAt(double tick, int briefingSeconds) {
            return subtitles.stream().filter(c -> c.active(tick, briefingSeconds)).findFirst().orElse(null);
        }
    }
    public record Scene(String key, Track attacker, Track defender) {
        public Scene {
            if (!validKey(key)) throw new IllegalArgumentException("无效的演出场景");
            attacker = attacker == null ? Track.empty() : attacker;
            defender = defender == null ? Track.empty() : defender;
        }
    }
    public static boolean validKey(String key) {
        return key != null && (key.equals("last") || terminal(key) || key.matches("remaining_[2-9]"));
    }
    public static boolean terminal(String key) { return "attacker_won".equals(key) || "attacker_lost".equals(key); }
    public static String selectScene(int battalion, int total, boolean attackersWon) {
        if (attackersWon) return "attacker_won";
        int remaining = total - battalion;
        return remaining <= 0 ? "attacker_lost" : remaining == 1 ? "last" : "remaining_" + remaining;
    }
    public static List<String> sceneKeys(int count) {
        List<String> keys = new ArrayList<>();
        for (int remaining = Math.min(9, count - 1); remaining >= 2; remaining--) keys.add("remaining_" + remaining);
        if (count > 1) keys.add("last");
        keys.add("attacker_won"); keys.add("attacker_lost"); return List.copyOf(keys);
    }
    public static String sceneLabel(String key) {
        return switch(key) { case "last" -> "最后营"; case "attacker_won" -> "进攻方获胜"; case "attacker_lost" -> "进攻方失败";
            default -> "剩 " + key.substring("remaining_".length()) + " 营"; };
    }
    public static String stageLabel(Phase phase, String scene) {
        return phase == Phase.NEXT_ASSAULT && terminal(scene) ? "行动结果" : stageLabel(phase);
    }
    public static String stageLabel(Phase phase) {
        return switch(phase) { case RESULT -> "结果停顿"; case TRAVEL_TO_BRIEFING -> "镜头运动"; case BRIEFING -> "战况简报";
            case BRIEFING_FADE_OUT -> "遮罩淡出"; case NEXT_ASSAULT -> "攻势提示"; default -> "结束"; };
    }
    public static int stageDuration(Phase phase, int seconds) {
        return switch(phase) { case RESULT -> BattalionSequence.RESULT_TICKS; case TRAVEL_TO_BRIEFING -> BattalionSequence.BRIEFING_TRAVEL_TICKS;
            case BRIEFING -> seconds * 20; case BRIEFING_FADE_OUT -> BattalionSequence.MASK_FADE_OUT_TICKS;
            case NEXT_ASSAULT -> BattalionSequence.ASSAULT_ANNOUNCEMENT_TICKS; default -> 0; };
    }
    public static int stageStart(Phase phase, int seconds) {
        int start = 0;
        for (Phase stage : STAGES) { if (stage == phase) return start; start += stageDuration(stage, seconds); }
        return start;
    }
    public static int length(String key, int seconds) { return stageStart(Phase.NEXT_ASSAULT, seconds) + stageDuration(Phase.NEXT_ASSAULT, seconds); }
    public static Phase phaseAt(int ticks, int seconds) {
        for (Phase phase : STAGES) if (ticks < stageStart(phase, seconds) + stageDuration(phase, seconds)) return phase;
        return Phase.NEXT_ASSAULT;
    }
    public static void validate(List<Scene> scenes, int seconds) {
        if (scenes == null) return;
        if (scenes.size() > 11) throw new IllegalArgumentException("演出场景数量超限");
        Set<String> keys = new HashSet<>();
        for (Scene scene : scenes) {
            if (scene == null || !keys.add(scene.key())) throw new IllegalArgumentException("演出场景重复或无效");
            for (Track track : List.of(scene.attacker(), scene.defender())) validateTrack(track, scene.key(), seconds);
        }
    }
    public static void validateTrack(Track track, String key, int seconds) {
        for (Clip clip : java.util.stream.Stream.concat(track.subtitles().stream(), track.sounds().stream()).toList()) {
            if (clip.offsetTicks() >= stageDuration(clip.phase(), seconds) || clip.end(seconds) > length(key, seconds)) throw new IllegalArgumentException("素材超出阶段或时间轴范围");
        }
        int end = -1;
        for (Clip clip : track.subtitles().stream().sorted(Comparator.comparingInt(c -> c.start(seconds))).toList()) {
            if (clip.start(seconds) < end) throw new IllegalArgumentException("同一阵营的字幕不能重叠"); end = clip.end(seconds);
        }
        for (Clip clip : track.sounds()) if (!clip.value().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("声音需使用 namespace:sound_event 格式");
    }
}
