package org.mods.gd656conquest.client.marker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.common.entity.AmmoBoxEntity;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientRoomTeamMirror;
import org.mods.gd656conquest.client.render.ClientWorldHudProjectionSupport;
import org.mods.gd656conquest.client.render.CaptureDiamondRenderer;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomMarkerActionPacket;
import org.mods.gd656conquest.network.packet.RoomMarkerSyncPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientRoomMarkerState {
    private static final double MAX_MARKER_DISTANCE = 256.0D;
    private static final double MAX_MARKER_DISTANCE_SQR = MAX_MARKER_DISTANCE * MAX_MARKER_DISTANCE;
    private static final int AUTO_MARK_AIM_TICKS = 20;
    private static final int AUTO_MARK_REFRESH_THRESHOLD_TICKS = 10;
    private static final float AUTO_MARK_TRIGGER_RADIUS_PIXELS = 15.0F;
    private static final float ICON_WORLD_Y_OFFSET = 1.10F;
    private static final float CROSSHAIR_HOVER_RADIUS_PIXELS = 24.0F;
    private static final float CROSSHAIR_LABEL_RADIUS_PIXELS = 70.0F;
    private static final float ICON_ENTRY_DURATION_TICKS = 14.0F;
    private static final long HELP_PULSE_DURATION_MS = 520L;
    private static final float WORLD_ICON_ALPHA_NEAR_CROSSHAIR = 0.05F;
    private static final float WORLD_ICON_ALPHA_FAR_CROSSHAIR = 0.80F;
    private static final float WORLD_ICON_CROSSHAIR_ALPHA_RADIUS_PIXELS = 300.0F;
    private static final float WORLD_ICON_CROSSHAIR_ALPHA_CURVE = 1.35F;
    private static final float MARKER_TRIANGLE_OPACITY_NEAR_CROSSHAIR = 0.30F;
    private static final float MARKER_TRIANGLE_OPACITY_FAR_CROSSHAIR = 0.80F;
    private static final float MARKER_TRIANGLE_OPACITY_RADIUS_PIXELS = 140.0F;
    private static final float MARKER_TRIANGLE_OPACITY_CURVE = 1.15F;
    private static final float MARKER_SHAPE_OPACITY = 0.90F;
    private static final float AUTO_ENEMY_SHAPE_OPACITY = 0.60F;
    private static final long DOUBLE_CLICK_WINDOW_MS = 200L;

    private static List<ClientMarker> markers = List.of();
    private static Matrix4f worldProjectionMatrix;
    private static UUID autoAimTargetId;
    private static long autoAimStartTick = -1L;
    private static UUID autoAimTriggeredTargetId;
    private static long lastKeyPressAtMs;
    private static final Map<UUID, Long> HELP_PULSE_STARTED_AT_MS = new HashMap<>();
    private static final Map<UUID, Long> dynamicEntityFirstSeen = new HashMap<>();

    private ClientRoomMarkerState() {
    }

    public static void update(List<RoomMarkerSyncPacket.MarkerEntry> incoming) {
        Minecraft minecraft = Minecraft.getInstance();
        long syncTick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        Map<String, ClientMarker> previousByKey = new HashMap<>();
        for (ClientMarker marker : markers) {
            if (marker != null) {
                previousByKey.put(marker.renderKey(), marker);
            }
        }
        List<ClientMarker> next = new ArrayList<>();
        if (incoming != null) {
            for (RoomMarkerSyncPacket.MarkerEntry entry : incoming) {
                if (entry == null || entry.remainingTicks() <= 0) {
                    continue;
                }
                MarkerKind kind = MarkerKind.from(entry.kind());
                if (kind.isEnemy() && isIgnoredEnemyTarget(entry.targetPlayerId())) {
                    continue;
                }
                String renderKey = ClientMarker.renderKeyOf(
                    entry.ownerId(),
                    kind,
                    entry.blockX(),
                    entry.blockY(),
                    entry.blockZ(),
                    entry.targetPlayerId()
                );
                ClientMarker previous = previousByKey.get(renderKey);
                next.add(new ClientMarker(
                    entry.ownerId(),
                    entry.ownerName(),
                    kind,
                    entry.blockX(),
                    entry.blockY(),
                    entry.blockZ(),
                    entry.targetPlayerId(),
                    entry.remainingTicks(),
                    syncTick,
                    previous == null ? syncTick : previous.firstSeenTick()
                ));
            }
        }
        next.sort(Comparator.comparing(ClientMarker::ownerName, String.CASE_INSENSITIVE_ORDER));
        markers = List.copyOf(next);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !isInRunningRoom()) {
            reset();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        markers = markers.stream()
            .filter(marker -> marker.remainingTicksAt(gameTime) > 0)
            .toList();
        HELP_PULSE_STARTED_AT_MS.entrySet().removeIf(entry -> nowMs() - entry.getValue() > HELP_PULSE_DURATION_MS);
        tickAutoEnemyMarking(minecraft, gameTime);
    }

    public static void reset() {
        markers = List.of();
        clearProjectionState();
        autoAimTargetId = null;
        autoAimStartTick = -1L;
        autoAimTriggeredTargetId = null;
        lastKeyPressAtMs = 0L;
        HELP_PULSE_STARTED_AT_MS.clear();
        dynamicEntityFirstSeen.clear();
    }

    public static void triggerHelpPulse(UUID playerId) {
        if (playerId == null) {
            return;
        }
        HELP_PULSE_STARTED_AT_MS.put(playerId, nowMs());
    }

    public static void handleKeyPress(Minecraft minecraft) {
        if (!canPlaceMarkerNow(minecraft)) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        boolean enemyGroundMode = nowMs - lastKeyPressAtMs <= DOUBLE_CLICK_WINDOW_MS;
        lastKeyPressAtMs = nowMs;
        MarkerPlacement placement = resolveMarkerPlacement(minecraft, enemyGroundMode);
        if (placement == null) {
            return;
        }
        NetworkManager.getChannel().sendToServer(RoomMarkerActionPacket.place(placement.blockPos(), placement.kind().name(), placement.targetPlayerId()));
    }

    public static void captureProjectionState(RenderLevelStageEvent event) {
        worldProjectionMatrix = ClientWorldHudProjectionSupport.captureViewProjectionMatrix(event);
        if (worldProjectionMatrix == null) {
            clearProjectionState();
            return;
        }
    }

    private static void clearProjectionState() {
        worldProjectionMatrix = null;
    }

    public static void renderHudOverlay(RenderGuiOverlayEvent.Post event) {
        renderHudOverlay(event.getGuiGraphics());
    }

    public static void renderHudOverlay(GuiGraphics gui) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || worldProjectionMatrix == null || !isInRunningRoom()) {
            return;
        }
        List<ClientMarker> renderMarkers = collectRenderableMarkers(minecraft, minecraft.level.getGameTime());
        if (renderMarkers.isEmpty()) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        HoveredMarker hovered = null;
        for (ClientMarker marker : renderMarkers) {
            int remainingTicks = marker.remainingTicksAt(gameTime);
            if (remainingTicks <= 0) {
                continue;
            }
            boolean isAmmoBoxEmpty = marker.kind() == MarkerKind.AMMO_BOX && isPrimaryWeaponEmpty(minecraft);
            Vec3 anchor = marker.worldAnchor(minecraft, minecraft.getFrameTime());
            ScreenProjection projection = projectToScreen(minecraft, marker, anchor, isAmmoBoxEmpty);
            if (projection == null) {
                continue;
            }
            double crosshairDistance = resolveCrosshairDistance(gui, projection);
            boolean isHovered = crosshairDistance <= CROSSHAIR_HOVER_RADIUS_PIXELS;
            if (projection.insideViewport() && isHovered && (hovered == null || crosshairDistance < hovered.crosshairDistance())) {
                hovered = new HoveredMarker(marker, projection, anchor, crosshairDistance);
            }
            float lifetimeAlpha = Mth.clamp(Math.min(remainingTicks, 20) / 20.0F, 0.0F, 1.0F);
            float weightedAlpha = resolveCrosshairWeightedIconAlpha(gui, projection) * lifetimeAlpha;
            if (weightedAlpha <= 0.01F) {
                continue;
            }
            float markerAge = marker.ageTicksAt(gameTime) + minecraft.getFrameTime();
            float pulse = 0.5F + 0.5F * Mth.sin(markerAge * 0.16F);
            float entryProgress = resolveEntryAnimationProgress(markerAge);
            float entryOffset = resolveEntryYOffset(entryProgress);
            float shapeOpacity = resolveShapeOpacity(marker.kind()) * lifetimeAlpha;
            String targetClass = "";
            if (marker.kind().isFriendlyPersistentOrDownedMarker() || marker.kind().isDownedMarker()) {
                Player targetPlayer = marker.resolveTrackedPlayer(minecraft);
                if (targetPlayer != null) {
                    targetClass = ClientRoomTeamMirror.getPlayerClass(targetPlayer);
                }
            }

            TacticalMarkerRenderer.drawMarker(
                gui,
                (float) projection.screenX(),
                (float) projection.screenY() - entryOffset,
                weightedAlpha,
                shapeOpacity,
                pulse,
                isHovered,
                marker.kind().toVisualType(),
                resolveEntryScale(entryProgress),
                resolveHelpPulseProgress(marker),
                targetClass,
                isAmmoBoxEmpty
            );
        }
        if (hovered != null && hovered.crosshairDistance() <= CROSSHAIR_LABEL_RADIUS_PIXELS) {
            renderHoveredLabel(gui, minecraft, hovered);
        }
    }

    private static boolean isPrimaryWeaponEmpty(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        net.minecraft.world.item.ItemStack primary = minecraft.player.getMainHandItem();
        if (primary.isEmpty()) {
            return false;
        }
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            java.lang.reflect.Method getIGunOrNull = iGunClass.getMethod("getIGunOrNull", net.minecraft.world.item.ItemStack.class);
            Object iGun = getIGunOrNull.invoke(null, primary);
            if (iGun != null) {
                int dummyAmmo = 0;
                try {
                    java.lang.reflect.Method useDummyAmmo = iGun.getClass().getMethod("useDummyAmmo", net.minecraft.world.item.ItemStack.class);
                    boolean usesDummyAmmo = (Boolean) useDummyAmmo.invoke(iGun, primary);
                    if (usesDummyAmmo) {
                        java.lang.reflect.Method getDummyAmmoAmount = iGun.getClass().getMethod("getDummyAmmoAmount", net.minecraft.world.item.ItemStack.class);
                        dummyAmmo = (Integer) getDummyAmmoAmount.invoke(iGun, primary);
                    }
                } catch (Exception ignored) {
                }
                
                int inventoryAmmo = 0;
                try {
                    java.lang.reflect.Method getGunId = iGun.getClass().getMethod("getGunId", net.minecraft.world.item.ItemStack.class);
                    Object gunIdObject = getGunId.invoke(iGun, primary);
                    if (gunIdObject instanceof net.minecraft.resources.ResourceLocation gunId) {
                        Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
                        java.lang.reflect.Method getCommonGunIndex = timelessApiClass.getMethod("getCommonGunIndex", net.minecraft.resources.ResourceLocation.class);
                        Object gunIndexOptionalObject = getCommonGunIndex.invoke(null, gunId);
                        if (gunIndexOptionalObject instanceof java.util.Optional<?> gunIndexOptional && gunIndexOptional.isPresent()) {
                            Object gunIndex = gunIndexOptional.get();
                            java.lang.reflect.Method getGunData = gunIndex.getClass().getMethod("getGunData");
                            Object gunData = getGunData.invoke(gunIndex);
                            if (gunData != null) {
                                java.lang.reflect.Method getAmmoId = gunData.getClass().getMethod("getAmmoId");
                                Object ammoIdObject = getAmmoId.invoke(gunData);
                                if (ammoIdObject instanceof net.minecraft.resources.ResourceLocation ammoId) {
                                    Class<?> builderClass = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
                                    java.lang.reflect.Method create = builderClass.getMethod("create");
                                    Object builder = create.invoke(null);
                                    java.lang.reflect.Method setId = builderClass.getMethod("setId", net.minecraft.resources.ResourceLocation.class);
                                    java.lang.reflect.Method setCount = builderClass.getMethod("setCount", int.class);
                                    java.lang.reflect.Method build = builderClass.getMethod("build");
                                    builder = setId.invoke(builder, ammoId);
                                    builder = setCount.invoke(builder, 1);
                                    Object ammoStackObject = build.invoke(builder);
                                    if (ammoStackObject instanceof net.minecraft.world.item.ItemStack ammoStack) {
                                        for (net.minecraft.world.item.ItemStack stack : minecraft.player.getInventory().items) {
                                            if (stack != null && !stack.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameTags(stack, ammoStack)) {
                                                inventoryAmmo += stack.getCount();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                return dummyAmmo + inventoryAmmo <= 0;
            }
        } catch (Exception ignored) {}
        try {
            Class<?> sbwBridge = Class.forName("org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge");
            java.lang.reflect.Method isGunItem = sbwBridge.getMethod("isGunItem", net.minecraft.world.item.ItemStack.class);
            if ((Boolean) isGunItem.invoke(null, primary)) {
                java.lang.reflect.Method getAmmo = sbwBridge.getMethod("getReserveAmmo", net.minecraft.world.item.ItemStack.class);
                int ammo = (Integer) getAmmo.invoke(null, primary);
                return ammo <= 0;
            }
        } catch (Exception ignored) {}
        
        return false;
    }

    private static void renderHoveredLabel(GuiGraphics gui, Minecraft minecraft, HoveredMarker hovered) {
        ClientMarker marker = hovered.marker();
        if (marker.kind() == MarkerKind.AUTO_ENEMY
            || marker.kind() == MarkerKind.ALLY_PERSISTENT
            || marker.kind() == MarkerKind.SQUAD_PERSISTENT) {
            return;
        }
        float weightedAlpha = resolveCrosshairWeightedIconAlpha(gui, hovered.projection());
        if (weightedAlpha <= 0.01F) {
            return;
        }
        double distance = minecraft.player == null ? 0.0D : minecraft.player.position().distanceTo(hovered.anchor());
        String line1 = marker.ownerName() + resolveMarkerLabelSuffix(marker.kind());
        String line2 = String.format(Locale.ROOT, "%.0fm", distance);
        int primaryRgb = resolveMarkerLabelColor(marker.kind());
        int secondaryRgb = marker.kind().isEnemy() ? 0x00F1C1C1 : 0x00E6EEF8;
        float centerX = (float) hovered.projection().screenX();
        float topY = (float) hovered.projection().screenY() - 30.0F;
        float alpha = 0.20F;
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, line1, centerX, topY, primaryRgb, alpha, true, 0.68F);
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, line2, centerX, topY + 10.5F, secondaryRgb, alpha, false, 0.58F);
    }

    private static HoveredMarker resolveHoveredMarker(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || worldProjectionMatrix == null) {
            return null;
        }
        List<ClientMarker> renderMarkers = collectRenderableMarkers(minecraft, minecraft.level.getGameTime());
        if (renderMarkers.isEmpty()) {
            return null;
        }
        HoveredMarker best = null;
        for (ClientMarker marker : renderMarkers) {
            if (marker.remainingTicksAt(minecraft.level.getGameTime()) <= 0) {
                continue;
            }
            boolean isAmmoBoxEmpty = marker.kind() == MarkerKind.AMMO_BOX && isPrimaryWeaponEmpty(minecraft);
            Vec3 anchor = marker.worldAnchor(minecraft, minecraft.getFrameTime());
            ScreenProjection projection = projectToScreen(minecraft, marker, anchor, isAmmoBoxEmpty);
            if (projection == null) {
                continue;
            }
            if (!projection.insideViewport()) {
                continue;
            }
            double distance = resolveCrosshairDistance(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(),
                projection
            );
            if (distance > CROSSHAIR_HOVER_RADIUS_PIXELS) {
                continue;
            }
            if (best == null || distance < best.crosshairDistance()) {
                best = new HoveredMarker(marker, projection, anchor, distance);
            }
        }
        return best;
    }

    private static MarkerPlacement resolveMarkerPlacement(Minecraft minecraft, boolean enemyGroundMode) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Vec3 start = minecraft.player.getEyePosition(minecraft.getFrameTime());
        Vec3 look = minecraft.player.getViewVector(minecraft.getFrameTime()).normalize();
        Vec3 end = start.add(look.scale(MAX_MARKER_DISTANCE));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        double blockHitDistanceSqr = blockHit.getType() == HitResult.Type.BLOCK ? start.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        DirectEnemyHit enemyHit = resolveDirectEnemyHit(minecraft, start, end, blockHitDistanceSqr);
        if (enemyHit != null) {
            return new MarkerPlacement(MarkerKind.DIRECT_ENEMY, enemyHit.targetPlayer().blockPosition(), enemyHit.targetPlayer().getUUID());
        }
        Player crosshairEnemy = resolveNearestCrosshairEnemy(minecraft, start, look);
        if (crosshairEnemy != null) {
            return new MarkerPlacement(MarkerKind.DIRECT_ENEMY, crosshairEnemy.blockPosition(), crosshairEnemy.getUUID());
        }
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (start.distanceToSqr(Vec3.atCenterOf(blockHit.getBlockPos())) > MAX_MARKER_DISTANCE_SQR) {
            return null;
        }
        return new MarkerPlacement(enemyGroundMode ? MarkerKind.ENEMY : MarkerKind.NORMAL, blockHit.getBlockPos(), null);
    }

    private static DirectEnemyHit resolveDirectEnemyHit(Minecraft minecraft, Vec3 start, Vec3 end, double blockHitDistanceSqr) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        DirectEnemyHit best = null;
        for (Player targetPlayer : minecraft.level.players()) {
            if (!isEnemyPlayer(minecraft.player, targetPlayer) || targetPlayer.isSpectator() || isIgnoredEnemyTarget(targetPlayer)) {
                continue;
            }
            AABB expandedBox = targetPlayer.getBoundingBox().inflate(0.45D, 0.30D, 0.45D);
            Optional<Vec3> clipped = expandedBox.clip(start, end);
            if (clipped.isEmpty()) {
                if (!expandedBox.contains(start)) {
                    continue;
                }
                clipped = Optional.of(start);
            }
            double entityDistanceSqr = start.distanceToSqr(clipped.get());
            if (entityDistanceSqr > MAX_MARKER_DISTANCE_SQR) {
                continue;
            }
            if (entityDistanceSqr > blockHitDistanceSqr + 9.0D) {
                continue;
            }
            if (!hasClearSightToPlayer(minecraft, start, targetPlayer)) {
                continue;
            }
            if (best == null || entityDistanceSqr < best.distanceSqr()) {
                best = new DirectEnemyHit(targetPlayer, entityDistanceSqr);
            }
        }
        return best;
    }

    private static Player resolveNearestCrosshairEnemy(Minecraft minecraft, Vec3 eyePosition, Vec3 look) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || eyePosition == null || look == null) {
            return null;
        }
        Player best = null;
        double bestAlignment = 0.9972D;
        for (Player targetPlayer : minecraft.level.players()) {
            if (!isEnemyPlayer(minecraft.player, targetPlayer) || targetPlayer.isSpectator() || isIgnoredEnemyTarget(targetPlayer)) {
                continue;
            }
            Vec3 targetCenter = targetPlayer.getBoundingBox().getCenter().add(0.0D, targetPlayer.getBbHeight() * 0.12D, 0.0D);
            Vec3 toTarget = targetCenter.subtract(eyePosition);
            double distanceSqr = toTarget.lengthSqr();
            if (distanceSqr <= 1.0E-6D || distanceSqr > MAX_MARKER_DISTANCE_SQR) {
                continue;
            }
            if (!hasClearSightToPlayer(minecraft, eyePosition, targetPlayer)) {
                continue;
            }
            double alignment = look.dot(toTarget.normalize());
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                best = targetPlayer;
            }
        }
        return best;
    }

    private static Player resolveAutoMarkTarget(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || worldProjectionMatrix == null) {
            return null;
        }
        Vec3 start = minecraft.player.getEyePosition(minecraft.getFrameTime());
        Vec3 look = minecraft.player.getViewVector(minecraft.getFrameTime()).normalize();
        Vec3 end = start.add(look.scale(MAX_MARKER_DISTANCE));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        double blockHitDistanceSqr = blockHit.getType() == HitResult.Type.BLOCK ? start.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double bestCrosshairDistance = AUTO_MARK_TRIGGER_RADIUS_PIXELS;
        Player best = null;
        DirectEnemyHit directHit = resolveDirectEnemyHit(minecraft, start, end, blockHitDistanceSqr);
        if (directHit != null) {
            best = directHit.targetPlayer();
            bestCrosshairDistance = 0.0D;
        }
        for (Player targetPlayer : minecraft.level.players()) {
            if (!isEnemyPlayer(minecraft.player, targetPlayer) || targetPlayer.isSpectator() || isIgnoredEnemyTarget(targetPlayer)) {
                continue;
            }
            Vec3 toTarget = targetPlayer.getBoundingBox().getCenter().subtract(start);
            double distanceSqr = toTarget.lengthSqr();
            if (distanceSqr <= 1.0E-6D || distanceSqr > MAX_MARKER_DISTANCE_SQR) {
                continue;
            }
            if (!hasClearSightToPlayer(minecraft, start, targetPlayer)) {
                continue;
            }
            ScreenProjection projection = projectToScreen(minecraft, resolveAutoMarkTriggerAnchor(targetPlayer, minecraft.getFrameTime()));
            if (projection == null) {
                continue;
            }
            double crosshairDistance = resolveCrosshairDistance(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(),
                projection
            );
            if (crosshairDistance > AUTO_MARK_TRIGGER_RADIUS_PIXELS || crosshairDistance > bestCrosshairDistance) {
                continue;
            }
            best = targetPlayer;
            bestCrosshairDistance = crosshairDistance;
        }
        return best;
    }

    private static boolean hasClearSightToPlayer(Minecraft minecraft, Vec3 start, Player targetPlayer) {
        if (minecraft == null || minecraft.level == null || start == null || targetPlayer == null) {
            return false;
        }
        for (Vec3 samplePoint : resolveSightSamplePoints(targetPlayer, minecraft.getFrameTime())) {
            BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
                start,
                samplePoint,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                minecraft.player
            ));
            if (blockHit.getType() != HitResult.Type.BLOCK) {
                return true;
            }
            if (start.distanceToSqr(blockHit.getLocation()) + 0.01D >= start.distanceToSqr(samplePoint)) {
                return true;
            }
        }
        return false;
    }

    private static List<Vec3> resolveSightSamplePoints(Player targetPlayer, float partialTick) {
        if (targetPlayer == null) {
            return List.of();
        }
        Vec3 position = targetPlayer.getPosition(partialTick);
        double height = targetPlayer.getBbHeight();
        return List.of(
            position.add(0.0D, height * 0.25D, 0.0D),
            position.add(0.0D, height * 0.58D, 0.0D),
            position.add(0.0D, height * 0.90D, 0.0D)
        );
    }

    private static boolean canPlaceMarkerNow(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.screen != null || !isInRunningRoom()) {
            return false;
        }
        return !ClientRoomLockState.shouldReleaseMouse();
    }

    private static void tickAutoEnemyMarking(Minecraft minecraft, long gameTick) {
        if (!canPlaceMarkerNow(minecraft)) {
            clearAutoAimState();
            return;
        }
        Player targetPlayer = resolveAutoMarkTarget(minecraft);
        if (targetPlayer == null) {
            clearAutoAimState();
            return;
        }
        UUID targetId = targetPlayer.getUUID();
        if (!targetId.equals(autoAimTargetId)) {
            autoAimTargetId = targetId;
            autoAimStartTick = gameTick;
            autoAimTriggeredTargetId = null;
            return;
        }
        if (autoAimTriggeredTargetId != null && autoAimTriggeredTargetId.equals(targetId)) {
            ClientMarker activeAutoMarker = resolveActiveAutoMarker(minecraft.player == null ? null : minecraft.player.getUUID(), targetId, gameTick);
            if (activeAutoMarker != null && activeAutoMarker.remainingTicksAt(gameTick) > AUTO_MARK_REFRESH_THRESHOLD_TICKS) {
                return;
            }
        }
        if (autoAimStartTick < 0L || gameTick - autoAimStartTick < AUTO_MARK_AIM_TICKS) {
            return;
        }
        autoAimTriggeredTargetId = targetId;
        NetworkManager.getChannel().sendToServer(RoomMarkerActionPacket.place(targetPlayer.blockPosition(), MarkerKind.AUTO_ENEMY.name(), targetId));
    }

    private static boolean isEnemyPlayer(Player selfPlayer, Player targetPlayer) {
        if (selfPlayer == null || targetPlayer == null || selfPlayer == targetPlayer || !targetPlayer.isAlive()) {
            return false;
        }
        Team selfTeam = selfPlayer.getTeam();
        Team targetTeam = targetPlayer.getTeam();
        if (selfTeam != null && targetTeam != null) {
            return !selfTeam.isAlliedTo(targetTeam);
        }
        TeamToken selfToken = TeamToken.fromClientDebug(ClientHudDebugState.getSelfTeam());
        TeamToken targetToken = TeamToken.fromScoreboardTeam(targetTeam);
        if (selfToken == TeamToken.UNKNOWN || targetToken == TeamToken.UNKNOWN) {
            return false;
        }
        return selfToken != targetToken;
    }

    private static boolean isIgnoredEnemyTarget(Player targetPlayer) {
        return targetPlayer != null && isIgnoredEnemyTarget(targetPlayer.getUUID());
    }

    private static boolean isIgnoredEnemyTarget(UUID targetPlayerId) {
        return targetPlayerId != null && ClientPlayerAnimatorDebugState.isPreviewActive(targetPlayerId);
    }

    private static ScreenProjection projectToScreen(Minecraft minecraft, ClientMarker marker, Vec3 worldPos, boolean isAmmoBoxEmpty) {
        if (minecraft.player == null || worldProjectionMatrix == null || worldPos == null || marker == null) {
            return null;
        }
        double edgePadding = resolveMarkerEdgePadding(marker.kind());
        ClientWorldHudProjectionSupport.ProjectedHudPoint projected =
            ClientWorldHudProjectionSupport.projectToHud(minecraft, worldProjectionMatrix, worldPos, edgePadding);
        if (projected == null) {
            return null;
        }
        if (!projected.insideViewport() && !shouldKeepMarkerPinnedToScreenEdge(marker, isAmmoBoxEmpty)) {
            return null;
        }
        return new ScreenProjection(projected.screenX(), projected.screenY(), projected.insideViewport());
    }

    private static ScreenProjection projectToScreen(Minecraft minecraft, Vec3 worldPos) {
        if (minecraft.player == null || worldProjectionMatrix == null || worldPos == null) {
            return null;
        }
        ClientWorldHudProjectionSupport.ProjectedHudPoint projected =
            ClientWorldHudProjectionSupport.projectToHud(minecraft, worldProjectionMatrix, worldPos, 0.0D);
        if (projected == null || !projected.insideViewport()) {
            return null;
        }
        return new ScreenProjection(projected.screenX(), projected.screenY(), true);
    }

    private static double resolveCrosshairDistance(GuiGraphics gui, ScreenProjection projection) {
        return resolveCrosshairDistance(gui.guiWidth(), gui.guiHeight(), projection);
    }

    private static double resolveCrosshairDistance(int width, int height, ScreenProjection projection) {
        float crosshairX = width * 0.5F;
        float crosshairY = height * 0.5F;
        double dx = projection.screenX() - crosshairX;
        double dy = projection.screenY() - crosshairY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static float resolveCrosshairWeightedIconAlpha(GuiGraphics gui, ScreenProjection projection) {
        if (ClientDeathFlowState.hasActiveCameraOverride()) {
            return WORLD_ICON_ALPHA_FAR_CROSSHAIR;
        }
        float distance = (float) resolveCrosshairDistance(gui, projection);
        float normalizedDistance = Mth.clamp(distance / WORLD_ICON_CROSSHAIR_ALPHA_RADIUS_PIXELS, 0.0F, 1.0F);
        float proximity = 1.0F - normalizedDistance;
        float factor = (float) Math.pow(proximity, WORLD_ICON_CROSSHAIR_ALPHA_CURVE);
        return Mth.lerp(factor, WORLD_ICON_ALPHA_FAR_CROSSHAIR, WORLD_ICON_ALPHA_NEAR_CROSSHAIR);
    }

    private static float resolveCrosshairWeightedTriangleOpacity(GuiGraphics gui, ScreenProjection projection) {
        if (ClientDeathFlowState.hasActiveCameraOverride()) {
            return MARKER_TRIANGLE_OPACITY_FAR_CROSSHAIR;
        }
        float distance = (float) resolveCrosshairDistance(gui, projection);
        float normalizedDistance = Mth.clamp(distance / MARKER_TRIANGLE_OPACITY_RADIUS_PIXELS, 0.0F, 1.0F);
        float factor = (float) Math.pow(normalizedDistance, MARKER_TRIANGLE_OPACITY_CURVE);
        return Mth.lerp(factor, MARKER_TRIANGLE_OPACITY_NEAR_CROSSHAIR, MARKER_TRIANGLE_OPACITY_FAR_CROSSHAIR);
    }

    private static float resolveEntryAnimationProgress(float ageTicks) {
        return Mth.clamp(ageTicks / ICON_ENTRY_DURATION_TICKS, 0.0F, 1.0F);
    }

    private static float resolveEntryScale(float progress) {
        float x = Mth.clamp(progress, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - x, 3.0D);
    }

    private static float resolveEntryYOffset(float progress) {
        float eased = resolveEntryScale(progress);
        float inverse = 1.0F - eased;
        return inverse * inverse * 10.0F;
    }

    private static float resolveShapeOpacity(MarkerKind kind) {
        return kind == MarkerKind.AUTO_ENEMY ? AUTO_ENEMY_SHAPE_OPACITY : MARKER_SHAPE_OPACITY;
    }

    private static Vec3 resolveAutoMarkTriggerAnchor(Player targetPlayer, float partialTick) {
        if (targetPlayer == null) {
            return Vec3.ZERO;
        }
        Vec3 pos = targetPlayer.getPosition(partialTick);
        return pos.add(0.0D, targetPlayer.getBbHeight() * 0.58D, 0.0D);
    }

    private static void clearAutoAimState() {
        autoAimTargetId = null;
        autoAimStartTick = -1L;
        autoAimTriggeredTargetId = null;
    }

    private static ClientMarker resolveActiveAutoMarker(UUID ownerId, UUID targetId, long gameTick) {
        if (ownerId == null || targetId == null) {
            return null;
        }
        for (ClientMarker marker : markers) {
            if (marker == null || marker.kind() != MarkerKind.AUTO_ENEMY) {
                continue;
            }
            if (!ownerId.equals(marker.ownerId()) || !targetId.equals(marker.targetPlayerId())) {
                continue;
            }
            if (marker.remainingTicksAt(gameTick) > 0) {
                return marker;
            }
        }
        return null;
    }

    private static List<ClientMarker> collectRenderableMarkers(Minecraft minecraft, long gameTick) {
        boolean deployOpen = ClientDeathFlowState.isDeployOpenPhase();
        List<ClientMarker> renderMarkers = new ArrayList<>();
        for (ClientMarker marker : markers) {
            if (marker == null) {
                continue;
            }
            if (deployOpen && marker.kind().isFriendlyPersistentOrDownedMarker()) {
                continue;
            }
            renderMarkers.add(marker);
        }
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return renderMarkers;
        }
        
        java.util.Set<UUID> currentFrameEntities = new java.util.HashSet<>();

        // Render ammo boxes
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == null || entity == minecraft.player) continue;
            if (entity instanceof AmmoBoxEntity ammoBox) {
                if (!ammoBox.isAlive() || ammoBox.distanceToSqr(minecraft.player) > 30.0D * 30.0D) {
                    continue;
                }
                currentFrameEntities.add(ammoBox.getUUID());
                renderMarkers.add(new ClientMarker(
                    ammoBox.getUUID(),
                    "弹药箱",
                    MarkerKind.AMMO_BOX,
                    ammoBox.getX(),
                    ammoBox.getY(),
                    ammoBox.getZ(),
                    null,
                    Integer.MAX_VALUE,
                    gameTick,
                    dynamicEntityFirstSeen.computeIfAbsent(ammoBox.getUUID(), k -> gameTick)
                ));
                continue;
            }
            
            // Check distance (30 blocks)
            if (entity.distanceToSqr(minecraft.player) > 30.0 * 30.0) continue;
            
            // Check if ammo box
            String entityType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
            if ("superbwarfare:ammo_box".equals(entityType) || "tacz:ammo_box".equals(entityType)) {
                currentFrameEntities.add(entity.getUUID());
                renderMarkers.add(new ClientMarker(
                    entity.getUUID(),
                    "弹药箱",
                    MarkerKind.AMMO_BOX,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    null, // Not tracked on a player
                    Integer.MAX_VALUE,
                    gameTick,
                    dynamicEntityFirstSeen.computeIfAbsent(entity.getUUID(), k -> gameTick)
                ));
            }
        }

        for (Player targetPlayer : minecraft.level.players()) {
            if (targetPlayer == null || targetPlayer == minecraft.player || targetPlayer.isSpectator() || !targetPlayer.isAlive()) {
                continue;
            }
            if (!ClientRoomTeamMirror.isTeammate(targetPlayer)) {
                continue;
            }
            boolean squadMate = ClientRoomTeamMirror.isSquadMate(targetPlayer);
            boolean downed = ClientPlayerAnimatorDebugState.isPreviewActive(targetPlayer.getUUID());
            if (deployOpen) {
                continue;
            }
            if (downed && !canSeeDownedFriendlyMarker(squadMate)) {
                continue;
            }
            MarkerKind kind = downed
                ? (squadMate ? MarkerKind.DOWNED_SQUAD : MarkerKind.DOWNED_ALLY)
                : (squadMate ? MarkerKind.SQUAD_PERSISTENT : MarkerKind.ALLY_PERSISTENT);
            
            currentFrameEntities.add(targetPlayer.getUUID());
            renderMarkers.add(new ClientMarker(
                targetPlayer.getUUID(),
                targetPlayer.getScoreboardName(),
                kind,
                targetPlayer.getX(),
                targetPlayer.getY(),
                targetPlayer.getZ(),
                targetPlayer.getUUID(),
                Integer.MAX_VALUE,
                gameTick,
                dynamicEntityFirstSeen.computeIfAbsent(targetPlayer.getUUID(), k -> gameTick)
            ));
        }
        
        dynamicEntityFirstSeen.keySet().retainAll(currentFrameEntities);
        
        return renderMarkers;
    }

    private static String resolveMarkerLabelSuffix(MarkerKind kind) {
        return switch (kind) {
            case ENEMY, DIRECT_ENEMY, AUTO_ENEMY -> " 敌军标记";
            case ALLY_PERSISTENT -> " 友军";
            case SQUAD_PERSISTENT -> " 小队队友";
            case DOWNED_ALLY -> " 倒地友军";
            case DOWNED_SQUAD -> " 倒地小队队友";
            default -> " 标记";
        };
    }

    private static int resolveMarkerLabelColor(MarkerKind kind) {
        return switch (kind) {
            case ENEMY, DIRECT_ENEMY, AUTO_ENEMY -> 0x00A61E1E;
            case ALLY_PERSISTENT, DOWNED_ALLY -> 0x002ADFFF;
            case SQUAD_PERSISTENT, DOWNED_SQUAD -> 0x0061FF94;
            default -> 0x00E7C75A;
        };
    }

    private static float resolveHelpPulseProgress(ClientMarker marker) {
        if (marker == null || !marker.kind().isDownedMarker()) {
            return -1.0F;
        }
        UUID pulsePlayerId = marker.targetPlayerId() != null ? marker.targetPlayerId() : marker.ownerId();
        Long startedAt = pulsePlayerId == null ? null : HELP_PULSE_STARTED_AT_MS.get(pulsePlayerId);
        if (startedAt == null) {
            return -1.0F;
        }
        float progress = (nowMs() - startedAt) / (float) HELP_PULSE_DURATION_MS;
        return progress >= 1.0F ? -1.0F : Mth.clamp(progress, 0.0F, 1.0F);
    }

    private static boolean isInRunningRoom() {
        String roomId = ClientHudDebugState.getRoomId();
        String roomState = ClientHudDebugState.getRoomState();
        if (roomId == null || roomId.isBlank() || "-".equals(roomId)) {
            return false;
        }
        if (roomState == null || roomState.isBlank() || roomState.contains("未加入")) {
            return false;
        }
        if (roomState.contains("匹配")) {
            return false;
        }
        return roomState.contains("进行") || roomState.contains("部署") || roomState.contains("结算");
    }

    private static long nowMs() {
        return System.currentTimeMillis();
    }

    private static boolean canSeeDownedFriendlyMarker(boolean squadMate) {
        return squadMate || isViewerAssault();
    }

    private static boolean shouldKeepMarkerPinnedToScreenEdge(ClientMarker marker, boolean isAmmoBoxEmpty) {
        if (marker == null || marker.kind() == null) {
            return false;
        }
        MarkerKind kind = marker.kind();
        if (kind == MarkerKind.AMMO_BOX) {
            return isAmmoBoxEmpty;
        }
        if (kind == MarkerKind.DOWNED_SQUAD || kind == MarkerKind.SQUAD_PERSISTENT) {
            return true;
        }
        if (kind == MarkerKind.DOWNED_ALLY || kind == MarkerKind.ALLY_PERSISTENT) {
            return isViewerAssault();
        }
        return false;
    }

    private static double resolveMarkerEdgePadding(MarkerKind kind) {
        if (kind == null) {
            return 8.0D;
        }
        return switch (kind) {
            case DOWNED_ALLY, DOWNED_SQUAD -> 9.0D;
            case AMMO_BOX -> 10.0D;
            default -> 8.0D;
        };
    }

    private static boolean isViewerAssault() {
        return "assault".equalsIgnoreCase(ClientHudDebugState.getSelfClass());
    }

    private record ScreenProjection(double screenX, double screenY, boolean insideViewport) {
    }

    private record HoveredMarker(ClientMarker marker, ScreenProjection projection, Vec3 anchor, double crosshairDistance) {
    }

    private record MarkerPlacement(MarkerKind kind, net.minecraft.core.BlockPos blockPos, UUID targetPlayerId) {
    }

    private record DirectEnemyHit(Player targetPlayer, double distanceSqr) {
    }

    private record ClientMarker(
        UUID ownerId,
        String ownerName,
        MarkerKind kind,
        double x,
        double y,
        double z,
        UUID targetPlayerId,
        int baseRemainingTicks,
        long syncTick,
        long firstSeenTick
    ) {
        private static String renderKeyOf(UUID ownerId, MarkerKind kind, double x, double y, double z, UUID targetPlayerId) {
            boolean trackedEntityMarker = kind != null && kind.isTrackedPlayerMarker();
            return (ownerId == null ? "0" : ownerId.toString())
                + "|" + (kind == null ? MarkerKind.NORMAL.name() : kind.name())
                + "|" + (trackedEntityMarker ? "entity" : ((int) x + "," + (int) y + "," + (int) z))
                + "|" + (targetPlayerId == null ? "-" : targetPlayerId.toString());
        }

        private String renderKey() {
            return renderKeyOf(ownerId, kind, x, y, z, targetPlayerId);
        }

        private Vec3 worldAnchor(Minecraft minecraft, float partialTick) {
            if (kind != null && kind.isTrackedPlayerMarker() && targetPlayerId != null && minecraft != null && minecraft.level != null) {
                Player targetPlayer = resolveTrackedPlayer(minecraft);
                if (targetPlayer != null) {
                    Vec3 pos = targetPlayer.getPosition(partialTick);
                    double heightOffset = kind.isDownedMarker() ? Math.max(0.35D, targetPlayer.getBbHeight() * 0.55D) : targetPlayer.getBbHeight() + 0.55D;
                    return pos.add(0.0D, heightOffset, 0.0D);
                }
            }
            if (kind == MarkerKind.AMMO_BOX) {
                return new Vec3(x, y + 0.5D, z);
            }
            return new Vec3((int) x + 0.5D, (int) y + ICON_WORLD_Y_OFFSET, (int) z + 0.5D);
        }

        private Player resolveTrackedPlayer(Minecraft minecraft) {
            if (minecraft == null || minecraft.level == null || targetPlayerId == null) {
                return null;
            }
            for (Player player : minecraft.level.players()) {
                if (player != null && targetPlayerId.equals(player.getUUID())) {
                    return player;
                }
            }
            return null;
        }

        private int ageTicksAt(long gameTick) {
            return (int) Math.max(0L, gameTick - firstSeenTick);
        }

        private int remainingTicksAt(long gameTick) {
            return baseRemainingTicks - (int) Math.max(0L, gameTick - syncTick);
        }
    }

    public enum MarkerKind {
        NORMAL,
        ENEMY,
        DIRECT_ENEMY,
        AUTO_ENEMY,
        ALLY_PERSISTENT,
        SQUAD_PERSISTENT,
        DOWNED_ALLY,
        DOWNED_SQUAD,
        AMMO_BOX;

        private static MarkerKind from(String value) {
            if (value == null || value.isBlank()) {
                return NORMAL;
            }
            try {
                return MarkerKind.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NORMAL;
            }
        }

        private boolean isEnemy() {
            return this == ENEMY || this == DIRECT_ENEMY || this == AUTO_ENEMY;
        }

        private boolean usesTriangleShape() {
            return this == DIRECT_ENEMY || this == AUTO_ENEMY;
        }

        private boolean isTrackedPlayerMarker() {
            return this == DIRECT_ENEMY
                || this == AUTO_ENEMY
                || this == ALLY_PERSISTENT
                || this == SQUAD_PERSISTENT
                || this == DOWNED_ALLY
                || this == DOWNED_SQUAD;
        }

        private boolean isDownedMarker() {
            return this == DOWNED_ALLY || this == DOWNED_SQUAD;
        }

        private boolean isFriendlyPersistentOrDownedMarker() {
            return this == ALLY_PERSISTENT
                || this == SQUAD_PERSISTENT
                || this == DOWNED_ALLY
                || this == DOWNED_SQUAD;
        }

        private TacticalMarkerRenderer.MarkerVisualType toVisualType() {
            return switch (this) {
                case NORMAL -> TacticalMarkerRenderer.MarkerVisualType.NORMAL;
                case ENEMY -> TacticalMarkerRenderer.MarkerVisualType.ENEMY_GROUND;
                case DIRECT_ENEMY -> TacticalMarkerRenderer.MarkerVisualType.ENEMY_TRACKED;
                case AUTO_ENEMY -> TacticalMarkerRenderer.MarkerVisualType.AUTO_ENEMY;
                case ALLY_PERSISTENT -> TacticalMarkerRenderer.MarkerVisualType.ALLY_PERSISTENT;
                case SQUAD_PERSISTENT -> TacticalMarkerRenderer.MarkerVisualType.SQUAD_PERSISTENT;
                case DOWNED_ALLY -> TacticalMarkerRenderer.MarkerVisualType.DOWNED_ALLY;
                case DOWNED_SQUAD -> TacticalMarkerRenderer.MarkerVisualType.DOWNED_SQUAD;
                case AMMO_BOX -> TacticalMarkerRenderer.MarkerVisualType.AMMO_BOX;
            };
        }
    }

    private enum TeamToken {
        CAMP_A,
        CAMP_B,
        UNKNOWN;

        private static TeamToken fromClientDebug(String selfTeam) {
            if (selfTeam == null || selfTeam.isBlank()) {
                return UNKNOWN;
            }
            String normalized = selfTeam.toLowerCase(Locale.ROOT);
            if (normalized.contains("campa") || normalized.contains("阵营a")) {
                return CAMP_A;
            }
            if (normalized.contains("campb") || normalized.contains("阵营b")) {
                return CAMP_B;
            }
            return UNKNOWN;
        }

        private static TeamToken fromScoreboardTeam(Team team) {
            if (team == null) {
                return UNKNOWN;
            }
            String name = team.getName() == null ? "" : team.getName().toLowerCase(Locale.ROOT);
            if (name.contains("camp_a") || name.contains("campa")) {
                return CAMP_A;
            }
            if (name.contains("camp_b") || name.contains("campb")) {
                return CAMP_B;
            }
            return UNKNOWN;
        }
    }
}
