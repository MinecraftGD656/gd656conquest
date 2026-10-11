package org.mods.gd656conquest.server.room.model;

import org.mods.gd656conquest.common.match.OperationsSettlementTiming;
import java.util.List;
import org.mods.gd656conquest.common.match.BattalionSequence;
import org.mods.gd656conquest.common.match.BriefingTimeline;

public record RoomBattalionConfig(int count, RoomViewPosition briefingViewPosition,
                                 int briefingSeconds, int preparationSeconds,
                                 List<BriefingTimeline.Scene> timelines,
                                 OperationsSettlementTiming settlementTiming) {
    public RoomBattalionConfig(int count, RoomViewPosition view, int briefingSeconds, int preparationSeconds) {
        this(count, view, briefingSeconds, preparationSeconds, List.of());
    }
    public RoomBattalionConfig(int count, RoomViewPosition view, int briefingSeconds, int preparationSeconds, List<BriefingTimeline.Scene> timelines) {
        this(count, view, briefingSeconds, preparationSeconds, timelines, OperationsSettlementTiming.ACTION_RESULT);
    }
    public RoomBattalionConfig {
        count = count <= 0 ? 3 : Math.min(10, count);
        briefingSeconds = briefingSeconds <= 0 ? 20 : Math.min(600, Math.max(BattalionSequence.MIN_BRIEFING_SECONDS, briefingSeconds));
        preparationSeconds = preparationSeconds <= 0 ? 10 : Math.min(300, preparationSeconds);
        settlementTiming = settlementTiming == null ? OperationsSettlementTiming.ACTION_RESULT : settlementTiming;
        timelines = timelines == null ? List.of() : List.copyOf(timelines);
        BriefingTimeline.validate(timelines, briefingSeconds);
        if (briefingViewPosition != null && (!Double.isFinite(briefingViewPosition.y())
            || !Float.isFinite(briefingViewPosition.yaw()) || !Float.isFinite(briefingViewPosition.pitch()))) {
            throw new IllegalArgumentException("战况简报位置和角度必须为有限数值");
        }
    }

    public static RoomBattalionConfig defaults() { return new RoomBattalionConfig(3, null, 20, 10); }
}
