package tests;

import java.util.UUID;
import org.mods.gd656conquest.common.player.DownedDeployModel;
import org.mods.gd656conquest.client.room.RoomChatLayout;
import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.common.player.KillerCardInfo;
import org.mods.gd656conquest.client.room.KillerCardModel;
import org.mods.gd656conquest.client.room.KillerCardLayout;
import org.mods.gd656conquest.client.room.DeathFilterTransition;
import org.mods.gd656conquest.server.room.entry.MatchDuelStats;

public final class KillerCardBehaviorTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        UUID self=UUID.randomUUID(),enemy=UUID.randomUUID(),third=UUID.randomUUID();
        KillerCardInfo info=new KillerCardInfo(enemy,"Player",48,14.2F,20,0,2,"AK-47","tacz:ak47","");
        check(info.healthPercent()==71,"HP percentage uses snapshot maximum");
        var model=new KillerCardModel();
        check(!model.accept(1,info),"metadata without accepted death cause cannot open card");
        for(DeathCause cause:DeathCause.values())if(cause!=DeathCause.PLAYER_KILL){
            model.reset();check(model.begin(1,cause),"nonplayer cause accepted");
            check(!model.accept(1,info)&&model.info()==null,"no killer columns for "+cause);
        }
        model.reset();check(model.begin(10,DeathCause.PLAYER_KILL)&&model.accept(10,info),"player kill opens snapshot");
        check(!model.accept(9,info)&&!model.accept(11,info),"wrong death metadata rejected");
        model.close();check(model.info()==null&&!model.accept(10,info)&&!model.begin(10,DeathCause.PLAYER_KILL),"revive/final death instantly close without replay");
        check(model.begin(11,DeathCause.PLAYER_KILL)&&model.info()==null,"new death does not reuse old killer");
        check(model.accept(11,info),"new death metadata accepted");
        var stats=new MatchDuelStats();
        check(stats.record(1,10,enemy,self).equals(new MatchDuelStats.Score(0,1)),"current fatal encounter included");
        check(stats.record(1,10,enemy,self).equals(new MatchDuelStats.Score(0,1)),"same fatal event counted once");
        check(stats.record(1,11,self,enemy).equals(new MatchDuelStats.Score(1,1)),"reciprocal combat uses viewing player's orientation");
        check(stats.record(1,12,third,self).equals(new MatchDuelStats.Score(0,1)),"third player independent");
        check(stats.record(1,13,enemy,self).equals(new MatchDuelStats.Score(1,2)),"revival then another accepted fatal hit adds one");
        check(stats.record(2,14,enemy,self).equals(new MatchDuelStats.Score(0,1)),"new round resets duel ledger");
        for(int width:new int[]{320,480,640,960,1280})for(int height:new int[]{180,270,540})for(int rescuers=0;rescuers<=4;rescuers++)for(boolean full:new boolean[]{false,true}){
            var box=KillerCardLayout.calculate(width,height,full,rescuers,0,0,0);
            check(box.left()==0&&box.top()>=0&&Math.abs(box.width()*box.scale()-width)<.1F,"footer fills GUI width");
            check(Math.abs(box.top()+box.height()*box.scale()-height)<.1F,"footer exactly touches bottom edge");
            check(box.avatarWidth()>16 && box.playerWidth()>20 && box.weaponWidth()>20,"empty killer fields retain allocated space");
            check(Math.abs(box.contentLeft() / (float)box.width() - .08F)<.002F && box.contentRight()==box.width()-box.contentLeft(),"content centered with equal eight-percent outer margins");
            check(Math.abs(box.weaponWidth()-box.actionWidth())<=1 && Math.abs(box.rescueWidth()-box.actionWidth())<=2,"weapon action and rescue columns have equal practical width");
            check(box.rescueLeft()+box.rescueWidth()==box.contentRight(),"columns exactly fill content area without entering right margin");
            check(box.avatarWidth()+box.playerWidth()==Math.round((box.contentRight()-box.contentLeft())*.34F),"avatar and killer share thirty-four-percent region");
            if(full){var solo=KillerCardLayout.calculate(width,height,false,rescuers,0,0,0);
                check(box.equals(solo),"rescuable and confirmed/nonplayer deaths use identical footer dimensions and columns");}
            if(height>=270){var moved=KillerCardLayout.calculate(width,height,full,rescuers,width,height-140,height-36);
                check(moved.equals(box),"chat never moves fixed full-width footer");}
        }
        for(int width:new int[]{320,480,640,854,1280})for(int height:new int[]{180,270,480,720})
          for(double scale:new double[]{.5,.8,1})for(int lineHeight:new int[]{9,12,18})for(boolean focused:new boolean[]{false,true}) {
            var chat=RoomChatLayout.calculate(width,height,scale,lineHeight,focused,50);
            var footer=KillerCardLayout.calculate(width,height,true,4,0,0,0);
            check(chat.left()+chat.width()<width*.45,"chat avoids center HUD and right deployment actions");
            check(chat.top()>=height*.19&&chat.baseline()<=chat.inputY(),"chat starts below top HUD");
            check(chat.inputY()+12<=footer.top(),"input and messages avoid full-width footer");
            check(chat.capacity()<= (focused?14:8)&&chat.capacity()>=1,"rows limited to available space");
            check(Math.abs(chat.chatX(chat.left()+24*scale,scale)-24)<.001,"link x follows chat scale and position");
            check(Math.abs(chat.chatY(chat.baseline()-2*lineHeight*scale,scale,lineHeight)-2)<.001,"hover y follows chat baseline and line spacing");
          }
        var deploy=new DownedDeployModel();deploy.begin(10);
        check(!deploy.accept(9,true,0,300,100,false),"stale deployment snapshot rejected");
        check(deploy.accept(10,false,0,300,100,false)&&!deploy.ready()&&!deploy.enabled(),"disabled option never unlocks single press");
        deploy.accept(10,true,40,300,100,false);
        check(deploy.deploymentSeconds(100)==2&&deploy.deploymentSeconds(120)==1,"deployment seconds interpolate");
        check(deploy.deploymentSeconds(160)==1&&!deploy.ready(),"local timer cannot unlock before server zero");
        check(!deploy.press(true,true),"pre-ready jump uses original hold behavior");
        deploy.accept(10,true,0,260,140,true);
        check(deploy.ready()&&!deploy.press(true,true),"held space across readiness does not trigger");
        check(!deploy.press(false,true)&&deploy.press(true,true),"new single press after release returns to deployment");
        check(!deploy.press(true,true),"holding after press cannot repeat request");
        deploy.press(false,true);check(!deploy.press(true,false),"revive/chat lock prevents request");
        check(!deploy.press(true,true),"press held across revive end cannot trigger");
        deploy.press(false,true);check(deploy.press(true,true),"fresh unlocked press allowed");
        check(deploy.deathSeconds(160)==12,"bleedout timer independent of deployment readiness");
        deploy.close();check(!deploy.accept(10,true,0,0,200,false)&&!deploy.ready(),"revive/confirmed-death close rejects replay");
        deploy.begin(11);check(!deploy.ready()&&!deploy.enabled(),"next downed event clears readiness");
        var filter=new DeathFilterTransition();
        check(filter.advance(true,0)==0,"fade starts transparent");
        float half=filter.advance(true,DeathFilterTransition.FADE_IN_SECONDS/2)/DeathFilterTransition.MAX_DESATURATION;
        check(Math.abs(half-.5F)<.001F,"card shares filter smoothstep midpoint");
        check(Math.abs(filter.advance(true,DeathFilterTransition.FADE_IN_SECONDS)/DeathFilterTransition.MAX_DESATURATION-1)<.001F,"card fully opaque at filter completion");
        System.out.println("PASS killer card cause/lifetime/duel/layout/filter behavior: "+checks);
    }
}
