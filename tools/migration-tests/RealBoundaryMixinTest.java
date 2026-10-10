package tests;

import java.io.InputStream;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraft.resources.ResourceLocation;

public final class RealBoundaryMixinTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static boolean calls(MethodNode m,String name){for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.name.contains(name))return true;return false;}
    static boolean hidingCrosshair;
    public static boolean hidePlayerCrosshair(){return hidingCrosshair;}
    static void overlayRegression(ClassNode tacz)throws Exception {
        // Execute the actual merged hook and vanilla subscriber against real Forge events.
        // Replace only the Minecraft singleton lookup, so no game boot is needed.
        var harness=new ClassNode();harness.version=Opcodes.V17;harness.access=Opcodes.ACC_PUBLIC;
        harness.name="tests/BoundaryOverlayHarness";harness.superName="java/lang/Object";
        var merged=tacz.methods.stream().filter(m->m.name.contains("hideBoundaryCrosshair")).findFirst().orElseThrow();
        byte[] subscriber;
        try(var in=RealBoundaryMixinTest.class.getClassLoader().getResourceAsStream("org/mods/gd656conquest/client/room/BoundaryHudEvents.class")){subscriber=in.readAllBytes();}
        var original=new ClassNode();new ClassReader(subscriber).accept(original,0);
        for(var method:List.of(merged,original.methods.stream().filter(m->m.name.equals("overlay")).findFirst().orElseThrow())) {
            var copy=new MethodNode(Opcodes.ASM9,Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,method.name,method.desc,method.signature,method.exceptions.toArray(String[]::new));method.accept(copy);
            for(var instruction:copy.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals("hidePlayerCrosshair"))call.owner="tests/RealBoundaryMixinTest";
            harness.methods.add(copy);
        }
        var writer=new ClassWriter(0);harness.accept(writer);
        class Loader extends ClassLoader {
            Loader(){super(RealBoundaryMixinTest.class.getClassLoader());}
            Class<?> define(byte[] bytes){return defineClass("tests.BoundaryOverlayHarness",bytes,0,bytes.length);}
        }
        var type=new Loader().define(writer.toByteArray());
        var hook=type.getMethod(merged.name,RenderGuiOverlayEvent.Pre.class,CallbackInfo.class);
        var vanilla=type.getMethod("overlay",RenderGuiOverlayEvent.Pre.class);
        List<NamedGuiOverlay> overlays=new ArrayList<>();
        for(var overlay:VanillaGuiOverlay.values())overlays.add(new NamedGuiOverlay(overlay.id(),null));
        for(String id:List.of("gd656conquest:main_hud","simplehealthbar:health_bar","tacz:ammo","other:vehicle_hud"))
            overlays.add(new NamedGuiOverlay(new ResourceLocation(id),null));
        int checks=0;
        for(boolean hiding:new boolean[]{false,true}) {
            hidingCrosshair=hiding;
            for(var overlay:overlays) {
                boolean crosshair=overlay.id().equals(VanillaGuiOverlay.CROSSHAIR.id());
                for(boolean alreadyCanceled:new boolean[]{false,true}) {
                    var event=new RenderGuiOverlayEvent.Pre(null,null,0,overlay);
                    if(alreadyCanceled)event.setCanceled(true);
                    var callback=new CallbackInfo("onRenderOverlay",true);
                    hook.invoke(null,event,callback);
                    check(callback.isCancelled()==(hiding&&crosshair),"TACZ callback only suppresses infantry reticle: "+overlay.id());checks++;
                    check(event.isCanceled()==(alreadyCanceled||(hiding&&crosshair)),"TACZ preserves unrelated HUD cancellation: "+overlay.id());checks++;
                    vanilla.invoke(null,event);
                    check(event.isCanceled()==(alreadyCanceled||(hiding&&crosshair)),"vanilla hook preserves every other overlay: "+overlay.id());checks++;
                }
            }
        }
        System.out.println("PASS real Forge HUD events preserve non-crosshair UI: "+checks);
    }
    public static void main(String[] args)throws Exception {
        MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.valueOf(args[0]));
        Mixins.addConfiguration("isolation-boundary.mixins.json");
        IMixinTransformer transformer=((IsolationMixinService)MixinService.getService()).factory().factory().createTransformer();
        String name="net.minecraft.client.renderer.GameRenderer";
        byte[] original;
        try(InputStream in=RealBoundaryMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){original=in.readAllBytes();}
        byte[] output=transformer.transformClassBytes(name,name,original);
        if(args[0].equals("SERVER")) {check(Arrays.equals(original,output),"boundary client mixin excluded on server");}
        else {
            var node=new ClassNode();new ClassReader(output).accept(node,0);
            check(node.methods.stream().anyMatch(m->calls(m,"beginFrame")&&m.name.contains("beginBoundaryFrame")),"boundary frame hook applied");
            check(node.methods.stream().anyMatch(m->calls(m,"renderWorld")&&m.name.contains("finishBoundaryWorld")),"boundary filter hook applied");
            var render=node.methods.stream().filter(m->m.name.equals("m_109093_")).findFirst().orElseThrow();
            int filter=-1,clear=-1,index=0;
            for(var i:render.instructions){if(i instanceof MethodInsnNode c){if(c.name.contains("finishBoundaryWorld"))filter=index;if(c.name.equals("clear")&&clear<0)clear=index;}index++;}
            check(filter>=0&&filter<clear,"boundary filter executes before HUD depth clear");
            for(String lifecycle:List.of("m_172767_","close")) {
                var method=node.methods.stream().filter(m->m.name.equals(lifecycle)).findFirst().orElseThrow();
                check(calls(method,"releaseBoundaryResources"),"boundary cleanup independently applied at "+lifecycle);
                check(calls(method,"releaseDeathResources"),"death cleanup remains applied at "+lifecycle);
            }
            check(render.instructions.iterator().hasNext()&&calls(render,"finishDeathWorld"),"both death and boundary effects compose");
            String tacz="com.tacz.guns.client.event.RenderCrosshairEvent";
            try(InputStream in=RealBoundaryMixinTest.class.getClassLoader().getResourceAsStream(tacz.replace('.','/')+".class")) {
                byte[] originalGun=in.readAllBytes();
                byte[] gun=transformer.transformClassBytes(tacz,tacz,originalGun);
                if(Boolean.getBoolean("gd.isolation.absent")) {
                    check(Arrays.equals(gun,originalGun),"absent TACZ does not receive optional hook");
                    System.out.println("PASS boundary rendering with TACZ absent");return;
                }
                var target=new ClassNode();new ClassReader(gun).accept(target,0);
                check(target.methods.stream().filter(m->m.name.equals("onRenderOverlay")).anyMatch(m->calls(m,"hideBoundaryCrosshair")),"TACZ receives the real reticle suppression hook");
                overlayRegression(target);
            }
        }
        System.out.println("PASS actual boundary GameRenderer Mixin / "+args[0]);
    }
}
