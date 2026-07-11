package org.mods.gd656conquest.client.compat.superbwarfare;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomMarkerActionPacket;
import org.mods.gd656conquest.network.packet.ShellingConfirmTargetPacket;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;

import java.util.UUID;
import org.lwjgl.glfw.GLFW;

public final class ClientSuperbWarfareShellingHook {
    private static final String SBW_ARTILLERY_SCREEN_CLASS = "com.atsuishio.superbwarfare.client.screens.ArtilleryIndicatorScreen";
    private static final int CONFIRM_HOLD_TICKS = 24;
    private static final String OBSERVE_GROUND_MARKER_KIND = "OBSERVE_GROUND";

    private int holdTicks;
    private boolean leftClickHeld;
    private boolean sentDuringCurrentHold;
    private long lastMissingTargetFeedbackMs;

    public void tick(Minecraft minecraft) {
        if (!canHandleShellingObservation(minecraft)) {
            reset();
            return;
        }
        boolean isLeftClickDown = isLeftClickDown(minecraft);
        if (!isLeftClickDown) {
            if (leftClickHeld) {
                handleLeftClickRelease(minecraft);
            }
            leftClickHeld = false;
            resetHoldState();
            return;
        }
        leftClickHeld = true;
        holdTicks = Math.min(CONFIRM_HOLD_TICKS + 1, holdTicks + 1);
        
        if (holdTicks < CONFIRM_HOLD_TICKS) {
            return;
        }
        if (sentDuringCurrentHold) {
            return;
        }
        BlockPos targetPos = resolveTargetBlockPos(minecraft);
        if (targetPos == null) {
            maybeShowMissingTargetHint(minecraft);
            sentDuringCurrentHold = true;
            return;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        String shellingId = SuperbWarfareCompatBridge.getManagedShellingId(stack);
        if (shellingId.isBlank()) {
            sentDuringCurrentHold = true;
            return;
        }
        UUID taggedPlayerId = SuperbWarfareCompatBridge.getManagedShellingPlayerId(stack);
        if (taggedPlayerId != null && !taggedPlayerId.equals(minecraft.player.getUUID())) {
            sentDuringCurrentHold = true;
            return;
        }
        NetworkManager.getChannel().sendToServer(ShellingConfirmTargetPacket.of(shellingId, targetPos));
        sentDuringCurrentHold = true;
        
        // 成功触发后关闭望远镜 GUI
        if (minecraft.screen != null && SBW_ARTILLERY_SCREEN_CLASS.equals(minecraft.screen.getClass().getName())) {
            minecraft.setScreen(null);
        }
    }

    public void reset() {
        leftClickHeld = false;
        resetHoldState();
        lastMissingTargetFeedbackMs = 0L;
    }

    private void resetHoldState() {
        holdTicks = 0;
        sentDuringCurrentHold = false;
    }

    private void handleLeftClickRelease(Minecraft minecraft) {
        if (sentDuringCurrentHold || holdTicks <= 0) {
            return;
        }
        BlockPos targetPos = resolveTargetBlockPos(minecraft);
        if (targetPos == null) {
            maybeShowMissingTargetHint(minecraft);
            return;
        }
        NetworkManager.getChannel().sendToServer(RoomMarkerActionPacket.place(targetPos, OBSERVE_GROUND_MARKER_KIND, null));
    }

    private boolean canHandleShellingObservation(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return false;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        return SuperbWarfareCompatBridge.isManagedArtilleryIndicator(stack);
    }

    private boolean isLeftClickDown(Minecraft minecraft) {
        return minecraft != null
            && minecraft.getWindow() != null
            && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private BlockPos resolveTargetBlockPos(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        net.minecraft.world.phys.HitResult result = minecraft.player.level().clip(
            new net.minecraft.world.level.ClipContext(
                minecraft.player.getEyePosition(1.0f),
                minecraft.player.getEyePosition(1.0f).add(minecraft.player.getViewVector(1.0f).scale(512.0)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                minecraft.player
            )
        );
        if (result == null || result.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return null;
        }
        return net.minecraft.core.BlockPos.containing(result.getLocation());
    }

    private void maybeShowMissingTargetHint(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        if (now - lastMissingTargetFeedbackMs < 1200L || minecraft == null || minecraft.player == null) {
            return;
        }
        lastMissingTargetFeedbackMs = now;
        minecraft.player.displayClientMessage(Component.literal("请先标定目标坐标"), true);
    }
}
