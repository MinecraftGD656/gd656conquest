package org.mods.gd656conquest.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.match.BattalionSequence.Phase;
import org.mods.gd656conquest.common.match.BattalionSequence;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomViewPosition;

public record BattalionBriefingPacket(String roomId, long matchId, long sequenceId, Phase phase,
    int durationTicks, int elapsedTicks, int battalion, int totalBattalions, int capturedSectors,
    boolean selfAttacker, boolean attackersWon, RoomViewPosition view, List<Sector> sectors) {

    public record Sector(int index, List<RoomPosition> points) {
        public static final int ATTACKER_BASE_INDEX = -1;
        public static final int DEFENDER_BASE_INDEX = 0;

        public static Sector attackerBase(List<RoomPosition> points) { return new Sector(ATTACKER_BASE_INDEX, points); }
        public static Sector defenderBase(List<RoomPosition> points) { return new Sector(DEFENDER_BASE_INDEX, points); }

        public float attackerControl(Phase phase, int capturedSectors, double elapsedTicks) {
            if (index == ATTACKER_BASE_INDEX) return 1;
            if (index == DEFENDER_BASE_INDEX) return 0;
            // Fade the completed snapshot; do not replay its ownership animation.
            double animationTicks = phase == Phase.BRIEFING_FADE_OUT ? BattalionSequence.MIN_BRIEFING_SECONDS * 20 : elapsedTicks;
            return BattalionSequence.capturedBlend(index, capturedSectors, animationTicks);
        }
    }

    public static void encode(BattalionBriefingPacket p, FriendlyByteBuf b) {
        b.writeUtf(p.roomId); b.writeLong(p.matchId); b.writeLong(p.sequenceId); b.writeEnum(p.phase);
        b.writeInt(p.durationTicks); b.writeInt(p.elapsedTicks); b.writeInt(p.battalion);
        b.writeInt(p.totalBattalions); b.writeInt(p.capturedSectors);
        b.writeBoolean(p.selfAttacker); b.writeBoolean(p.attackersWon);
        b.writeBoolean(p.view != null);
        if (p.view != null) {
            b.writeInt(p.view.x()); b.writeDouble(p.view.y()); b.writeInt(p.view.z());
            b.writeFloat(p.view.yaw()); b.writeFloat(p.view.pitch());
        }
        b.writeVarInt(p.sectors.size());
        for (Sector s : p.sectors) {
            b.writeInt(s.index); b.writeVarInt(s.points.size());
            for (RoomPosition point : s.points) {
                b.writeInt(point.x()); b.writeDouble(point.y()); b.writeInt(point.z());
            }
        }
    }

    public static BattalionBriefingPacket decode(FriendlyByteBuf b) {
        String room = b.readUtf(); long match = b.readLong(); long seq = b.readLong();
        Phase phase = b.readEnum(Phase.class);
        int duration = b.readInt(), elapsed = b.readInt(), battalion = b.readInt(), total = b.readInt(), captured = b.readInt();
        boolean self = b.readBoolean(), won = b.readBoolean();
        RoomViewPosition view = b.readBoolean() ? new RoomViewPosition(b.readInt(), b.readDouble(), b.readInt(), b.readFloat(), b.readFloat()) : null;
        int count = b.readVarInt();
        if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid briefing sector count");
        List<Sector> sectors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = b.readInt(), size = b.readVarInt();
            if (index < Sector.ATTACKER_BASE_INDEX || index > 6) throw new IllegalArgumentException("Invalid briefing sector index");
            if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid briefing polygon size");
            List<RoomPosition> points = new ArrayList<>();
            for (int j = 0; j < size; j++) points.add(new RoomPosition(b.readInt(), b.readDouble(), b.readInt()));
            sectors.add(new Sector(index, List.copyOf(points)));
        }
        return new BattalionBriefingPacket(room, match, seq, phase, duration, elapsed, battalion, total, captured, self, won, view, List.copyOf(sectors));
    }

    public static void handle(BattalionBriefingPacket p, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.mods.gd656conquest.client.room.ClientBattalionBriefingState.accept(p)));
        context.setPacketHandled(true);
    }
}
