package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ServerboundBoundaryHeightStatePacket;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;

public final class ClientBoundaryHeightReportService {
    private static final long REPORT_INTERVAL_TICKS = 5L;
    private static long lastReportTick = Long.MIN_VALUE;
    private static boolean lastReportedPlayerMaxYExceeded;
    private static boolean lastReportedAircraftPassengerMaxYExceeded;
    private static boolean lastKnownInRoom;

    private ClientBoundaryHeightReportService() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        LocalPlayer player = minecraft.player;
        boolean inRoom = isInRoom();
        Entity vehicle = player.getVehicle();
        boolean aircraftVehicle = VehicleCompatFacade.isAircraftEntity(vehicle);
        int playerMaxY = ClientHudDebugState.getPlayerMaxY();
        int aircraftPassengerMaxY = ClientHudDebugState.getAircraftPassengerMaxY();
        boolean abovePlayerMaxY = inRoom && !aircraftVehicle && playerMaxY > 0 && player.getY() > playerMaxY;
        boolean aboveAircraftPassengerMaxY = inRoom && aircraftVehicle && aircraftPassengerMaxY > 0
            && Math.max(player.getY(), vehicle == null ? player.getY() : vehicle.getY()) > aircraftPassengerMaxY;
        long gameTick = minecraft.level.getGameTime();
        boolean stateChanged = abovePlayerMaxY != lastReportedPlayerMaxYExceeded
            || aboveAircraftPassengerMaxY != lastReportedAircraftPassengerMaxYExceeded
            || inRoom != lastKnownInRoom;
        if (!stateChanged && gameTick - lastReportTick < REPORT_INTERVAL_TICKS) {
            return;
        }
        NetworkManager.getChannel().sendToServer(new ServerboundBoundaryHeightStatePacket(
            abovePlayerMaxY,
            aboveAircraftPassengerMaxY
        ));
        lastReportTick = gameTick;
        lastReportedPlayerMaxYExceeded = abovePlayerMaxY;
        lastReportedAircraftPassengerMaxYExceeded = aboveAircraftPassengerMaxY;
        lastKnownInRoom = inRoom;
    }

    public static void clear() {
        lastReportTick = Long.MIN_VALUE;
        lastReportedPlayerMaxYExceeded = false;
        lastReportedAircraftPassengerMaxYExceeded = false;
        lastKnownInRoom = false;
    }

    private static boolean isInRoom() {
        String roomId = ClientHudDebugState.getRoomId();
        String roomState = ClientHudDebugState.getRoomState();
        return roomId != null
            && !roomId.isBlank()
            && !"-".equals(roomId)
            && (roomState == null || !roomState.contains("未加入"));
    }
}
