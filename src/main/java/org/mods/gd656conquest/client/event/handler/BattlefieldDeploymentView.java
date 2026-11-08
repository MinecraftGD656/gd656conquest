package org.mods.gd656conquest.client.event.handler;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL30;
import java.util.*;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState;
import org.mods.gd656conquest.client.loadout.preview.LoadoutWeaponPreview;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.LoadoutUiRequestPacket;
import org.mods.gd656conquest.network.packet.LoadoutUiActionPacket;
import org.mods.gd656conquest.network.packet.RoomDeploySelectPacket;
import org.mods.gd656killicon.client.gui.theme.*;
import org.mods.gd656killicon.client.gui.scoreboard.ScoreboardMatchSource;
import static org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.*;
import static org.mods.gd656killicon.client.gui.theme.BattlefieldPovLayout.*;
import static org.mods.gd656killicon.client.gui.theme.BattlefieldDeploymentLayout.*;

/** Client display consumes native selected target and loadout snapshots. */
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BattlefieldDeploymentView {
    private static ClientCaptureOverlayState state;
    private static ClientCaptureOverlayModel.TeamToken self;
    private static RenderTarget scene;
    private static long lastRequest;
    private static ClientLoadoutUiState.Snapshot cached;
    private static final List<ItemStack> stacks=new ArrayList<>();
    private static List<Equipment> equipment=List.of();
    private static String classKey="",className="—";
    private static float previewU=.5f,previewV=.5f;
    private static long previewTime;
    static void publish(ClientCaptureOverlayState value,ClientCaptureOverlayModel.TeamToken team){state=value;self=team;}
    static void clear(){state=null;self=null;cached=null;stacks.clear();equipment=List.of();lastRequest=0;if(scene!=null){scene.destroyBuffers();scene=null;}}
    private static ClientCaptureOverlayModel.RenderedIconBounds selected(){
        if(state==null)return null;
        return state.renderedDeployIcons.stream().filter(v->v.entry().renderId().equals(state.selectedDeployIconId)).findFirst().orElse(null);
    }
    static boolean openLoadout(double mouseX,double mouseY,int button){
        if(button!=0)return false;
        Minecraft mc=Minecraft.getInstance();var theme=BattlefieldUiTheme.current();
        float scale=Math.min(mc.getWindow().getGuiScaledWidth()/1920f,mc.getWindow().getGuiScaledHeight()/1080f);
        double x=(mouseX-(mc.getWindow().getGuiScaledWidth()-1920*scale)/2)/scale;
        double y=(mouseY-(mc.getWindow().getGuiScaledHeight()-1080*scale)/2)/scale;
        if(theme!=Theme.BF2042){String[] classes={"assault","support","engineer","recon"};for(int i=0;i<classes.length;i++)if(contains(BattlefieldDeploymentLayout.classChoice(theme,classKey.equals("recon")?"scout":classKey,i),x,y)){
            NetworkManager.getChannel().sendToServer(LoadoutUiActionPacket.selectClass(classes[i]));if(!classes[i].equals(classKey))org.mods.gd656killicon.client.sounds.BattlefieldAudio.play(org.mods.gd656killicon.client.sounds.BattlefieldAudio.Event.UI_SELECT);return true;
        }}
        if(contains(BattlefieldDeploymentLayout.classRect(theme),x,y)){mc.setScreen(new DeploymentLoadoutScreen(mc.screen,-1));return true;}
        if(cached==null)return false;var cls=cached.classes().get(cached.currentClassKey());if(cls==null)return false;
        var slots=cls.slots().values().stream().sorted(Comparator.comparingInt(ClientLoadoutUiState.SlotState::slotIndex)).toList();
        for(int i=0;i<slots.size();i++)if(contains(BattlefieldDeploymentLayout.equipmentRect(theme,i,slots.size()),x,y)){
            mc.setScreen(new DeploymentLoadoutScreen(mc.screen,slots.get(i).slotIndex()));return true;
        }
        return false;
    }
    private static boolean contains(Rect r,double x,double y){return x>=r.x()&&x<r.x()+r.w()&&y>=r.y()&&y<r.y()+r.h();}
    static boolean ready(){var v=selected();return v!=null&&v.entry().isDeployableFor(self)&&ClientDeathFlowState.canConfirmDeploySelection();}
    static void confirm(){
        if(!ready())return;
        BattlefieldDeployActions.send(selected().entry().resolveDeployTarget(),true);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){if(state!=null||scene!=null)clear();return;}
        if(BattlefieldAccessibility.classic()||!ClientDeathFlowState.shouldRenderDeployBoundaries()||MenuWorldOverlay.menuActive()){previewTime=0;if(scene!=null){scene.destroyBuffers();scene=null;}return;}
        long now=System.currentTimeMillis();if(now-lastRequest>=1200){lastRequest=now;NetworkManager.getChannel().sendToServer(new LoadoutUiRequestPacket(false));}
        var snapshot=ClientLoadoutUiState.snapshot();if(snapshot==cached)return;cached=snapshot;
        stacks.clear();var list=new ArrayList<Equipment>();className="—";classKey="";
        if(snapshot!=null){var cls=snapshot.classes().get(snapshot.currentClassKey());
            if(cls!=null){className=cls.className();classKey=cls.classKey();
                for(var slot:cls.slots().values().stream().sorted(Comparator.comparingInt(ClientLoadoutUiState.SlotState::slotIndex)).toList()){
                    var entry=slot.entries().get(slot.selectedId());ItemStack stack=entry==null?ItemStack.EMPTY:ClientLoadoutUiState.parseAndCacheStack(entry.itemSnbt()).copy();
                    var panel=snapshot.attachmentPanels().get(slot.slotIndex());if(panel!=null)stack=LoadoutWeaponPreview.compose(stack,panel.attachmentTypes());
                    list.add(new Equipment(stacks.size(),slot.slotLabel(),entry==null?"—":entry.displayName()));stacks.add(stack);
                }
            }
        }
        equipment=List.copyOf(list);
    }
    @SubscribeEvent public static void backdrop(RenderGuiEvent.Pre event){
        Minecraft mc=Minecraft.getInstance();if(BattlefieldAccessibility.classic()||mc.player==null||!ClientDeathFlowState.shouldRenderDeployBoundaries()||MenuWorldOverlay.menuActive())return;
        var theme=BattlefieldUiTheme.current();GuiGraphics g=event.getGuiGraphics();
        var target=selected();
        if(theme==Theme.BF6&&target!=null){
            RenderTarget source=mc.getMainRenderTarget();Rect pane=BattlefieldDeploymentLayout.preview(theme);
            float u=(float)(target.screenX()/g.guiWidth()),v=1-(float)(target.screenY()/g.guiHeight());long now=net.minecraft.Util.getMillis();
            previewU=BattlefieldPreviewCrop.follow(previewU,u,now-previewTime,previewTime==0);previewV=BattlefieldPreviewCrop.follow(previewV,v,now-previewTime,previewTime==0);previewTime=now;
            var crop=BattlefieldPreviewCrop.region(source.width,source.height,previewU,previewV,pane.w(),pane.h());int w=crop.width(),h=crop.height();
            if(scene==null)scene=new TextureTarget(w,h,false,Minecraft.ON_OSX);else if(scene.width!=w||scene.height!=h)scene.resize(w,h,Minecraft.ON_OSX);
            scene.setFilterMode(GL30.GL_LINEAR);
            g.flush();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);
            GL30.glBlitFramebuffer(crop.x0(),crop.y0(),crop.x1(),crop.y1(),0,0,w,h,GL30.GL_COLOR_BUFFER_BIT,GL30.GL_LINEAR);source.bindWrite(true);
        }else previewTime=0;
        int color=switch(theme){case BF6->0x500b1b25;case BF2042->0x2409161e;case BF1->0x34212a32;};
        g.fill(0,0,g.guiWidth(),g.guiHeight(),color);
        if(theme==Theme.BF6){int step=Math.max(30,g.guiWidth()/10);for(int x=0;x<g.guiWidth();x+=step)g.fill(x,0,x+1,g.guiHeight(),0x133d7487);for(int y=0;y<g.guiHeight();y+=step)g.fill(0,y,g.guiWidth(),y+1,0x133d7487);}
    }
    static void render(GuiGraphics g,boolean switchVisible,String switchLabel,boolean switchEnabled,int mx,int my){
        Minecraft mc=Minecraft.getInstance();var theme=BattlefieldUiTheme.current();float scale=Math.min(g.guiWidth()/1920f,g.guiHeight()/1080f),ox=(g.guiWidth()-1920*scale)/2,oy=(g.guiHeight()-1080*scale)/2;
        var target=selected();String label=target==null?"":target.entry().note();if(label.isBlank()&&target!=null)label=target.entry().code();
        int hover=-1;for(Action action:Action.values()){Rect r=BattlefieldDeploymentLayout.action(theme,action);float x=(mx-ox)/scale,y=(my-oy)/scale;if(x>=r.x()&&x<r.x()+r.w()&&y>=r.y()&&y<r.y()+r.h())hover=action.ordinal();}
        String team=mc.player.getTeam()==null?"—":mc.player.getTeam() instanceof net.minecraft.world.scores.PlayerTeam pt?pt.getDisplayName().getString():mc.player.getTeam().getName();
        var data=new State(ScoreboardMatchSource.read(mc.player.getTeam()==null?"":mc.player.getTeam().getName()),team,org.mods.gd656killicon.client.profile.ProfileClient.label(mc.player.getUUID(),mc.player.getScoreboardName()),classKey.equals("recon")?"scout":classKey,className,equipment,label,Math.max(0,(ClientDeathFlowState.currentDeployCountdownRemainingTicks()+19)/20),ready(),switchVisible,switchLabel,switchEnabled,hover,mc.options.fov().get());
        g.pose().pushPose();g.pose().translate(ox,oy,0);g.pose().scale(scale,scale,1);BattlefieldDeploymentLayout.render(new Canvas(g,theme,target),theme,data);g.pose().popPose();
    }
    private static final class Canvas extends BattlefieldPovOverlay.GameCanvas {
        final ClientCaptureOverlayModel.RenderedIconBounds target;
        Canvas(GuiGraphics g,Theme t,ClientCaptureOverlayModel.RenderedIconBounds target){super(g,t,true);this.target=target;}
        @Override public void item(int index,Rect r,int tint){if(index>=0&&index<stacks.size())itemStack(stacks.get(index),r,tint);}
        @Override public void sprite(String name,Rect r,int color){if(name.equals("class_recon"))name="class_scout";if(name.startsWith("class_")&&!Set.of("class_assault","class_engineer","class_support","class_scout").contains(name))return;super.sprite(name,r,color);}
        @Override public void preview(Rect r){
            if(scene==null||target==null){super.preview(r);fitted("选择出生点",Face.TEXT,20,r,0xff94aab4,Align.CENTER);return;}
            g.flush();RenderSystem.enableBlend();RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,scene.getColorTextureId());RenderSystem.setShaderColor(.8f,.9f,.93f,1);
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);var m=g.pose().last().pose();
            b.vertex(m,r.x(),r.y()+r.h(),0).uv(0,0).endVertex();b.vertex(m,r.x()+r.w(),r.y()+r.h(),0).uv(1,0).endVertex();b.vertex(m,r.x()+r.w(),r.y(),0).uv(1,1).endVertex();b.vertex(m,r.x(),r.y(),0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());RenderSystem.setShaderColor(1,1,1,1);
        }
    }
}
