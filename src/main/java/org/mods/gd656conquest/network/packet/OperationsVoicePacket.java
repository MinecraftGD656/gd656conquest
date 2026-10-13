package org.mods.gd656conquest.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.common.audio.OperationsVoiceContext;
import org.mods.gd656conquest.common.audio.OperationsVoiceCue;

/** Empty soundId updates validity only. Follow-up delay is measured after actual client sound completion. */
public record OperationsVoicePacket(OperationsVoiceContext context, String soundId, String followup, int gapTicks) {
    public static OperationsVoicePacket state(OperationsVoiceContext context) { return new OperationsVoicePacket(context, "", "", 0); }
    public static OperationsVoicePacket cue(OperationsVoiceContext context, OperationsVoiceCue cue, boolean chain) {
        return new OperationsVoicePacket(context, cue.soundId(), chain ? OperationsVoiceCue.CLEANUP_INITIATE_ATTACKERS.soundId() : "", chain ? 20 : 0);
    }
    public static void encode(OperationsVoicePacket packet, FriendlyByteBuf b) {
        var c = packet.context;
        b.writeUtf(c.roomId(),128); b.writeLong(c.matchId()); b.writeInt(c.battalion()); b.writeInt(c.sector());
        b.writeBoolean(c.running()); b.writeBoolean(c.retreatActive()); b.writeBoolean(c.attacker()); b.writeBoolean(c.pendingDefender());
        b.writeUtf(packet.soundId,128); b.writeUtf(packet.followup,128); b.writeInt(packet.gapTicks);
    }
    public static OperationsVoicePacket decode(FriendlyByteBuf b) {
        return new OperationsVoicePacket(new OperationsVoiceContext(b.readUtf(128),b.readLong(),b.readInt(),b.readInt(),
            b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean()),b.readUtf(128),b.readUtf(128),b.readInt());
    }
    public static void handle(OperationsVoicePacket packet, Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.mods.gd656conquest.client.audio.ClientAudioManager.getInstance().enqueueOperationVoice(packet)));
        c.setPacketHandled(true);
    }
}
