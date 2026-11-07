package org.mods.gd656conquest.client.event.handler;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector4f;
import org.mods.gd656killicon.client.gui.theme.*;

/** Shared real-map geometry, drawn below deployment targets using their same camera matrix. */
final class CaptureRegionOverlay {
    private record Region(String code,int[] faces,int[] edges) {}
    private static List<Region> regions=List.of();
    private static float[] world=new float[0],screen=new float[0];
    private static boolean[] visible=new boolean[0];
    private static final org.joml.Matrix4f lastMatrix=new org.joml.Matrix4f();
    private static final Vector4f temporary=new Vector4f();
    private static int width,height;
    private static boolean projectionValid;
    static void clear(){regions=List.of();world=new float[0];screen=new float[0];visible=new boolean[0];projectionValid=false;}
    static void replace(Map<BlockPos,String> blocks){
        Map<CaptureRegionMesh.Cell,String> cells=new HashMap<>();
        blocks.forEach((p,code)->cells.put(new CaptureRegionMesh.Cell(p.getX(),p.getY(),p.getZ()),code));
        Map<CaptureRegionMesh.Point,Integer> indices=new LinkedHashMap<>();List<Region> compiled=new ArrayList<>();
        for(var region:CaptureRegionMesh.build(cells)){
            int[] faces=new int[region.faces().size()*4],edges=new int[region.edges().size()*2];int i=0;
            for(var q:region.faces())for(var p:List.of(q.a(),q.b(),q.c(),q.d()))faces[i++]=indices.computeIfAbsent(p,k->indices.size());
            i=0;for(var e:region.edges())for(var p:List.of(e.a(),e.b()))edges[i++]=indices.computeIfAbsent(p,k->indices.size());
            compiled.add(new Region(region.code(),faces,edges));
        }
        world=new float[indices.size()*3];screen=new float[indices.size()*2];visible=new boolean[indices.size()];
        indices.forEach((p,i)->{world[i*3]=(float)p.x();world[i*3+1]=(float)p.y();world[i*3+2]=(float)p.z();});
        regions=List.copyOf(compiled);projectionValid=false;
    }
    static void render(GuiGraphics g,ClientCaptureOverlayState state,ClientCaptureOverlayModel.TeamToken self,float alpha){
        if(regions.isEmpty()||state.worldProjectionMatrix==null||alpha<=.001f)return;
        project(g,state);
        var theme=BattlefieldUiTheme.current();
        float pixel=g.guiWidth()/(float)Minecraft.getInstance().getWindow().getWidth();
        
        var mc=Minecraft.getInstance();var playerTeam=mc.player.getTeam();
        var match=org.mods.gd656killicon.client.gui.scoreboard.ScoreboardMatchSource.read(playerTeam==null?"":playerTeam.getName());
        var selfCamp=self==ClientCaptureOverlayModel.TeamToken.CAMP_A?org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Camp.A:self==ClientCaptureOverlayModel.TeamToken.CAMP_B?org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Camp.B:org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Camp.NEUTRAL;
        float time=(mc.level.getGameTime()+mc.getFrameTime())/20f;
        for(var region:regions){
            var entry=state.objectiveEntryByCode.get(region.code());if(entry==null)continue;
            org.mods.gd656killicon.client.gui.scoreboard.BattlefieldScoreboardLayout.Objective objective=null;
            for(var candidate:match.objectives())if(candidate.code().equals(region.code())){objective=candidate;break;}
            var visual=objective==null?null:BattlefieldObjectiveStyle.resolve(theme,objective,selfCamp);
            int rgb=visual!=null?visual.outline():entry.owner()==ClientCaptureOverlayModel.TeamToken.NEUTRAL||self==ClientCaptureOverlayModel.TeamToken.NEUTRAL?0xffd5dddc:entry.owner()==self?BattlefieldPovLayout.ally(theme):BattlefieldPovLayout.enemy(theme);
            boolean selected=entry.renderId().equals(state.selectedDeployIconId);
            int fill=((int)(alpha*(selected?35:15))<<24)|(rgb&0xffffff),edge=((int)(alpha*(selected?220:135))<<24)|(rgb&0xffffff);
            var buffer=g.bufferSource().getBuffer(RenderType.gui());var pose=g.pose().last().pose();
            int[] faces=region.faces();
            for(int offset=0;offset<faces.length;offset+=4){
                boolean valid=true;float area=0;
                for(int k=0;k<4;k++){int a=faces[offset+k],b=faces[offset+(k+1)%4];valid&=visible[a];area+=screen[a*2]*screen[b*2+1]-screen[b*2]*screen[a*2+1];}
                if(!valid)continue;
                for(int k=0;k<4;k++){int index=faces[offset+(area>0?3-k:k)];buffer.vertex(pose,screen[index*2],screen[index*2+1],0).color(fill>>16&255,fill>>8&255,fill&255,fill>>>24).endVertex();}
            }
            int[] edges=region.edges();
            for(int k=0;k<edges.length;k+=2){
                int a=edges[k],b=edges[k+1];if(!visible[a]||!visible[b])continue;
                line(buffer,pose,screen[a*2],screen[a*2+1],screen[b*2],screen[b*2+1],pixel*(selected?4:3.25f),((int)(alpha*90)<<24)|0x081217);
                line(buffer,pose,screen[a*2],screen[a*2+1],screen[b*2],screen[b*2+1],pixel*(selected?2:1.25f),edge);
                if(visual!=null&&(visual.contested()||visual.capturing())&&(k/2)%3==0){
                    int pulse=((int)(alpha*(160+55*Math.sin(time*5)))<<24)|(visual.activity()&0xffffff);
                    line(buffer,pose,screen[a*2],screen[a*2+1],screen[b*2],screen[b*2+1],pixel*2,pulse);
                }
            }
        }
    }
    private static void line(com.mojang.blaze3d.vertex.VertexConsumer buffer,org.joml.Matrix4f pose,float x,float y,float xx,float yy,float width,int color){
        float dx=xx-x,dy=yy-y,length=(float)Math.sqrt(dx*dx+dy*dy);if(length<1e-5f)return;
        float nx=-dy/length*width/2,ny=dx/length*width/2;int r=color>>16&255,g=color>>8&255,b=color&255,a=color>>>24;
        buffer.vertex(pose,x-nx,y-ny,0).color(r,g,b,a).endVertex();buffer.vertex(pose,x+nx,y+ny,0).color(r,g,b,a).endVertex();
        buffer.vertex(pose,xx+nx,yy+ny,0).color(r,g,b,a).endVertex();buffer.vertex(pose,xx-nx,yy-ny,0).color(r,g,b,a).endVertex();
    }
    private static void project(GuiGraphics g,ClientCaptureOverlayState state){
        if(projectionValid&&width==g.guiWidth()&&height==g.guiHeight()&&lastMatrix.equals(state.worldProjectionMatrix))return;
        width=g.guiWidth();height=g.guiHeight();lastMatrix.set(state.worldProjectionMatrix);projectionValid=true;
        for(int i=0;i<visible.length;i++){
            var v=lastMatrix.transform(temporary.set(world[i*3],world[i*3+1],world[i*3+2],1));
            float x=(1+v.x/v.w)*width/2,y=(1-v.y/v.w)*height/2;
            visible[i]=v.w>1e-5f&&Float.isFinite(x)&&Float.isFinite(y);screen[i*2]=x;screen[i*2+1]=y;
        }
    }
}
