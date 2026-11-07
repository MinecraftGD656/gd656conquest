package org.mods.gd656conquest.client.event.handler;

import java.util.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL30;
import org.joml.Vector4f;

/** Full-resolution multiplicative mask: one cached triangle mesh replaces screen-column rebuilds. */
final class DeploymentBoundaryMask {
    private record Mesh(List<ClientCaptureOverlayModel.BoundaryPoint> points,int[] triangles) {}
    private List<Mesh> meshes=List.of();
    private long version=Long.MIN_VALUE;
    private ClientCaptureOverlayModel.TeamToken team;
    private RenderTarget mask;
    private final Vector4f vertex=new Vector4f();
    private int rebuilds;
    private final org.joml.Matrix4f imageMatrix=new org.joml.Matrix4f();
    private boolean imageValid;
    private float imageShade;
    void close(){if(mask!=null){mask.destroyBuffers();mask=null;}meshes=List.of();version=Long.MIN_VALUE;imageValid=false;}
    int rebuilds(){return rebuilds;}
    boolean render(GuiGraphics g,ClientCaptureOverlayState state,ClientCaptureOverlayModel.TeamToken self,List<ClientCaptureOverlayModel.BoundaryPolygon> polygons,float alpha){
        if(polygons.isEmpty()||state.worldProjectionMatrix==null)return false;
        if(version!=state.boundaryGeometryVersion||team!=self){
            List<Mesh> next=new ArrayList<>();
            try{
                for(var polygon:polygons){
                    var points=polygon.points();
                    var flat=points.stream().map(p->new DeploymentPolygon.Point(p.x(),p.z())).toList();
                    int[] indices=DeploymentPolygon.triangles(flat);
                    if(indices.length>0)next.add(new Mesh(points,indices));
                }
            }catch(IllegalArgumentException e){
                org.mods.gd656conquest.client.log.ClientLogger.warning("deployment polygon fallback: %s",e.getMessage());
                meshes=List.of();version=state.boundaryGeometryVersion;team=self;return false;
            }
            meshes=List.copyOf(next);version=state.boundaryGeometryVersion;team=self;rebuilds++;imageValid=false;
        }
        if(meshes.isEmpty())return false;
        var source=Minecraft.getInstance().getMainRenderTarget();g.flush();
        if(mask==null){mask=new TextureTarget(source.width,source.height,false,Minecraft.ON_OSX);imageValid=false;}
        else if(mask.width!=source.width||mask.height!=source.height){mask.resize(source.width,source.height,Minecraft.ON_OSX);imageValid=false;}
        boolean depth=GL30.glIsEnabled(GL30.GL_DEPTH_TEST),blend=GL30.glIsEnabled(GL30.GL_BLEND),cull=GL30.glIsEnabled(GL30.GL_CULL_FACE);
        float shade=1-.42f*Math.max(0,Math.min(1,alpha));
        try{
            RenderSystem.disableDepthTest();RenderSystem.disableCull();RenderSystem.setShaderColor(1,1,1,1);
            var b=Tesselator.getInstance().getBuilder();
            if(!imageValid||imageShade!=shade||!imageMatrix.equals(state.worldProjectionMatrix)){
            mask.setClearColor(shade,shade,shade,1);mask.clear(Minecraft.ON_OSX);mask.bindWrite(true);
            RenderSystem.disableDepthTest();RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
            for(var mesh:meshes)for(int start=0;start<mesh.triangles.length;start+=3){
                boolean valid=true;
                for(int k=0;k<3;k++){var p=mesh.points.get(mesh.triangles[start+k]);state.worldProjectionMatrix.transform(vertex.set((float)p.x(),(float)p.y(),(float)p.z(),1));valid&=vertex.w>1e-5f;}
                if(!valid)continue;
                for(int k=0;k<3;k++){var p=mesh.points.get(mesh.triangles[start+k]);state.worldProjectionMatrix.transform(vertex.set((float)p.x(),(float)p.y(),(float)p.z(),1));
                    float x=(1+vertex.x/vertex.w)*g.guiWidth()/2,y=(1-vertex.y/vertex.w)*g.guiHeight()/2;
                    b.vertex(g.pose().last().pose(),x,y,0).color(255,255,255,255).endVertex();}
            }
            BufferUploader.drawWithShader(b.end());imageMatrix.set(state.worldProjectionMatrix);imageShade=shade;imageValid=true;
            }
            source.bindWrite(true);
            RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR,GlStateManager.DestFactor.ZERO,GlStateManager.SourceFactor.ZERO,GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,mask.getColorTextureId());
            var pose=g.pose().last().pose();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            b.vertex(pose,0,g.guiHeight(),0).uv(0,0).endVertex();b.vertex(pose,g.guiWidth(),g.guiHeight(),0).uv(1,0).endVertex();
            b.vertex(pose,g.guiWidth(),0,0).uv(1,1).endVertex();b.vertex(pose,0,0,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());
        }finally{
            source.bindWrite(true);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        }
        return true;
    }
}
