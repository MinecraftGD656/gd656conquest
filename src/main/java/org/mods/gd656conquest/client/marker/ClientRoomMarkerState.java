package org.mods.gd656conquest.client.marker;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
import org.mods.gd656conquest.client.compat.lrtactical.LrThrowableOwnerSyncAccess;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.common.entity.AmmoBoxEntity;
import org.mods.gd656conquest.common.entity.MedicalBoxEntity;
import org.mods.gd656conquest.common.entity.SquadDeployBeaconEntity;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientRoomTeamMirror;
import org.mods.gd656conquest.client.render.ClientCrosshairFadeSupport;
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
    private static final long NEED_AMMO_PULSE_INTERVAL_MS = 1200L;
    private static final float WORLD_ICON_ALPHA_NEAR_CROSSHAIR = 0.05F;
    private static final float WORLD_ICON_ALPHA_FAR_CROSSHAIR = 0.40F;
    private static final float WORLD_ICON_CROSSHAIR_ALPHA_RADIUS_PIXELS = 300.0F;
    private static final float WORLD_ICON_CROSSHAIR_ALPHA_CURVE = 1.20F;
    private static final float MARKER_TRIANGLE_OPACITY_NEAR_CROSSHAIR = 0.30F;
    private static final float MARKER_TRIANGLE_OPACITY_FAR_CROSSHAIR = 0.80F;
    private static final float MARKER_TRIANGLE_OPACITY_RADIUS_PIXELS = 140.0F;
    private static final float MARKER_TRIANGLE_OPACITY_CURVE = 1.15F;
    private static final float MARKER_SHAPE_OPACITY = 0.90F;
    private static final float AUTO_ENEMY_SHAPE_OPACITY = 0.60F;
    private static final float FRIENDLY_PERSISTENT_DISTANCE_SCALE_NEAR = 1.10F;
    private static final float FRIENDLY_PERSISTENT_DISTANCE_SCALE_FAR = 0.72F;
    private static final float FRIENDLY_PERSISTENT_DISTANCE_SCALE_NEAR_DISTANCE = 3.0F;
    private static final float FRIENDLY_PERSISTENT_DISTANCE_SCALE_FAR_DISTANCE = 30.0F;
    private static final float PROXIMITY_ICON_SCALE_NEAR = 1.65F;
    private static final float PROXIMITY_ICON_SCALE_FAR = 0.92F;
    private static final float PROXIMITY_ICON_SCALE_NEAR_DISTANCE = 0.75F;
    private static final float PROXIMITY_ICON_SCALE_FAR_DISTANCE = 7.0F;
    private static final float GRENADE_ICON_SCALE_FAR_DISTANCE = 15.0F;
    private static final float BEACON_ICON_SCALE_FAR_DISTANCE = 10.0F;
    private static final double SUPPORT_BOX_MARKER_DISTANCE_SQR = 30.0D * 30.0D;
    private static final double PROXIMITY_THREAT_DISTANCE_SQR = 7.0D * 7.0D;
    private static final double GRENADE_MARKER_DISTANCE_SQR = 15.0D * 15.0D;
    private static final double BEACON_FRIENDLY_MARKER_DISTANCE_SQR = 10.0D * 10.0D;
    private static final double BEACON_HOSTILE_MARKER_DISTANCE_SQR = 2.0D * 2.0D;
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

    private static boolean isPlayerInDroneView(Minecraft minecraft) {
        if (minecraft.player == null) return false;
        net.minecraft.world.item.ItemStack mainHand = minecraft.player.getMainHandItem();
        if (mainHand.getItem().getClass().getName().equals("com.atsuishio.superbwarfare.item.misc.MonitorItem") && mainHand.getOrCreateTag().getBoolean("Using")) return true;
        net.minecraft.world.item.ItemStack offHand = minecraft.player.getOffhandItem();
        if (offHand.getItem().getClass().getName().equals("com.atsuishio.superbwarfare.item.misc.MonitorItem") && offHand.getOrCreateTag().getBoolean("Using")) return true;
        return false;
    }

    private static boolean aoeDroneSpot(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        Vec3 start = resolveMarkerRayStart(minecraft);
        if (start == null) {
            return false;
        }
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        boolean spotted = false;

        for (Player targetPlayer : minecraft.level.players()) {
            if (targetPlayer == minecraft.player || !targetPlayer.isAlive() || targetPlayer.isSpectator()) {
                continue;
            }
            if (org.mods.gd656conquest.client.room.ClientRoomTeamMirror.isTeammate(targetPlayer)) {
                continue;
            }
            Vec3 anchor = resolveAutoMarkTriggerAnchor(targetPlayer, minecraft.getFrameTime());
            ScreenProjection projection = projectToScreen(minecraft, anchor);
            if (projection != null && projection.insideViewport()) {
                Vec3 toTarget = targetPlayer.getBoundingBox().getCenter().subtract(start);
                double distanceSqr = toTarget.lengthSqr();
                if (distanceSqr <= 1.0E-6D || distanceSqr > MAX_MARKER_DISTANCE_SQR) {
                    continue;
                }
                if (!hasClearSightToPlayer(minecraft, start, targetPlayer)) {
                    continue;
                }
                double distance = resolveCrosshairDistance(guiWidth, guiHeight, projection);
                if (distance <= 75.0D) {
                    NetworkManager.getChannel().sendToServer(
                        RoomMarkerActionPacket.place(targetPlayer.blockPosition(), MarkerKind.DIRECT_ENEMY.name(), targetPlayer.getUUID())
                    );
                    spotted = true;
                }
            }
        }
        return spotted;
    }

    public static void handleKeyPress(Minecraft minecraft) {
        if (!canPlaceMarkerNow(minecraft)) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        boolean enemyGroundMode = nowMs - lastKeyPressAtMs <= DOUBLE_CLICK_WINDOW_MS;
        lastKeyPressAtMs = nowMs;
        
        if (isPlayerInDroneView(minecraft)) {
            if (aoeDroneSpot(minecraft)) {
                return;
            }
        }

        MarkerPlacement placement = resolveMarkerPlacement(minecraft, enemyGroundMode);
        if (placement == null) {
            return;
        }
        NetworkManager.getChannel().sendToServer(RoomMarkerActionPacket.place(placement.blockPos(), placement.kind().name(), placement.targetPlayerId()));
    }

    private static Vec3 resolveMarkerRayStart(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        if (isPlayerInDroneView(minecraft) && minecraft.gameRenderer != null) {
            Camera camera = minecraft.gameRenderer.getMainCamera();
            if (camera != null && camera.isInitialized() && camera.getPosition() != null) {
                return camera.getPosition();
            }
        }
        return minecraft.player.getEyePosition(minecraft.getFrameTime());
    }

    private static Vec3 resolveMarkerRayLook(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        if (isPlayerInDroneView(minecraft) && minecraft.gameRenderer != null) {
            Camera camera = minecraft.gameRenderer.getMainCamera();
            if (camera != null && camera.isInitialized()) {
                return Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).normalize();
            }
        }
        return minecraft.player.getViewVector(minecraft.getFrameTime()).normalize();
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
            boolean supportBoxAlert = shouldAlertSupportBox(marker.kind(), minecraft);
            Vec3 anchor = marker.worldAnchor(minecraft, minecraft.getFrameTime());
            ScreenProjection projection = projectToScreen(minecraft, marker, anchor, supportBoxAlert);
            if (projection == null) {
                continue;
            }
            double crosshairDistance = resolveCrosshairDistance(gui, projection);
            boolean isHovered = crosshairDistance <= CROSSHAIR_HOVER_RADIUS_PIXELS;
            if (projection.insideViewport() && isHovered && (hovered == null || crosshairDistance < hovered.crosshairDistance())) {
                hovered = new HoveredMarker(marker, projection, anchor, crosshairDistance);
            }
            float lifetimeAlpha = Mth.clamp(Math.min(remainingTicks, 20) / 20.0F, 0.0F, 1.0F);
            double actualDistance = minecraft.player == null ? 0.0D : minecraft.player.position().distanceTo(anchor);
            float weightedAlpha = resolveMarkerRenderAlpha(gui, marker.kind(), projection) * lifetimeAlpha;
            if (weightedAlpha <= 0.01F) {
                continue;
            }
            float markerAge = marker.ageTicksAt(gameTime) + minecraft.getFrameTime();
            float pulse = 0.5F + 0.5F * Mth.sin(markerAge * 0.16F);
            float entryProgress = resolvePresenceAnimationProgress(markerAge, remainingTicks);
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
                resolveEntryScale(entryProgress) * resolveMarkerDistanceScale(marker.kind(), actualDistance),
                resolvePulseProgress(marker),
                targetClass,
                supportBoxAlert
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
        float weightedAlpha = resolveMarkerRenderAlpha(gui, marker.kind(), hovered.projection());
        if (weightedAlpha <= 0.01F) {
            return;
        }
        double distance = minecraft.player == null ? 0.0D : minecraft.player.position().distanceTo(hovered.anchor());
        String line1 = resolveHoveredLabelLine1(marker);
        String line2 = String.format(Locale.ROOT, "%.0fm", distance);
        int primaryRgb = resolveMarkerLabelColor(marker.kind());
        int secondaryRgb = marker.kind().isEnemy() ? 0x00F1C1C1 : 0x00E6EEF8;
        float centerX = (float) hovered.projection().screenX();
        float topY = (float) hovered.projection().screenY() - 30.0F;
        float alpha = 0.20F;
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, line1, centerX, topY, primaryRgb, alpha, true, 0.68F);
        CaptureDiamondRenderer.drawCenteredHudText(gui, minecraft.font, line2, centerX, topY + 10.5F, secondaryRgb, alpha, false, 0.58F);
    }

    private static String resolveHoveredLabelLine1(ClientMarker marker) {
        if (marker == null || marker.kind() == null) {
            return "";
        }
        return switch (marker.kind()) {
            case AMMO_BOX, MEDICAL_BOX, AMMO_BAG, MEDICAL_BAG, GRENADE_SELF, GRENADE_FRIENDLY, GRENADE_HOSTILE, CLAYMORE_FRIENDLY, BEACON_FRIENDLY, BEACON_HOSTILE ->
                marker.ownerName();
            default -> marker.ownerName() + resolveMarkerLabelSuffix(marker.kind());
        };
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
            boolean supportBoxAlert = shouldAlertSupportBox(marker.kind(), minecraft);
            Vec3 anchor = marker.worldAnchor(minecraft, minecraft.getFrameTime());
            ScreenProjection projection = projectToScreen(minecraft, marker, anchor, supportBoxAlert);
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
        Vec3 start = resolveMarkerRayStart(minecraft);
        Vec3 look = resolveMarkerRayLook(minecraft);
        if (start == null || look == null) {
            return null;
        }
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

    private static ScreenProjection projectToScreen(Minecraft minecraft, ClientMarker marker, Vec3 worldPos, boolean supportBoxAlert) {
        if (minecraft.player == null || worldProjectionMatrix == null || worldPos == null || marker == null) {
            return null;
        }
        double edgePadding = resolveMarkerEdgePadding(marker.kind());
        ClientWorldHudProjectionSupport.ProjectedHudPoint projected =
            ClientWorldHudProjectionSupport.projectToHud(minecraft, worldProjectionMatrix, worldPos, edgePadding);
        if (projected == null) {
            return null;
        }
        if (!projected.insideViewport() && !shouldKeepMarkerPinnedToScreenEdge(marker, supportBoxAlert)) {
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
        return ClientCrosshairFadeSupport.resolveWeightedAlpha(
            gui.guiWidth(),
            gui.guiHeight(),
            projection.screenX(),
            projection.screenY(),
            WORLD_ICON_ALPHA_NEAR_CROSSHAIR,
            WORLD_ICON_ALPHA_FAR_CROSSHAIR,
            WORLD_ICON_CROSSHAIR_ALPHA_RADIUS_PIXELS,
            WORLD_ICON_CROSSHAIR_ALPHA_CURVE
        );
    }

    private static float resolveMarkerRenderAlpha(GuiGraphics gui, MarkerKind kind, ScreenProjection projection) {
        if (kind == MarkerKind.ALLY_PERSISTENT || kind == MarkerKind.SQUAD_PERSISTENT) {
            return resolveCrosshairWeightedIconAlpha(gui, projection);
        }
        return 1.0F;
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

    private static float resolvePresenceAnimationProgress(float ageTicks, int remainingTicks) {
        float enterProgress = resolveEntryAnimationProgress(ageTicks);
        float exitProgress = remainingTicks == Integer.MAX_VALUE ? 1.0F : resolveEntryAnimationProgress(Math.max(0, remainingTicks));
        return Math.min(enterProgress, exitProgress);
    }

    private static float resolveEntryScale(float progress) {
        float x = Mth.clamp(progress, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - x, 3.0D);
    }

    private static float resolveMarkerDistanceScale(MarkerKind kind, double actualDistance) {
        if (kind == MarkerKind.ALLY_PERSISTENT || kind == MarkerKind.SQUAD_PERSISTENT) {
            float clampedDistance = Mth.clamp((float) actualDistance, FRIENDLY_PERSISTENT_DISTANCE_SCALE_NEAR_DISTANCE, FRIENDLY_PERSISTENT_DISTANCE_SCALE_FAR_DISTANCE);
            float t = (clampedDistance - FRIENDLY_PERSISTENT_DISTANCE_SCALE_NEAR_DISTANCE)
                / (FRIENDLY_PERSISTENT_DISTANCE_SCALE_FAR_DISTANCE - FRIENDLY_PERSISTENT_DISTANCE_SCALE_NEAR_DISTANCE);
            return Mth.lerp(t, FRIENDLY_PERSISTENT_DISTANCE_SCALE_NEAR, FRIENDLY_PERSISTENT_DISTANCE_SCALE_FAR);
        }
        if (kind == MarkerKind.GRENADE_SELF || kind == MarkerKind.GRENADE_FRIENDLY || kind == MarkerKind.GRENADE_HOSTILE) {
            float clampedDistance = Mth.clamp((float) actualDistance, PROXIMITY_ICON_SCALE_NEAR_DISTANCE, GRENADE_ICON_SCALE_FAR_DISTANCE);
            float t = (clampedDistance - PROXIMITY_ICON_SCALE_NEAR_DISTANCE)
                / (GRENADE_ICON_SCALE_FAR_DISTANCE - PROXIMITY_ICON_SCALE_NEAR_DISTANCE);
            return Mth.lerp(t, PROXIMITY_ICON_SCALE_NEAR, PROXIMITY_ICON_SCALE_FAR);
        }
        if (kind == MarkerKind.BEACON_FRIENDLY || kind == MarkerKind.BEACON_HOSTILE) {
            float clampedDistance = Mth.clamp((float) actualDistance, PROXIMITY_ICON_SCALE_NEAR_DISTANCE, BEACON_ICON_SCALE_FAR_DISTANCE);
            float t = (clampedDistance - PROXIMITY_ICON_SCALE_NEAR_DISTANCE)
                / (BEACON_ICON_SCALE_FAR_DISTANCE - PROXIMITY_ICON_SCALE_NEAR_DISTANCE);
            return Mth.lerp(t, PROXIMITY_ICON_SCALE_NEAR, PROXIMITY_ICON_SCALE_FAR);
        }
        if (kind == MarkerKind.CLAYMORE_FRIENDLY) {
            float clampedDistance = Mth.clamp((float) actualDistance, PROXIMITY_ICON_SCALE_NEAR_DISTANCE, PROXIMITY_ICON_SCALE_FAR_DISTANCE);
            float t = (clampedDistance - PROXIMITY_ICON_SCALE_NEAR_DISTANCE)
                / (PROXIMITY_ICON_SCALE_FAR_DISTANCE - PROXIMITY_ICON_SCALE_NEAR_DISTANCE);
            return Mth.lerp(t, PROXIMITY_ICON_SCALE_NEAR, PROXIMITY_ICON_SCALE_FAR);
        }
        return 1.0F;
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

        // Render support boxes and nearby threat/supply markers
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == null || entity == minecraft.player) continue;
            if (entity instanceof AmmoBoxEntity ammoBox) {
                if (!ammoBox.isAlive() || ammoBox.distanceToSqr(minecraft.player) > SUPPORT_BOX_MARKER_DISTANCE_SQR) {
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
            if (entity instanceof MedicalBoxEntity medicalBox) {
                if (!medicalBox.isAlive() || medicalBox.distanceToSqr(minecraft.player) > SUPPORT_BOX_MARKER_DISTANCE_SQR) {
                    continue;
                }
                currentFrameEntities.add(medicalBox.getUUID());
                renderMarkers.add(new ClientMarker(
                    medicalBox.getUUID(),
                    "医疗箱",
                    MarkerKind.MEDICAL_BOX,
                    medicalBox.getX(),
                    medicalBox.getY(),
                    medicalBox.getZ(),
                    null,
                    Integer.MAX_VALUE,
                    gameTick,
                    dynamicEntityFirstSeen.computeIfAbsent(medicalBox.getUUID(), k -> gameTick)
                ));
                continue;
            }
            if (entity instanceof SquadDeployBeaconEntity beacon) {
                if (!beacon.isAlive()) {
                    continue;
                }
                MarkerKind beaconKind = resolveBeaconMarkerKind(minecraft, beacon);
                if (beaconKind == null) {
                    continue;
                }
                double beaconDistanceSqr = beaconKind == MarkerKind.BEACON_HOSTILE
                    ? BEACON_HOSTILE_MARKER_DISTANCE_SQR
                    : BEACON_FRIENDLY_MARKER_DISTANCE_SQR;
                if (beacon.distanceToSqr(minecraft.player) > beaconDistanceSqr) {
                    continue;
                }
                currentFrameEntities.add(beacon.getUUID());
                renderMarkers.add(new ClientMarker(
                    beacon.getUUID(),
                    beaconKind == MarkerKind.BEACON_HOSTILE ? "敌军支援信标" : "友军支援信标",
                    beaconKind,
                    beacon.getX(),
                    beacon.getY(),
                    beacon.getZ(),
                    null,
                    Integer.MAX_VALUE,
                    gameTick,
                    dynamicEntityFirstSeen.computeIfAbsent(beacon.getUUID(), k -> gameTick)
                ));
                continue;
            }

            double distanceSqr = entity.distanceToSqr(minecraft.player);
            net.minecraft.resources.ResourceLocation entityTypeKey = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            String entityType = entityTypeKey == null ? "" : entityTypeKey.toString();
            if (distanceSqr <= SUPPORT_BOX_MARKER_DISTANCE_SQR
                && ("superbwarfare:ammo_box".equals(entityType) || "tacz:ammo_box".equals(entityType))) {
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
                continue;
            }
            if (distanceSqr <= SUPPORT_BOX_MARKER_DISTANCE_SQR
                && ("superbwarfare:medical_box".equals(entityType) || "tacz:medical_box".equals(entityType))) {
                currentFrameEntities.add(entity.getUUID());
                renderMarkers.add(new ClientMarker(
                    entity.getUUID(),
                    "医疗箱",
                    MarkerKind.MEDICAL_BOX,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    null,
                    Integer.MAX_VALUE,
                    gameTick,
                    dynamicEntityFirstSeen.computeIfAbsent(entity.getUUID(), k -> gameTick)
                ));
                continue;
            }
            MarkerKind dynamicKind = resolveDynamicEntityMarkerKind(minecraft, entity, entityType);
            if (dynamicKind == null) {
                continue;
            }
            double markerDistanceSqr = (dynamicKind == MarkerKind.GRENADE_SELF
                || dynamicKind == MarkerKind.GRENADE_FRIENDLY
                || dynamicKind == MarkerKind.GRENADE_HOSTILE)
                ? GRENADE_MARKER_DISTANCE_SQR
                : dynamicKind == MarkerKind.BEACON_FRIENDLY
                    ? BEACON_FRIENDLY_MARKER_DISTANCE_SQR
                    : dynamicKind == MarkerKind.BEACON_HOSTILE
                        ? BEACON_HOSTILE_MARKER_DISTANCE_SQR
                        : PROXIMITY_THREAT_DISTANCE_SQR;
            if (distanceSqr > markerDistanceSqr) {
                continue;
            }
            currentFrameEntities.add(entity.getUUID());
            renderMarkers.add(new ClientMarker(
                entity.getUUID(),
                resolveDynamicEntityMarkerName(dynamicKind),
                dynamicKind,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                null,
                Integer.MAX_VALUE,
                gameTick,
                dynamicEntityFirstSeen.computeIfAbsent(entity.getUUID(), k -> gameTick)
            ));
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

    private static MarkerKind resolveDynamicEntityMarkerKind(Minecraft minecraft, Entity entity, String entityType) {
        if (minecraft == null || minecraft.player == null || entity == null || entityType == null || entityType.isBlank()) {
            return null;
        }
        if ("superbwarfare:claymore".equals(entityType)) {
            Boolean friendly = isFriendlyOwnedEntity(minecraft, entity);
            return Boolean.TRUE.equals(friendly) ? MarkerKind.CLAYMORE_FRIENDLY : null;
        }
        if (!isLrGrenadeEntityType(entityType)) {
            return null;
        }
        Player owner = resolveOwningPlayer(minecraft, entity);
        if (owner == null) {
            // LR 手雷实体自身未额外同步 owner 数据，客户端在部分情况下拿不到投掷者。
            // 这里优先保证手雷图标能渲染出来，无法判定归属时按危险目标处理。
            return MarkerKind.GRENADE_HOSTILE;
        }
        if (owner == minecraft.player) {
            return MarkerKind.GRENADE_SELF;
        }
        return ClientRoomTeamMirror.isTeammate(owner) ? MarkerKind.GRENADE_FRIENDLY : MarkerKind.GRENADE_HOSTILE;
    }

    private static MarkerKind resolveBeaconMarkerKind(Minecraft minecraft, SquadDeployBeaconEntity beacon) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || beacon == null) {
            return null;
        }
        String currentRoomId = ClientHudDebugState.getRoomId();
        if (currentRoomId != null && !currentRoomId.isBlank() && !currentRoomId.equalsIgnoreCase(beacon.getRoomId())) {
            return null;
        }
        TeamToken selfToken = TeamToken.fromClientDebug(ClientHudDebugState.getSelfTeam());
        TeamToken beaconToken = TeamToken.fromBeaconToken(beacon.getTeamToken());
        if (selfToken == TeamToken.UNKNOWN || beaconToken == TeamToken.UNKNOWN) {
            return null;
        }
        Vec3 start = minecraft.player.getEyePosition(minecraft.getFrameTime());
        Vec3 beaconAnchor = new Vec3(beacon.getX(), beacon.getY() + 0.9D, beacon.getZ());
        if (!hasClearSightToPoint(minecraft, start, beaconAnchor)) {
            return null;
        }
        return selfToken == beaconToken ? MarkerKind.BEACON_FRIENDLY : MarkerKind.BEACON_HOSTILE;
    }

    private static boolean isLrGrenadeEntityType(String entityType) {
        return "lrtactical:explode_grenade".equals(entityType)
            || "lrtactical:sticky_grenade_entity".equals(entityType);
    }

    private static String resolveDynamicEntityMarkerName(MarkerKind kind) {
        if (kind == null) {
            return "标记";
        }
        return switch (kind) {
            case GRENADE_SELF -> "来源于自己手雷";
            case GRENADE_FRIENDLY -> "友军手雷";
            case GRENADE_HOSTILE -> "敌军手雷";
            case CLAYMORE_FRIENDLY -> "友军阔剑地雷";
            case BEACON_FRIENDLY -> "友军支援信标";
            case BEACON_HOSTILE -> "敌军支援信标";
            default -> "标记";
        };
    }

    private static Boolean isFriendlyOwnedEntity(Minecraft minecraft, Entity entity) {
        Player owner = resolveOwningPlayer(minecraft, entity);
        if (minecraft == null || minecraft.player == null || owner == null) {
            return null;
        }
        if (owner == minecraft.player) {
            return true;
        }
        return ClientRoomTeamMirror.isTeammate(owner);
    }

    private static Player resolveOwningPlayer(Minecraft minecraft, Entity entity) {
        if (minecraft == null || minecraft.level == null || entity == null) {
            return null;
        }
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof Player ownerPlayer) {
            return ownerPlayer;
        }
        UUID ownerId = null;
        if (entity instanceof OwnableEntity ownableEntity) {
            ownerId = ownableEntity.getOwnerUUID();
        }
        if (ownerId == null) {
            ownerId = resolveOwnerUuidReflectively(entity);
        }
        return ownerId == null ? null : minecraft.level.getPlayerByUUID(ownerId);
    }

    private static UUID resolveOwnerUuidReflectively(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof LrThrowableOwnerSyncAccess syncAccess) {
            UUID syncedOwnerUuid = syncAccess.gd656conquest$getSyncedOwnerUuid();
            if (syncedOwnerUuid != null) {
                return syncedOwnerUuid;
            }
        }
        try {
            java.lang.reflect.Method getOwnerUuid = entity.getClass().getMethod("getOwnerUUID");
            Object ownerUuid = getOwnerUuid.invoke(entity);
            if (ownerUuid instanceof UUID uuid) {
                return uuid;
            }
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method getOwner = entity.getClass().getMethod("getOwner");
            Object owner = getOwner.invoke(entity);
            if (owner instanceof Player player) {
                return player.getUUID();
            }
            if (owner instanceof Entity ownerEntity) {
                return ownerEntity.getUUID();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resolveMarkerLabelSuffix(MarkerKind kind) {
        return switch (kind) {
            case OBSERVE_GROUND -> " 地面标记";
            case REQUEST_STRIKE -> " 请求打击";
            case ENEMY, DIRECT_ENEMY, AUTO_ENEMY -> " 敌军标记";
            case ALLY_PERSISTENT -> " 友军";
            case SQUAD_PERSISTENT -> " 小队队友";
            case DOWNED_ALLY -> " 倒地友军";
            case DOWNED_SQUAD -> " 倒地小队队友";
            case NEED_AMMO_ALLY -> " 需要弹药";
            case NEED_AMMO_SQUAD -> " 小队队友需要弹药";
            case AMMO_BOX -> " 弹药箱";
            case MEDICAL_BOX -> " 医疗箱";
            case AMMO_BAG -> " 弹药包";
            case MEDICAL_BAG -> " 医疗包";
            case GRENADE_SELF -> " 来源于自己手雷";
            case GRENADE_FRIENDLY -> " 友军手雷";
            case GRENADE_HOSTILE -> " 敌军手雷";
            case CLAYMORE_FRIENDLY -> " 友军阔剑地雷";
            case BEACON_FRIENDLY -> " 友军支援信标";
            case BEACON_HOSTILE -> " 敌军支援信标";
            default -> " 标记";
        };
    }

    private static int resolveMarkerLabelColor(MarkerKind kind) {
        return switch (kind) {
            case OBSERVE_GROUND, REQUEST_STRIKE -> 0x00F28A1A;
            case ENEMY, DIRECT_ENEMY, AUTO_ENEMY, GRENADE_SELF, GRENADE_HOSTILE, BEACON_HOSTILE -> 0x00A61E1E;
            case ALLY_PERSISTENT, DOWNED_ALLY, NEED_AMMO_ALLY, AMMO_BOX, MEDICAL_BOX, AMMO_BAG, MEDICAL_BAG, BEACON_FRIENDLY -> 0x002ADFFF;
            case SQUAD_PERSISTENT, DOWNED_SQUAD, NEED_AMMO_SQUAD, GRENADE_FRIENDLY, CLAYMORE_FRIENDLY -> 0x0061FF94;
            default -> 0x00E7C75A;
        };
    }

    private static float resolvePulseProgress(ClientMarker marker) {
        if (marker == null || marker.kind() == null) {
            return -1.0F;
        }
        if (marker.kind() == MarkerKind.NEED_AMMO_ALLY || marker.kind() == MarkerKind.NEED_AMMO_SQUAD) {
            long phaseMs = nowMs() % NEED_AMMO_PULSE_INTERVAL_MS;
            if (phaseMs >= HELP_PULSE_DURATION_MS) {
                return -1.0F;
            }
            return Mth.clamp(phaseMs / (float) HELP_PULSE_DURATION_MS, 0.0F, 1.0F);
        }
        if (!marker.kind().isDownedMarker()
            && marker.kind() != MarkerKind.ALLY_PERSISTENT
            && marker.kind() != MarkerKind.SQUAD_PERSISTENT) {
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

    private static boolean shouldKeepMarkerPinnedToScreenEdge(ClientMarker marker, boolean supportBoxAlert) {
        if (marker == null || marker.kind() == null) {
            return false;
        }
        MarkerKind kind = marker.kind();
        if (supportBoxAlert) {
            return true;
        }
        if (kind == MarkerKind.DOWNED_SQUAD || kind == MarkerKind.SQUAD_PERSISTENT || kind == MarkerKind.NEED_AMMO_SQUAD) {
            return true;
        }
        if (kind == MarkerKind.NEED_AMMO_ALLY) {
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
            case DOWNED_ALLY, DOWNED_SQUAD, NEED_AMMO_ALLY, NEED_AMMO_SQUAD -> 9.0D;
            case AMMO_BOX, MEDICAL_BOX, AMMO_BAG, MEDICAL_BAG, GRENADE_SELF, GRENADE_FRIENDLY, GRENADE_HOSTILE, CLAYMORE_FRIENDLY, BEACON_FRIENDLY, BEACON_HOSTILE -> 10.0D;
            default -> 8.0D;
        };
    }

    private static boolean shouldAlertSupportBox(MarkerKind kind, Minecraft minecraft) {
        if (kind == MarkerKind.AMMO_BOX) {
            return isPrimaryWeaponEmpty(minecraft);
        }
        if (kind == MarkerKind.MEDICAL_BOX) {
            return isMedicalBoxNeeded(minecraft);
        }
        if (kind == MarkerKind.GRENADE_SELF || kind == MarkerKind.GRENADE_HOSTILE || kind == MarkerKind.BEACON_HOSTILE) {
            return true;
        }
        return false;
    }

    private static boolean isMedicalBoxNeeded(Minecraft minecraft) {
        return minecraft != null
            && minecraft.player != null
            && minecraft.player.isAlive()
            && minecraft.player.getHealth() < 4.0F;
    }

    private static boolean isViewerAssault() {
        return "assault".equalsIgnoreCase(ClientHudDebugState.getSelfClass());
    }

    private static boolean hasClearSightToPoint(Minecraft minecraft, Vec3 start, Vec3 end) {
        if (minecraft == null || minecraft.level == null || start == null || end == null) {
            return false;
        }
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            minecraft.player
        ));
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return true;
        }
        return start.distanceToSqr(blockHit.getLocation()) + 0.01D >= start.distanceToSqr(end);
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
            if (kind == MarkerKind.AMMO_BOX || kind == MarkerKind.MEDICAL_BOX) {
                return new Vec3(x, y + 0.5D, z);
            }
            if (kind == MarkerKind.AMMO_BAG || kind == MarkerKind.MEDICAL_BAG) {
                return new Vec3(x + 0.5D, y + 0.35D, z + 0.5D);
            }
            if (kind == MarkerKind.GRENADE_SELF || kind == MarkerKind.GRENADE_FRIENDLY || kind == MarkerKind.GRENADE_HOSTILE || kind == MarkerKind.CLAYMORE_FRIENDLY) {
                return new Vec3(x, y + 0.2D, z);
            }
            if (kind == MarkerKind.BEACON_FRIENDLY || kind == MarkerKind.BEACON_HOSTILE) {
                return new Vec3(x, y + 0.9D, z);
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
        OBSERVE_GROUND,
        REQUEST_STRIKE,
        ENEMY,
        DIRECT_ENEMY,
        AUTO_ENEMY,
        ALLY_PERSISTENT,
        SQUAD_PERSISTENT,
        DOWNED_ALLY,
        DOWNED_SQUAD,
        NEED_AMMO_ALLY,
        NEED_AMMO_SQUAD,
        AMMO_BOX,
        MEDICAL_BOX,
        AMMO_BAG,
        MEDICAL_BAG,
        GRENADE_SELF,
        GRENADE_FRIENDLY,
        GRENADE_HOSTILE,
        CLAYMORE_FRIENDLY,
        BEACON_FRIENDLY,
        BEACON_HOSTILE;

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
            return this == ENEMY || this == DIRECT_ENEMY || this == AUTO_ENEMY || this == GRENADE_SELF || this == GRENADE_HOSTILE || this == BEACON_HOSTILE;
        }

        private boolean usesTriangleShape() {
            return this == OBSERVE_GROUND || this == DIRECT_ENEMY || this == AUTO_ENEMY;
        }

        private boolean isTrackedPlayerMarker() {
            return this == DIRECT_ENEMY
                || this == AUTO_ENEMY
                || this == ALLY_PERSISTENT
                || this == SQUAD_PERSISTENT
                || this == DOWNED_ALLY
                || this == DOWNED_SQUAD
                || this == NEED_AMMO_ALLY
                || this == NEED_AMMO_SQUAD;
        }

        private boolean isDownedMarker() {
            return this == DOWNED_ALLY || this == DOWNED_SQUAD;
        }

        private boolean isFriendlyPersistentOrDownedMarker() {
            return this == ALLY_PERSISTENT
                || this == SQUAD_PERSISTENT
                || this == DOWNED_ALLY
                || this == DOWNED_SQUAD
                || this == NEED_AMMO_ALLY
                || this == NEED_AMMO_SQUAD;
        }

        private TacticalMarkerRenderer.MarkerVisualType toVisualType() {
            return switch (this) {
                case NORMAL -> TacticalMarkerRenderer.MarkerVisualType.NORMAL;
                case OBSERVE_GROUND -> TacticalMarkerRenderer.MarkerVisualType.OBSERVE_GROUND;
                case REQUEST_STRIKE -> TacticalMarkerRenderer.MarkerVisualType.REQUEST_STRIKE;
                case ENEMY -> TacticalMarkerRenderer.MarkerVisualType.ENEMY_GROUND;
                case DIRECT_ENEMY -> TacticalMarkerRenderer.MarkerVisualType.ENEMY_TRACKED;
                case AUTO_ENEMY -> TacticalMarkerRenderer.MarkerVisualType.AUTO_ENEMY;
                case ALLY_PERSISTENT -> TacticalMarkerRenderer.MarkerVisualType.ALLY_PERSISTENT;
                case SQUAD_PERSISTENT -> TacticalMarkerRenderer.MarkerVisualType.SQUAD_PERSISTENT;
                case DOWNED_ALLY -> TacticalMarkerRenderer.MarkerVisualType.DOWNED_ALLY;
                case DOWNED_SQUAD -> TacticalMarkerRenderer.MarkerVisualType.DOWNED_SQUAD;
                case NEED_AMMO_ALLY -> TacticalMarkerRenderer.MarkerVisualType.NEED_AMMO_ALLY;
                case NEED_AMMO_SQUAD -> TacticalMarkerRenderer.MarkerVisualType.NEED_AMMO_SQUAD;
                case AMMO_BOX -> TacticalMarkerRenderer.MarkerVisualType.AMMO_BOX;
                case MEDICAL_BOX -> TacticalMarkerRenderer.MarkerVisualType.MEDICAL_BOX;
                case AMMO_BAG -> TacticalMarkerRenderer.MarkerVisualType.AMMO_BAG;
                case MEDICAL_BAG -> TacticalMarkerRenderer.MarkerVisualType.MEDICAL_BAG;
                case GRENADE_SELF -> TacticalMarkerRenderer.MarkerVisualType.GRENADE_SELF;
                case GRENADE_FRIENDLY -> TacticalMarkerRenderer.MarkerVisualType.GRENADE_FRIENDLY;
                case GRENADE_HOSTILE -> TacticalMarkerRenderer.MarkerVisualType.GRENADE_HOSTILE;
                case CLAYMORE_FRIENDLY -> TacticalMarkerRenderer.MarkerVisualType.CLAYMORE_FRIENDLY;
                case BEACON_FRIENDLY -> TacticalMarkerRenderer.MarkerVisualType.BEACON_FRIENDLY;
                case BEACON_HOSTILE -> TacticalMarkerRenderer.MarkerVisualType.BEACON_HOSTILE;
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

        private static TeamToken fromBeaconToken(String token) {
            if (token == null || token.isBlank()) {
                return UNKNOWN;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            if (normalized.contains("camp_a") || normalized.contains("campa") || "a".equals(normalized) || "t".equals(normalized)) {
                return CAMP_A;
            }
            if (normalized.contains("camp_b") || normalized.contains("campb") || "b".equals(normalized) || "s".equals(normalized)) {
                return CAMP_B;
            }
            return UNKNOWN;
        }
    }
}
