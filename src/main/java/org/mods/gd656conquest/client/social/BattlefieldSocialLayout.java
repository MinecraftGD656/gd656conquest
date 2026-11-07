package org.mods.gd656conquest.client.social;

import java.util.*;
import org.mods.gd656killicon.client.gui.theme.BattlefieldDeploymentLayout;
import static org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.*;
import static org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.*;

/** The three social compositions share server-backed rows and exact click geometry. */
public final class BattlefieldSocialLayout {
    public static final int WIDTH=960,HEIGHT=450,ROWS=6;
    public enum Tab { FRIENDS, PARTY, SQUAD }
    public enum Directory { PLAYERS, PARTIES, INVITATIONS, FRIENDS, FRIEND_REQUESTS, MEMBERS, SQUAD_INVITATIONS }
    public record Row(String key,String title,String detail,String badge,UUID avatar,String classKey,boolean leader,boolean online,boolean selected) {}
    public record Command(String id,String label,boolean enabled,String reason,boolean danger) {}
    public record View(Tab tab,Directory directory,String context,String leftTitle,String rightTitle,List<Row> left,List<Row> right,
                       List<Command> leftActions,List<Command> rightActions,int leftPage,int leftPages,int rightPage,int rightPages,
                       String message,boolean loading,boolean pending,int friendRequestCount,int squadInvitationCount) {}
    public record Hit(String id,String label,Rect box,boolean enabled,String hint) {}
    public interface Canvas extends BattlefieldDeploymentLayout.Canvas {void avatar(UUID id,String name,Rect box);}
    private BattlefieldSocialLayout() {}
    private static void outline(Canvas c,Rect r,int color,float line){c.rect(r.x(),r.y(),r.w(),line,color);c.rect(r.x(),r.y()+r.h()-line,r.w(),line,color);c.rect(r.x(),r.y(),line,r.h(),color);c.rect(r.x()+r.w()-line,r.y(),line,r.h(),color);}
    public static Rect left(Theme t){return switch(t){case BF1->new Rect(30,100,345,246);case BF2042->new Rect(24,100,372,246);case BF6->new Rect(28,100,440,246);};}
    public static Rect right(Theme t){return switch(t){case BF1->new Rect(419,100,511,246);case BF2042->new Rect(430,100,506,246);case BF6->new Rect(504,100,428,246);};}
    public static boolean inside(Rect r,double x,double y){return x>=r.x()&&x<r.x()+r.w()&&y>=r.y()&&y<r.y()+r.h();}
    public static List<Hit> render(Canvas c,Theme t,View d,double mx,double my,String focus){
        var hits=new ArrayList<Hit>();int fg=ink(t),muted=t==Theme.BF2042?0xffa1ccc4:0xffb8c4c7,accent=t==Theme.BF2042?0xff32f5d5:t==Theme.BF1?0xffeee9da:0xffcbd5d6;
        int base=t==Theme.BF1?0xd9182024:t==Theme.BF2042?0xee071c22:0xf20b1014;
        c.rect(0,0,WIDTH,HEIGHT,base);
        if(t==Theme.BF2042){c.rect(0,0,285,53,accent);c.text("小队与好友",Face.HEADING,27,24,13,0xff082c2b,Align.LEFT);}
        else if(t==Theme.BF1){c.text("小队与好友",Face.LIGHT,30,30,14,fg,Align.LEFT);c.line(30,57,930,57,1,0x658f9b9c);}
        else {c.text("小队与好友",Face.HEADING,30,28,12,fg,Align.LEFT);c.line(28,56,932,56,1,0x687d8c91);}
        tab(c,t,new Rect(300,8,139,39),"tab:friends",d.friendRequestCount()>0?"好友 · 新申请":"好友",d.tab()==Tab.FRIENDS,mx,my,focus,hits);
        tab(c,t,new Rect(443,8,139,39),"tab:party","组队",d.tab()==Tab.PARTY,mx,my,focus,hits);
        tab(c,t,new Rect(586,8,164,39),"tab:squad",d.squadInvitationCount()>0?"局内小队 · 新邀请":"局内小队",d.tab()==Tab.SQUAD,mx,my,focus,hits);
        button(c,t,new Command("refresh","刷新",!d.pending(),"",false),new Rect(768,13,74,29),mx,my,focus,hits);
        button(c,t,new Command("back","返回",true,"",false),new Rect(852,13,80,29),mx,my,focus,hits);
        c.fitted(d.context(),Face.TEXT,12,new Rect(t==Theme.BF1?30:24,65,710,22),muted,Align.LEFT);
        if(d.pending())c.fitted("正在处理",Face.TEXT,12,new Rect(788,65,144,22),accent,Align.RIGHT);
        Rect l=left(t),r=right(t);
        c.fitted(d.leftTitle(),Face.HEADING,t==Theme.BF1?17:16,new Rect(l.x(),l.y()-2,l.w(),24),fg,Align.LEFT);
        Directory[] directories=switch(d.tab()){
            case FRIENDS->new Directory[]{Directory.PLAYERS,Directory.FRIEND_REQUESTS};
            case PARTY->new Directory[]{Directory.FRIENDS,Directory.PLAYERS,Directory.PARTIES,Directory.INVITATIONS};
            case SQUAD->new Directory[]{Directory.MEMBERS,Directory.FRIENDS,Directory.PLAYERS,Directory.SQUAD_INVITATIONS};
        };
        for(int i=0;i<directories.length;i++){
            Directory dir=directories[i];String label=switch(dir){
                case PLAYERS->d.tab()==Tab.SQUAD?"同阵营玩家":"在线玩家";
                case PARTIES->"寻找队伍";case INVITATIONS->"组队邀请";case FRIENDS->"我的好友";
                case FRIEND_REQUESTS->"好友申请"+(d.friendRequestCount()>0?" · "+d.friendRequestCount():"");
                case MEMBERS->"小队成员";case SQUAD_INVITATIONS->"小队邀请"+(d.squadInvitationCount()>0?" · "+d.squadInvitationCount():"");
            };
            tab(c,t,new Rect(r.x()+i*r.w()/directories.length,r.y()-4,r.w()/directories.length-3,27),"dir:"+dir.name(),label,d.directory()==dir,mx,my,focus,hits);
        }
        String leftEmpty=d.loading()?"读取成员中":switch(d.tab()){case FRIENDS->"从右侧选择玩家，发送好友申请";case PARTY->"创建队伍，或从右侧加入队伍";case SQUAD->"在大厅组队邀请玩家，入场后编为同一小队";};
        list(c,t,d.left(),new Rect(l.x(),129,l.w(),216),leftEmpty,mx,my,focus,hits);
        String empty=d.loading()?"读取列表中":switch(d.directory()){case PLAYERS->d.tab()==Tab.SQUAD?"当前没有可邀请的同阵营玩家":"当前没有其他在线玩家";case PARTIES->"当前没有其他队伍";case INVITATIONS->"暂时没有组队邀请或申请";case FRIENDS->"添加好友后，可在这里邀请入队";case FRIEND_REQUESTS->"收到和发出的好友申请会显示在这里";case MEMBERS->"选择小队查看成员";case SQUAD_INVITATIONS->"收到小队邀请后，可在这里接受或拒绝";};
        list(c,t,d.right(),new Rect(r.x(),129,r.w(),216),empty,mx,my,focus,hits);
        pager(c,t,l,d.leftPage(),d.leftPages(),"left",mx,my,focus,hits);
        pager(c,t,r,d.rightPage(),d.rightPages(),"right",mx,my,focus,hits);
        commands(c,t,d.leftActions(),l.x(),376,l.w(),mx,my,focus,hits);
        commands(c,t,d.rightActions(),r.x(),376,r.w(),mx,my,focus,hits);
        c.line(24,423,936,423,1,0x507c969c);
        String note=d.message();
        for(var hit:hits)if(!hit.hint().isBlank()&&(inside(hit.box(),mx,my)||hit.id().equals(focus))){note=hit.hint();break;}
        c.fitted(note.isBlank()?"Enter  执行     Tab / 方向键  选择":note,Face.TEXT,11,new Rect(28,429,904,17),note.isBlank()?muted:fg,Align.LEFT);
        return List.copyOf(hits);
    }
    private static void list(Canvas c,Theme t,List<Row> rows,Rect area,String empty,double mx,double my,String focus,List<Hit> hits){
        int fg=ink(t),muted=t==Theme.BF2042?0xffa1ccc4:0xffb8c4c7;
        if(rows.isEmpty()){c.rect(area.x(),area.y(),area.w(),216,0x32000000);c.fitted(empty,Face.TEXT,13,new Rect(area.x()+14,area.y()+76,area.w()-28,54),muted,Align.CENTER);return;}
        for(int i=0;i<Math.min(ROWS,rows.size());i++){
            Row row=rows.get(i);Rect box=new Rect(area.x(),area.y()+i*36,area.w(),34);boolean over=inside(box,mx,my)||row.key().equals(focus);
            int bg=t==Theme.BF2042?row.selected()?0xff32f5d5:over?0xff1d464c:0xa30c2c34:t==Theme.BF1?row.selected()?0xe9e9e6dc:over?0xa95c6668:0x800b1115:row.selected()?0xc8495b51:over?0xe2424d53:0xc91b242a;
            int color=row.selected()&&t!=Theme.BF6?0xff102526:row.online()?fg:muted;
            c.rect(box.x(),box.y(),box.w(),box.h(),bg);
            if(t==Theme.BF6&&over)outline(c,box,0xffdce4e5,1);
            else c.line(box.x(),box.y()+box.h(),box.x()+box.w(),box.y()+box.h(),.7f,t==Theme.BF1?0x607d8585:0x7059797c);
            float x=box.x()+10;
            if(row.avatar()!=null){c.avatar(row.avatar(),row.title(),new Rect(x,box.y()+5,24,24));x+=32;}
            else {c.fitted(row.badge().isBlank()?"—":row.badge().replace(" / ","/"),Face.TEXT,12,new Rect(x,box.y()+3,44,28),color,Align.CENTER);x+=54;}
            float badgeWidth=row.avatar()!=null&&!row.badge().isBlank()?55:0;
            c.fitted(row.title(),t==Theme.BF1?Face.TEXT:Face.HEADING,13,new Rect(x,box.y()+3,box.x()+box.w()-x-12-badgeWidth-(row.leader()?15:0),16),color,Align.LEFT);
            c.fitted(row.detail(),Face.TEXT,10,new Rect(x,box.y()+20,box.x()+box.w()-x-12,11),row.selected()&&t!=Theme.BF6?0xff284446:muted,Align.LEFT);
            if(badgeWidth>0)c.fitted(row.badge(),Face.TEXT,11,new Rect(box.x()+box.w()-65,box.y()+4,53,15),color,Align.RIGHT);
            if(row.leader()){float sx=box.x()+box.w()-10-badgeWidth;leader(c,sx,box.y()+11,color);}
            hits.add(new Hit(row.key(),row.title(),box,true,row.detail()));
        }
    }
    private static void leader(Canvas c,float x,float y,int color){
        for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5,b=a+Math.PI/5;float r=i%2==0?5:2.2f,n=i%2==0?2.2f:5;c.line(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,x+(float)Math.cos(b)*n,y+(float)Math.sin(b)*n,.8f,color);}
    }
    private static void pager(Canvas c,Theme t,Rect region,int page,int pages,String key,double mx,double my,String focus,List<Hit> hits){
        if(pages<=1)return;
        c.fitted((page+1)+" / "+pages,Face.NUMBER,11,new Rect(region.x(),349,region.w()-130,20),ink(t),Align.LEFT);
        button(c,t,new Command(key+":previous","上一页",page>0,"",false),new Rect(region.x()+region.w()-124,349,58,20),mx,my,focus,hits);
        button(c,t,new Command(key+":next","下一页",page+1<pages,"",false),new Rect(region.x()+region.w()-60,349,60,20),mx,my,focus,hits);
    }
    private static void commands(Canvas c,Theme t,List<Command> commands,float x,float y,float width,double mx,double my,String focus,List<Hit> hits){
        if(commands.isEmpty())return;int count=commands.size(),columns=count>4?(count+1)/2:count;float gap=5,w=(width-gap*(columns-1))/columns;
        for(int i=0;i<count;i++)button(c,t,commands.get(i),new Rect(x+(i%columns)*(w+gap),count>4?371+(i/columns)*25:y,w,count>4?23:32),mx,my,focus,hits);
    }
    private static void tab(Canvas c,Theme t,Rect r,String id,String label,boolean selected,double mx,double my,String focus,List<Hit> hits){
        boolean over=inside(r,mx,my)||id.equals(focus);int accent=t==Theme.BF2042?0xff32f5d5:0xffe8eeee;
        if(t==Theme.BF2042&&selected)c.rect(r.x(),r.y(),r.w(),r.h(),accent);
        else if(over)c.rect(r.x(),r.y(),r.w(),r.h(),0x5052737a);
        if(selected&&t!=Theme.BF2042)c.line(r.x()+5,r.y()+r.h()-1,r.x()+r.w()-5,r.y()+r.h()-1,t==Theme.BF1?1:2,accent);
        c.fitted(label,t==Theme.BF1?Face.TEXT:Face.HEADING,13,r,t==Theme.BF2042&&selected?0xff0b2b2a:ink(t),Align.CENTER);hits.add(new Hit(id,label,r,true,""));
    }
    private static void button(Canvas c,Theme t,Command command,Rect r,double mx,double my,String focus,List<Hit> hits){
        boolean over=inside(r,mx,my)||command.id().equals(focus);int accent=command.danger()?0xffe9a58e:t==Theme.BF2042?0xff32f5d5:0xffd4dfe1;
        c.rect(r.x(),r.y(),r.w(),r.h(),over&&command.enabled()?accent:t==Theme.BF1?0x501b262a:0xff1b3035);
        if(t==Theme.BF1)c.line(r.x(),r.y()+r.h()-1,r.x()+r.w(),r.y()+r.h()-1,1,command.enabled()?accent:0xff59666a);
        else outline(c,r,command.enabled()?over?accent:0xff687f84:0xff46575c,1);
        c.fitted(command.label(),t==Theme.BF1?Face.TEXT:Face.HEADING,12,new Rect(r.x()+4,r.y()+2,r.w()-8,r.h()-4),!command.enabled()?0xff87999e:over?0xff10292b:accent,Align.CENTER);
        hits.add(new Hit(command.id(),command.label(),r,command.enabled(),command.reason()));
    }
}
