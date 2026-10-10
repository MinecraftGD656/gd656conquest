package tests;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.mods.gd656conquest.common.player.KillerCardInfo;
import org.mods.gd656conquest.network.packet.DownedDeployPacket;
import org.mods.gd656conquest.network.packet.KillerCardPacket;

/** Actual production records and codecs from the current reobfuscated main JAR. */
public final class KillerCardPacketCheck {
    static int checks;
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    static void rejects(Runnable action, String why) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, why); return; }
        throw new AssertionError(why);
    }
    static KillerCardInfo info(float hp, float max, int level, int kills, String name, String weapon) {
        return new KillerCardInfo(UUID.fromString("12345678-1234-5678-1234-567812345678"), name,
            level, hp, max, kills, 2, weapon, "pack:gun", "pack:skin", true);
    }
    public static void main(String[] args) {
        for (boolean head : new boolean[]{false, true}) for (int level : new int[]{0, 48, 100})
            for (float hp : new float[]{0, 14.2F, 20}) {
                var value = new KillerCardInfo(UUID.randomUUID(), "玩家😀".repeat(24), level,
                    hp, 20, Integer.MAX_VALUE, 2, "超长武器名称".repeat(40), "pack:gun", "pack:skin", head);
                var expected = new KillerCardPacket(Long.MAX_VALUE, Integer.MAX_VALUE, "minecraft:overworld", value);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    KillerCardPacket.encode(expected, buffer);
                    check(expected.equals(KillerCardPacket.decode(buffer)), "All card fields roundtrip, including Unicode/headshot/display-ID");
                    check(buffer.readableBytes() == 0, "Card codec consumes all fields");
                } finally { buffer.release(); }
            }
        for (boolean enabled : new boolean[]{false, true}) for (boolean confirmed : new boolean[]{false, true})
            for (int ticks : new int[]{0, 1, 200, Integer.MAX_VALUE}) {
                var expected = new DownedDeployPacket(Long.MAX_VALUE, 17, "minecraft:the_nether", enabled, ticks, 600, confirmed);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    DownedDeployPacket.encode(expected, buffer);
                    check(expected.equals(DownedDeployPacket.decode(buffer)), "Independent deployment/bleedout/confirmed-death fields roundtrip");
                    check(buffer.readableBytes() == 0, "Deployment codec consumes all fields");
                } finally { buffer.release(); }
            }
        rejects(() -> info(Float.NaN, 20, 1, 0, "P", "Gun"), "NaN health");
        rejects(() -> info(1, Float.POSITIVE_INFINITY, 1, 0, "P", "Gun"), "Infinite maximum");
        rejects(() -> info(1, 0, 1, 0, "P", "Gun"), "Zero maximum");
        rejects(() -> info(-1, 20, 1, 0, "P", "Gun"), "Negative health");
        rejects(() -> info(21, 20, 1, 0, "P", "Gun"), "Health above maximum");
        rejects(() -> info(1, 20, 101, 0, "P", "Gun"), "Level above cap");
        rejects(() -> info(1, 20, -1, 0, "P", "Gun"), "Negative level");
        rejects(() -> info(1, 20, 1, -1, "P", "Gun"), "Negative duel count");
        rejects(() -> info(1, 20, 1, 0, " ", "Gun"), "Blank player name");
        rejects(() -> info(1, 20, 1, 0, "x".repeat(129), "Gun"), "Oversized player name");
        rejects(() -> info(1, 20, 1, 0, "P", "x".repeat(257)), "Oversized weapon name");
        var value = info(1, 20, 1, 0, "P", "Gun");
        rejects(() -> new KillerCardPacket(0, 1, "minecraft:overworld", value), "Invalid card death ID");
        rejects(() -> new KillerCardPacket(1, -1, "minecraft:overworld", value), "Invalid card entity ID");
        rejects(() -> new KillerCardPacket(1, 1, null, value), "Null card dimension");
        rejects(() -> new KillerCardPacket(1, 1, "x".repeat(257), value), "Oversized card dimension");
        rejects(() -> new KillerCardPacket(1, 1, "minecraft:overworld", null), "Null metadata");
        rejects(() -> new DownedDeployPacket(0, 1, "minecraft:overworld", true, 0, 0), "Invalid deployment death ID");
        rejects(() -> new DownedDeployPacket(1, -1, "minecraft:overworld", true, 0, 0), "Invalid deployment entity ID");
        rejects(() -> new DownedDeployPacket(1, 1, null, true, 0, 0), "Null deployment dimension");
        rejects(() -> new DownedDeployPacket(1, 1, "x".repeat(257), true, 0, 0), "Oversized deployment dimension");
        rejects(() -> new DownedDeployPacket(1, 1, "minecraft:overworld", true, -1, 0), "Negative deployment time");
        rejects(() -> new DownedDeployPacket(1, 1, "minecraft:overworld", true, 0, -1), "Negative bleedout time");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeLong(1); buffer.m_130130_(1); buffer.m_130070_("minecraft:overworld");
            buffer.writeBoolean(true); buffer.m_130130_(-1); buffer.m_130130_(0); buffer.writeBoolean(false);
            rejects(() -> DownedDeployPacket.decode(buffer), "Malformed wire timer rejected by actual decode");
        } finally { buffer.release(); }
        System.out.println("PASS real killer-card/deployment packet codecs: " + checks);
    }
}
