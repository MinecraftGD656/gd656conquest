package org.mods.gd656conquest.client.loadout;

import java.util.Objects;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656killicon.client.gui.scoreboard.ConquestMatchDecoder;
import org.mods.gd656killicon.client.gui.scoreboard.ScoreboardMatchSource;
import static org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Match;

/** The author's synchronized HUD state, read directly through its compiled client API. */
public final class ConquestMatchProvider implements ScoreboardMatchSource.Provider {
    private ConquestMatchDecoder.Raw previous;
    private String previousTeam;
    private Object previousRooms;
    private Match cached=Match.unknown();
    @Override public Match snapshot(String leftTeam){
        if(!org.mods.gd656conquest.client.room.ClientRoomViewScope.current()){previous=null;previousRooms=null;return Match.unknown();}
        var rooms=org.mods.gd656conquest.client.loadout.state.ClientRoomSelectionState.snapshot();
        var raw=new ConquestMatchDecoder.Raw(ClientHudDebugState.getRoomId(),ClientHudDebugState.getRoomState(),ClientHudDebugState.getSelfTeam(),ClientHudDebugState.getScoreMethod(),
            ClientHudDebugState.getCampAScore(),ClientHudDebugState.getCampBScore(),ClientHudDebugState.getCampAGoal(),ClientHudDebugState.getCampBGoal(),
            ClientHudDebugState.getRunningSeconds(),ClientHudDebugState.getRemainSeconds(),ClientHudDebugState.getCaptureIcons(),ClientHudDebugState.getExtraPayload());
        if(!raw.equals(previous)||!Objects.equals(leftTeam,previousTeam)||rooms!=previousRooms){
            cached=ConquestMatchDecoder.decode(raw,leftTeam);
            if(rooms!=null)for(var room:rooms.entries())if(room.roomId().equals(ClientHudDebugState.getRoomId())){
                String name=room.mapName();if(name==null||name.isBlank())name=room.displayName();
                if(name!=null&&!name.isBlank())cached=new Match(cached.mode(),name,cached.time(),cached.leftGauge(),cached.rightGauge(),cached.objectives(),cached.objectivesKnown(),cached.leftIsCampB());break;
            }
            previous=raw;previousTeam=leftTeam;previousRooms=rooms;
        }
        return cached;
    }
}
