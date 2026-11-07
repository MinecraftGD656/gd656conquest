package org.mods.gd656conquest.client.social;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.social.SocialState;
import org.mods.gd656conquest.social.SocialState.*;
import org.mods.gd656killicon.client.profile.ProfileClient;
import org.mods.gd656killicon.client.gui.theme.*;
import org.mods.gd656killicon.client.gui.theme.lobby.*;
import org.mods.gd656killicon.client.sounds.BattlefieldAudio;
import static org.mods.gd656conquest.client.social.BattlefieldSocialLayout.*;
import static org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.Rect;

/** Native social page; authoritative snapshots drive members, permissions and action results. */
public final class BattlefieldSocialScreen extends Screen {
    private final Screen parent;
    private Tab tab=Tab.FRIENDS;
    private Directory directory=Directory.PLAYERS;
    private String selectedMember="",selectedPlayer="",selectedParty="",selectedInvitation="",selectedSquad="",selectedSquadMember="",focus="";
    private String selectedFriend="",selectedFriendRequest="",selectedSquadInvitation="",confirmRemoveFriend="";
    private int leftPage,rightPage;
    private long nextRefresh;
    private Snapshot observed;
    private List<Hit> hits=List.of();
    private Map<String,Request> requests=new LinkedHashMap<>();
    private boolean keyboardFocus;
    private boolean confirmDisband;
    public record Request(String kind,String target,String value) {}
    public BattlefieldSocialScreen(Screen parent){super(Component.literal("小队与好友"));this.parent=parent;}
    @Override protected void init(){SocialClient.request();nextRefresh=net.minecraft.Util.getMillis()+1500;updateSelection();}
    @Override public void tick(){
        if(SocialClient.snapshot()!=observed)updateSelection();
        long now=net.minecraft.Util.getMillis();if(now>=nextRefresh&&!SocialClient.pending()){SocialClient.request();nextRefresh=now+1500;}
    }
    private void updateSelection(){
        observed=SocialClient.snapshot();if(observed==null)return;
        var s=observed;
        selectedMember=keep(selectedMember,s.party()==null?List.of():s.party().members().stream().map(m->m.id().toString()).toList());
        selectedPlayer=keep(selectedPlayer,players(s).stream().map(m->m.id().toString()).toList());
        selectedFriend=keep(selectedFriend,s.friends().stream().map(m->m.id().toString()).toList());
        selectedFriendRequest=keep(selectedFriendRequest,friendRequests(s).stream().map(FriendRequest::id).toList());
        selectedSquadInvitation=keep(selectedSquadInvitation,s.squadInvitations().stream().map(SquadInvitation::id).toList());
        if(!confirmRemoveFriend.equals(selectedFriend))confirmRemoveFriend="";
        selectedParty=keep(selectedParty,s.parties().stream().map(PartyEntry::id).toList());
        selectedInvitation=keep(selectedInvitation,s.invitations().stream().map(Invitation::id).toList());
        if(selectedSquad.isEmpty())selectedSquad=s.currentSquad();selectedSquad=keep(selectedSquad,s.squads().stream().map(Squad::label).toList());
        Squad squad=s.squads().stream().filter(v->v.label().equals(selectedSquad)).findFirst().orElse(null);
        selectedSquadMember=keep(selectedSquadMember,squad==null?List.of():squad.members().stream().map(m->m.id().toString()).toList());
    }
    private static String keep(String selected,List<String> keys){return keys.contains(selected)?selected:keys.isEmpty()?"":keys.get(0);}
    private float scale(){return Math.min(width/(float)WIDTH,height/(float)HEIGHT);}
    private double[] local(double x,double y){float s=scale();return new double[]{(x-(width-WIDTH*s)/2)/s,(y-(height-HEIGHT*s)/2)/s};}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        if(SocialClient.snapshot()!=observed)updateSelection();
        var point=local(mouseX,mouseY);float s=scale();g.pose().pushPose();g.pose().translate((width-WIDTH*s)/2,(height-HEIGHT*s)/2,0);g.pose().scale(s,s,1);
        hits=BattlefieldSocialLayout.render(new Canvas(g),BattlefieldUiTheme.current(),view(),point[0],point[1],keyboardFocus?focus:"");g.pose().popPose();
    }
    private View view(){
        Snapshot s=SocialClient.snapshot();requests=new LinkedHashMap<>();var left=new ArrayList<Row>();var right=new ArrayList<Row>();var leftActions=new ArrayList<Command>();var rightActions=new ArrayList<Command>();
        String context="组队后一起进入同一场战斗",leftTitle="我的队伍",rightTitle="成员";
        boolean pending=SocialClient.pending();
        if(s!=null){
            context=s.currentRoom().isBlank()?"大厅":s.currentRoom()+(s.currentTeam().isBlank()?"":"  /  "+s.currentTeam());
            if(tab==Tab.FRIENDS){
                leftTitle="我的好友  "+s.friends().size();
                for(Member m:s.friends())left.add(member("friend:",m,m.id().toString().equals(selectedFriend),false));
                Member friend=findMember(s.friends(),selectedFriend);
                inviteParty(leftActions,s,friend);
                if(!s.currentRoom().isBlank())inviteSquad(leftActions,s,friend);
                command(leftActions,s,"FRIEND_REMOVE",confirmRemoveFriend.equals(selectedFriend)&&friend!=null?"确认删除好友":"删除好友",selectedFriend,"",friend!=null,"选择要删除的好友，再次确认后删除");
                if(directory==Directory.FRIEND_REQUESTS||directory==Directory.SENT_FRIEND_REQUESTS){
                    var visibleRequests=friendRequests(s);
                    for(FriendRequest request:visibleRequests)right.add(new Row("friend-request:"+request.id(),displayName(request.accountName(),request.displayName()),request.outgoing()?"等待对方确认":"希望与你成为好友",request.outgoing()?"已发出":"收到",request.playerId(),"",false,true,request.id().equals(selectedFriendRequest)));
                    FriendRequest request=visibleRequests.stream().filter(v->v.id().equals(selectedFriendRequest)).findFirst().orElse(null);
                    if(directory==Directory.SENT_FRIEND_REQUESTS)command(rightActions,s,"FRIEND_CANCEL","撤销申请",selectedFriendRequest,"",request!=null,"");
                    else{
                        command(rightActions,s,"FRIEND_ACCEPT","接受申请",selectedFriendRequest,"",request!=null,"选择收到的好友申请");
                        command(rightActions,s,"FRIEND_REFUSE","拒绝申请",selectedFriendRequest,"",request!=null,"选择收到的好友申请");
                    }
                }else{
                    for(Member m:players(s))right.add(member("player:",m,m.id().toString().equals(selectedPlayer),false));
                    Member player=findMember(s.onlinePlayers(),selectedPlayer);
                    boolean friends=friendOf(s,selectedPlayer),waiting=s.friendRequests().stream().anyMatch(v->v.playerId().toString().equals(selectedPlayer));
                    command(rightActions,s,"FRIEND_REQUEST",friends?"已是好友":waiting?"申请处理中":"添加好友",selectedPlayer,"",player!=null&&player.online()&&!friends&&!waiting,waiting?"在好友申请中查看或处理请求":friends?"这位玩家已在你的好友列表":"选择在线玩家，发送好友申请");
                }
            }else if(tab==Tab.PARTY){
                Party party=s.party();
                if(party==null){command(leftActions,s,"PARTY_CREATE","创建队伍","","",true,"");}
                else{
                    leftTitle="我的队伍  "+party.members().size()+" / 4  ·  "+(party.open()?"公开":"仅邀请");
                    for(Member m:party.members())left.add(member("member:",m,m.id().toString().equals(selectedMember),false));
                    boolean leader=party.leaderId().equals(s.self()),other=!selectedMember.isBlank()&&!selectedMember.equals(s.self().toString());
                    leftActions.add(new Command("rooms","选择房间",leader&&!pending&&!rosterLocked(s),rosterLocked(s)?"编制已锁定，先整队返回大厅":"队长选择房间后整队入场",false));
                    if(leader){
                        command(leftActions,s,"PARTY_PRIVACY",party.open()?"设为仅邀请":"设为公开","",party.open()?"invite":"open",true,"");
                        command(leftActions,s,"PARTY_PROMOTE","转让队长",selectedMember,"",other,"选择其他队员后转让队长");
                        command(leftActions,s,"PARTY_KICK","移出队员",selectedMember,"",other,"选择其他队员后移出队伍");
                        command(leftActions,s,"PARTY_DISBAND",confirmDisband?"确认解散":"解散队伍","","",true,"解散会让所有成员离开好友队伍");
                    }
                    command(leftActions,s,"PARTY_LEAVE","离开队伍","","",true,"");
                    if(!s.currentRoom().isBlank())command(leftActions,s,"PARTY_RETURN_HUB","返回大厅","","",leader,"由队长带队返回大厅");
                }
                switch(directory){
                    case FRIENDS->{
                        for(Member m:s.friends())right.add(member("friend:",m,m.id().toString().equals(selectedFriend),false));
                        inviteParty(rightActions,s,findMember(s.friends(),selectedFriend));
                    }
                    case PLAYERS->{
                        for(Member m:s.onlinePlayers())if(!m.id().equals(s.self()))right.add(member("player:",m,m.id().toString().equals(selectedPlayer),false));
                        inviteParty(rightActions,s,findMember(s.onlinePlayers(),selectedPlayer));
                    }
                    case PARTIES->{
                        for(PartyEntry p:s.parties())right.add(new Row("party:"+p.id(),p.leaderName()+" 的队伍",p.open()?"公开队伍": "仅邀请 · 可申请加入",p.size()+" / 4",null,"",false,true,p.id().equals(selectedParty)));
                        PartyEntry p=s.parties().stream().filter(v->v.id().equals(selectedParty)).findFirst().orElse(null);
                        command(rightActions,s,"PARTY_JOIN",p!=null&&!p.open()?"申请加入":"加入队伍",selectedParty,"",p!=null&&p.canJoin(),p==null?"选择队伍":p.reason());
                    }
                    case INVITATIONS->{
                        for(Invitation i:s.invitations())right.add(new Row("invitation:"+i.id(),i.fromName(),i.type().equals("JOIN_APPLICATION")?"申请加入你的队伍":"邀请你加入队伍",i.remainingSeconds()+"秒",null,"",false,true,i.id().equals(selectedInvitation)));
                        Invitation i=s.invitations().stream().filter(v->v.id().equals(selectedInvitation)).findFirst().orElse(null);
                        command(rightActions,s,"PARTY_ACCEPT",i!=null&&i.type().equals("JOIN_APPLICATION")?"同意申请":"接受邀请",selectedInvitation,"",i!=null,"选择邀请或申请");
                        command(rightActions,s,"PARTY_REFUSE","拒绝",selectedInvitation,"",i!=null,"选择邀请或申请");
                    }
                    default->{}
                }
            }else{
                leftTitle=s.squads().size()+" 支小队";
                if(s.currentRoom().isBlank())leftActions.add(new Command("lobby-party","大厅组队",!pending,"邀请玩家组成四人队伍，入场后分入同一小队",false));
                for(Squad q:s.squads())left.add(new Row("squad:"+q.label(),q.label()+(q.mine()?" · 我的小队":""),q.locked()?"私密 · 仅限邀请":q.reason().isBlank()?"公开 · 开放加入":q.reason(),q.members().size()+" / 4",null,"",false,true,q.label().equals(selectedSquad)));
                Squad q=s.squads().stream().filter(v->v.label().equals(selectedSquad)).findFirst().orElse(null);
                rightTitle=q==null?"小队成员":q.label()+"  ·  "+q.members().size()+" / 4";
                if(!s.currentRoom().isBlank()){
                    command(leftActions,s,"SQUAD_CREATE","创建小队","","",true,"匹配等待时可创建小队");
                    command(leftActions,s,"SQUAD_JOIN","加入小队",selectedSquad,"",q!=null&&q.canJoin(),q==null?"选择小队":q.reason());
                    command(leftActions,s,"SQUAD_LEAVE","离开小队","","",!s.currentSquad().isBlank(),"");
                }
                if(directory==Directory.FRIENDS){
                    for(Member m:s.friends())right.add(member("friend:",m,m.id().toString().equals(selectedFriend),false));
                    inviteSquad(rightActions,s,findMember(s.friends(),selectedFriend));
                }else if(directory==Directory.PLAYERS){
                    for(Member m:players(s))right.add(member("player:",m,m.id().toString().equals(selectedPlayer),false));
                    inviteSquad(rightActions,s,findMember(players(s),selectedPlayer));
                }else if(directory==Directory.SQUAD_INVITATIONS){
                    for(SquadInvitation i:s.squadInvitations())right.add(new Row("squad-invitation:"+i.id(),i.fromName(),"邀请加入 "+i.squadLabel()+" 小队 · "+i.roomId(),i.remainingSeconds()+"秒",i.fromId(),"",false,true,i.id().equals(selectedSquadInvitation)));
                    SquadInvitation invitation=s.squadInvitations().stream().filter(i->i.id().equals(selectedSquadInvitation)).findFirst().orElse(null);
                    command(rightActions,s,"SQUAD_ACCEPT","接受小队邀请",selectedSquadInvitation,"",invitation!=null,"选择收到的小队邀请");
                    command(rightActions,s,"SQUAD_REFUSE","拒绝邀请",selectedSquadInvitation,"",invitation!=null,"选择收到的小队邀请");
                }else if(q!=null){
                    for(Member m:q.members())right.add(member("squad-member:",m,m.id().toString().equals(selectedSquadMember),q.mine()));
                }
                if(directory==Directory.MEMBERS&&q!=null&&q.mine()){
                    boolean leader=q.leaderId()!=null&&q.leaderId().equals(s.self()),other=!selectedSquadMember.isBlank()&&!selectedSquadMember.equals(s.self().toString());
                    command(rightActions,s,"SQUAD_LOCK",q.locked()?"设为公开":"设为私密",q.label(),Boolean.toString(!q.locked()),leader,"私密小队由队长邀请同阵营玩家加入");
                    command(rightActions,s,"SQUAD_PROMOTE","转让队长",selectedSquadMember,"",leader&&other,"小队长选择其他队员后转让");
                    command(rightActions,s,"SQUAD_KICK","移出小队",selectedSquadMember,"",leader&&other,"小队长选择其他队员后移出");
                }
            }
        }
        int lp=pages(left.size()),rp=pages(right.size());leftPage=Math.min(leftPage,lp-1);rightPage=Math.min(rightPage,rp-1);
        String message=s==null?(SocialClient.supported()?"正在读取队伍":"当前服务器尚未提供组队服务"):!SocialClient.error().isBlank()?SocialClient.error():!s.error().isBlank()?s.error():s.notice();
        return new View(tab,directory,context,leftTitle,rightTitle,page(left,leftPage),page(right,rightPage),leftActions,rightActions,leftPage,lp,rightPage,rp,message,s==null,pending,s==null?0:(int)s.friendRequests().stream().filter(r->!r.outgoing()).count(),s==null?0:s.squadInvitations().size());
    }
    private static Member findMember(List<Member> members,String id){return members.stream().filter(m->m.id().toString().equals(id)).findFirst().orElse(null);}
    private List<FriendRequest> friendRequests(Snapshot state){
        boolean outgoing=directory==Directory.SENT_FRIEND_REQUESTS;
        return state.friendRequests().stream().filter(request->request.outgoing()==outgoing).toList();
    }
    private List<Member> players(Snapshot s){
        Member self=findMember(s.onlinePlayers(),s.self().toString());
        if(self==null)self=s.squads().stream().flatMap(q->q.members().stream()).filter(m->m.id().equals(s.self())).findFirst().orElse(null);
        String team=self==null?"":self.team();
        return s.onlinePlayers().stream().filter(m->!m.id().equals(s.self())).filter(m->tab!=Tab.SQUAD||s.currentRoom().isBlank()||!team.isBlank()&&m.room().equals(s.currentRoom())&&m.team().equals(team)).toList();
    }
    private static boolean friendOf(Snapshot s,String id){return findMember(s.friends(),id)!=null;}
    private static String displayName(String account,String display){return display.isBlank()?account:display;}
    private void inviteParty(List<Command> commands,Snapshot s,Member member){
        Party party=s.party();
        String reason=member==null?"选择玩家":!member.online()?"玩家已离线":party==null?"在组队页创建队伍后邀请":!party.leaderId().equals(s.self())?"由队长邀请玩家":party.members().stream().anyMatch(m->m.id().equals(member.id()))?"已在当前队伍":party.members().size()>=4?"队伍已满（4/4）":"";
        command(commands,s,"PARTY_INVITE","邀请组队",member==null?"":member.id().toString(),"",reason.isBlank(),reason);
    }
    private void inviteSquad(List<Command> commands,Snapshot s,Member member){
        if(s.currentRoom().isBlank()){inviteParty(commands,s,member);return;}
        String label=s.currentSquad().isBlank()?"邀请入小队":"邀请至 "+s.currentSquad()+" 小队";
        command(commands,s,"SQUAD_INVITE",label,member==null?"":member.id().toString(),"",member!=null&&member.canSquadInvite(),member==null?"选择同房间、同阵营的玩家":member.squadInviteReason());
    }
    private void command(List<Command> list,Snapshot s,String kind,String label,String target,String value,boolean targetAllowed,String reason){
        boolean allowed=s.actions().contains(kind)&&targetAllowed&&!SocialClient.pending();
        String why=reason.isBlank()&&!s.actions().contains(kind)?"当前身份或队伍状态无法执行":reason;
        if(!s.actions().contains(kind)&&(kind.startsWith("PARTY_")&&rosterLocked(s)||kind.startsWith("SQUAD_")&&!s.currentRoom().isBlank()&&!s.actions().contains("SQUAD_CREATE")))why="阵营选择已开始，编制已锁定；返回大厅后可调整";
        list.add(new Command("do:"+kind,label,allowed,why,kind.endsWith("KICK")||kind.endsWith("DISBAND")||kind.equals("FRIEND_REMOVE")));
        requests.put("do:"+kind,new Request(kind,target,value));
    }
    private static boolean rosterLocked(Snapshot s){return !s.actions().contains("PARTY_CREATE")&&!s.actions().contains("PARTY_LEAVE");}
    private static Row member(String prefix,Member m,boolean selected,boolean showDeploy){
        String name=displayName(m.accountName(),m.displayName());
        String detail=!m.online()?"离线":showDeploy?(m.deployable()?"可作为队友部署点":m.deployReason()):m.status();
        if(detail.isBlank())detail=m.room().isBlank()?"大厅":"房间 "+m.room();
        return new Row(prefix+m.id(),name,m.leader()?"队长 · "+detail:detail,className(m.classKey()),m.id(),m.classKey(),m.leader(),m.online(),selected);
    }
    private static String className(String key){return switch(key){case "assault"->"突击";case "support"->"支援";case "engineer"->"工程";case "recon","scout"->"侦察";case "medic"->"医疗";default->"";};}
    /** BF6 lists show more rows per page; the slice and the page count must use the same value. */
    private static int perPage(){return org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme.current()==org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Theme.BF6?BattlefieldSocialLayout.BF6_ROWS:ROWS;}
    private static int pages(int count){int per=perPage();return Math.max(1,(count+per-1)/per);}
    private static List<Row> page(List<Row> rows,int page){int per=perPage();int from=Math.min(rows.size(),page*per);return List.copyOf(rows.subList(from,Math.min(rows.size(),from+per)));}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);keyboardFocus=false;var p=local(x,y);
        for(Hit hit:hits)if(inside(hit.box(),p[0],p[1])){if(hit.enabled())activate(hit.id());else BattlefieldAudio.play(BattlefieldAudio.Event.UI_DENIED);return true;}return false;
    }
    private void activate(String id){
        if(!id.equals("do:PARTY_DISBAND"))confirmDisband=false;
        if(!id.equals("do:FRIEND_REMOVE"))confirmRemoveFriend="";
        if(id.equals("back")){onClose();return;}
        if(id.equals("refresh")){SocialClient.request();return;}
        if(id.equals("lobby-party")){tab=Tab.PARTY;directory=Directory.FRIENDS;updateSelection();leftPage=rightPage=0;focus="";BattlefieldAudio.play(BattlefieldAudio.Event.UI_SELECT);return;}
        if(id.startsWith("tab:")){tab=switch(id){case "tab:friends"->Tab.FRIENDS;case "tab:squad"->Tab.SQUAD;default->Tab.PARTY;};directory=switch(tab){case FRIENDS->Directory.PLAYERS;case PARTY->Directory.FRIENDS;case SQUAD->Directory.MEMBERS;};updateSelection();leftPage=rightPage=0;focus="";BattlefieldAudio.play(BattlefieldAudio.Event.UI_SELECT);return;}
        if(id.startsWith("dir:")){directory=Directory.valueOf(id.substring(4));updateSelection();rightPage=0;focus="";BattlefieldAudio.play(BattlefieldAudio.Event.UI_SELECT);return;}
        if(id.startsWith("left:")||id.startsWith("right:")){
            boolean left=id.startsWith("left:");int delta=id.endsWith("next")?1:-1;
            if(left)leftPage=Math.max(0,leftPage+delta);else rightPage=Math.max(0,rightPage+delta);
            View next=view();List<Row> rows=left?next.left():next.right();
            if(!rows.isEmpty()){activate(rows.get(0).key());return;}
            BattlefieldAudio.play(BattlefieldAudio.Event.UI_SELECT);return;
        }
        if(id.equals("rooms")){
            if(minecraft.screen instanceof BattlefieldLobbyScreen lobby)lobby.requestPage("rooms",BattlefieldLobbyLayout.Section.PLAY);
            else minecraft.setScreen(BattlefieldLobbyScreen.forPage(this,BattlefieldLobbyLayout.Section.PLAY,"rooms"));return;
        }
        if(id.startsWith("do:")){
            View current=view();boolean allowed=java.util.stream.Stream.concat(current.leftActions().stream(),current.rightActions().stream()).anyMatch(c->c.id().equals(id)&&c.enabled());
            Request request=requests.get(id);if(allowed&&request!=null){
                if(request.kind().equals("PARTY_DISBAND")&&!confirmDisband){confirmDisband=true;BattlefieldAudio.play(BattlefieldAudio.Event.UI_SELECT);return;}
                if(request.kind().equals("FRIEND_REMOVE")&&!confirmRemoveFriend.equals(request.target())){confirmRemoveFriend=request.target();BattlefieldAudio.play(BattlefieldAudio.Event.UI_SELECT);return;}
                confirmDisband=false;confirmRemoveFriend="";SocialClient.action(request.kind(),request.target(),request.value());BattlefieldAudio.play(BattlefieldAudio.Event.UI_CONFIRM);
            }return;
        }
        int split=id.indexOf(':');if(split<0)return;String key=id.substring(split+1);
        switch(id.substring(0,split)){case "friend"->selectedFriend=key;case "friend-request"->selectedFriendRequest=key;case "squad-invitation"->selectedSquadInvitation=key;case "member"->selectedMember=key;case "player"->selectedPlayer=key;case "party"->selectedParty=key;case "invitation"->selectedInvitation=key;case "squad"->{selectedSquad=key;selectedSquadMember="";updateSelection();rightPage=0;}case "squad-member"->selectedSquadMember=key;default->{return;}}
        BattlefieldAudio.play(BattlefieldAudio.Event.UI_SELECT);
    }
    @Override public boolean mouseScrolled(double x,double y,double amount){
        if(amount==0)return false;var p=local(x,y);boolean left=p[0]<BattlefieldSocialLayout.right(BattlefieldUiTheme.current()).x();activate((left?"left:":"right:")+(amount<0?"next":"previous"));return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_F6){BattlefieldUiTheme.cycle();return true;}
        if(key==GLFW.GLFW_KEY_TAB||key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){
            var enabled=hits.stream().filter(Hit::enabled).toList();if(enabled.isEmpty())return true;int at=-1;for(int i=0;i<enabled.size();i++)if(enabled.get(i).id().equals(focus)){at=i;break;}
            int step=key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_TAB&&(modifiers&GLFW.GLFW_MOD_SHIFT)!=0?-1:1;
            int nextIndex=at<0?(step<0?enabled.size()-1:0):Math.floorMod(at+step,enabled.size());
            Hit next=enabled.get(nextIndex);focus=next.id();keyboardFocus=true;minecraft.getNarrator().sayNow(Component.literal(next.label()));return true;
        }
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER||key==GLFW.GLFW_KEY_SPACE){for(Hit hit:hits)if(hit.id().equals(focus)&&hit.enabled()){activate(hit.id());return true;}}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose(){BattlefieldAudio.play(BattlefieldAudio.Event.UI_BACK);minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private final class Canvas extends BattlefieldPovOverlay.GameCanvas implements BattlefieldSocialLayout.Canvas {
        Canvas(GuiGraphics g){super(g,BattlefieldUiTheme.current(),true);}
        public void avatar(UUID id,String name,Rect box){ProfileClient.avatar(g,ProfileClient.identity(id,name),box);}
    }
}
