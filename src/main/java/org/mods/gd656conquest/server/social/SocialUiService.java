package org.mods.gd656conquest.server.social;

import java.util.*;
import java.io.IOException;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.social.*;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.loadout.application.CustomLoadoutService;
import org.mods.gd656conquest.server.room.core.RoomSquadService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomSessionTransitionService;
import org.mods.gd656conquest.server.social.party.application.*;
import org.mods.gd656conquest.server.social.party.domain.*;
import org.mods.gd656conquest.server.social.party.infra.InMemoryPartyRepository;
import org.mods.gd656conquest.server.social.party.presentation.*;
import org.mods.gd656killicon.profile.ProfileServer;

/** The network and three visual themes share these server-side permissions and effects. */
public final class SocialUiService {
    private SocialUiService() {}
    public static void query(ServerPlayer actor){SocialNetwork.reply(actor,snapshot(actor,0,false,"",""));}
    public static void action(ServerPlayer actor,SocialState.Action action){
        var repo=InMemoryPartyRepository.of(actor.server);Set<UUID> affected=new LinkedHashSet<>();affected.add(actor.getUUID());
        repo.findByPlayerId(actor.getUUID()).ifPresent(p->affected.addAll(p.memberIds()));
        try{
            if(action.requestId()<=0)throw new PartyError("无效的操作标识");
            String kind=action.kind(),target=action.target(),value=action.value();var ui=PartyUiFacade.of(actor.server);
            if(kind.startsWith("FRIEND_")){
                var store=SocialServerState.friends(actor.server);
                switch(kind){
                    case "FRIEND_REQUEST"->{var player=online(actor,target);store.request(SocialServerState.profile(actor),SocialServerState.profile(player));affected.add(player.getUUID());player.sendSystemMessage(Component.literal(ProfileServer.displayName(actor)+" 向你发送好友申请，请打开组队页面的好友申请列表处理。"));}
                    case "FRIEND_ACCEPT","FRIEND_REFUSE","FRIEND_CANCEL"->{var request=store.resolve(actor.getUUID(),target,kind);affected.add(request.from());affected.add(request.to());}
                    case "FRIEND_REMOVE"->{UUID id=uuid(target);store.remove(actor.getUUID(),id);affected.add(id);}
                    default->throw new PartyError("未知的好友操作");
                }
            }else if(kind.startsWith("SQUAD_")){
                var room=RoomSquadService.view(actor);if(room!=null)affected.addAll(room.players());
                var invites=SocialServerState.squads(actor.server);
                switch(kind){
                    case "SQUAD_INVITE"->{var invitation=invites.invite(actor,uuid(target));affected.add(invitation.to());actor.server.getPlayerList().getPlayer(invitation.to()).sendSystemMessage(Component.literal(ProfileServer.displayName(actor)+" 邀请你加入 "+invitation.label()+" 小队，请在 30 秒内打开组队页面的小队邀请列表处理。"));}
                    case "SQUAD_ACCEPT"->{var invitation=invites.accept(actor,target);affected.add(invitation.from());}
                    case "SQUAD_REFUSE"->{var invitation=invites.refuse(actor,target);affected.add(invitation.from());}
                    default->{RoomSquadService.action(actor,kind,target,value);invites.prune();}
                }
            }else switch(kind){
                case "PARTY_CREATE"->ui.createParty(actor);
                case "PARTY_LEAVE"->ui.leaveParty(actor);
                case "PARTY_DISBAND"->ui.disbandParty(actor);
                case "PARTY_PRIVACY"->{if(!value.equals("open")&&!value.equals("invite"))throw new PartyError("无效的队伍隐私设置");ui.setOpen(actor,value.equals("open"));}
                case "PARTY_INVITE"->{var player=online(actor,target);affected.add(player.getUUID());var request=ui.createInvite(actor,player.getScoreboardName());ui.getRequestView(request.requestId()).ifPresent(v->player.sendSystemMessage(PartyMessageFactory.buildInviteNotification(v,actor.server.getTickCount())));}
                case "PARTY_JOIN"->{
                    var party=repo.findByPartyId(target).orElseThrow(()->new PartyError("队伍已解散"));affected.addAll(party.memberIds());
                    if(party.open())ui.joinOpen(actor,target);else{var request=ui.createJoinApplication(actor,target);var leader=actor.server.getPlayerList().getPlayer(request.toPlayerId());if(leader!=null)ui.getRequestView(request.requestId()).ifPresent(v->leader.sendSystemMessage(PartyMessageFactory.buildJoinApplicationNotification(v,actor.server.getTickCount())));}
                }
                case "PARTY_ACCEPT"->{var request=ui.acceptRequest(actor,target);affected.add(request.fromPlayerId());affected.add(request.toPlayerId());}
                case "PARTY_REFUSE"->{var request=ui.refuseRequest(actor,target);affected.add(request.fromPlayerId());affected.add(request.toPlayerId());}
                case "PARTY_PROMOTE"->{var player=online(actor,target);ui.transferLeader(actor,player.getScoreboardName());}
                case "PARTY_KICK"->{UUID id=uuid(target);affected.add(id);ui.kickMember(actor,id);}
                case "PARTY_RETURN_HUB"->{
                    var party=repo.findByPlayerId(actor.getUUID()).orElseThrow(()->new PartyError("你当前不在好友队伍中"));party.requireLeader(actor.getUUID());
                    var result=RoomSessionTransitionService.of(actor.server).returnToHub(actor);if(!result.deniedMessage().isBlank())throw new PartyError(result.deniedMessage());
                }
                default->throw new PartyError("未知的组队操作");
            }
            repo.findByPlayerId(actor.getUUID()).ifPresent(p->affected.addAll(p.memberIds()));
            SocialNetwork.reply(actor,snapshot(actor,action.requestId(),true,"","操作已完成"));
            for(UUID id:affected)if(!id.equals(actor.getUUID())){var player=actor.server.getPlayerList().getPlayer(id);if(player!=null&&SocialNetwork.supported(player.connection.connection))query(player);}
        }catch(PartyError error){SocialNetwork.reply(actor,snapshot(actor,action.requestId(),true,error.getMessage(),""));}
        catch(IOException error){LogUtils.getLogger().error("Cannot commit social action {} for {}",action.kind(),actor.getUUID(),error);SocialNetwork.reply(actor,snapshot(actor,action.requestId(),true,"好友资料保存失败，请联系服主检查存档与日志",""));}
    }
    private static UUID uuid(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException error){throw new PartyError("无效的玩家标识");}}
    private static ServerPlayer online(ServerPlayer actor,String target){var player=actor.server.getPlayerList().getPlayer(uuid(target));if(player==null)throw new PartyError("目标玩家已经离线");return player;}
    public static SocialState.Snapshot snapshot(ServerPlayer actor,long requestId,boolean actionResult,String error,String notice){
        var repo=InMemoryPartyRepository.of(actor.server);var manager=RoomCoreRuntimeManager.of(actor.server);var view=RoomSquadService.view(actor);
        var current=repo.findByPlayerId(actor.getUUID()).orElse(null);var actions=new ArrayList<String>();
        String rosterFailure=SocialRosterPolicy.failure(actor.server,actor.getUUID(),null,null);
        SocialState.Party party=null;
        if(current==null){if(rosterFailure==null)actions.addAll(List.of("PARTY_CREATE","PARTY_JOIN"));}
        else{
            party=new SocialState.Party(current.partyId(),current.leaderId(),current.open(),manager.findPlayerRoomId(current.leaderId()).orElse(""),current.memberIds().stream().map(id->member(actor,id,current.isLeader(id),view)).toList());
            if(rosterFailure==null){actions.add("PARTY_LEAVE");if(current.isLeader(actor.getUUID()))actions.addAll(List.of("PARTY_INVITE","PARTY_PROMOTE","PARTY_KICK","PARTY_PRIVACY","PARTY_DISBAND"));}
            if(current.isLeader(actor.getUUID()))actions.add("PARTY_RETURN_HUB");
        }
        var parties=new ArrayList<SocialState.PartyEntry>();
        for(var candidate:repo.findAll()){
            String phaseFailure=SocialRosterPolicy.failure(actor.server,actor.getUUID(),candidate.partyId(),null);
            boolean available=phaseFailure==null&&current==null&&!candidate.isFull()&&candidate.lockState()!=PartyLockState.ROOM_TRANSITION_LOCKED;
            String reason=phaseFailure!=null?phaseFailure:current!=null?"先离开当前好友队伍":candidate.isFull()?"队伍已满":candidate.lockState()==PartyLockState.ROOM_TRANSITION_LOCKED?"队伍正在切换房间":"";
            var leader=actor.server.getPlayerList().getPlayer(candidate.leaderId());String name=leader==null?candidate.leaderId().toString():ProfileServer.displayName(leader);
            parties.add(new SocialState.PartyEntry(candidate.partyId(),name,candidate.memberCount(),candidate.open(),available,reason));
        }
        var invitations=PartyUiFacade.of(actor.server).listPendingRequests(actor).stream().map(v->{
            var from=actor.server.getPlayerList().getPlayer(v.fromPlayerId());String name=from==null?v.fromPlayerName():ProfileServer.displayName(from);
            return new SocialState.Invitation(v.requestId(),v.partyId(),name,v.type().name(),(v.remainingTicks(actor.server.getTickCount())+19)/20);
        }).toList();
        if(!invitations.isEmpty()){if(rosterFailure==null)actions.add("PARTY_ACCEPT");actions.add("PARTY_REFUSE");}
        var candidates=actor.server.getPlayerList().getPlayers().stream().map(p->member(actor,p.getUUID(),false,view)).toList();
        var squads=new ArrayList<SocialState.Squad>();String own="",team="";
        if(view!=null){
            String phaseFailure=RoomSquadService.editFailure(view);
            own=view.labels().getOrDefault(actor.getUUID(),"");team=manager.getPlayerTeamDisplayName(actor.getUUID());
            var labels=new TreeSet<String>();for(UUID id:view.players())if(view.teams().get(id)==view.team()){String label=view.labels().get(id);if(label!=null&&!label.isBlank())labels.add(label);}
            for(String label:labels){
                var settings=RoomSquadService.settings(view,label);var ids=RoomSquadService.members(view,label);boolean mine=label.equals(own);
                boolean join=phaseFailure==null&&!mine&&!settings.locked()&&ids.size()<4;String reason=phaseFailure!=null?phaseFailure:mine?"当前小队":settings.locked()?"小队已锁定":ids.size()>=4?"小队已满":"";
                squads.add(new SocialState.Squad(label,settings.leader(),settings.locked(),mine,join,reason,ids.stream().map(id->member(actor,id,id.equals(settings.leader()),view)).toList()));
            }
            if(phaseFailure==null){actions.addAll(List.of("SQUAD_CREATE","SQUAD_JOIN","SQUAD_LEAVE"));
                var settings=RoomSquadService.settings(view,own);if(settings!=null&&settings.leader().equals(actor.getUUID()))actions.addAll(List.of("SQUAD_LOCK","SQUAD_PROMOTE","SQUAD_KICK","SQUAD_INVITE"));}
        }
        List<SocialState.Member> friends=List.of();List<SocialState.FriendRequest> requests=List.of();
        try{
            var store=SocialServerState.friends(actor.server);
            friends=store.friends(actor.getUUID()).stream().map(p->member(actor,p.id(),false,view,p)).toList();
            requests=store.requests(actor.getUUID()).stream().map(r->{
                boolean outgoing=r.from().equals(actor.getUUID());UUID id=outgoing?r.to():r.from();var player=actor.server.getPlayerList().getPlayer(id);var profile=player==null?store.profile(id):SocialServerState.profile(player);
                return new SocialState.FriendRequest(r.id(),id,profile.accountName(),profile.displayName(),outgoing);
            }).toList();
            actions.add("FRIEND_REQUEST");if(!friends.isEmpty())actions.add("FRIEND_REMOVE");
            if(requests.stream().anyMatch(r->!r.outgoing()))actions.addAll(List.of("FRIEND_ACCEPT","FRIEND_REFUSE"));
            if(requests.stream().anyMatch(SocialState.FriendRequest::outgoing))actions.add("FRIEND_CANCEL");
        }catch(IOException failure){LogUtils.getLogger().error("Cannot read social data for {}",actor.getUUID(),failure);if(error.isBlank())error="好友资料读取失败，请联系服主检查存档与日志";notice="";}
        var squadInvitations=SocialServerState.squads(actor.server).incoming(actor.getUUID()).stream().map(i->{
            var from=actor.server.getPlayerList().getPlayer(i.from());return new SocialState.SquadInvitation(i.id(),i.from(),ProfileServer.displayName(from),i.roomId(),i.label(),(i.expires()-actor.server.getTickCount()+19)/20);
        }).toList();
        if(!squadInvitations.isEmpty()){if(RoomSquadService.editFailure(view)==null)actions.add("SQUAD_ACCEPT");actions.add("SQUAD_REFUSE");}
        if(rosterFailure!=null&&notice.isBlank())notice=rosterFailure;
        return new SocialState.Snapshot(requestId,actionResult,actor.getUUID(),error,notice,manager.findPlayerRoomId(actor.getUUID()).orElse(""),team,own,party,List.copyOf(parties),invitations,candidates,List.copyOf(squads),List.copyOf(actions),friends,requests,squadInvitations);
    }
    private static SocialState.Member member(ServerPlayer viewer,UUID id,boolean leader,RoomSquadService.View view){
        return member(viewer,id,leader,view,null);
    }
    private static SocialState.Member member(ServerPlayer viewer,UUID id,boolean leader,RoomSquadService.View view,FriendStore.Profile profile){
        var player=viewer.server.getPlayerList().getPlayer(id);if(player==null)return new SocialState.Member(id,profile==null?id.toString():profile.accountName(),profile==null?id.toString():profile.displayName(),"",false,leader,"","离线",false,"玩家已离线","",false,"玩家已离线");
        var manager=RoomCoreRuntimeManager.of(viewer.server);String room=manager.findPlayerRoomId(id).orElse("");var running=manager.findRunningState(room).orElse(null);
        var cls=CustomLoadoutService.resolveCurrentClass(ConquestDataManager.of(viewer.server).playerData().getPlayerData(id),running,id);
        String reason="进入对局后可以部署到小队队友";
        if(view!=null&&view.running())reason=manager.coreService().deploySelectionService().describeSelectionFailure(view.room(),view.runtime().getRunningState(),viewer.getUUID(),view.team(),"teammate",id.toString());
        String status=room.isBlank()?"大厅":running!=null&&running.isPlayerDowned(id)?"等待救援":running!=null&&running.isInRespawnFlow(id)?"部署中":running!=null&&running.isPlayerInCombat(id,viewer.server.getTickCount())?"交火中":"待命";
        var playerTeam=manager.findCurrentTeam(id).orElse(null);
        String inviteReason=RoomSquadService.invitationFailure(viewer,view,id);
        return new SocialState.Member(id,player.getScoreboardName(),ProfileServer.displayName(player),cls==null?"":cls.getKey(),true,leader,room,status,reason==null,reason==null?"":reason,playerTeam==null?"":playerTeam.name(),inviteReason==null,inviteReason==null?"":inviteReason);
    }
}
