package org.mods.gd656conquest.social;

import com.google.gson.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import static org.mods.gd656conquest.social.SocialState.*;

/** Validates unknown server state once, at decoding, before the UI sees immutable records. */
public final class SocialCodec {
    private static final Gson JSON=new Gson();
    private static final int MAX_TEXT=262144;
    private static final Set<String> ACTIONS=Set.of("PARTY_CREATE","PARTY_INVITE","PARTY_ACCEPT","PARTY_REFUSE","PARTY_JOIN","PARTY_PROMOTE","PARTY_KICK","PARTY_PRIVACY","PARTY_LEAVE","PARTY_DISBAND","PARTY_RETURN_HUB","SQUAD_CREATE","SQUAD_JOIN","SQUAD_LEAVE","SQUAD_LOCK","SQUAD_PROMOTE","SQUAD_KICK","FRIEND_REQUEST","FRIEND_ACCEPT","FRIEND_REFUSE","FRIEND_CANCEL","FRIEND_REMOVE","SQUAD_INVITE","SQUAD_ACCEPT","SQUAD_REFUSE");
    private SocialCodec() {}
    public static void write(Snapshot value,FriendlyByteBuf buffer){buffer.writeUtf(JSON.toJson(value),MAX_TEXT);}
    public static Snapshot read(FriendlyByteBuf buffer){return decode(buffer.readUtf(MAX_TEXT));}
    public static Snapshot decode(String raw){
        if(raw==null||raw.length()>MAX_TEXT)throw invalid("payload size");
        JsonElement tree=JsonParser.parseString(raw);if(!tree.isJsonObject())throw invalid("root");
        JsonObject object=tree.getAsJsonObject();
        for(String key:List.of("requestId","actionResult","self","error","notice","currentRoom","currentTeam","currentSquad","parties","invitations","onlinePlayers","squads","actions","friends","friendRequests","squadInvitations"))if(!object.has(key)||object.get(key).isJsonNull())throw invalid(key);
        Snapshot v=JSON.fromJson(object,Snapshot.class);if(v.self()==null||v.requestId()<0||v.actionResult()&&v.requestId()==0)throw invalid("identity/acknowledgement");
        text(v.error(),256);text(v.notice(),256);text(v.currentRoom(),64);text(v.currentTeam(),128);text(v.currentSquad(),32);
        List<PartyEntry> parties=list(v.parties(),256);Set<String> partyIds=new HashSet<>();
        for(var p:parties){if(p==null||!partyIds.add(p.id()))throw invalid("party entry");text(p.id(),64);text(p.leaderName(),64);text(p.reason(),256);if(p.size()<1||p.size()>4)throw invalid("party size");}
        List<Invitation> invites=list(v.invitations(),256);Set<String> inviteIds=new HashSet<>();
        for(var i:invites){if(i==null||!inviteIds.add(i.id()))throw invalid("invitation");text(i.id(),64);text(i.partyId(),64);text(i.fromName(),64);if(i.type()==null||!Set.of("INVITE","JOIN_APPLICATION").contains(i.type())||i.remainingSeconds()<0||i.remainingSeconds()>30)throw invalid("invitation state");}
        List<Member> candidates=members(v.onlinePlayers(),256,null);List<Squad> squads=list(v.squads(),64);List<Squad> copied=new ArrayList<>();Set<String> labels=new HashSet<>();int mine=0;
        for(var s:squads){
            if(s==null||!labels.add(s.label()))throw invalid("squad");text(s.label(),32);text(s.reason(),256);var members=members(s.members(),4,s.leaderId());
            if(s.mine()){mine++;if(!s.label().equals(v.currentSquad()))throw invalid("current squad");}
            copied.add(new Squad(s.label(),s.leaderId(),s.locked(),s.mine(),s.canJoin(),s.reason(),members));
        }
        if(mine>1)throw invalid("current squad count");
        Party party=v.party();if(party!=null){text(party.id(),64);text(party.room(),64);var members=members(party.members(),4,party.leaderId());if(members.stream().noneMatch(m->m.id().equals(v.self())))throw invalid("party self");party=new Party(party.id(),party.leaderId(),party.open(),party.room(),members);}
        List<String> actions=list(v.actions(),32);if(new HashSet<>(actions).size()!=actions.size()||!ACTIONS.containsAll(actions))throw invalid("actions");
        var friends=members(v.friends(),256,null);if(friends.stream().anyMatch(m->m.id().equals(v.self())))throw invalid("friend self");
        var requests=list(v.friendRequests(),32);Set<String> requestIds=new HashSet<>();Set<UUID> requestPlayers=new HashSet<>();
        for(var r:requests){if(r==null||r.playerId()==null||r.playerId().equals(v.self())||!requestIds.add(r.id())||!requestPlayers.add(r.playerId()))throw invalid("friend request");identifier(r.id());text(r.accountName(),64);text(r.displayName(),64);}
        var squadInvitations=list(v.squadInvitations(),16);Set<String> squadInviteIds=new HashSet<>();
        for(var i:squadInvitations){if(i==null||i.fromId()==null||i.fromId().equals(v.self())||!squadInviteIds.add(i.id()))throw invalid("squad invitation");identifier(i.id());text(i.fromName(),64);text(i.roomId(),64);text(i.squadLabel(),32);if(i.remainingSeconds()<0||i.remainingSeconds()>30)throw invalid("squad invitation expiry");}
        return new Snapshot(v.requestId(),v.actionResult(),v.self(),v.error(),v.notice(),v.currentRoom(),v.currentTeam(),v.currentSquad(),party,parties,invites,candidates,List.copyOf(copied),actions,friends,requests,squadInvitations);
    }
    private static List<Member> members(List<Member> values,int max,UUID leader){
        var result=list(values,max);Set<UUID> ids=new HashSet<>();int leaders=0;
        for(var m:result){if(m==null||m.id()==null||!ids.add(m.id()))throw invalid("member identity");text(m.accountName(),64);text(m.displayName(),64);text(m.classKey(),64);text(m.room(),64);text(m.status(),128);text(m.deployReason(),256);text(m.team(),16);text(m.squadInviteReason(),256);if(!Set.of("","CAMP_A","CAMP_B").contains(m.team()))throw invalid("member team");if(m.canSquadInvite()&&(!m.online()||m.room().isBlank()||m.team().isBlank()||!m.squadInviteReason().isEmpty()))throw invalid("squad invitation permission");if(m.leader())leaders++;if(leader!=null&&m.leader()!=m.id().equals(leader))throw invalid("member role");}
        if(leader!=null&&(result.isEmpty()||!ids.contains(leader)||leaders!=1))throw invalid("leader");
        if(max==4&&leader==null)throw invalid("missing leader");
        return result;
    }
    private static void text(String value,int max){if(value==null||value.length()>max)throw invalid("text limit");}
    private static void identifier(String value){if(value==null||!UUID.fromString(value).toString().equals(value))throw invalid("request identifier");}
    private static <T> List<T> list(List<T> value,int max){if(value==null||value.size()>max||value.contains(null))throw invalid("list limit");return List.copyOf(value);}
    private static IllegalArgumentException invalid(String field){return new IllegalArgumentException("Invalid social state: "+field);}
}
