package tests;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.network.packet.DeathCausePacket;

public final class DeathCausePacketCheck {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private static void rejects(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, label); return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) {
        for (DeathCause cause : DeathCause.values()) {
            var expected = new DeathCausePacket(Long.MAX_VALUE, 123456789, "minecraft:overworld", cause);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DeathCausePacket.encode(expected, buffer);
                check(expected.equals(DeathCausePacket.decode(buffer)), "Full death ID / target / dimension / cause roundtrip");
                check(buffer.readableBytes() == 0, "Full cause packet consumed");
                buffer.readerIndex(0);
                buffer.setByte(buffer.writerIndex() - 1, 255);
                check(DeathCausePacket.decode(buffer).cause() == DeathCause.KILLED_IN_ACTION, "Unknown wire cause fallback");
            } finally { buffer.release(); }
        }
        check(new DeathCausePacket(1, 1, "minecraft:overworld", null).cause() == DeathCause.KILLED_IN_ACTION, "Null cause fallback");
        rejects(() -> new DeathCausePacket(0, 1, "minecraft:overworld", DeathCause.FALL), "Nonpositive death ID rejected");
        rejects(() -> new DeathCausePacket(1, -1, "minecraft:overworld", DeathCause.FALL), "Negative entity ID rejected");
        rejects(() -> new DeathCausePacket(1, 1, null, DeathCause.FALL), "Null dimension rejected");
        rejects(() -> new DeathCausePacket(1, 1, "x".repeat(257), DeathCause.FALL), "Oversized dimension rejected");
        System.out.println("PASS real death-cause packet codec: " + checks);
    }
}
