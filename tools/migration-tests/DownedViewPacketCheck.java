package tests;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.common.player.DownedCameraMode;
import org.mods.gd656conquest.network.packet.DownedViewPacket;
import org.mods.gd656conquest.network.packet.RevivedProneReleasePacket;

public final class DownedViewPacketCheck {
    static int checks;
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    static void rejects(Runnable action, String why) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, why); return; }
        throw new AssertionError(why);
    }
    static DownedViewPacket packet(long id, int seq, int entity, String dim, float yaw, Vec3 pos) {
        return new DownedViewPacket(DownedViewPacket.Action.START, id, seq, entity, dim,
            DownedCameraMode.FIXED, yaw, pos, null, new Vec3(0, 0, 0), false);
    }
    public static void main(String[] args) {
        for (var mode : DownedCameraMode.values()) for (var action : DownedViewPacket.Action.values())
            for (UUID killer : new UUID[]{null, UUID.randomUUID()}) {
                var expected = new DownedViewPacket(action, Long.MAX_VALUE, Integer.MAX_VALUE, 123456,
                    "minecraft:overworld", mode, -179.5F, new Vec3(-.125, 64.25, 29999999), killer,
                    new Vec3(120, -15.5, 22.25), killer != null);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    DownedViewPacket.encode(expected, buffer);
                    check(expected.equals(DownedViewPacket.decode(buffer)), "Complete view packet roundtrip");
                    check(buffer.readableBytes() == 0, "All view fields consumed");
                    buffer.readerIndex(0); buffer.setByte(0, 255);
                    rejects(() -> DownedViewPacket.decode(buffer), "Unknown action rejected");
                } finally { buffer.release(); }
            }
        rejects(() -> packet(0, 1, 1, "minecraft:overworld", 0, new Vec3(0, 0, 0)), "Invalid session");
        rejects(() -> packet(1, -1, 1, "minecraft:overworld", 0, new Vec3(0, 0, 0)), "Invalid sequence");
        rejects(() -> packet(1, 1, -1, "minecraft:overworld", 0, new Vec3(0, 0, 0)), "Invalid target");
        rejects(() -> packet(1, 1, 1, "x".repeat(257), 0, new Vec3(0, 0, 0)), "Oversized dimension");
        rejects(() -> packet(1, 1, 1, null, 0, new Vec3(0, 0, 0)), "Null dimension");
        rejects(() -> packet(1, 1, 1, "minecraft:overworld", Float.NaN, new Vec3(0, 0, 0)), "Invalid rotation");
        rejects(() -> packet(1, 1, 1, "minecraft:overworld", 0, new Vec3(Double.POSITIVE_INFINITY, 0, 0)), "Invalid position");
        for (long session : new long[]{1, Long.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var expected = new RevivedProneReleasePacket(session);
                RevivedProneReleasePacket.encode(expected, buffer);
                check(expected.equals(RevivedProneReleasePacket.decode(buffer)), "Release session roundtrip");
                check(buffer.readableBytes() == 0, "Release contains only sender session");
                buffer.clear(); buffer.writeLong(0);
                rejects(() -> RevivedProneReleasePacket.decode(buffer), "Invalid release rejected");
            } finally { buffer.release(); }
        }
        System.out.println("PASS real camera/prone packet codec: " + checks);
    }
}
