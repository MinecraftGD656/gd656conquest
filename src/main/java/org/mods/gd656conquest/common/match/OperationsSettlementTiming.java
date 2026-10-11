package org.mods.gd656conquest.common.match;

import java.util.List;
import java.util.Locale;

public enum OperationsSettlementTiming {
    MATCH_END("对局结束时"), ACTION_RESULT("行动结果文字出现时");
    private final String label;
    OperationsSettlementTiming(String label) { this.label = label; }
    public static List<String> suggestions() { return List.of(ACTION_RESULT.label, MATCH_END.label); }
    public static OperationsSettlementTiming of(String raw) {
        return switch (raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)) {
            case "MATCH_END", "对局结束时" -> MATCH_END;
            case "ACTION_RESULT", "行动结果文字出现时" -> ACTION_RESULT;
            default -> throw new IllegalArgumentException("结算时机须为“对局结束时”或“行动结果文字出现时”");
        };
    }
    @Override public String toString() { return label; }
}
