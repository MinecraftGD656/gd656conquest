package org.mods.gd656conquest.network.packet;

import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import org.mods.gd656conquest.common.match.BriefingTimeline;

public record BriefingPlaybackPacket(String roomId, long matchId, long sequenceId, String scene,
    int briefingSeconds, int elapsedTicks, boolean active, BriefingTimeline.Track track) {
    private static final Gson GSON = new Gson();
    public BriefingPlaybackPacket {
        if (roomId == null || roomId.length() > 128 || !BriefingTimeline.validKey(scene) || briefingSeconds < 8 || briefingSeconds > 600
            || elapsedTicks < 0 || elapsedTicks > BriefingTimeline.length(scene, briefingSeconds)) throw new IllegalArgumentException("Invalid playback snapshot");
        track = track == null ? BriefingTimeline.Track.empty() : track;
        BriefingTimeline.validateTrack(track, scene, briefingSeconds);
    }
    public static void encode(BriefingPlaybackPacket p, FriendlyByteBuf b) {
        b.writeUtf(p.roomId,128); b.writeLong(p.matchId); b.writeLong(p.sequenceId); b.writeUtf(p.scene,32);
        b.writeInt(p.briefingSeconds); b.writeInt(p.elapsedTicks); b.writeBoolean(p.active); b.writeUtf(GSON.toJson(p.track),262144);
    }
    public static BriefingPlaybackPacket decode(FriendlyByteBuf b) {
        return new BriefingPlaybackPacket(b.readUtf(128),b.readLong(),b.readLong(),b.readUtf(32),b.readInt(),b.readInt(),b.readBoolean(),GSON.fromJson(b.readUtf(262144),BriefingTimeline.Track.class));
    }
    public static void handle(BriefingPlaybackPacket p, Supplier<NetworkEvent.Context> supplier) {
        var c=supplier.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.mods.gd656conquest.client.room.ClientBriefingPlayback.accept(p))); c.setPacketHandled(true);
    }
}
