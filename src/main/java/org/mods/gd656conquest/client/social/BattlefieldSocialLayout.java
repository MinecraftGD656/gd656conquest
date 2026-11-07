package org.mods.gd656conquest.client.social;

import java.util.*;
import org.mods.gd656killicon.client.gui.theme.BattlefieldDeploymentLayout;
import org.mods.gd656killicon.client.gui.theme.BattlefieldUiTheme;
import static org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.*;
import static org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.*;

/**
 * The three social compositions share server-backed rows and exact click geometry.
 *
 * <p>BF6 uses the loadout-style composition (26px header with small tabs, two list columns and a
 * footer that owns 刷新 / 返回). BF1 and BF2042 keep the original composition untouched.
 */
public final class BattlefieldSocialLayout {
    public static final int WIDTH=960,HEIGHT=450,ROWS=6;
    /** BF6 lists are taller than the legacy composition, so they page differently. */
    public static final int BF6_ROWS=8;
    public enum Tab { FRIENDS, PARTY, SQUAD }
    public enum Directory { PLAYERS, PARTIES, INVITATIONS, FRIENDS, FRIEND_REQUESTS, SENT_FRIEND_REQUESTS, MEMBERS, SQUAD_INVITATIONS }
    public record Row(String key,String title,String detail,String badge,UUID avatar,String classKey,boolean leader,boolean online,boolean selected) {}
    public record Command(String id,String label,boolean enabled,String reason,boolean danger) {}
    public record View(Tab tab,Directory directory,String context,String leftTitle,String rightTitle,List<Row> left,List<Row> right,
                       List<Command> leftActions,List<Command> rightActions,int leftPage,int leftPages,int rightPage,int rightPages,
                       String message,boolean loading,boolean pending,int friendRequestCount,int squadInvitationCount) {}
    public record Hit(String id,String label,Rect box,boolean enabled,String hint) {}
    public interface Canvas extends BattlefieldDeploymentLayout.Canvas {void avatar(UUID id,String name,Rect box);}
    private BattlefieldSocialLayout() {}
    private static void outline(Canvas c,Rect r,int color,float line){c.hairline(r,color);}

    // BF6 composition metrics (the other two themes keep their original rectangles below).
    private static final int BF6_HEADER_H=26,BF6_FOOTER_H=24,BF6_ROW_H=34;
    private static final int BF6_LEFT_X=14,BF6_LEFT_W=460,BF6_RIGHT_X=482,BF6_RIGHT_W=464;
    private static final int BF6_TITLE_Y=31,BF6_LIST_TOP=54,BF6_LIST_BOTTOM=352,BF6_ACTIONS_BOTTOM=418;

    public static Rect left(Theme t){return switch(t){case BF1->new Rect(30,100,345,246);case BF2042->new Rect(24,100,372,246);case BF6->new Rect(BF6_LEFT_X,BF6_LIST_TOP,BF6_LEFT_W,BF6_LIST_BOTTOM-BF6_LIST_TOP);};}
    public static Rect right(Theme t){return switch(t){case BF1->new Rect(419,100,511,246);case BF2042->new Rect(430,100,506,246);case BF6->new Rect(BF6_RIGHT_X,BF6_LIST_TOP,BF6_RIGHT_W,BF6_LIST_BOTTOM-BF6_LIST_TOP);};}
    public static boolean inside(Rect r,double x,double y){return x>=r.x()&&x<r.x()+r.w()&&y>=r.y()&&y<r.y()+r.h();}

    public static List<Hit> render(Canvas c,Theme t,View d,double mx,double my,String focus){
        if(t==Theme.BF6)return bf6(c,d,mx,my,focus);
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
        Directory[] directories=directories(d);
        for(int i=0;i<directories.length;i++){
            Directory dir=directories[i];String label=directoryLabel(dir,d);
            tab(c,t,new Rect(r.x()+i*r.w()/directories.length,r.y()-4,r.w()/directories.length-3,27),"dir:"+dir.name(),label,d.directory()==dir,mx,my,focus,hits);
        }
        String leftEmpty=d.loading()?"读取成员中":switch(d.tab()){case FRIENDS->"从右侧选择玩家，发送好友申请";case PARTY->"创建队伍，或从右侧加入队伍";case SQUAD->"在大厅组队邀请玩家，入场后编为同一小队";};
        list(c,t,d.left(),new Rect(l.x(),129,l.w(),216),leftEmpty,mx,my,focus,hits);
        String empty=d.loading()?"读取列表中":switch(d.directory()){case PLAYERS->d.tab()==Tab.SQUAD?"当前没有可邀请的同阵营玩家":"当前没有其他在线玩家";case PARTIES->"当前没有其他队伍";case INVITATIONS->"暂时没有组队邀请或申请";case FRIENDS->"添加好友后，可在这里邀请入队";case FRIEND_REQUESTS->"暂时没有收到的好友申请";case SENT_FRIEND_REQUESTS->"暂时没有已发出的好友申请";case MEMBERS->"选择小队查看成员";case SQUAD_INVITATIONS->"收到小队邀请后，可在这里接受或拒绝";};
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

    // ------------------------------------------------------------------ BF6

    private static List<Hit> bf6(Canvas c,View d,double mx,double my,String focus){
        var hits=new ArrayList<Hit>();
        var p=BattlefieldUiTheme.palette();
        c.rect(0,0,WIDTH,HEIGHT,0x9e0b1014);
        c.rect(0,0,WIDTH,BF6_HEADER_H,p.header());
        c.rect(0,BF6_HEADER_H-1,WIDTH,1,p.line());
        c.text("小队与好友",Face.HEADING,12,8,7,p.text(),Align.LEFT);
        float x=c.textWidth("小队与好友",Face.HEADING,12)+41;
        Tab[] tabs={Tab.FRIENDS,Tab.PARTY,Tab.SQUAD};
        String[] labels={"好友","组队","局内小队"};
        String[] ids={"tab:friends","tab:party","tab:squad"};
        for(int i=0;i<tabs.length;i++){
            String label=labels[i]+(tabs[i]==Tab.FRIENDS&&d.friendRequestCount()>0?" "+d.friendRequestCount()
                :tabs[i]==Tab.SQUAD&&d.squadInvitationCount()>0?" "+d.squadInvitationCount():"");
            float w=c.textWidth(label,Face.HEADING,12)+14;
            bf6HeaderTab(c,new Rect(x,2,w,24),ids[i],label,d.tab()==tabs[i],p,mx,my,focus,hits);
            x+=w+2;
        }
        String right=d.pending()?"处理中":d.context();
        c.text(right,Face.TEXT,11,WIDTH-8,8,d.pending()?p.accent():p.muted(),Align.RIGHT);

        // The action strip is anchored to the footer and the lists stretch down to it.
        int actionRows=Math.max(rowsOf(d.leftActions()),rowsOf(d.rightActions()));
        int actionsH=actionRows*26+(actionRows-1)*6;
        int actionsTop=Math.max(BF6_LIST_TOP+BF6_ROWS*BF6_ROW_H+6,BF6_ACTIONS_BOTTOM-actionsH);
        int listBottom=actionsTop-6;
        Rect l=new Rect(BF6_LEFT_X,BF6_LIST_TOP,BF6_LEFT_W,listBottom-BF6_LIST_TOP);
        Rect r=new Rect(BF6_RIGHT_X,BF6_LIST_TOP,BF6_RIGHT_W,listBottom-BF6_LIST_TOP);
        c.text(d.leftTitle(),Face.HEADING,12,l.x(),BF6_TITLE_Y+4,p.text(),Align.LEFT);
        bf6Pager(c,l,d.leftPage(),d.leftPages(),"left",p,mx,my,focus,hits);
        c.rect(l.x(),l.y(),l.w(),l.h(),0x48070c11);
        bf6List(c,d.left(),l,"暂无",p,mx,my,focus,hits);
        bf6Commands(c,d.leftActions(),new Rect(l.x(),actionsTop,l.w(),BF6_ACTIONS_BOTTOM-actionsTop),p,mx,my,focus,hits);

        // Right column: directory tabs over the candidate list.
        float dx=r.x();
        for(Directory dir:directories(d)){
            String label=directoryLabel(dir,d);
            float w=c.textWidth(label,Face.HEADING,12)+14;
            bf6HeaderTab(c,new Rect(dx,BF6_TITLE_Y,Math.min(w,r.w()),22),"dir:"+dir.name(),label,d.directory()==dir,p,mx,my,focus,hits);
            dx+=w+2;
        }
        bf6Pager(c,r,d.rightPage(),d.rightPages(),"right",p,mx,my,focus,hits);
        c.rect(r.x(),r.y(),r.w(),r.h(),0x48070c11);
        bf6List(c,d.right(),r,"暂无",p,mx,my,focus,hits);
        bf6Commands(c,d.rightActions(),new Rect(r.x(),actionsTop,r.w(),BF6_ACTIONS_BOTTOM-actionsTop),p,mx,my,focus,hits);

        // Footer: 刷新 bottom-left, 返回 bottom-right, same geometry as the loadout page.
        c.rect(0,HEIGHT-BF6_FOOTER_H,WIDTH,BF6_FOOTER_H,p.header());
        c.rect(0,HEIGHT-BF6_FOOTER_H,WIDTH,1,p.line());
        bf6Button(c,new Command("refresh","刷新",!d.pending(),"",false),new Rect(10,HEIGHT-20,58,17),p,mx,my,focus,hits);
        bf6Button(c,new Command("back","返回",true,"",false),new Rect(WIDTH-104,HEIGHT-20,94,17),p,mx,my,focus,hits);
        String note=d.message();
        for(Hit hit:hits)if(!hit.hint().isBlank()&&(inside(hit.box(),mx,my)||hit.id().equals(focus))){note=hit.hint();break;}
        if(!note.isBlank())c.fitted(note,Face.TEXT,10,new Rect(76,HEIGHT-BF6_FOOTER_H+7,WIDTH-190,14),p.text(),Align.CENTER);
        return List.copyOf(hits);
    }

    private static void bf6HeaderTab(Canvas c,Rect r,String id,String label,boolean selected,BattlefieldUiTheme.Palette p,
            double mx,double my,String focus,List<Hit> hits){
        boolean over=inside(r,mx,my)||id.equals(focus);
        if(selected){c.rect(r.x(),r.y(),r.w(),r.h(),0x283e5059);c.rect(r.x(),r.y()+r.h()-2,r.w(),2,p.accent());}
        else if(over)c.rect(r.x(),r.y(),r.w(),r.h(),p.panel());
        c.fitted(label,Face.HEADING,12,r,selected?p.text():over?p.text():p.muted(),Align.CENTER);
        hits.add(new Hit(id,label,r,true,""));
    }

    private static void bf6List(Canvas c,List<Row> rows,Rect area,String empty,BattlefieldUiTheme.Palette p,
            double mx,double my,String focus,List<Hit> hits){
        if(rows.isEmpty()){
            c.fitted(empty,Face.TEXT,12,new Rect(area.x(),area.y()+(area.h()-16)/2,area.w(),16),p.muted(),Align.CENTER);
            return;
        }
        int visible=Math.min(BF6_ROWS,(int)(area.h()/BF6_ROW_H));
        for(int i=0;i<Math.min(visible,rows.size());i++){
            Row row=rows.get(i);
            Rect box=new Rect(area.x(),area.y()+i*BF6_ROW_H,area.w(),BF6_ROW_H-1);
            boolean over=inside(box,mx,my)||row.key().equals(focus);
            c.rect(box.x(),box.y(),box.w(),box.h(),row.selected()?0xc8495b51:over?0xb0424d53:0x8a1b242a);
            if(row.selected())c.rect(box.x(),box.y(),2,box.h(),0xffcbd5d6);
            else if(over)c.hairline(box,0x80dce4e5);
            int color=row.online()?p.text():p.muted();
            float x=box.x()+10;
            if(row.avatar()!=null){c.avatar(row.avatar(),row.title(),new Rect(x,box.y()+5,24,24));x+=32;}
            else {c.fitted(row.badge().isBlank()?"—":row.badge().replace(" / ","/"),Face.TEXT,12,new Rect(x,box.y()+4,44,26),color,Align.CENTER);x+=54;}
            float badgeWidth=row.avatar()!=null&&!row.badge().isBlank()?55:0;
            c.fitted(row.title(),Face.HEADING,13,new Rect(x,box.y()+4,box.x()+box.w()-x-12-badgeWidth-(row.leader()?15:0),16),color,Align.LEFT);
            if(!row.detail().isBlank())c.fitted(row.detail(),Face.TEXT,10,new Rect(x,box.y()+21,box.x()+box.w()-x-12,11),p.muted(),Align.LEFT);
            if(badgeWidth>0)c.fitted(row.badge(),Face.TEXT,11,new Rect(box.x()+box.w()-65,box.y()+5,53,15),color,Align.RIGHT);
            if(row.leader())leader(c,box.x()+box.w()-10-badgeWidth,box.y()+12,color);
            hits.add(new Hit(row.key(),row.title(),box,true,row.detail()));
        }
    }

    private static void bf6Pager(Canvas c,Rect area,int page,int pages,String key,BattlefieldUiTheme.Palette p,
            double mx,double my,String focus,List<Hit> hits){
        if(pages<=1)return;
        c.fitted((page+1)+" / "+pages,Face.NUMBER,10,new Rect(area.x()+area.w()-104,BF6_TITLE_Y+4,44,14),p.muted(),Align.RIGHT);
        bf6Button(c,new Command(key+":previous","<",page>0,"",false),new Rect(area.x()+area.w()-56,BF6_TITLE_Y+2,26,17),p,mx,my,focus,hits);
        bf6Button(c,new Command(key+":next",">",page+1<pages,"",false),new Rect(area.x()+area.w()-27,BF6_TITLE_Y+2,27,17),p,mx,my,focus,hits);
    }

    private static void bf6Commands(Canvas c,List<Command> commands,Rect area,BattlefieldUiTheme.Palette p,
            double mx,double my,String focus,List<Hit> hits){
        if(commands.isEmpty())return;
        int count=commands.size(),columns=count>3?2:1;float gap=6,h=26;
        float width=(area.w()-gap*(columns-1))/columns;
        for(int i=0;i<count;i++){
            float x=area.x()+(i%columns)*(width+gap),y=area.y()+(i/columns)*(h+gap);
            if(y+h>area.y()+area.h())break;
            bf6Button(c,commands.get(i),new Rect(x,y,width,h),p,mx,my,focus,hits);
        }
    }

    /** Same palette as GDButton / BattlefieldUiTheme.button, so both pages' buttons match. */
    private static void bf6Button(Canvas c,Command command,Rect r,BattlefieldUiTheme.Palette p,
            double mx,double my,String focus,List<Hit> hits){
        boolean over=inside(r,mx,my)||command.id().equals(focus);
        boolean hot=over&&command.enabled();
        c.rect(r.x(),r.y(),r.w(),r.h(),hot?p.selection():p.panel());
        c.hairline(r,hot?p.accent():p.line());
        int label=!command.enabled()?p.muted():hot?p.onSelection():p.text();
        c.fitted(command.label(),Face.HEADING,11,new Rect(r.x()+4,r.y()+1,r.w()-8,r.h()-2),label,Align.CENTER);
        hits.add(new Hit(command.id(),command.label(),r,command.enabled(),command.reason()));
    }

    /** Same column rule as {@link #bf6Commands}: single column up to three commands, then two. */
    private static int rowsOf(List<Command> commands){
        if(commands==null||commands.isEmpty())return 0;
        int count=commands.size();
        return count>3?(count+1)/2:count;
    }

    private static Directory[] directories(View d){
        return switch(d.tab()){
            case FRIENDS->new Directory[]{Directory.PLAYERS,Directory.FRIEND_REQUESTS,Directory.SENT_FRIEND_REQUESTS};
            case PARTY->new Directory[]{Directory.FRIENDS,Directory.PLAYERS,Directory.PARTIES,Directory.INVITATIONS};
            case SQUAD->new Directory[]{Directory.MEMBERS,Directory.FRIENDS,Directory.PLAYERS,Directory.SQUAD_INVITATIONS};
        };
    }

    private static String directoryLabel(Directory dir,View d){
        return switch(dir){
            case PLAYERS->d.tab()==Tab.SQUAD?"同阵营玩家":"在线玩家";
            case PARTIES->"寻找队伍";case INVITATIONS->"组队邀请";case FRIENDS->"我的好友";
            case FRIEND_REQUESTS->"收到申请"+(d.friendRequestCount()>0?" · "+d.friendRequestCount():"");
            case SENT_FRIEND_REQUESTS->"已发出";
            case MEMBERS->"小队成员";case SQUAD_INVITATIONS->"小队邀请"+(d.squadInvitationCount()>0?" · "+d.squadInvitationCount():"");
        };
    }

    // ------------------------------------------------------------------ BF1 / BF2042 (original)

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
