package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656killicon.client.gui.theme.*;
import java.util.*;
import static org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.*;
import static org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.*;

/** Native server selections in a modal over the live deployment camera. */
public final class DeploymentLoadoutScreen extends Screen {
    private record Choice(String id,String label,String detail,boolean enabled,boolean selected,ItemStack stack) {}
    private final Screen parent;
    private final int slotIndex;
    private List<Choice> choices=List.of();
    private String classKey="",heading="",pending="";
    private int page,focused;
    private long requestedAt;
    public DeploymentLoadoutScreen(Screen parent,int slotIndex){super(Component.literal("部署配装"));this.parent=parent;this.slotIndex=slotIndex;}
    @Override protected void init(){request();refresh();}
    private void request(){NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));}
    private void refresh(){
        var snapshot=ClientLoadoutUiState.snapshot();if(snapshot==null){choices=List.of();return;}
        classKey=snapshot.currentClassKey();var result=new ArrayList<Choice>();
        if(slotIndex<0){heading="选择兵种";for(var cls:snapshot.classes().values())result.add(new Choice(cls.classKey(),cls.className(),"",true,cls.classKey().equals(classKey),ItemStack.EMPTY));}
        else{var cls=snapshot.classes().get(classKey);var slot=cls==null?null:cls.slots().get(slotIndex);
            if(slot==null){choices=List.of();return;}heading=slot.slotLabel();
            for(var entry:slot.sortedEntries())result.add(new Choice(entry.id(),entry.displayName(),!entry.roomEnabled()?"本房间不可用":!entry.unlocked()&&!entry.trial()?"等级 "+entry.unlockLevel()+" 解锁":entry.trial()?"本房间试用":"",entry.roomEnabled()&&(entry.unlocked()||entry.trial()),entry.id().equals(slot.selectedId()),ClientLoadoutUiState.parseAndCacheStack(entry.itemSnbt())));
        }
        choices=List.copyOf(result);
        if(!pending.isEmpty()&&choices.stream().anyMatch(c->c.id().equals(pending)&&c.selected())){pending="";onClose();}
        page=Math.max(0,Math.min(page,Math.max(0,(choices.size()-1)/capacity())));
    }
    @Override public void tick(){if(!ClientDeathFlowState.shouldRenderDeployBoundaries()){onClose();return;}refresh();}
    private Rect panel(){return switch(BattlefieldUiTheme.current()){case BF6->new Rect(1220,190,640,720);case BF2042->new Rect(420,535,1080,390);case BF1->new Rect(62,214,580,675);};}
    private int columns(){return switch(BattlefieldUiTheme.current()){case BF6->2;case BF2042->4;case BF1->1;};}
    private int capacity(){return switch(BattlefieldUiTheme.current()){case BF6->10;case BF2042->8;case BF1->7;};}
    private Rect tile(int i){Rect p=panel();int cols=columns(),rows=capacity()/cols;float w=(p.w()-40)/cols,h=(p.h()-126)/rows;return new Rect(p.x()+20+(i%cols)*w,p.y()+68+(i/cols)*h,w-6,h-6);}
    private Rect closeRect(){Rect p=panel();return new Rect(p.x()+p.w()-100,p.y()+15,80,35);}
    private Rect previous(){Rect p=panel();return new Rect(p.x()+20,p.y()+p.h()-44,130,28);}
    private Rect next(){Rect p=panel();return new Rect(p.x()+p.w()-150,p.y()+p.h()-44,130,28);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        float scale=Math.min(width/1920f,height/1080f),ox=(width-1920*scale)/2,oy=(height-1080*scale)/2;double mx=(mouseX-ox)/scale,my=(mouseY-oy)/scale;
        g.pose().pushPose();g.pose().translate(ox,oy,0);g.pose().scale(scale,scale,1);
        var theme=BattlefieldUiTheme.current();var c=new BattlefieldPovOverlay.GameCanvas(g,theme);Rect p=panel();int accent=theme==Theme.BF2042?0xff45f4d4:0xfff0f1e8;
        c.rect(0,0,1920,1080,0x35000000);c.rect(p.x(),p.y(),p.w(),p.h(),theme==Theme.BF2042?0xf008272b:0xf0151c22);c.rect(p.x(),p.y(),p.w(),2,accent);
        c.fitted(heading.isBlank()?"载入配装":heading,Face.HEADING,24,new Rect(p.x()+20,p.y()+14,p.w()-140,40),accent,Align.LEFT);
        c.fitted("关闭",Face.TEXT,17,closeRect(),0xffe6eeed,Align.CENTER);
        int offset=page*capacity();
        for(int i=0;i<capacity()&&offset+i<choices.size();i++){
            Choice choice=choices.get(offset+i);Rect r=tile(i);boolean hover=contains(r,mx,my)||offset+i==focused;int fg=choice.enabled()?0xffeaf5f4:0xff7e8c8d;
            c.rect(r.x(),r.y(),r.w(),r.h(),choice.selected()?accent:hover?0x904b666c:0x80212f36);if(choice.selected())fg=0xff102227;
            if(!choice.stack().isEmpty())c.itemStack(choice.stack(),new Rect(r.x()+8,r.y()+11,Math.min(102,r.w()*.37f),r.h()-25),fg);
            float tx=choice.stack().isEmpty()?r.x()+14:r.x()+Math.min(122,r.w()*.42f);
            c.fitted(choice.label(),Face.TEXT,17,new Rect(tx,r.y()+12,r.x()+r.w()-tx-10,29),fg,Align.LEFT);
            c.fitted(choice.selected()?"已装备":choice.id().equals(pending)?"正在更新":choice.detail().isEmpty()?"选择":choice.detail(),Face.TEXT,13,new Rect(tx,r.y()+r.h()-31,r.x()+r.w()-tx-10,20),fg,Align.LEFT);
        }
        c.fitted("‹ 上一页",Face.TEXT,15,previous(),accent,Align.LEFT);c.fitted("下一页 ›",Face.TEXT,15,next(),accent,Align.RIGHT);
        String footer=pending.isEmpty()?(page+1)+" / "+Math.max(1,(choices.size()+capacity()-1)/capacity()):System.currentTimeMillis()-requestedAt>6000?"等待服务器确认":"正在更新";
        c.fitted(footer,Face.TEXT,15,new Rect(p.x()+155,p.y()+p.h()-44,p.w()-310,28),accent,Align.CENTER);g.pose().popPose();
    }
    private void choose(int index){
        if(index<0||index>=choices.size())return;Choice choice=choices.get(index);if(!choice.enabled())return;
        if(choice.selected()){onClose();return;}pending=choice.id();requestedAt=System.currentTimeMillis();
        NetworkManager.getChannel().sendToServer(slotIndex<0?LoadoutUiActionPacket.selectClass(choice.id()):LoadoutUiActionPacket.selectSlot(classKey,slotIndex,choice.id()));request();
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return true;float s=Math.min(width/1920f,height/1080f);x=(x-(width-1920*s)/2)/s;y=(y-(height-1080*s)/2)/s;
        if(contains(closeRect(),x,y)||!contains(panel(),x,y)){onClose();return true;}
        if(contains(previous(),x,y)){page=Math.max(0,page-1);return true;}if(contains(next(),x,y)){page=Math.min(Math.max(0,(choices.size()-1)/capacity()),page+1);return true;}
        for(int i=0;i<capacity();i++)if(contains(tile(i),x,y)){choose(page*capacity()+i);break;}return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){page=Math.max(0,Math.min(Math.max(0,(choices.size()-1)/capacity()),page+(delta<0?1:-1)));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256){onClose();return true;}if(key==264||key==262||key==258){focused=Math.min(choices.size()-1,focused+1);page=Math.max(0,focused/capacity());return true;}if(key==265||key==263){focused=Math.max(0,focused-1);page=focused/capacity();return true;}if(key==257||key==32){choose(focused);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private static boolean contains(Rect r,double x,double y){return x>=r.x()&&x<r.x()+r.w()&&y>=r.y()&&y<r.y()+r.h();}
}
