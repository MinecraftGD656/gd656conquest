package org.mods.gd656conquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;

import java.util.Objects;
import java.util.function.Supplier;

public final class RoomHudSyncPacket {
    private final String roomId;
    private final String roomState;
    private final String audioState;
    private final String selfTeam;
    private final String selfClass;
    private final int campAScore;
    private final int campBScore;
    private final int runningSeconds;
    private final String scoreMethod;
    private final int campAGoal;
    private final int campBGoal;
    private final int remainSeconds;
    private final int playerMaxY;
    private final int aircraftPassengerMaxY;
    private final boolean allowPlayerCampSwitch;
    private final String captureStatus;
    private final String captureIcons;
    private final String captureEvent;
    private final String captureFocus;

    public RoomHudSyncPacket(String roomId, String roomState, String selfTeam, String selfClass, int campAScore, int campBScore, int runningSeconds, String scoreMethod, int campAGoal, int campBGoal, int remainSeconds, int playerMaxY, int aircraftPassengerMaxY, boolean allowPlayerCampSwitch, String captureStatus, String captureIcons, String captureEvent, String captureFocus) {
        this(roomId, roomState, "-", selfTeam, selfClass, campAScore, campBScore, runningSeconds, scoreMethod, campAGoal, campBGoal, remainSeconds, playerMaxY, aircraftPassengerMaxY, allowPlayerCampSwitch, captureStatus, captureIcons, captureEvent, captureFocus);
    }

    public RoomHudSyncPacket(String roomId, String roomState, String audioState, String selfTeam, String selfClass, int campAScore, int campBScore, int runningSeconds, String scoreMethod, int campAGoal, int campBGoal, int remainSeconds, int playerMaxY, int aircraftPassengerMaxY, boolean allowPlayerCampSwitch, String captureStatus, String captureIcons, String captureEvent, String captureFocus) {
        this.roomId = roomId;
        this.roomState = roomState;
        this.audioState = audioState;
        this.selfTeam = selfTeam;
        this.selfClass = selfClass;
        this.campAScore = campAScore;
        this.campBScore = campBScore;
        this.runningSeconds = runningSeconds;
        this.scoreMethod = scoreMethod;
        this.campAGoal = campAGoal;
        this.campBGoal = campBGoal;
        this.remainSeconds = remainSeconds;
        this.playerMaxY = playerMaxY;
        this.aircraftPassengerMaxY = aircraftPassengerMaxY;
        this.allowPlayerCampSwitch = allowPlayerCampSwitch;
        this.captureStatus = captureStatus;
        this.captureIcons = captureIcons;
        this.captureEvent = captureEvent;
        this.captureFocus = captureFocus;
    }

    public static void encode(RoomHudSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.roomId);
        buffer.writeUtf(message.roomState);
        buffer.writeUtf(message.audioState);
        buffer.writeUtf(message.selfTeam);
        buffer.writeUtf(message.selfClass);
        buffer.writeInt(message.campAScore);
        buffer.writeInt(message.campBScore);
        buffer.writeInt(message.runningSeconds);
        buffer.writeUtf(message.scoreMethod);
        buffer.writeInt(message.campAGoal);
        buffer.writeInt(message.campBGoal);
        buffer.writeInt(message.remainSeconds);
        buffer.writeInt(message.playerMaxY);
        buffer.writeInt(message.aircraftPassengerMaxY);
        buffer.writeBoolean(message.allowPlayerCampSwitch);
        buffer.writeUtf(message.captureStatus);
        buffer.writeUtf(message.captureIcons);
        buffer.writeUtf(message.captureEvent);
        buffer.writeUtf(message.captureFocus);
    }

    public static RoomHudSyncPacket decode(FriendlyByteBuf buffer) {
        return new RoomHudSyncPacket(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    public static void handle(RoomHudSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientHudDebugState.update(
                    message.roomId,
                    message.roomState,
                    message.audioState,
                    message.selfTeam,
                    message.selfClass,
                    message.campAScore,
                    message.campBScore,
                    message.runningSeconds,
                    message.scoreMethod,
                    message.campAGoal,
                    message.campBGoal,
                    message.remainSeconds,
                    message.playerMaxY,
                    message.aircraftPassengerMaxY,
                    message.allowPlayerCampSwitch,
                    message.captureStatus,
                    message.captureIcons,
                    message.captureEvent,
                    message.captureFocus
            );
        }));
        context.setPacketHandled(true);
    }

    public String cacheKey() {
        return String.join("\u001F",
            safe(roomId),
            safe(roomState),
            safe(audioState),
            safe(selfTeam),
            safe(selfClass),
            Integer.toString(campAScore),
            Integer.toString(campBScore),
            Integer.toString(runningSeconds),
            safe(scoreMethod),
            Integer.toString(campAGoal),
            Integer.toString(campBGoal),
            Integer.toString(remainSeconds),
            Integer.toString(playerMaxY),
            Integer.toString(aircraftPassengerMaxY),
            Boolean.toString(allowPlayerCampSwitch),
            safe(captureStatus),
            safe(captureIcons),
            safe(captureEvent),
            safe(captureFocus)
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RoomHudSyncPacket other)) {
            return false;
        }
        return campAScore == other.campAScore
            && campBScore == other.campBScore
            && runningSeconds == other.runningSeconds
            && campAGoal == other.campAGoal
            && campBGoal == other.campBGoal
            && remainSeconds == other.remainSeconds
            && playerMaxY == other.playerMaxY
            && aircraftPassengerMaxY == other.aircraftPassengerMaxY
            && allowPlayerCampSwitch == other.allowPlayerCampSwitch
            && Objects.equals(roomId, other.roomId)
            && Objects.equals(roomState, other.roomState)
            && Objects.equals(audioState, other.audioState)
            && Objects.equals(selfTeam, other.selfTeam)
            && Objects.equals(selfClass, other.selfClass)
            && Objects.equals(scoreMethod, other.scoreMethod)
            && Objects.equals(captureStatus, other.captureStatus)
            && Objects.equals(captureIcons, other.captureIcons)
            && Objects.equals(captureEvent, other.captureEvent)
            && Objects.equals(captureFocus, other.captureFocus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            roomId,
            roomState,
            audioState,
            selfTeam,
            selfClass,
            campAScore,
            campBScore,
            runningSeconds,
            scoreMethod,
            campAGoal,
            campBGoal,
            remainSeconds,
            playerMaxY,
            aircraftPassengerMaxY,
            captureStatus,
            captureIcons,
            captureEvent,
            captureFocus
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
