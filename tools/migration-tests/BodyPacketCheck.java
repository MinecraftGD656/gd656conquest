package tests;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.network.packet.*;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSubscriptionPolicy;

/** Runs the current reobfuscated GD packet codecs against the matching Forge SRG dependency. */
public final class BodyPacketCheck {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static <T> T roundTrip(T packet, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode) {
        FriendlyByteBuf first = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf second = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode.accept(packet, first);
            byte[] original = new byte[first.readableBytes()];
            first.getBytes(0, original);
            T decoded = decode.apply(first);
            check(first.readableBytes() == 0, "Full packet consumed");
            encode.accept(decoded, second);
            byte[] repeated = new byte[second.readableBytes()];
            second.readBytes(repeated);
            check(Arrays.equals(original, repeated), "Payload preserved through codec");
            return decoded;
        } finally {
            first.release();
            second.release();
        }
    }
    private static void rejects(Runnable action, String label) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, label); return; }
        throw new AssertionError(label);
    }
    public static void main(String[] args) {
        UUID victim = new UUID(1, 2), owner = new UUID(3, 4);
        Vec3 anchor = new Vec3(10, 64, -20);
        long generation = Long.MAX_VALUE - 1;
        roundTrip(new DownedRagdollStatePacket(victim, 42, generation, "minecraft:overworld", true,
            anchor, new byte[]{1, 2, 3}, owner), DownedRagdollStatePacket::encode, DownedRagdollStatePacket::decode);
        roundTrip(new DownedRagdollStatePacket(victim, 42, generation, "minecraft:overworld", false, anchor),
            DownedRagdollStatePacket::encode, DownedRagdollStatePacket::decode);
        var entry = new RagdollCatalogPacket.Entry(42, generation, generation, false, true, true,
            anchor, new AABB(9, 63, -21, 11, 65, -19));
        var catalog = roundTrip(new RagdollCatalogPacket("minecraft:overworld", 100, List.of(entry)),
            RagdollCatalogPacket::encode, RagdollCatalogPacket::decode);
        check(catalog.entries().get(0).generation() == generation, "Catalog preserves full 64-bit generation");
        for (var observation : List.of(new RagdollObservationPacket(42, generation, generation, true, false),
            new RagdollObservationPacket(42, generation, generation, false, false),
            new RagdollObservationPacket(42, generation, generation, false, true))) {
            check(observation.equals(roundTrip(observation, RagdollObservationPacket::encode, RagdollObservationPacket::decode)),
                "Start, reversible pause and terminal removal stay distinct");
        }
        var subscription = new RagdollSubscribePacket(List.of(
            new RagdollSubscribePacket.Entry(42, generation, true, true),
            new RagdollSubscribePacket.Entry(43, generation - 1, false, false)));
        check(subscription.equals(roundTrip(subscription, RagdollSubscribePacket::encode, RagdollSubscribePacket::decode)),
            "Subscription token, refresh and unsubscribe survive codec");
        rejects(() -> new DownedRagdollStatePacket(victim, 42, generation, "minecraft:overworld", true,
            anchor, new byte[DownedRagdollStatePacket.MAX_SPAWN_BYTES + 1], owner), "Oversized spawn rejected");
        rejects(() -> new RagdollSubscribePacket(java.util.Collections.nCopies(RagdollSubscriptionPolicy.MAX_REQUEST_ENTRIES + 1,
            subscription.entries().get(0))), "Oversized subscription rejected");
        FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RagdollCatalogPacket.encode(new RagdollCatalogPacket("minecraft:overworld", 100, List.of()), malformed);
            malformed.writerIndex(malformed.writerIndex() - 1); // replace the empty count with an oversized VarInt
            int count = RagdollSubscriptionPolicy.MAX_DIRECTORY_ENTRIES + 1;
            while ((count & ~0x7f) != 0) {
                malformed.writeByte((count & 0x7f) | 0x80);
                count >>>= 7;
            }
            malformed.writeByte(count);
            rejects(() -> RagdollCatalogPacket.decode(malformed), "Oversized wire directory rejected before allocation");
        } finally { malformed.release(); }
        System.out.println("PASS GD body / catalog / observation / subscription packet codecs: " + checks);
    }
}
