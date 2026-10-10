package tests;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

/** Applies the real Mixin engine to both vanilla food branches on actual Forge SRG. */
public final class RealNaturalRegenMixinTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static long calls(MethodNode method,String marker){return Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode c&&c.name.contains(marker)).count();}
    public static void main(String[] args)throws Exception {
        var side=MixinEnvironment.Side.valueOf(args[0]);MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(side);Mixins.addConfiguration("isolation-regen.mixins.json");
        var transformer=((IsolationMixinService)MixinService.getService()).factory().factory().createTransformer();
        String target="net.minecraft.world.food.FoodData";byte[] input;try(var in=RealNaturalRegenMixinTest.class.getClassLoader().getResourceAsStream(target.replace('.','/')+".class")){input=in.readAllBytes();}
        var node=new ClassNode();new ClassReader(transformer.transformClassBytes(target,target,input)).accept(node,0);
        var tick=node.methods.stream().filter(m->m.name.equals("m_38710_")).findFirst().orElseThrow();
        check(calls(tick,"gd656$canHeal")==2,"both fast and slow vanilla food heals redirected");
        check(calls(tick,"gd656$roomFoodRule")==1,"actual world rule redirected once");
        check(calls(tick,"m_36325_")==0&&calls(tick,"m_46207_")==0,"original calls replaced, no concurrent old rule path");
        check(node.methods.stream().anyMatch(m->calls(m,"pausePassive")==1),"actual shared treatment pause hook installed");
        check(node.methods.stream().anyMatch(m->calls(m,"vanillaAllowed")==1),"actual room/global policy hook installed");
        System.out.println("PASS real Forge food regeneration Mixin: "+side+" / "+checks);
    }
}
