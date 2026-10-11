package org.mods.gd656conquest.common.match;

/** Shared room setting for editor preview and synchronized cinematic subtitles. */
public final class BriefingSubtitleStyle {
    public static final double DEFAULT_SCALE = 1.5;
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 3.0;
    private BriefingSubtitleStyle() {}

    public static double validate(double scale) {
        if (!Double.isFinite(scale) || scale < MIN_SCALE || scale > MAX_SCALE)
            throw new IllegalArgumentException("战况字幕字号倍率范围：0.5–3.0");
        return scale;
    }
}
