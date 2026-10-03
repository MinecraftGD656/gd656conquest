package org.mods.gd656conquest.client.compat.xaero;

import org.mods.gd656conquest.client.hud.ClientHudDebugState;

/**
 * GD656Mods: 记录 Xaero 小地图"最近一次实际渲染"的位置, 决定原版聊天组件的布局。
 *
 * <p>左上角布局(聊天底部固定在屏幕顶端往下 {@link #CHAT_BOTTOM_OFFSET} 像素)适用于两种情况:</p>
 * <ol>
 *   <li>小地图正在显示且位于屏幕下半部分 —— 聊天上移, 保持显示在小地图上方;</li>
 *   <li>小地图没有显示(主城/关闭小地图等)但玩家正在对局内 —— 直接使用左上角布局。</li>
 * </ol>
 * <p>小地图显示但在屏幕上半部分、或不在对局内时, 聊天按原版位置渲染。</p>
 */
public final class XaeroMinimapRenderState {
    /** 超过该时长没有渲染则认为小地图当前未显示。 */
    private static final long VALID_WINDOW_NANOS = 300_000_000L;
    /** 原版聊天底部距屏幕底部的固定边距(与原版渲染基准一致)。 */
    private static final double VANILLA_BOTTOM_MARGIN = 40.0D;
    /** 左上角布局下, 聊天组件底部距屏幕顶端的距离。 */
    public static final double CHAT_BOTTOM_OFFSET = 100.0D;

    private static volatile boolean renderedInLowerHalf;
    private static volatile long lastRenderNanos;

    private XaeroMinimapRenderState() {
    }

    /**
     * 由 Xaero 渲染钩子调用, 记录本帧小地图的占位尺寸与位置。
     *
     * @param pixels       小地图占位尺寸(像素), 仅用于判断本帧是否真的渲染了小地图
     * @param positionY    小地图在屏幕上的位置(像素, 顶边)
     * @param screenHeight 屏幕高度(像素)
     */
    public static void recordRenderedPlaceholder(int pixels, int positionY, int screenHeight) {
        if (pixels <= 0 || screenHeight <= 0) {
            return;
        }
        renderedInLowerHalf = positionY >= screenHeight / 2;
        lastRenderNanos = System.nanoTime();
    }

    /**
     * 聊天需要整体向上移动的像素数, 使聊天底部落在屏幕顶端往下 {@link #CHAT_BOTTOM_OFFSET} 像素处;
     * 0 表示沿用原版位置。
     */
    public static double resolveShiftUpPixels(int guiScaledHeight) {
        if (!shouldUseTopLeftLayout()) {
            return 0.0D;
        }
        double vanillaBottom = guiScaledHeight - VANILLA_BOTTOM_MARGIN;
        return Math.max(0.0D, vanillaBottom - CHAT_BOTTOM_OFFSET);
    }

    /** 是否需要把聊天放到左上角布局。 */
    private static boolean shouldUseTopLeftLayout() {
        if (isMinimapShown()) {
            return renderedInLowerHalf;
        }
        return isInRunningRoom();
    }

    /** 小地图最近是否真的在渲染(显示中)。 */
    private static boolean isMinimapShown() {
        return System.nanoTime() - lastRenderNanos <= VALID_WINDOW_NANOS;
    }

    /** 玩家当前是否在对局内(与 Xaero 联动侧的对局判定保持一致)。 */
    private static boolean isInRunningRoom() {
        String roomId = ClientHudDebugState.getRoomId();
        String roomState = ClientHudDebugState.getRoomState();
        if (roomId == null || roomId.isBlank() || "-".equals(roomId)) {
            return false;
        }
        if (roomState == null || roomState.isBlank()) {
            return false;
        }
        return !roomState.contains("未加入")
            && !roomState.contains("主城")
            && !roomState.contains("匹配")
            && !roomState.contains("维护")
            && (roomState.contains("进行") || roomState.contains("部署") || roomState.contains("结算"));
    }
}
