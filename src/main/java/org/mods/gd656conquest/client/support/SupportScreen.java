package org.mods.gd656conquest.client.support;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.mods.gd656conquest.common.support.SupportHeroKitTypeRegistry;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.SupportUiActionPacket;
import org.mods.gd656conquest.network.packet.SupportUiRequestPacket;
import org.mods.gd656killicon.client.gui.theme.*;
import org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.Rect;
import org.mods.gd656killicon.client.gui.theme.BattlefieldSupportLayout.*;
import org.mods.gd656killicon.client.sounds.BattlefieldAudio;
import java.util.*;

/** Native support screen: selects a real server entry, then confirms one original action. */
public final class SupportScreen extends Screen {
    private final Screen parent;
    private List<Entry> entries=List.of();
    private ClientSupportUiState.Snapshot snapshot;
    private long requestedAt,openedAt;
    private int selected=-1,page;
    private boolean submitted;
    private double lastMouseX=Double.NaN,lastMouseY=Double.NaN;
    public SupportScreen(Screen parent){super(Component.translatable("gd656conquest.client.gui.support.title"));this.parent=parent;}
    @Override protected void init(){openedAt=net.minecraft.Util.getMillis();refreshFromState();request(true);}
    @Override public void tick(){request(false);if(snapshot!=ClientSupportUiState.snapshot())refreshFromState();}
    public void refreshFromState(){
        String key=selected>=0&&selected<entries.size()?entries.get(selected).key():"";
        snapshot=ClientSupportUiState.snapshot();
        entries=snapshot==null?List.of():snapshot.entries().stream().map(v->new Entry(v.kind(),v.id(),v.displayName().isBlank()?v.id():v.displayName(),v.typeKey(),v.subtitleText(),v.detailText(),v.buttonText(),v.cost(),v.available()&&Set.of("hero_kit","shelling").contains(v.kind())))
            .sorted(Comparator.comparing(Entry::kind).thenComparingInt(Entry::cost).thenComparing(Entry::id)).toList();
        selected=-1;for(int i=0;i<entries.size();i++)if(entries.get(i).key().equals(key)){selected=i;break;}
        if(selected<0&&!entries.isEmpty())selected=Math.min(page*6,entries.size()-1);
        page=selected<0?0:selected/6;
    }
    private void request(boolean force){long now=net.minecraft.Util.getMillis();if(minecraft==null||minecraft.player==null||!force&&now-requestedAt<1000)return;requestedAt=now;NetworkManager.getChannel().sendToServer(new SupportUiRequestPacket());}
    private float scale(){return Math.min(width/960f,height/540f);}
    private double[] local(double x,double y){float s=scale();return new double[]{(x-(width-960*s)/2)/s,(y-(height-540*s)/2)/s};}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        double[] p=local(mouseX,mouseY);if(p[0]!=lastMouseX||p[1]!=lastMouseY){int hit=BattlefieldSupportLayout.hit(BattlefieldUiTheme.current(),p[0],p[1]);if(hit>=0&&page*6+hit<entries.size())select(page*6+hit,false);lastMouseX=p[0];lastMouseY=p[1];}
        var state=new State(snapshot==null?"—":snapshot.roomDisplayName(),snapshot==null?"—":snapshot.teamDisplayName(),snapshot==null?0:snapshot.currentScore(),snapshot==null?0:snapshot.spentScore(),snapshot==null?0:snapshot.remainingScore(),snapshot==null?"":snapshot.statusText(),entries,page,selected,snapshot==null);
        float s=scale();g.pose().pushPose();g.pose().translate((width-960*s)/2,(height-540*s)/2,0);g.pose().scale(s,s,1);
        float t=Math.min(1,(net.minecraft.Util.getMillis()-openedAt)/180f);BattlefieldSupportLayout.render(new Canvas(g),BattlefieldUiTheme.current(),state,p[0],p[1],1-(1-t)*(1-t)*(1-t));g.pose().popPose();
    }
    private void select(int index,boolean announce){if(index<0||index>=entries.size())return;if(selected!=index)BattlefieldAudio.play(BattlefieldAudio.Event.SUPPORT_SELECT);selected=index;page=index/6;if(announce){var e=entries.get(index);minecraft.getNarrator().sayNow(Component.literal(e.name()+"，"+e.cost()+" 积分，"+e.detail()));}}
    private void turnPage(int delta){int next=Math.max(0,Math.min(BattlefieldSupportLayout.pages(entries.size())-1,page+delta));if(next!=page){page=next;select(page*6,true);}}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);var p=local(x,y);
        if(BattlefieldSupportLayout.inside(BattlefieldSupportLayout.CLOSE,p[0],p[1])){onClose();return true;}
        if(BattlefieldSupportLayout.inside(BattlefieldSupportLayout.REFRESH,p[0],p[1])){request(true);return true;}
        if(BattlefieldSupportLayout.pages(entries.size())>1){if(BattlefieldSupportLayout.inside(BattlefieldSupportLayout.PREVIOUS,p[0],p[1])){turnPage(-1);return true;}if(BattlefieldSupportLayout.inside(BattlefieldSupportLayout.NEXT,p[0],p[1])){turnPage(1);return true;}}
        if(BattlefieldSupportLayout.inside(BattlefieldSupportLayout.CONFIRM,p[0],p[1])){callSelected();return true;}
        int slot=BattlefieldSupportLayout.hit(BattlefieldUiTheme.current(),p[0],p[1]);if(slot>=0){select(page*6+slot,true);return true;}return false;
    }
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(delta==0)return false;turnPage(delta>0?-1:1);return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_F6){BattlefieldUiTheme.cycle();return true;}
        if(key>=GLFW.GLFW_KEY_1&&key<=GLFW.GLFW_KEY_6){select(page*6+key-GLFW.GLFW_KEY_1,true);return true;}
        if(key==GLFW.GLFW_KEY_PAGE_UP||key==GLFW.GLFW_KEY_PAGE_DOWN){turnPage(key==GLFW.GLFW_KEY_PAGE_UP?-1:1);return true;}
        if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_TAB){if(!entries.isEmpty())select(Math.floorMod(selected+(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_UP||hasShiftDown()?-1:1),entries.size()),true);return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER||key==GLFW.GLFW_KEY_SPACE){callSelected();return true;}return super.keyPressed(key,scan,modifiers);
    }
    private void callSelected(){
        if(submitted||selected<0||selected>=entries.size()||snapshot==null||minecraft.player==null)return;
        var entry=entries.get(selected);if(!entry.available()||entry.cost()>snapshot.remainingScore()){BattlefieldAudio.play(BattlefieldAudio.Event.UI_DENIED);return;}
        submitted=true;
        if(entry.kind().equals("shelling"))NetworkManager.getChannel().sendToServer(SupportUiActionPacket.startShelling(entry.id()));
        else if(entry.kind().equals("hero_kit"))NetworkManager.getChannel().sendToServer(SupportUiActionPacket.deployHeroKit(entry.id()));
        BattlefieldAudio.play(BattlefieldAudio.Event.SUPPORT_CONFIRM);onClose();
    }
    @Override public boolean shouldCloseOnEsc(){return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(minecraft!=null){if(!submitted)BattlefieldAudio.play(BattlefieldAudio.Event.UI_BACK);minecraft.setScreen(parent);}}
    private final class Canvas extends BattlefieldPovOverlay.GameCanvas implements BattlefieldSupportLayout.Canvas {
        Canvas(GuiGraphics g){super(g,BattlefieldUiTheme.current());}
        public void polygon(float[] xy,int color){
            var b=g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());var m=g.pose().last().pose();
            int half=xy.length/4;for(int i=0;i<half-1;i++)for(int corner=0;corner<4;corner++){int index=switch(corner){case 0->i;case 1->i+1;case 2->2*half-2-i;default->2*half-1-i;};b.vertex(m,xy[index*2],xy[index*2+1],0).color(color>>16&255,color>>8&255,color&255,color>>>24).endVertex();}
        }
        public void supportIcon(String kind,String type,Rect r,int color){
            ResourceLocation icon=kind.equals("hero_kit")&&type.matches("[a-z0-9_]+")?SupportHeroKitTypeRegistry.texture(type):null;
            if(icon!=null&&minecraft.getResourceManager().getResource(icon).isPresent()){
                g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,(color>>>24)/255f);g.blit(icon,Math.round(r.x()),Math.round(r.y()),Math.round(r.w()),Math.round(r.h()),0,0,1,1,1,1);g.flush();RenderSystem.setShaderColor(1,1,1,1);
            }else {float x=r.x()+r.w()/2,y=r.y()+r.h()/2;line(x-r.w()*.3f,y+r.h()*.3f,x+r.w()*.3f,y-r.h()*.3f,3,color);line(x-r.w()*.15f,y+r.h()*.35f,x-r.w()*.35f,y+r.h()*.15f,2,color);line(x+r.w()*.12f,y-r.h()*.32f,x+r.w()*.32f,y-r.h()*.12f,3,color);}
        }
    }
}
