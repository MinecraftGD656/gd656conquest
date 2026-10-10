package tests;

import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.mods.gd656conquest.network.packet.SuppressionPacket;

/** Runs actual client state bytecode with an explicit frame clock and renderer adapters. */
public final class SuppressionClientTest {
    static int checks;static long nanos=1_000_000_000;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static long clock(){return nanos;}
    public static class World {}
    public static class Player {public boolean alive=true,spectator;public boolean m_6084_(){return alive;}public boolean m_5833_(){return spectator;}}
    public static class Target {public int f_83915_=100,f_83916_=100,binds;public void m_83947_(boolean reset){binds++;}}
    public static class Uniform {public float value;public void m_5985_(float value){this.value=value;}}
    public static class Chain {public int processes,closes,resizes;public void close(){closes++;}public void m_110023_(float tick){processes++;}public void m_110025_(int w,int h){resizes++;}}
    public static class Render {
        public static int restores;public static void disableBlend(){}public static void disableDepthTest(){}public static void resetTextureMatrix(){}
        public static void setShaderColor(float r,float g,float b,float a){restores++;}public static void defaultBlendFunc(){}
    }
    public static class Minecraft {
        public static final Minecraft INSTANCE=new Minecraft();public World f_91073_=new World();public Player f_91074_=new Player();public Target target=new Target();
        public static Minecraft m_91087_(){return INSTANCE;}public Target m_91385_(){return target;}
    }
    public static class Gates {public static boolean death,deploy,boundary;public static boolean deathActive(){return death;}public static boolean shouldRenderDeployBoundaries(){return deploy;}public static boolean hasWorldEffect(){return boundary;}}
    static void field(Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    static float opacity(Method get)throws Exception{return (float)get.invoke(null);}
    public static void main(String[] args)throws Exception {
        String original="org/mods/gd656conquest/client/room/ClientSuppressionState";var node=AmmoReserveFixtureTest.read(original);
        for(var method:node.methods)for(var i:method.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("java/lang/System")&&c.name.equals("nanoTime")){c.owner="tests/SuppressionClientTest";c.name="clock";}
        var map=new HashMap<String,String>();map.put(original,"tests/client/SuppressionState");
        for(var e:Map.ofEntries(Map.entry("net/minecraft/client/Minecraft",Minecraft.class),Map.entry("net/minecraft/client/multiplayer/ClientLevel",World.class),Map.entry("net/minecraft/client/player/LocalPlayer",Player.class),Map.entry("net/minecraft/client/renderer/PostChain",Chain.class),Map.entry("com/mojang/blaze3d/pipeline/RenderTarget",Target.class),Map.entry("com/mojang/blaze3d/shaders/Uniform",Uniform.class),Map.entry("com/mojang/blaze3d/systems/RenderSystem",Render.class),Map.entry("org/mods/gd656conquest/client/room/ClientDeathFilter",Gates.class),Map.entry("org/mods/gd656conquest/client/room/ClientDeathFlowState",Gates.class),Map.entry("org/mods/gd656conquest/client/room/ClientBoundaryState",Gates.class)).entrySet())map.put(e.getKey(),org.objectweb.asm.Type.getInternalName(e.getValue()));
        var loader=new AmmoReserveFixtureTest.Loader();AmmoReserveFixtureTest.add(loader,node,map);var state=loader.loadClass("tests.client.SuppressionState");
        var accept=state.getMethod("accept",SuppressionPacket.class);var begin=state.getMethod("beginFrame");var get=state.getMethod("opacity");var reset=state.getMethod("reset");var release=state.getMethod("release");var draw=state.getMethod("renderWorld",float.class);
        accept.invoke(null,new SuppressionPacket(true,.25F));begin.invoke(null);check(opacity(get)==0,"entry has no first-frame jump");
        nanos+=100_000_000;begin.invoke(null);check(Math.abs(opacity(get)-.125F)<.00001F,"smooth entry halfway at 0.10 seconds");nanos+=100_000_000;begin.invoke(null);check(opacity(get)==.25F,"entry ends at 0.20 seconds");
        accept.invoke(null,new SuppressionPacket(true,.25F));nanos+=10_000_000;begin.invoke(null);check(opacity(get)==.25F,"repeated server state does not restart transition");
        accept.invoke(null,new SuppressionPacket(false,.25F));nanos+=250_000_000;begin.invoke(null);check(Math.abs(opacity(get)-.125F)<.00001F,"exit halfway at 0.25 seconds");nanos+=250_000_000;begin.invoke(null);check(opacity(get)==0,"exit ends at 0.50 seconds");
        for(int fps:new int[]{20,30,60,144,240}){
            reset.invoke(null);accept.invoke(null,new SuppressionPacket(true,.6F));begin.invoke(null);float previous=0;
            for(int frame=0;frame<=Math.ceil(fps*.2);frame++){nanos+=(long)(1e9/fps);begin.invoke(null);float current=opacity(get);check(current>=previous&&current<=.6F,"monotonic bounded entry at "+fps+" FPS");previous=current;}
            check(Math.abs(opacity(get)-.6F)<.00001F,"frame-rate independent full opacity");
        }
        var chain=new Chain();var uniform=new Uniform();field(state,"chain",chain);field(state,"renderTarget",Minecraft.INSTANCE.target);field(state,"uniform",uniform);field(state,"width",100);field(state,"height",100);
        Gates.boundary=true;draw.invoke(null,0F);check(chain.processes==0,"standalone pass skipped when combined boundary effect active");Gates.boundary=false;draw.invoke(null,0F);check(chain.processes==1&&uniform.value==.6F&&Minecraft.INSTANCE.target.binds==1&&Render.restores==1,"world pass uses real opacity and restores output target/state");
        Minecraft.INSTANCE.target.f_83915_=200;draw.invoke(null,0F);check(chain.resizes==1&&chain.processes==2,"window size change resizes current chain");release.invoke(null);check(chain.closes==1&&opacity(get)==.6F,"resource reload closes chain while retaining active visual state");
        for(String gate:List.of("death","deploy","spectator","dead")){
            reset.invoke(null);accept.invoke(null,new SuppressionPacket(true,.25F));begin.invoke(null);nanos+=200_000_000;begin.invoke(null);
            switch(gate){case "death"->Gates.death=true;case "deploy"->Gates.deploy=true;case "spectator"->Minecraft.INSTANCE.f_91074_.spectator=true;default->Minecraft.INSTANCE.f_91074_.alive=false;}
            begin.invoke(null);check(opacity(get)==0,"visual cleared immediately by "+gate);Gates.death=Gates.deploy=false;Minecraft.INSTANCE.f_91074_.spectator=false;Minecraft.INSTANCE.f_91074_.alive=true;
        }
        accept.invoke(null,new SuppressionPacket(true,.25F));begin.invoke(null);nanos+=200_000_000;begin.invoke(null);Minecraft.INSTANCE.f_91073_=new World();begin.invoke(null);check(opacity(get)==0,"world change clears stale visual");
        Minecraft.INSTANCE.f_91074_=null;accept.invoke(null,new SuppressionPacket(true,.25F));begin.invoke(null);check(opacity(get)==0,"logout/null player cannot accept lingering effect");
        System.out.println("PASS actual suppression client clock/transition/combined-pass/lifecycle: "+checks);
    }
}
