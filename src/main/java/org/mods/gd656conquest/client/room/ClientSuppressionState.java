package org.mods.gd656conquest.client.room;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import org.mods.gd656conquest.network.packet.SuppressionPacket;
import org.mods.gd656conquest.mixin.client.DeathPostChainAccessor;
/** One visual state, independent of the server's longer healing cooldown. World-only vignette. */
public final class ClientSuppressionState {
    private static boolean active,failed;
    private static float current,start,target,elapsed,maximum=.25F;
    private static long previous;
    private static Object level;
    private static PostChain chain;
    private static RenderTarget renderTarget;
    private static Uniform uniform;
    private static int width=-1,height=-1;
    private ClientSuppressionState(){}
    public static void accept(SuppressionPacket p){
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null)return;
        if(level!=mc.level){reset();level=mc.level;}
        active=p.active();maximum=p.opacity();setTarget(active?maximum:0);
    }
    private static void setTarget(float value){if(target!=value){start=current;target=value;elapsed=0;}}
    public static void beginFrame(){
        var mc=Minecraft.getInstance();
        if(mc.level!=level || mc.player==null){reset();level=mc.level;}
        if(mc.player==null || !mc.player.isAlive() || mc.player.isSpectator()
            || ClientDeathFilter.deathActive() || ClientDeathFlowState.shouldRenderDeployBoundaries()) {active=false;current=start=target=elapsed=0;}
        long now=System.nanoTime();float dt=previous==0?0:Math.max(0,(now-previous)/1e9F);previous=now;
        elapsed+=dt;float t=Math.min(1,elapsed/(target>start?.2F:.5F));t=t*t*(3-2*t);
        current=start+(target-start)*t;
    }
    public static float opacity(){return current;}
    public static void renderWorld(float partialTick){
        if(current<=.0001F || ClientBoundaryState.hasWorldEffect())return;
        var mc=Minecraft.getInstance();if(mc.level==null || !ensure(mc))return;
        try{uniform.set(current);RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();chain.process(partialTick);}
        finally{mc.getMainRenderTarget().bindWrite(true);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();}
    }
    private static boolean ensure(Minecraft mc){
        if(failed)return false;
        try{
            var target=mc.getMainRenderTarget();
            if(chain==null || renderTarget!=target){release();chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),target,new ResourceLocation("gd656conquest","shaders/post/suppression_world.json"));renderTarget=target;
                uniform=((DeathPostChainAccessor)chain).gd656conquest$passes().get(0).getEffect().getUniform("SuppressionOpacity");
                if(uniform==null)throw new IllegalStateException("Suppression uniform missing");}
            if(width!=target.width || height!=target.height){width=target.width;height=target.height;chain.resize(width,height);}
            return true;
        }catch(Exception e){release();failed=true;com.mojang.logging.LogUtils.getLogger().error("Unable to load suppression vignette",e);return false;}
    }
    public static void release(){if(chain!=null)chain.close();chain=null;renderTarget=null;uniform=null;width=height=-1;failed=false;}
    public static void reset(){active=false;current=start=target=elapsed=0;previous=0;release();}
}
