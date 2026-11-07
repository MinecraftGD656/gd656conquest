package org.mods.gd656conquest.server.room.core;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.room.runtime.RoomSquadAssignmentService;
import org.mods.gd656conquest.server.social.party.domain.PartyError;
import org.mods.gd656conquest.server.social.SocialRosterPolicy;

/** Squad controls share the room's existing membership tables; friend parties remain independent. */
public final class RoomSquadService {
    public record Settings(UUID leader,boolean locked,UUID tenure) {
        public Settings(UUID leader,boolean locked){this(leader,locked,UUID.randomUUID());}
    }
    public record View(String roomId,RoomDefinition room,RoomRuntimeState runtime,boolean running,RoomTeamId team,
                       Collection<UUID> players,Map<UUID,RoomTeamId> teams,Map<UUID,String> labels) {}
    private RoomSquadService() {}
    public static String key(RoomTeamId team,String label){return team.name()+":"+label;}
    public static View view(ServerPlayer player){
        var manager=RoomCoreRuntimeManager.of(player.server);String id=manager.findPlayerRoomId(player.getUUID()).orElse("");
        if(id.isBlank())return null;
        var room=manager.findRoom(id).orElse(null);var runtime=((org.mods.gd656conquest.social.RoomSocialAccess)(Object)manager.coreService()).socialRuntime(id).orElse(null);
        if(room==null||runtime==null||room.lifecycleState()==RoomLifecycleState.MAINTENANCE)return null;
        boolean running=room.lifecycleState()==RoomLifecycleState.RUNNING||room.lifecycleState()==RoomLifecycleState.ENDING;
        var teams=running?runtime.getRunningState().getRoomTeams():runtime.getMatchingState().getQueueTeams();
        var players=running?runtime.getRunningState().getRoomPlayers():runtime.getMatchingState().getQueuePlayers();
        var labels=running?runtime.getRunningState().getSquadLabelsByPlayer():runtime.getSquadLabelsByPlayer();
        RoomTeamId team=teams.get(player.getUUID());if(team==null||!players.contains(player.getUUID()))return null;
        reconcile(runtime,running);return new View(id,room,runtime,running,team,players,teams,labels);
    }
    public static void reconcile(RoomRuntimeState runtime,boolean running){
        var teams=running?runtime.getRunningState().getRoomTeams():runtime.getMatchingState().getQueueTeams();
        var players=running?runtime.getRunningState().getRoomPlayers():runtime.getMatchingState().getQueuePlayers();
        var labels=running?runtime.getRunningState().getSquadLabelsByPlayer():runtime.getSquadLabelsByPlayer();
        Map<String,List<UUID>> rosters=new LinkedHashMap<>();
        for(UUID id:players){var team=teams.get(id);String label=labels.getOrDefault(id,"");if(team!=null&&!label.isBlank())rosters.computeIfAbsent(key(team,label),k->new ArrayList<>()).add(id);}
        runtime.getSocialSquads().keySet().retainAll(rosters.keySet());
        rosters.forEach((key,ids)->{var old=runtime.getSocialSquads().get(key);if(old==null||!ids.contains(old.leader()))runtime.getSocialSquads().put(key,new Settings(ids.get(0),old!=null&&old.locked()));});
    }
    public static List<UUID> members(View view,String label){return view.players().stream().filter(id->view.teams().get(id)==view.team()&&label.equals(view.labels().get(id))).toList();}
    public static Settings settings(View view,String label){return view.runtime().getSocialSquads().get(key(view.team(),label));}
    public static String editFailure(View view){return view==null?"请在大厅的战前组队中邀请玩家":SocialRosterPolicy.roomFailure(view.room(),view.runtime());}
    /** A single permission result feeds both target buttons and the authoritative invitation action. */
    public static String invitationFailure(ServerPlayer leader,View view,UUID target){
        String phaseFailure=editFailure(view);if(phaseFailure!=null)return phaseFailure;
        String own=view.labels().getOrDefault(leader.getUUID(),"");Settings settings=settings(view,own);
        if(settings==null||!leader.getUUID().equals(settings.leader()))return "只有小队长可以邀请";
        if(leader.getUUID().equals(target))return "请选择其他玩家";
        if(leader.server.getPlayerList().getPlayer(target)==null)return "玩家已离线";
        if(!view.players().contains(target)||!RoomCoreRuntimeManager.of(leader.server).findPlayerRoomId(target).orElse("").equals(view.roomId()))return "玩家不在同一房间";
        if(view.teams().get(target)!=view.team())return "玩家不在同一阵营";
        if(own.equals(view.labels().get(target)))return "已在当前小队";
        if(members(view,own).size()>=RoomSquadAssignmentService.SQUAD_SIZE)return "小队已满";
        return null;
    }
    public static void joinInvited(ServerPlayer recipient,ServerPlayer leader,String expectedLabel){
        View view=view(leader);String failure=invitationFailure(leader,view,recipient.getUUID());
        if(failure!=null)throw new PartyError(failure);
        if(!expectedLabel.equals(view.labels().get(leader.getUUID())))throw new PartyError("邀请的小队已变化");
        move(recipient,view,recipient.getUUID(),expectedLabel);
    }
    public static String nextEmptyLabel(View view){
        var counts=new LinkedHashMap<String,Integer>();
        for(UUID id:view.players())if(view.teams().get(id)==view.team()){String label=view.labels().get(id);if(label!=null&&!label.isBlank())counts.put(label,RoomSquadAssignmentService.SQUAD_SIZE);}
        return RoomSquadAssignmentService.chooseSingleSquadLabel(counts);
    }
    public static String labelForGroup(RoomCoreRuntimeManager manager,String roomId,RoomTeamId team,Collection<UUID> group,String preferred){
        if(team==null)return "";
        var runtime=((org.mods.gd656conquest.social.RoomSocialAccess)(Object)manager.coreService()).socialRuntime(roomId).orElseThrow(()->new PartyError("房间已不可用"));
        var room=manager.findRoom(roomId).orElseThrow(()->new PartyError("房间已不可用"));
        boolean running=room.lifecycleState()==RoomLifecycleState.RUNNING||room.lifecycleState()==RoomLifecycleState.ENDING;
        var teams=running?runtime.getRunningState().getRoomTeams():runtime.getMatchingState().getQueueTeams();
        var players=running?runtime.getRunningState().getRoomPlayers():runtime.getMatchingState().getQueuePlayers();
        var labels=running?runtime.getRunningState().getSquadLabelsByPlayer():runtime.getSquadLabelsByPlayer();
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(UUID id:players)if(teams.get(id)==team&&!group.contains(id)){String label=labels.get(id);if(label!=null&&!label.isBlank())counts.merge(label,1,Integer::sum);}
        reconcile(runtime,running);
        Settings preferredSettings=preferred==null?null:runtime.getSocialSquads().get(key(team,preferred));
        boolean mayUsePreferred=preferredSettings==null||!preferredSettings.locked()||group.contains(preferredSettings.leader());
        if(preferred!=null&&!preferred.isBlank()&&mayUsePreferred&&counts.getOrDefault(preferred,0)+group.size()<=RoomSquadAssignmentService.SQUAD_SIZE)return preferred;
        runtime.getSocialSquads().forEach((key,value)->{if(value.locked()&&key.startsWith(team.name()+":"))counts.put(key.substring(key.indexOf(':')+1),RoomSquadAssignmentService.SQUAD_SIZE);});
        return RoomSquadAssignmentService.chooseGroupSquadLabel(counts,group.size());
    }
    public static void action(ServerPlayer actor,String kind,String target,String value){
        View view=view(actor);if(view==null)throw new PartyError("进入作战房间后可以管理小队");
        String phaseFailure=editFailure(view);if(phaseFailure!=null)throw new PartyError(phaseFailure);
        String own=view.labels().getOrDefault(actor.getUUID(),"");Settings control=settings(view,own);
        switch(kind){
            case "SQUAD_CREATE","SQUAD_LEAVE"->move(actor,view,actor.getUUID(),nextEmptyLabel(view));
            case "SQUAD_JOIN"->{
                Settings to=settings(view,target);if(to==null)throw new PartyError("目标小队已不存在");
                if(target.equals(own))throw new PartyError("你已经在这个小队中");
                if(to.locked())throw new PartyError("这个小队已锁定");
                if(members(view,target).size()>=RoomSquadAssignmentService.SQUAD_SIZE)throw new PartyError("这个小队已经满员");
                move(actor,view,actor.getUUID(),target);
            }
            case "SQUAD_LOCK"->{requireLeader(actor,control);if(!value.equals("true")&&!value.equals("false"))throw new PartyError("无效的小队锁定状态");view.runtime().getSocialSquads().put(key(view.team(),own),new Settings(control.leader(),Boolean.parseBoolean(value),control.tenure()));}
            case "SQUAD_PROMOTE","SQUAD_KICK"->{
                requireLeader(actor,control);UUID id=uuid(target);
                if(id.equals(actor.getUUID())||!members(view,own).contains(id))throw new PartyError("请选择当前小队中的其他成员");
                if(kind.equals("SQUAD_PROMOTE"))view.runtime().getSocialSquads().put(key(view.team(),own),new Settings(id,control.locked()));
                else move(actor,view,id,nextEmptyLabel(view));
            }
            default->throw new PartyError("未知的小队操作");
        }
    }
    private static void requireLeader(ServerPlayer actor,Settings settings){if(settings==null||!actor.getUUID().equals(settings.leader()))throw new PartyError("只有小队长可以执行此操作");}
    private static UUID uuid(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException e){throw new PartyError("无效的玩家标识");}}
    private static void move(ServerPlayer actor,View view,UUID id,String label){
        var manager=RoomCoreRuntimeManager.of(actor.server);
        if(!manager.applyGroupPlacement(view.roomId(),id,view.team(),label,actor.server.getTickCount()))throw new PartyError("当前房间状态已变化，请重试");
        if(view.running()){
            var state=view.runtime().getRunningState();var deploy=manager.coreService().deploySelectionService();
            for(UUID member:List.copyOf(state.getRoomPlayers())){
                String kind=state.getDeploySelectionKinds().getOrDefault(member,"");
                if(!kind.equals("teammate")&&!kind.equals("beacon"))continue;
                var team=state.getRoomTeams().get(member);String target=state.getDeploySelectionTargetIds().getOrDefault(member,"");
                if(deploy.describeSelectionFailure(view.room(),state,member,team,kind,target)!=null)deploy.replaceSelectionWithTeamFallback(state,member,team);
            }
        }
        reconcile(view.runtime(),view.running());
        var player=actor.server.getPlayerList().getPlayer(id);if(player!=null)manager.resyncPlayerPresentation(player,actor.server.getTickCount());
    }
}
