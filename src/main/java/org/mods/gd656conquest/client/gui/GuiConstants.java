package org.mods.gd656conquest.client.gui;

/**
 * GUI系统的全局常量。
 * 所有关键GUI常量统一集中在此类中管理。
 */
public class GuiConstants {
    // ==================== 布局常量 ====================
    /** 所有UI元素距离屏幕边缘的统一距离 */
    public static final int SCREEN_EDGE_DISTANCE = 18;
    public static final int HEADER_HEIGHT = 25;
    public static final int GOLD_BAR_HEIGHT = 1;
    public static final int DEFAULT_PADDING = 8;
    public static final int ROW_HEADER_HEIGHT = 17;
    public static final int FLEX_COLUMN_MIN_WIDTH = 60;

    // ==================== 主题色系 #708090 ====================
    public static final int COLOR_THEME        = 0xFF708090;
    public static final int COLOR_THEME_LIGHT  = 0xFF90A0B0;
    public static final int COLOR_THEME_DARK   = 0xFF506070;
    public static final int COLOR_THEME_BG     = 0x4D708090;
    public static final int COLOR_THEME_ACCENT = 0xFF8A9AA8;

    // 兼容旧常量名，均已映射为主题色系
    public static final int COLOR_GOLD             = COLOR_THEME_ACCENT;
    public static final int COLOR_GOLD_ORANGE      = COLOR_THEME_ACCENT;
    public static final int COLOR_DARK_GOLD_ORANGE = COLOR_THEME_DARK;
    public static final int COLOR_BLACK            = COLOR_THEME_DARK;

    public static final int COLOR_WHITE       = 0xFFFFFFFF;
    public static final int COLOR_GRAY        = 0xFFAAAAAA;
    public static final int COLOR_DARK_GRAY   = 0xFF666666;
    public static final int COLOR_BG          = 0x4D444444;
    public static final int COLOR_HOVER_BORDER = 0x40808080;
    public static final int COLOR_RED         = 0xFFFF0000;
    public static final int COLOR_GREEN       = 0xFF00FF00;
    public static final int COLOR_SKY_BLUE    = 0xFF87CEEB;
    public static final int COLOR_DARK_RED    = 0xFF8B0000;

    // ==================== 动画与时间常量 ====================
    public static final long INTRO_DURATION_MS = 600;
    public static final long SLICE_DURATION_MS = 800;
    public static final long BUTTON_ANIM_DURATION_MS = 200;
    public static final int TAB_DELAY_MS = 100;
    public static final float ANIMATION_SPEED = 10.0f;
    public static final float TITLE_ANIM_OFFSET = 100.0f;
    public static final double SCROLL_AMOUNT = 40.0;
    public static final double SCROLL_SMOOTHING = 15.0;

    // ==================== 选项卡常量 ====================
    public static final int TAB_SPACING = 2;
    public static final int TAB_Y_OFFSET = 2;
    public static final int SPLIT_POINT_OFFSET = 25;
    public static final int HEADER_CLICK_ZONE = 5;
    public static final int HEADER_SCROLL_ZONE = 20;

    // ==================== 区域高度常量 ====================
    public static final int REGION_4_HEIGHT = 35;
    public static final int AREA1_BOTTOM_OFFSET = 40;

    // ==================== 版本信息 ====================
    public static final String MOD_VERSION = "0.3.6-TEST-B";
    public static final String MOD_ONLINE_VERSION = "https://modrinth.com/mod/gd656killicon/version/1.1.1.003-1.20.1-forge";
}
