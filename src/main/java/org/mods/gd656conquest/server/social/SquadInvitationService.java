package org.mods.gd656conquest.server.social;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.room.core.RoomSquadService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.social.party.domain.PartyError;

/** Short-lived consent to enter a squad. Friends and temporary parties are independent of these invitations. */
public final class SquadInvitationService {
    public static final int LIFETIME_TICKS=600,MAX_PER_PLAYER=16,MAX_TOTAL=512;
    public record Invitation(String id,UUID from,UUID to,String roomId,RoomTeamId team,String label,
                             RoomRuntimeState runtime,RoomSquadService.Settings leadership,boolean running,long cycle,long expires) {}
    private final MinecraftServer server;
    private final Map<String,Invitation> invitations=new LinkedHashMap<>();
    public SquadInvitationService(MinecraftServer server){this.server=server;}
    public Invitation invite(ServerPlayer actor,UUID target){
        prune();var view=RoomSquadService.view(actor);String failure=RoomSquadService.invitationFailure(actor,view,target);
        if(failure!=null)throw new PartyError(failure);
        for(var invite:invitations.values())if(invite.from().equals(actor.getUUID())&&invite.to().equals(target))throw new PartyError("小队邀请已经发出");
        if(invitations.size()>=MAX_TOTAL||count(actor.getUUID())>=MAX_PER_PLAYER||count(target)>=MAX_PER_PLAYER)throw new PartyError("待处理小队邀请已满，请稍后再试");
        String label=view.labels().get(actor.getUUID());
        var invite=new Invitation(UUID.randomUUID().toString(),actor.getUUID(),target,view.roomId(),view.team(),label,view.runtime(),RoomSquadService.settings(view,label),view.running(),cycle(view),(long)server.getTickCount()+LIFETIME_TICKS);
        invitations.put(invite.id(),invite);return invite;
    }
    public Invitation accept(ServerPlayer actor,String id){
        var invite=requireRecipient(actor,id);
        String failure=invalid(invite);if(failure!=null){invitations.remove(id);throw new PartyError(failure);}
        var leader=server.getPlayerList().getPlayer(invite.from());
        RoomSquadService.joinInvited(actor,leader,invite.label());
        invitations.remove(id);prune();return invite;
    }
    public Invitation refuse(ServerPlayer actor,String id){var invite=requireRecipient(actor,id);invitations.remove(id);return invite;}
    public List<Invitation> incoming(UUID player){prune();return invitations.values().stream().filter(i->i.to().equals(player)).toList();}
    public void invalidate(UUID player){invitations.values().removeIf(i->i.from().equals(player)||i.to().equals(player));}
    public void prune(){invitations.values().removeIf(i->invalid(i)!=null);}
    private long count(UUID id){return invitations.values().stream().filter(i->i.from().equals(id)||i.to().equals(id)).count();}
    private Invitation requireRecipient(ServerPlayer actor,String id){
        var invite=invitations.get(id);if(invite==null)throw new PartyError("小队邀请已结束");
        if(!invite.to().equals(actor.getUUID()))throw new PartyError("这条小队邀请属于其他玩家");return invite;
    }
    private String invalid(Invitation invite){
        if(server.getTickCount()>=invite.expires())return "小队邀请已过期";
        var leader=server.getPlayerList().getPlayer(invite.from());if(leader==null)return "邀请者已离线";
        var view=RoomSquadService.view(leader);
        if(view==null||view.runtime()!=invite.runtime()||view.running()!=invite.running()||cycle(view)!=invite.cycle()||view.team()!=invite.team()||!view.roomId().equals(invite.roomId()))return "房间或阵营已变化，小队邀请已结束";
        // A handoff ends the old tenure even when the same player later becomes leader again.
        var control=RoomSquadService.settings(view,invite.label());
        if(control==null||!control.tenure().equals(invite.leadership().tenure()))return "小队长已变化，请重新邀请";
        return RoomSquadService.invitationFailure(leader,view,invite.to());
    }
    private static long cycle(RoomSquadService.View view){return view.running()?view.runtime().getRunningState().getActiveMatchInstanceId():view.runtime().getMatchingState().getQueueStartTick();}
}
