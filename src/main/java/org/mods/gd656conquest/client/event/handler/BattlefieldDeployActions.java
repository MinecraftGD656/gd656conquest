package org.mods.gd656conquest.client.event.handler;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;
import org.mods.gd656conquest.client.event.handler.ClientCaptureOverlayModel.DeployTargetAction;

/** Every map click, key and deployment button resolves through the same original packet factories. */
final class BattlefieldDeployActions {
    private BattlefieldDeployActions() {}
    static RoomDeploySelectPacket packet(DeployTargetAction action,boolean confirm){
        if(action==null||action.targetId()==null||action.targetId().isBlank())return null;
        return switch(action.type()){
            case TEAMMATE->confirm?RoomDeploySelectPacket.confirmTeammate(action.targetId()):RoomDeploySelectPacket.selectTeammate(action.targetId());
            case BEACON->confirm?RoomDeploySelectPacket.confirmBeacon(action.targetId()):RoomDeploySelectPacket.selectBeacon(action.targetId());
            case TEAM->confirm?RoomDeploySelectPacket.confirmTeam(action.targetId()):RoomDeploySelectPacket.selectTeam(action.targetId());
            case CAPTURE->confirm?RoomDeploySelectPacket.confirmCapture(action.targetId()):RoomDeploySelectPacket.selectCapture(action.targetId());
            case VEHICLE->confirm?RoomDeploySelectPacket.confirmVehicle(action.targetId()):RoomDeploySelectPacket.selectVehicle(action.targetId());
        };
    }
    static void send(DeployTargetAction action,boolean confirm){var packet=packet(action,confirm);if(packet!=null){NetworkManager.getChannel().sendToServer(packet);if(confirm)org.mods.gd656killicon.client.sounds.BattlefieldAudio.play(org.mods.gd656killicon.client.sounds.BattlefieldAudio.Event.DEPLOY_CONFIRM);}}
}
