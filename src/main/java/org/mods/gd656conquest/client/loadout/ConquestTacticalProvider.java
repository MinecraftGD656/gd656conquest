package org.mods.gd656conquest.client.loadout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.mods.gd656conquest.client.event.handler.ClientCaptureObjectiveWaypointSource;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.room.ClientRoomTeamMirror;
import org.mods.gd656conquest.client.room.ClientRoomViewScope;
import org.mods.gd656killicon.client.gui.theme.BattlefieldMinimapSource;
import org.mods.gd656killicon.client.gui.theme.compat.SquadRosterAccess;

/** Public, typed read path for the existing server room/team/objective packets. */
public final class ConquestTacticalProvider {
    private ConquestTacticalProvider() {}
    public static List<BattlefieldMinimapSource.Objective> objectives() {
        if (!ClientRoomViewScope.current()) return List.of();
        return ClientCaptureObjectiveWaypointSource.snapshotObjectives().stream()
            .map(v -> new BattlefieldMinimapSource.Objective(v.code(),v.x(),v.z())).toList();
    }
    /** Room markers (respawn beacon, supplies, spotted enemies) shown on the theater minimap. */
    public static List<BattlefieldMinimapSource.Marker> markers() {
        if (!ClientRoomViewScope.current()) return List.of();
        var result = new ArrayList<BattlefieldMinimapSource.Marker>();
        for (var mark : org.mods.gd656conquest.client.marker.ClientRoomMinimapMarkerSource.snapshotMarkers()) {
            if (mark == null) continue;
            result.add(new BattlefieldMinimapSource.Marker(mark.iconKind().name(), mark.x(), mark.z(),
                mark.label() == null ? "" : mark.label(), markerColor(mark.iconKind())));
        }
        return List.copyOf(result);
    }
    private static int markerColor(org.mods.gd656conquest.client.marker.ClientRoomMinimapMarkerSnapshot.MarkerIconKind kind) {
        return switch (kind) {
            case RESPAWN_BEACON -> 0xff54e86a;
            case SUPPLY_BOX, SUPPLY_BAG -> 0xff59c8ff;
            case ENEMY_GROUND, ENEMY_TRACKED -> 0xffff6a4c;
            case ALLY, ALLY_TEAM, ALLY_SQUAD -> 0xff71ecfc;
            case OBSERVE_GROUND -> 0xffffd166;
            case REQUEST_STRIKE -> 0xffff9a3c;
            default -> 0xffd0d7d2;
        };
    }
    public static SquadRosterAccess.Snapshot squad(Minecraft mc) {
        if (mc.player==null || mc.level==null || mc.getConnection()==null || !ClientRoomViewScope.current()) return SquadRosterAccess.Snapshot.empty();
        var roster=ClientRoomTeamMirror.squadSnapshot(mc.player.getScoreboardName());
        if (!roster.roomId().equalsIgnoreCase(ClientHudDebugState.getRoomId())) return SquadRosterAccess.Snapshot.empty();
        var players=new HashMap<String,PlayerInfo>();
        for(var info:mc.getConnection().getOnlinePlayers()) players.put(info.getProfile().getName().toLowerCase(Locale.ROOT),info);
        var members=new ArrayList<SquadRosterAccess.Member>();
        for(var member:roster.members()) {
            var info=players.get(member.playerName());
            var id=info==null?null:info.getProfile().getId();
            var entity=id==null?null:mc.level.getPlayerByUUID(id);
            members.add(new SquadRosterAccess.Member(info==null?member.playerName():info.getProfile().getName(),id,member.classKey(),
                entity==null?null:entity.getHealth(),entity==null?null:entity.getMaxHealth(),entity==null?null:entity.isAlive()));
        }
        return new SquadRosterAccess.Snapshot(roster.squadLabel(),members);
    }
}
