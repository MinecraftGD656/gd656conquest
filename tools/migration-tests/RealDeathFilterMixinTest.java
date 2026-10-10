package tests;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

/** Apply the real Mixin engine and verify color-layer ordering in authentic Forge bytecode. */
public final class RealDeathFilterMixinTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static int callIndex(MethodNode method, String name) {
        AbstractInsnNode[] code=method.instructions.toArray();
        for(int i=0;i<code.length;i++)if(code[i] instanceof MethodInsnNode call && call.name.contains(name))return i;
        return -1;
    }
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        MixinEnvironment env=MixinEnvironment.getDefaultEnvironment();
        env.setSide(MixinEnvironment.Side.valueOf(args[0]));
        Mixins.addConfiguration("migration-world-filters.mixins.json");
        IMixinTransformer transformer=((IsolationMixinService)MixinService.getService()).factory().factory().createTransformer();
        for(String name:List.of("net.minecraft.client.renderer.GameRenderer","net.minecraft.client.renderer.LevelRenderer","net.minecraft.client.renderer.PostChain")){
            byte[] original;
            try(InputStream in=RealDeathFilterMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){original=in.readAllBytes();}
            byte[] transformed=transformer.transformClass(env,name,original);
            if(args[0].equals("SERVER")){check(Arrays.equals(original,transformed),"Client filter must not load server-side");continue;}
            check(!Arrays.equals(original,transformed),"Mixin actually applied: "+name);
            ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);
            if(name.endsWith("GameRenderer")){
                MethodNode render=node.methods.stream().filter(m->m.name.equals("m_109093_")).findFirst().orElseThrow();
                int begin=callIndex(render,"beginDeathFrame"),level=callIndex(render,"m_109089_"),defer=callIndex(render,"deferDeathOutline"),post=callIndex(render,"m_110023_"),finish=callIndex(render,"finishDeathWorld"),clear=callIndex(render,"clear");
                check(begin>=0 && begin<level && level<defer && defer<post && post<finish && finish<clear,"World -> existing post -> death pass -> GUI depth clear");
                check(callIndex(render,"m_109769_")==-1,"Native outline composition exclusively routed through deferral");
                check(node.methods.stream().filter(m->m.name.contains("finishDeathWorld")).anyMatch(m->callIndex(m,"finishWorld")>=0),"Death filter executes at the world/UI boundary");
            }
            if(name.endsWith("GameRenderer")) {
                for(String lifecycle:List.of("m_172767_","close")) {
                    MethodNode method=node.methods.stream().filter(m->m.name.equals(lifecycle)).findFirst().orElseThrow();
                    check(callIndex(method,"releaseDeathResources")>=0,"Reload and close both release resources: "+lifecycle);
                }
            }
            if(name.endsWith("LevelRenderer")){
                MethodNode render=node.methods.stream().filter(m->m.name.equals("m_109599_")).findFirst().orElseThrow();
                check(callIndex(render,"localKillerGlow")>=0 && callIndex(render,"localKillerColor")>=0,"Local outline membership and red color override");
            }
            if(name.endsWith("PostChain"))check(node.interfaces.contains("org/mods/gd656conquest/mixin/client/DeathPostChainAccessor"),"Uniform accessor applied");
            Path file=Path.of(args[1]).resolve(name.replace('.','/')+".class");Files.createDirectories(file.getParent());Files.write(file,transformed);
            System.out.println("PASS death filter real Mixin target: "+name);
        }
        System.out.println("PASS death color layering and "+args[0]+" isolation");
    }
}
