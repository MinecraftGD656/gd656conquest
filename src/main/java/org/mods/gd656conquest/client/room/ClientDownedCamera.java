package org.mods.gd656conquest.client.room;

import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.mods.gd656conquest.common.player.DownedCameraMode;
import org.mods.gd656conquest.common.player.RevivedPronePose;
import org.mods.gd656conquest.mixin.client.DownedCameraAccessor;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.DownedViewPacket;
import org.mods.gd656conquest.network.packet.RevivedProneReleasePacket;
import static org.mods.gd656conquest.client.room.DownedCameraMath.*;

@EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT)
public final class ClientDownedCamera {
    private enum Stage { IDLE, DOWNED, RETURN_PENDING, RETURNING }
    private static Stage stage = Stage.IDLE;
    private static long session, closedSession, lastFrameNanos, killerSampleNanos, leaseTick, tick;
    private static int sequence, entityId, side = 1;
    private static Object level;
    private static Player playerIdentity;
    private static DownedCameraMode mode = DownedCameraMode.FIXED;
    private static Vec3 anchor, killer, smoothedKiller, reactionAway, contactEye, shotAnchor;
    private static Vec3 killerSampleVelocity = Vec3.ZERO;
    private static PositionSpring killerMotion;
    private static PositionSpring cameraMotion;
    private static PositionSpring cameraDestinationMotion;
    private static double bodyFollowWeight;
    private static UUID killerId;
    private static Player killerEntity;
    private static Vec3 renderedKiller, renderedVelocity = Vec3.ZERO;
    private static double yawFollowVelocity, pitchFollowVelocity;
    private static boolean tracking, prone, releasedLocally, jumpWasDown, crawlWasDown;
    private static boolean consumeStandJump;
    private static boolean reviveVisualActive;
    private static boolean ordinaryDeath;
    private static Frame from, current, impactEnd;
    private static Shot shot;
    private static float savedYaw;
    private static float lastRoll, fromRoll, impactEndRoll;
    private static double elapsed, solveElapsed, poorFramingElapsed, edgeFollowWeight, killerWeight, fov = 75, baseFov = 75, returnElapsed;
    private static CameraType previousPerspective;
    private static KeyMapping crawlKey;
    private static boolean resolvedCrawlKey;
    private ClientDownedCamera() {}

    public static void accept(DownedViewPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.getId() != packet.entityId()
            || !mc.level.dimension().location().toString().equals(packet.dimension())) return;
        if (level != mc.level || playerIdentity != mc.player) reset();
        level = mc.level; playerIdentity = mc.player;
        if (packet.session() < session || packet.session() <= closedSession
            || packet.session() == session && packet.sequence() <= sequence) return;
        boolean starts=packet.action()==DownedViewPacket.Action.START || packet.action()==DownedViewPacket.Action.START_DEATH;
        if (packet.session() > session && !starts) return;
        if (starts) {
            if (packet.session() == session) return;
            stopProne(); cancelCamera();
            session = packet.session(); entityId = packet.entityId(); mode = packet.mode(); savedYaw = packet.yaw();
            stage = Stage.DOWNED; elapsed = 0; solveElapsed = 1; shot = null; side = 1;
            ordinaryDeath=packet.action()==DownedViewPacket.Action.START_DEATH;
            if(ordinaryDeath)ClientDownedRagdollState.beginDeathObservation(entityId,mc.player.getUUID(),packet.position());
            releasedLocally = false;
            anchor = packet.position(); killer = packet.killerPosition(); smoothedKiller = killer;
            killerId = packet.killerId(); killerEntity = null; renderedKiller = null; renderedVelocity = Vec3.ZERO;
            killerMotion = new PositionSpring(killer); killerSampleVelocity = Vec3.ZERO;
            killerSampleNanos = System.nanoTime(); yawFollowVelocity = 0; pitchFollowVelocity = 0;
            tracking = packet.tracking(); killerWeight = tracking ? 1 : 0;
            previousPerspective = mc.options.getCameraType();
            baseFov = mc.options.fov().get().doubleValue();
            from = current = new Frame(mc.player.getEyePosition(), mc.player.getYRot(), mc.player.getXRot(), fov);
            Vec3 away = tracking ? sub(anchor, killer) : scale(forward(from.yaw(), 0), -1);
            reactionAway = unit(new Vec3(away.x, 0, away.z));
            if (length(reactionAway) < .01) reactionAway = scale(forward(from.yaw(), 0), -1);
            contactEye = add(from.position(), add(scale(reactionAway, .18), new Vec3(0, -.90, 0)));
            impactEnd = null; impactEndRoll = 0; poorFramingElapsed = 0; shotAnchor = null;
            edgeFollowWeight = 0;
            side = impactSide(from, reactionAway) < 0 ? -1 : 1;
            lastFrameNanos = 0;
            forceFirstPerson(mc);
        }
        sequence = packet.sequence(); leaseTick = tick + 80;
        switch (packet.action()) {
            case START, START_DEATH -> {}
            case UPDATE -> {
                if (stage != Stage.DOWNED) return;
                long now = System.nanoTime();
                double interval = (now - killerSampleNanos) / 1e9;
                if (tracking && packet.tracking() && interval >= .075 && interval <= .6) {
                    Vec3 velocity = scale(sub(packet.killerPosition(),killer), 1 / interval);
                    if (length(velocity) > 12) velocity = scale(unit(velocity),12);
                    killerSampleVelocity = lerp(killerSampleVelocity,velocity,.6);
                } else killerSampleVelocity = Vec3.ZERO;
                killerSampleNanos = now;
                anchor = packet.position(); killer = packet.killerPosition(); tracking = packet.tracking();
            }
            case PREPARE_REVIVE -> {
                ClientKillerCardState.hide();
                ClientDeathCauseState.hide();
                if (stage != Stage.DOWNED || ordinaryDeath) return;
                // Capture the last displayed view BEFORE native body removal and generic clears.
                if (current == null) {
                    Camera camera = mc.gameRenderer.getMainCamera();
                    current = new Frame(camera.getPosition(), camera.getYRot(), camera.getXRot(), fov);
                }
                from = current; fromRoll = lastRoll; lastPosition = current.position(); stage = Stage.RETURN_PENDING; returnElapsed = 0;
            }
            case REVIVED -> {
                if (releasedLocally || ordinaryDeath) return;
                boolean first = !prone;
                if (first) {
                    // These local operations follow all old downed/animator clear packets.
                    ClientDownedState.clear(); ClientDeathFlowState.clear();
                    prone = true; RevivedPronePose.begin(mc.player, session);
                    mc.player.setPos(packet.position().x,packet.position().y,packet.position().z);
                    mc.player.setYRot(savedYaw); mc.player.setXRot(0);
                    mc.player.setYBodyRot(savedYaw); mc.player.setYHeadRot(savedYaw);
                    mc.player.yBodyRotO = savedYaw; mc.player.yHeadRotO = savedYaw;
                    mc.player.yRotO = savedYaw; mc.player.xRotO = 0;
                    // A held key from before the revive does not immediately stand the player up.
                    jumpWasDown = mc.options.keyJump.isDown(); crawlWasDown = crawlDown();
                    consumeStandJump = false;
                    if (from == null) from = current = new Frame(mc.player.getEyePosition(),savedYaw,0,baseFov);
                    stage = Stage.RETURNING; returnElapsed = 0; lastFrameNanos = 0; reviveVisualActive = true;
                    forceFirstPerson(mc);
                }
                RevivedPronePose.apply(mc.player);
            }
            case END -> { ClientKillerCardState.hide(); ClientDeathCauseState.hide(); closedSession = packet.session(); stopProne(); cancelCamera(); }
            case PRONE_RELEASED -> { releasedLocally = true; stopProne(); }
        }
    }
    public static boolean automaticDowned() { return stage == Stage.DOWNED && mode == DownedCameraMode.AUTOMATIC; }
    public static boolean deathFilterActive() { return stage == Stage.DOWNED || stage == Stage.RETURN_PENDING; }
    public static long deathFilterSession() { return deathFilterActive() ? session : 0; }
    public static Player killerOutlineTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (!deathFilterActive() || !tracking || killerId == null || mc.level == null) return null;
        Player target = mc.level.getPlayerByUUID(killerId);
        return target != null && target.isAlive() && !target.isRemoved()
            && (killerEntity == null || killerEntity == target) ? target : null;
    }
    public static boolean observingDeath() { return stage==Stage.DOWNED && ordinaryDeath; }
    public static boolean usesFixedDeathCamera() { return observingDeath() && mode==DownedCameraMode.FIXED; }
    public static boolean ownsCamera() { return automaticDowned() || stage == Stage.RETURN_PENDING || stage == Stage.RETURNING
        || usesFixedDeathCamera() && !ClientDownedRagdollState.hasCameraOverride(); }
    /** Called before the old deployment transition reads its starting pose. */
    public static void handoffToDeployment() {
        if(!observingDeath())return;
        Frame view=current;
        if(view!=null)ClientDeathFlowState.adoptCameraPose(view.position().x,view.position().y,view.position().z,view.yaw(),view.pitch());
        closedSession=session;stopProne();cancelCamera();
    }
    public static void onDeathFlowCleared() {
        if(observingDeath()){closedSession=session;stopProne();cancelCamera();}
    }
    public static boolean blocksLook() {
        return Minecraft.getInstance().screen == null && (stage != Stage.IDLE || ClientDownedState.isActive());
    }
    public static boolean hideHands() { return stage != Stage.IDLE || ClientDownedState.isActive(); }
    public static boolean returning() { return stage == Stage.RETURN_PENDING || stage == Stage.RETURNING; }
    public static long reviveVisualSession() { return reviveVisualActive ? session : 0; }
    public static double reviveVisualSeconds() { return returnElapsed; }
    public static float revivePulseStrength() { return reviveVisualActive ? ReviveTransition.pulse(returnElapsed) : 0; }
    private static void forceFirstPerson(Minecraft mc) { mc.options.setCameraType(CameraType.FIRST_PERSON); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHand(RenderHandEvent event) {
        if (hideHands()) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START && mc.player != null
            && (stage != Stage.IDLE || ClientDownedState.isActive())) forceFirstPerson(mc);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance(); tick++;
        if (mc.level != level || mc.player != playerIdentity || mc.player == null) { reset(); return; }
        if (session != 0 && tick >= leaseTick) { closedSession = session; stopProne(); cancelCamera(); return; }
        if (stage != Stage.IDLE) forceFirstPerson(mc);
        if (prone) {
            RevivedPronePose.apply(mc.player);
            boolean crawl = crawlDown();
            if (stage == Stage.IDLE && mc.screen == null && crawl && !crawlWasDown) releaseProne();
            crawlWasDown = crawl;
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyJump.matches(event.getKey(),event.getScanCode())) return;
        if (event.getAction() == 0) { consumeStandJump = false; jumpWasDown = false; return; }
        if (!prone || stage != Stage.IDLE || mc.screen != null || event.getAction() != 1) return;
        standFromJump();
    }
    /** Runs before LocalPlayer consumes jumping, covering keyboard and remapped/controller input. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != playerIdentity || mc.player != playerIdentity || mc.screen != null) return;
        boolean jump = event.getInput().jumping;
        if (!jump) { consumeStandJump = false; jumpWasDown = false; return; }
        if (prone && stage == Stage.IDLE && !jumpWasDown) standFromJump();
        jumpWasDown = true;
        // A held pre-revive key never stands the player. A new press stands without a prone hop;
        // keep consuming that press until release so holding space cannot jump after standing.
        if (prone || consumeStandJump) event.getInput().jumping = false;
    }
    private static void standFromJump() {
        consumeStandJump = true; jumpWasDown = true;
        releaseProne();
    }
    public static boolean handleCrawlKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        if (stage != Stage.IDLE || ClientDownedState.isActive()) return true;
        KeyMapping key = crawlKey();
        if (prone && key != null && key.matches(event.getKey(),event.getScanCode())) {
            if (event.getAction() == 1) releaseProne();
            return true;
        }
        return false;
    }
    public static boolean handleCrawlController(boolean pressed) {
        if (stage != Stage.IDLE || ClientDownedState.isActive()) return true;
        if (!prone) return false;
        if (pressed) releaseProne();
        return true;
    }
    private static void releaseProne() {
        if (!prone || releasedLocally) return;
        releasedLocally = true; stopProne();
        NetworkManager.getChannel().sendToServer(new RevivedProneReleasePacket(session));
    }
    private static KeyMapping crawlKey() {
        if (!resolvedCrawlKey) {
            resolvedCrawlKey = true;
            try {
                Field key = Class.forName("com.tacz.guns.client.input.CrawlKey").getDeclaredField("CRAWL_KEY");
                key.setAccessible(true); crawlKey = (KeyMapping)key.get(null);
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        return crawlKey;
    }
    private static boolean crawlDown() { KeyMapping key=crawlKey(); return key!=null && key.isDown(); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != level) return;
        if (stage == Stage.IDLE) {
            // Continue the visual tail without overriding the live camera or player controls.
            if (reviveVisualActive) {
                long now = System.nanoTime();
                returnElapsed += lastFrameNanos == 0 ? 0 : Math.max(0,(now-lastFrameNanos)/1e9);
                lastFrameNanos = now;
                if (returnElapsed >= ReviveTransition.PULSE_SECONDS) {
                    reviveVisualActive = false; lastFrameNanos = 0;
                }
            }
            return;
        }
        Camera camera = event.getCamera();
        if (!ownsCamera()) {
            current = new Frame(camera.getPosition(),event.getYaw(),event.getPitch(),fov);
            lastRoll = event.getRoll();
            return;
        }
        long now = System.nanoTime();
        double frameSeconds = lastFrameNanos == 0 ? 0 : Math.max(0,(now-lastFrameNanos)/1e9);
        double dt = clamp(frameSeconds,0,.1); lastFrameNanos=now;
        Frame previous = current;
        double aspect = (double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        if (stage == Stage.DOWNED) {
            if(!ordinaryDeath)ClientDeathFlowState.enterDownedView();
            Vec3 localAnchor = ClientDownedRagdollState.cameraAnchor();
            if (localAnchor != null) anchor = localAnchor;
            elapsed += dt; solveElapsed += dt;
            Vec3 predicted = predictSubject(killer,killerSampleVelocity,(now-killerSampleNanos)/1e9);
            smoothedKiller = killerMotion.advance(predicted,dt);
            Vec3 subjectVelocity = killerMotion.velocity();
            // Use the same interpolated position as the rendered player, rather than a separate
            // 5 Hz downed-view stream. Server tracking still authorizes and ends this session.
            Player visibleKiller = tracking && killerId != null ? mc.level.getPlayerByUUID(killerId) : null;
            if (visibleKiller != null && visibleKiller.isAlive() && !visibleKiller.isRemoved()
                && (killerEntity == null || killerEntity == visibleKiller)) {
                killerEntity = visibleKiller;
                Vec3 rendered = visibleKiller.getEyePosition((float)event.getPartialTick());
                if (renderedKiller != null && dt > 0) {
                    Vec3 velocity = scale(sub(rendered,renderedKiller),1/dt);
                    if (length(velocity) > 12) velocity = scale(unit(velocity),12);
                    renderedVelocity = lerp(renderedVelocity,velocity,1-Math.exp(-12*dt));
                }
                renderedKiller = rendered; smoothedKiller = rendered; subjectVelocity = renderedVelocity;
            } else { renderedKiller = null; renderedVelocity = Vec3.ZERO; }
            killerWeight += ((tracking?1:0)-killerWeight)*(1-Math.exp(-2*dt));
            // Ease toward the body when the killer disappears; no instantaneous 180-degree switch.
            Vec3 target = killerWeight < .01 ? null : lerp(add(anchor,new Vec3(0,.95,0)),smoothedKiller,killerWeight);
            AABB bounds = ClientDownedRagdollState.cameraBounds();
            if (bounds == null) bounds = new AABB(anchor.x-1,anchor.y-.3,anchor.z-1,
                anchor.x+1,anchor.y+.8,anchor.z+1);
            if (ordinaryDeath && mode==DownedCameraMode.FIXED) {
                Vec3 head=ClientDownedRagdollState.cameraHeadPosition();
                Vec3 position=head==null ? from.position() : add(head,new Vec3(0,.05,0));
                current=new Frame(position,from.yaw(),from.pitch(),baseFov);
            } else if (elapsed < IMPACT_SECONDS) {
                Vec3 head = ClientDownedRagdollState.cameraHeadPosition();
                if (head != null) {
                    Vec3 eye = add(head, add(scale(forward(from.yaw(), 0), .18), new Vec3(0, .05, 0)));
                    Vec3 delta = sub(eye, from.position());
                    if (length(delta) > 3) eye = add(from.position(), scale(unit(delta), 3));
                    contactEye = lerp(contactEye, eye, 1 - Math.exp(-18 * dt));
                }
                current = impact(from, contactEye, reactionAway, elapsed);
            } else {
                if (impactEnd == null) {
                    impactEnd = impact(from, contactEye, reactionAway, IMPACT_SECONDS);
                    impactEndRoll = impactRoll(from, reactionAway, IMPACT_SECONDS);
                }
                if (shot == null) {
                    shot = solve(anchor,bounds,target,savedYaw,baseFov,aspect,side,ClientDownedCamera::clip);
                    side = shot.side(); shotAnchor = anchor; solveElapsed = 0;
                }
                Frame desired = compose(shot.frame().position(),anchor,bounds,target,baseFov,aspect);
                Frame anticipated = new Frame(current.position(),
                    (float)(current.yaw()+clamp(yawFollowVelocity*.35,-12,12)),
                    (float)clamp(current.pitch()+clamp(pitchFollowVelocity*.35,-10,10),-85,85),current.fov());
                double bodyPressure = Math.max(foregroundPressure(current,bounds,aspect),foregroundPressure(anticipated,bounds,aspect));
                bodyFollowWeight += (bodyPressure-bodyFollowWeight)*(1-Math.exp(-6*dt));
                // Stay at the chosen death location: normal killer movement changes aim, not orbit.
                // Check the displayed pose as well as the ideal pose; constraints and angular
                // acceleration can keep the real camera far from the ideal solution.
                boolean cannotFrame = penalty(desired,bounds,target,aspect) > .08
                    || elapsed > DOWNED_REVEAL_SECONDS && penalty(current,bounds,target,aspect) > .12;
                // A nearby subject can cross behind this fixed camera before error-based
                // repositioning reacts. Anticipate that geometric failure while still visible.
                Vec3 ahead = target == null ? null : add(target,scale(subjectVelocity,1.1));
                boolean approachingCamera = tracking && ahead != null && length(sub(ahead,current.position())) < 3.5
                    && length(sub(ahead,current.position())) < length(sub(target,current.position()))
                    && length(sub(target,anchor)) > length(sub(current.position(),anchor)) - .5;
                poorFramingElapsed = cannotFrame ? poorFramingElapsed + dt : 0;
                if (elapsed >= DOWNED_REVEAL_SECONDS && solveElapsed >= .25
                    && (approachingCamera || bodyFollowWeight > .30 || poorFramingElapsed > .35 || length(sub(anchor,shotAnchor)) > 1.75)) {
                    shot = solve(anchor,bounds,target,savedYaw,baseFov,aspect,side,shot.frame().position(),ClientDownedCamera::clip);
                    side = shot.side(); shotAnchor = anchor; solveElapsed = 0; poorFramingElapsed = 0;
                    desired = compose(shot.frame().position(),anchor,bounds,target,baseFov,aspect);
                }
                if (elapsed > DOWNED_REVEAL_SECONDS) {
                    if (cameraMotion == null) cameraMotion = new PositionSpring(current.position(),6);
                    if (cameraDestinationMotion == null) cameraDestinationMotion = new PositionSpring(current.position(),8);
                    // Smoothing only the camera preserves velocity but still gives it a sharp
                    // acceleration step when a discrete candidate changes by several blocks.
                    Vec3 destination = cameraDestinationMotion.advance(shot.frame().position(),dt);
                    Vec3 movingPosition = cameraMotion.advance(destination,dt);
                    desired = compose(movingPosition,anchor,bounds,target,baseFov,aspect);
                }
                if (elapsed <= DOWNED_REVEAL_SECONDS) {
                    current = reveal(impactEnd,desired,reactionAway,elapsed-IMPACT_SECONDS);
                } else if (tracking && target != null) {
                    // Predict edge risk before the subject reaches the viewport boundary.
                    Vec3 warning = add(target,scale(subjectVelocity,.18));
                    double urgency = Math.max(edgeFollowStrength(current,target,aspect),edgeFollowStrength(current,warning,aspect));
                    edgeFollowWeight += (urgency - edgeFollowWeight) * (1 - Math.exp(-14 * dt));
                    current = trackWithinScreen(current,desired,bounds,target,subjectVelocity,aspect,dt,edgeFollowWeight);
                } else {
                    edgeFollowWeight = 0;
                    Frame held = new Frame(desired.position(),current.yaw(),current.pitch(),current.fov());
                    current = approach(held,desired,dt,2);
                }
            }
            if (dt > 0 && elapsed > DOWNED_REVEAL_SECONDS) {
                double yawRequested = wrap(current.yaw()-previous.yaw()) / dt;
                double pitchRequested = (current.pitch()-previous.pitch()) / dt;
                double nextYaw = followVelocity(yawFollowVelocity,yawRequested,dt,260);
                double nextPitch = followVelocity(pitchFollowVelocity,pitchRequested,dt,200);
                current = new Frame(current.position(),
                    (float)(previous.yaw() + (yawFollowVelocity+nextYaw)*.5*dt),
                    (float)clamp(previous.pitch() + (pitchFollowVelocity+nextPitch)*.5*dt,-90,90),current.fov());
                yawFollowVelocity = nextYaw; pitchFollowVelocity = nextPitch;
            } else if (dt > 0) {
                yawFollowVelocity = wrap(current.yaw()-previous.yaw())/dt;
                pitchFollowVelocity = (current.pitch()-previous.pitch())/dt;
            }
            // Clip both the motion segment and the body-to-camera segment after interpolation.
            Vec3 moved=clip(lastPosition == null ? from.position() : lastPosition,current.position());
            Vec3 safe=clip(anchor,moved);
            current=new Frame(safe,current.yaw(),current.pitch(),current.fov());
        } else if (stage == Stage.RETURNING) {
            // Keep the short revival timeline in wall time even if a render frame stalls.
            returnElapsed+=frameSeconds;
            // Camera.setup has already interpolated the live player eye (including prone height).
            // Finishing at this exact native pose prevents a snap on the next unmanaged frame.
            Vec3 eye=camera.getPosition();
            Frame target=new Frame(eye,savedYaw,0,baseFov);
            double t=ReviveTransition.cameraProgress(returnElapsed);
            current=new Frame(lerp(from.position(),target.position(),t),
                (float)(from.yaw()+wrap(target.yaw()-from.yaw())*t),
                (float)(from.pitch()+(target.pitch()-from.pitch())*t),from.fov()+(target.fov()-from.fov())*t);
            current=new Frame(clip(lastPosition,current.position()),current.yaw(),current.pitch(),current.fov());
            if (returnElapsed>=ReviveTransition.CAMERA_SECONDS) {
                current=target; stage=Stage.IDLE;
                if (returnElapsed >= ReviveTransition.PULSE_SECONDS) {
                    reviveVisualActive = false; lastFrameNanos = 0;
                }
                // Revival always returns to first person, irrespective of the entry perspective.
                previousPerspective=null;
            }
        }
        if (current == null) return;
        DownedCameraAccessor access=(DownedCameraAccessor)camera;
        access.gd656conquest$setPosition(current.position());
        access.gd656conquest$setRotation(current.yaw(),current.pitch());
        access.gd656conquest$setDetached(stage == Stage.DOWNED && elapsed > IMPACT_SECONDS + .08 || returning());
        float roll = 0;
        if (stage == Stage.DOWNED && mode==DownedCameraMode.AUTOMATIC) {
            roll = elapsed < IMPACT_SECONDS ? impactRoll(from,reactionAway,elapsed)
                : (float)(impactEndRoll * (1 - smooth((elapsed-IMPACT_SECONDS)/REVEAL_SECONDS)));
        } else if (stage == Stage.RETURN_PENDING) roll = fromRoll;
        else if (stage == Stage.RETURNING) {
            roll=(float)(fromRoll*(1-ReviveTransition.cameraProgress(returnElapsed)));
        }
        event.setYaw(current.yaw()); event.setPitch(current.pitch()); event.setRoll(roll);
        // Keep Camera's vectors/quaternion consistent with the final event, including native head roll.
        var rotation = camera.rotation().rotationYXZ((float)Math.toRadians(-current.yaw()),
            (float)Math.toRadians(current.pitch()),(float)Math.toRadians(roll));
        camera.getLookVector().set(0,0,1).rotate(rotation);
        camera.getUpVector().set(0,1,0).rotate(rotation);
        camera.getLeftVector().set(1,0,0).rotate(rotation);
        lastRoll=roll;
        lastPosition=current.position();
    }
    private static Vec3 lastPosition;
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (stage == Stage.IDLE) { baseFov=event.getFOV();fov=baseFov;return; }
        if (current != null && ownsCamera()) { fov=current.fov();event.setFOV(fov); }
        else { fov=baseFov;event.setFOV(fov); }
    }
    private static Vec3 clip(Vec3 from,Vec3 desired) {
        Minecraft mc=Minecraft.getInstance();
        if (from==null || mc.level==null) return desired;
        if (!clear(from)) from=escape(from);
        double length=length(sub(desired,from));
        if (length<1e-6) return clear(desired)?desired:escape(from);
        double allowed=1;
        for (int i=0;i<8;i++) {
            Vec3 offset=new Vec3((i&1)==0?-.12:.12,(i&2)==0?-.12:.12,(i&4)==0?-.12:.12);
            Vec3 a=add(from,offset),b=add(desired,offset);
            if (!mc.level.hasChunkAt(BlockPos.containing(b.x,b.y,b.z))) return from;
            var hit=mc.level.clip(new ClipContext(a,b,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player));
            if (hit.getType()!=HitResult.Type.MISS)
                allowed=Math.min(allowed,Math.max(0,length(sub(hit.getLocation(),a))-.08)/length);
        }
        Vec3 position=lerp(from,desired,allowed);
        return clear(position)?position:escape(position);
    }
    private static boolean clear(Vec3 p) {
        Minecraft mc=Minecraft.getInstance();
        if (!mc.level.hasChunkAt(BlockPos.containing(p.x,p.y,p.z))) return false;
        return mc.level.noCollision(new AABB(p.x-.12,p.y-.12,p.z-.12,
            p.x+.12,p.y+.12,p.z+.12));
    }
    private static Vec3 escape(Vec3 p) {
        for(double d=.1;d<=.8;d+=.1) for(Vec3 direction:new Vec3[]{new Vec3(0,1,0),new Vec3(1,0,0),
            new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1)}) {
            Vec3 candidate=add(p,scale(direction,d));if(clear(candidate))return candidate;
        }
        return lastPosition!=null && clear(lastPosition)?lastPosition:p;
    }
    private static void stopProne() {
        if (playerIdentity!=null) RevivedPronePose.end(playerIdentity);
        prone=false;
    }
    private static void cancelCamera() {
        Minecraft mc=Minecraft.getInstance();
        if (previousPerspective!=null && mc.player==playerIdentity) mc.options.setCameraType(previousPerspective);
        previousPerspective=null;stage=Stage.IDLE;from=null;current=null;impactEnd=null;shot=null;shotAnchor=null;
        reviveVisualActive=false;
        consumeStandJump=false;jumpWasDown=false;
        contactEye=null;reactionAway=null;lastPosition=null;lastFrameNanos=0;lastRoll=0;
        edgeFollowWeight=0;
        killerMotion=null;killerSampleVelocity=Vec3.ZERO;killerSampleNanos=0;
        cameraMotion=null;
        cameraDestinationMotion=null;bodyFollowWeight=0;
        ClientDownedRagdollState.endDeathObservation();ordinaryDeath=false;
        killerId=null;killerEntity=null;renderedKiller=null;renderedVelocity=Vec3.ZERO;
        yawFollowVelocity=0;pitchFollowVelocity=0;
    }
    public static void reset() {
        stopProne();cancelCamera();level=null;playerIdentity=null;session=0;closedSession=0;sequence=0;
        releasedLocally=false;leaseTick=0;killer=null;anchor=null;
    }
}
