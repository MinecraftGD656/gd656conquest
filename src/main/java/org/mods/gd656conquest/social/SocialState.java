package org.mods.gd656conquest.social;

import java.util.List;
import java.util.UUID;

/** Immutable server-owned state shared by the three social views. Actions use UUIDs, labels use profiles. */
public final class SocialState {
    private SocialState() {}
    public record Member(UUID id,String accountName,String displayName,String classKey,boolean online,
                         boolean leader,String room,String status,boolean deployable,String deployReason,
                         String team,boolean canSquadInvite,String squadInviteReason) {
        public Member(UUID id,String accountName,String displayName,String classKey,boolean online,boolean leader,
                      String room,String status,boolean deployable,String deployReason){
            this(id,accountName,displayName,classKey,online,leader,room,status,deployable,deployReason,"",false,"");
        }
    }
    public record Party(String id,UUID leaderId,boolean open,String room,List<Member> members) {}
    public record PartyEntry(String id,String leaderName,int size,boolean open,boolean canJoin,String reason) {}
    public record Invitation(String id,String partyId,String fromName,String type,long remainingSeconds) {}
    public record FriendRequest(String id,UUID playerId,String accountName,String displayName,boolean outgoing) {}
    public record SquadInvitation(String id,UUID fromId,String fromName,String roomId,String squadLabel,long remainingSeconds) {}
    public record Squad(String label,UUID leaderId,boolean locked,boolean mine,boolean canJoin,String reason,List<Member> members) {}
    public record Snapshot(long requestId,boolean actionResult,UUID self,String error,String notice,
                           String currentRoom,String currentTeam,String currentSquad,Party party,
                           List<PartyEntry> parties,List<Invitation> invitations,List<Member> onlinePlayers,
                           List<Squad> squads,List<String> actions,List<Member> friends,
                           List<FriendRequest> friendRequests,List<SquadInvitation> squadInvitations) {
        public Snapshot(long requestId,boolean actionResult,UUID self,String error,String notice,String currentRoom,
                        String currentTeam,String currentSquad,Party party,List<PartyEntry> parties,
                        List<Invitation> invitations,List<Member> onlinePlayers,List<Squad> squads,List<String> actions){
            this(requestId,actionResult,self,error,notice,currentRoom,currentTeam,currentSquad,party,parties,
                    invitations,onlinePlayers,squads,actions,List.of(),List.of(),List.of());
        }
    }
    public record Action(long requestId,String kind,String target,String value) {}
}
