package org.mods.gd656conquest.client.room;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Deterministic composition and frame-rate-independent motion, separate from world access. */
public final class DownedCameraMath {
    public static final double IMPACT_SECONDS = .32;
    public static final double REVEAL_SECONDS = 1.10;
    public static final double DOWNED_REVEAL_SECONDS = IMPACT_SECONDS + REVEAL_SECONDS;
    public record Frame(Vec3 position, float yaw, float pitch, double fov) {}
    public record Shot(Frame frame, int side, double score) {}
    public record Projection(double x, double y, double depth) {}
    public interface Collision { Vec3 clip(Vec3 from, Vec3 desired); }
    /** Keeps velocity across network samples; replacing a target never restarts an easing curve. */
    public static final class PositionSpring {
        private Vec3 position, velocity = Vec3.ZERO;
        private final double omega;
        public PositionSpring(Vec3 position) { this(position,18); }
        public PositionSpring(Vec3 position, double omega) { this.position = position; this.omega = omega; }
        public Vec3 position() { return position; }
        public Vec3 velocity() { return velocity; }
        public Vec3 advance(Vec3 target, double seconds) {
            double dt = clamp(seconds, 0, .1), decay = Math.exp(-omega * dt);
            Vec3 error = sub(position, target), c = add(velocity, scale(error, omega));
            position = add(target, scale(add(error, scale(c, dt)), decay));
            velocity = scale(sub(velocity, scale(c, omega * dt)), decay);
            return position;
        }
    }
    public static Vec3 predictSubject(Vec3 latest, Vec3 velocity, double age) {
        double lead = clamp(age + .03, 0, .18) * (1 - smooth((age - .30) / .20));
        Vec3 offset = scale(velocity, lead);
        if (length(offset) > .65) offset = scale(unit(offset), .65);
        return add(latest, offset);
    }
    public static double followVelocity(double previous, double requested, double seconds, double maxAcceleration) {
        double dt = clamp(seconds, 0, .1);
        return previous + clamp((requested - previous) * (1 - Math.exp(-14 * dt)),
            -maxAcceleration * dt, maxAcceleration * dt);
    }
    private DownedCameraMath() {}

    public static Shot solve(Vec3 body, AABB bounds, Vec3 killer, float initialYaw,
                             double baseFov, double aspect, int preferredSide, Collision collision) {
        return solve(body, bounds, killer, initialYaw, baseFov, aspect, preferredSide, null, collision);
    }
    public static Shot solve(Vec3 body, AABB bounds, Vec3 killer, float initialYaw,
                             double baseFov, double aspect, int preferredSide, Vec3 previousPosition, Collision collision) {
        Vec3 forward = killer == null ? forward(initialYaw, 0) : unit(sub(killer, body));
        forward = unit(new Vec3(forward.x, 0, forward.z));
        if (length(forward) < .01) forward = forward(initialYaw, 0);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Shot best = null;
        // Finite candidates keep the camera local and the collision workload bounded.
        for (int side : new int[]{preferredSide, -preferredSide}) for (double distance : new double[]{2.8, 4.2, 5.5}) {
            Vec3 desired = add(add(body, scale(forward, -distance * .85)),
                add(scale(right, side * distance * .42), new Vec3(0, distance < 4 ? .90 : 1.25, 0)));
            Vec3 position = collision.clip(body, desired);
            Frame frame = compose(position, body, bounds, killer, baseFov, aspect);
            double score = penalty(frame, bounds, killer, aspect) * 25 + focusPenalty(frame, body, killer, aspect) * 8
                + Math.abs(frame.fov - baseFov) * .035 + length(sub(position, body)) * .08
                + (side == preferredSide ? 0 : .8) + length(sub(desired, position)) * 2
                + (previousPosition == null ? 0 : length(sub(position, previousPosition)) * .65);
            if (best == null || score < best.score) best = new Shot(frame, side, score);
        }
        return best;
    }
    public static Frame compose(Vec3 camera, Vec3 body, AABB bounds, Vec3 killer, double baseFov, double aspect) {
        // Begin with the killer as the subject, then move the aim only as far as the body needs.
        // The corpse is foreground context, rather than the midpoint of an angular bisector.
        Vec3 subject = killer == null ? add(body, new Vec3(0, .95, 0)) : killer;
        Vec3 direction = unit(sub(subject, camera));
        if (length(direction) < .01) direction = unit(sub(body, camera));
        Vec3[] visible = points(bounds, killer);
        Frame best = null; double bestScore = Double.POSITIVE_INFINITY;
        double startFov = clamp(baseFov, 50, 95);
        for (int step = 0; step <= 15; step++) {
            double candidateFov = Math.min(95, startFov + step * 3);
            double tan = Math.tan(Math.toRadians(candidateFov) * .5);
            float yaw = yaw(direction), pitch = (float)(pitch(direction) + Math.toDegrees(Math.atan(.12 * tan)));
            Frame frame = new Frame(camera, yaw, pitch, candidateFov);
            // Projection constraints preserve the body bounds without automatically centering them.
            for (int iteration = 0; iteration < 12; iteration++) {
                double yawMin = 0, yawMax = 0, pitchMin = 0, pitchMax = 0;
                for (Vec3 point : visible) {
                    Projection p = project(frame, point, aspect);
                    if (p.depth <= .01) {
                        double turn = wrap(yaw(sub(point, camera)) - yaw);
                        yawMin = Math.min(yawMin, turn * .5); yawMax = Math.max(yawMax, turn * .5);
                        continue;
                    }
                    double yawCorrection = Math.toDegrees(Math.atan(p.x * aspect * tan)
                        - Math.atan(clamp(p.x, -.80, .80) * aspect * tan));
                    double pitchCorrection = -Math.toDegrees(Math.atan(p.y * tan)
                        - Math.atan(clamp(p.y, -.68, .68) * tan));
                    yawMin = Math.min(yawMin, yawCorrection); yawMax = Math.max(yawMax, yawCorrection);
                    pitchMin = Math.min(pitchMin, pitchCorrection); pitchMax = Math.max(pitchMax, pitchCorrection);
                }
                double yawStep = yawMin + yawMax, pitchStep = pitchMin + pitchMax;
                if (Math.abs(yawStep) + Math.abs(pitchStep) < .01) break;
                yaw += (float)(yawStep * .75); pitch = (float)clamp(pitch + pitchStep * .75, -85, 85);
                frame = new Frame(camera, yaw, pitch, candidateFov);
            }
            double score = penalty(frame, bounds, killer, aspect) * 100
                + focusPenalty(frame, body, killer, aspect) * 8 + (candidateFov - startFov) * .008;
            if (score < bestScore) { bestScore = score; best = frame; }
            if (candidateFov >= 95) break;
        }
        return best;
    }
    private static double focusPenalty(Frame frame, Vec3 body, Vec3 killer, double aspect) {
        Projection foreground = project(frame, body, aspect);
        double score = Math.max(0, foreground.y + .20) * .15;
        if (killer != null) {
            Projection subject = project(frame, killer, aspect);
            score += subject.depth <= .01 ? 100 : subject.x * subject.x + Math.pow(subject.y - .12, 2);
        }
        return score;
    }

    /** A single directional hit, followed by a short fall; no looping shake. */
    public static Frame impact(Frame start, Vec3 contactEye, Vec3 away, double seconds) {
        double t = clamp(seconds / IMPACT_SECONDS, 0, 1), settle = smooth(t);
        double hit = hitPulse(seconds);
        double sign = impactSide(start, away);
        Vec3 position = add(lerp(start.position, contactEye, settle), scale(away, .11 * hit));
        return new Frame(position, (float)(start.yaw + sign * 5 * hit),
            (float)clamp(start.pitch - 10 * hit + 24 * settle, -90, 90), start.fov + 1.5 * hit);
    }
    public static float impactRoll(Frame start, Vec3 away, double seconds) {
        return (float)(impactSide(start, away) * (10 * hitPulse(seconds) + 4 * smooth(seconds / IMPACT_SECONDS)));
    }
    private static double hitPulse(double seconds) {
        double t = clamp(seconds, 0, IMPACT_SECONDS) / .09;
        return t * Math.exp(1 - t);
    }
    public static double impactSide(Frame start, Vec3 away) {
        double y = Math.toRadians(start.yaw);
        double side = dot(away, new Vec3(-Math.cos(y), 0, -Math.sin(y)));
        return side < -.05 ? -1 : 1;
    }
    /** Ease out along an arc after the head has fallen, with a delayed change of attention. */
    public static Frame reveal(Frame contact, Frame destination, Vec3 away, double seconds) {
        double t = smooth(seconds / REVEAL_SECONDS);
        double yaw = Math.toRadians(contact.yaw);
        Vec3 lateral = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 p1 = add(contact.position, add(scale(away, .55),
            add(scale(lateral, .30 * impactSide(contact, away)), new Vec3(0, -.10, 0))));
        Vec3 p2 = add(destination.position, add(scale(unit(sub(contact.position, destination.position)), .8), new Vec3(0, .70, 0)));
        double u = 1 - t;
        Vec3 position = add(add(scale(contact.position, u*u*u), scale(p1, 3*u*u*t)),
            add(scale(p2, 3*u*t*t), scale(destination.position, t*t*t)));
        double attention = smooth((seconds / REVEAL_SECONDS - .10) / .90);
        return new Frame(position, (float)(contact.yaw + wrap(destination.yaw - contact.yaw) * attention),
            (float)(contact.pitch + (destination.pitch - contact.pitch) * attention),
            contact.fov + (destination.fov - contact.fov) * smooth(seconds / REVEAL_SECONDS));
    }
    public static double penalty(Frame frame, AABB bounds, Vec3 killer, double aspect) {
        double total = 0;
        for (Vec3 point : points(bounds, null)) total += outside(project(frame, point, aspect)) * 2;
        if (killer != null) total += outside(project(frame, killer, aspect));
        return total;
    }
    private static double outside(Projection p) {
        return p.depth <= .01 ? 100 : Math.max(0, Math.abs(p.x) - .82) + Math.max(0, Math.abs(p.y) - .70);
    }
    public static Projection project(Frame frame, Vec3 point, double aspect) {
        Vec3 look = forward(frame.yaw, frame.pitch);
        double yaw = Math.toRadians(frame.yaw), pitch = Math.toRadians(frame.pitch);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = new Vec3(-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch));
        Vec3 delta = sub(point, frame.position);
        double depth = dot(delta, look), tan = Math.tan(Math.toRadians(frame.fov) * .5);
        double divisor = Math.max(depth, 1e-6) * tan;
        return new Projection(dot(delta, right) / (divisor * Math.max(.25, aspect)), dot(delta, up) / divisor, depth);
    }
    public static Frame approach(Frame current, Frame target, double seconds, double rate) {
        double t = 1 - Math.exp(-rate * clamp(seconds, 0, .1));
        float yaw = (float)(current.yaw + clamp(wrap(target.yaw - current.yaw) * t, -150 * seconds, 150 * seconds));
        float pitch = (float)(current.pitch + clamp((target.pitch - current.pitch) * t, -100 * seconds, 100 * seconds));
        return new Frame(lerp(current.position, target.position, t), yaw, pitch, current.fov + (target.fov - current.fov) * t);
    }
    /** Continuous urgency rises well before the subject reaches a viewport edge. */
    public static double edgeFollowStrength(Frame current, Vec3 subject, double aspect) {
        Projection p = project(current, subject, aspect);
        if (p.depth <= .01) return 1;
        double horizontal = smooth((Math.abs(p.x) - .38) / (.82 - .38));
        double vertical = smooth((Math.abs(p.y) - .28) / (.70 - .28));
        return Math.max(horizontal, vertical);
    }
    /** Foreground pressure starts inside the viewport, before a corner has been cut off. */
    public static double foregroundPressure(Frame frame, AABB bounds, double aspect) {
        double pressure = 0;
        for (Vec3 corner : points(bounds,null)) {
            Projection p = project(frame,corner,aspect);
            if (p.depth <= .01) return 1;
            pressure = Math.max(pressure, Math.max(smooth((Math.abs(p.x)-.60)/.28),
                smooth((Math.abs(p.y)-.66)/.26)));
        }
        return pressure;
    }
    /** Small drift inside the safe area; recover toward its boundary, rather than to the center. */
    public static Frame trackWithinScreen(Frame current, Frame composed, AABB bounds, Vec3 subject,
                                          double aspect, double seconds, double correctionStrength) {
        return trackWithinScreen(current, composed, bounds, subject, Vec3.ZERO, aspect, seconds, correctionStrength);
    }
    public static Frame trackWithinScreen(Frame current, Frame composed, AABB bounds, Vec3 subject,
                                          Vec3 subjectVelocity, double aspect, double seconds, double correctionStrength) {
        double dt = clamp(seconds, 0, .1);
        double strength = clamp(correctionStrength, 0, 1);
        // The controller integrates position with a persistent spring, including reselections.
        Vec3 position = composed.position;
        double fov = current.fov + (composed.fov - current.fov) * (1 - Math.exp(-1.2 * dt));
        Frame held = new Frame(position, current.yaw, current.pitch, fov);
        Projection p = project(held, subject, aspect);
        Frame aim;
        if (p.depth <= .01) {
            aim = new Frame(position, composed.yaw, composed.pitch, fov);
        } else {
            double tan = Math.tan(Math.toRadians(fov) * .5);
            double xLimit = .22 + .18 * strength, yLimit = .18 + .10 * strength;
            double x = clamp(p.x, -xLimit, xLimit), y = clamp(p.y, -yLimit, yLimit);
            double yaw = Math.toDegrees(Math.atan(p.x * aspect * tan) - Math.atan(x * aspect * tan));
            double pitch = -Math.toDegrees(Math.atan(p.y * tan) - Math.atan(y * tan));
            aim = new Frame(position, (float)(held.yaw + yaw), (float)clamp(held.pitch + pitch, -85, 85), fov);
            // Foreground is a soft preference. An unconditional correction pins the camera to
            // the corpse, stalls tracking, then reverses the turn when the killer passes behind it.
            Frame foreground = constrainForeground(aim, bounds, aspect);
            Projection constrained = project(foreground, subject, aspect);
            double risk = constrained.depth <= .01 ? 1 : Math.max(Math.abs(constrained.x) / .68, Math.abs(constrained.y) / .58);
            double preserve = 1 - smooth((risk - .45) / .55);
            aim = new Frame(position, (float)(aim.yaw + wrap(foreground.yaw - aim.yaw) * preserve),
                (float)(aim.pitch + (foreground.pitch - aim.pitch) * preserve), fov);
        }
        double bodyRisk = foregroundPressure(held, bounds, aspect);
        double urgency = p.depth <= .01 ? 1 : Math.max(strength, bodyRisk);
        double t = 1 - Math.exp(-(.22 + 5.58 * urgency) * dt);
        double yawLimit = (10 + 140 * urgency) * dt, pitchLimit = (8 + 92 * urgency) * dt;
        // Match the subject's angular motion before positional error builds up. This is a
        // continuous velocity input, not a new easing animation at each network sample.
        Vec3 cameraVelocity = dt > 0 ? scale(sub(position,current.position),1/dt) : Vec3.ZERO;
        Vec3 nextDirection = sub(add(subject, scale(subjectVelocity, .05)), add(position,scale(cameraVelocity,.05)));
        Vec3 direction = sub(subject, position);
        double feedYaw = clamp(wrap(yaw(nextDirection) - yaw(direction)) / .05, -150, 150) * strength * dt;
        double feedPitch = clamp((pitch(nextDirection) - pitch(direction)) / .05, -100, 100) * strength * dt;
        return new Frame(position,
            (float)(current.yaw + clamp(wrap(aim.yaw - current.yaw) * t + feedYaw, -yawLimit, yawLimit)),
            (float)(current.pitch + clamp((aim.pitch - current.pitch) * t + feedPitch, -pitchLimit, pitchLimit)), fov);
    }
    private static Frame constrainForeground(Frame frame, AABB bounds, double aspect) {
        Vec3[] corners = points(bounds, null);
        double tan = Math.tan(Math.toRadians(frame.fov) * .5);
        for (int iteration = 0; iteration < 6; iteration++) {
            double yawMin = 0, yawMax = 0, pitchMin = 0, pitchMax = 0;
            for (Vec3 corner : corners) {
                Projection p = project(frame, corner, aspect);
                if (p.depth <= .01) continue; // Repositioning handles an irreconcilable view.
                // A smooth ramp introduces a small correction while corners are still visible,
                // rather than abruptly pushing back once they cross a hard boundary.
                double yaw = Math.toDegrees(Math.atan(p.x * aspect * tan)
                    - Math.atan(clamp(p.x, -.60, .60) * aspect * tan)) * smooth((Math.abs(p.x)-.60)/.28);
                double pitch = -Math.toDegrees(Math.atan(p.y * tan)
                    - Math.atan(clamp(p.y, -.66, .66) * tan)) * smooth((Math.abs(p.y)-.66)/.26);
                yawMin = Math.min(yawMin, yaw); yawMax = Math.max(yawMax, yaw);
                pitchMin = Math.min(pitchMin, pitch); pitchMax = Math.max(pitchMax, pitch);
            }
            double yaw = yawMin + yawMax, pitch = pitchMin + pitchMax;
            if (Math.abs(yaw) + Math.abs(pitch) < .01) break;
            frame = new Frame(frame.position, (float)(frame.yaw + yaw * .75),
                (float)clamp(frame.pitch + pitch * .75, -85, 85), frame.fov);
        }
        return frame;
    }
    public static Frame blend(Frame from, Frame to, double t) {
        t = smooth(t);
        return new Frame(lerp(from.position,to.position,t), (float)(from.yaw+wrap(to.yaw-from.yaw)*t),
            (float)(from.pitch+(to.pitch-from.pitch)*t), from.fov+(to.fov-from.fov)*t);
    }
    public static double smooth(double t) { t = clamp(t, 0, 1); return t*t*(3-2*t); }
    public static Vec3[] points(AABB b, Vec3 killer) {
        Vec3[] result = new Vec3[killer == null ? 8 : 9];
        for (int i=0;i<8;i++) result[i]=new Vec3((i&1)==0?b.minX:b.maxX,
            (i&2)==0?b.minY:b.maxY, (i&4)==0?b.minZ:b.maxZ);
        if (killer != null) result[8]=killer;
        return result;
    }
    public static Vec3 forward(float yaw, float pitch) {
        double y=Math.toRadians(yaw), p=Math.toRadians(pitch);
        return new Vec3(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));
    }
    public static float yaw(Vec3 v) { return (float)(Math.toDegrees(Math.atan2(v.z,v.x))-90); }
    public static float pitch(Vec3 v) { return (float)-Math.toDegrees(Math.atan2(v.y,Math.hypot(v.x,v.z))); }
    public static double wrap(double angle) { return ((angle % 360)+540)%360-180; }
    public static double clamp(double n,double min,double max){return Math.max(min,Math.min(max,n));}
    public static Vec3 add(Vec3 a,Vec3 b){return new Vec3(a.x+b.x,a.y+b.y,a.z+b.z);}
    public static Vec3 sub(Vec3 a,Vec3 b){return new Vec3(a.x-b.x,a.y-b.y,a.z-b.z);}
    public static Vec3 scale(Vec3 a,double t){return new Vec3(a.x*t,a.y*t,a.z*t);}
    public static double dot(Vec3 a,Vec3 b){return a.x*b.x+a.y*b.y+a.z*b.z;}
    public static double length(Vec3 a){return Math.sqrt(dot(a,a));}
    public static Vec3 unit(Vec3 a){double length=length(a);return length<1e-6?Vec3.ZERO:scale(a,1/length);}
    public static Vec3 lerp(Vec3 a,Vec3 b,double t){return add(a,scale(sub(b,a),clamp(t,0,1)));}
}
