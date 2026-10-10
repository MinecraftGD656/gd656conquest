package tests;

import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

/** Applies the real Mixin engine to the shipped Minecraft and TACZ classes. */
public final class RealSuppressionMixinTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static boolean calls(ClassNode n,String owner,String method){
        return n.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
            .anyMatch(i->i instanceof MethodInsnNode c && c.owner.endsWith(owner) && c.name.equals(method));
    }
    static boolean callsInMethod(MethodNode m,String name){return Arrays.stream(m.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode c&&c.name.contains("gd656$")&&c.name.contains(name));}
    public static void main(String[] args)throws Exception {
        var side=MixinEnvironment.Side.valueOf(args[0]);
        MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(side);
        Mixins.addConfiguration("isolation-suppression.mixins.json");
        IMixinTransformer transformer=((IsolationMixinService)MixinService.getService()).factory().factory().createTransformer();
        var targets=new LinkedHashMap<String,String[]>();
        targets.put("net.minecraft.server.network.ServerGamePacketListenerImpl",new String[]{"SupportDropGuard","drop","SupportDropGuard","blockClick"});
        targets.put("net.minecraft.world.food.FoodData",new String[]{"SupportRuntime","pausePassive","NaturalRegenService","vanillaAllowed"});
        targets.put("net.minecraft.client.renderer.GameRenderer",new String[]{"ClientSuppressionState","beginFrame","ClientSuppressionState","renderWorld"});
        targets.put("net.minecraft.client.sounds.SoundEngine",new String[]{});
        if(!Boolean.getBoolean("gd.isolation.absent")) {
            targets.put("com.tacz.guns.util.EntityUtil",new String[]{"TaczSuppression","path"});
            targets.put("com.tacz.guns.item.ModernKineticGunScriptAPI",new String[]{"TaczSuppression","beginShot","TaczSuppression","endShot"});
            targets.put("com.tacz.guns.entity.EntityKineticBullet",new String[]{"TaczSuppression","tag"});
        }
        for(var target:targets.entrySet()) {
            byte[] original;
            try(var in=RealSuppressionMixinTest.class.getClassLoader().getResourceAsStream(target.getKey().replace('.','/')+".class")) {original=in.readAllBytes();}
            byte[] result=transformer.transformClassBytes(target.getKey(),target.getKey(),original);
            if(target.getKey().contains("client.") && side==MixinEnvironment.Side.SERVER) {check(Arrays.equals(original,result),"client effects excluded on dedicated server");continue;}
            var node=new ClassNode();new ClassReader(result).accept(node,0);
            if(target.getKey().endsWith("SoundEngine"))check(node.methods.stream()
                .filter(m->m.name.equals("gd656$ammoSoundChannels"))
                .anyMatch(m->Arrays.stream(m.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("f_120226_"))),"real sound channel accessor applied");
            for(int i=0;i<target.getValue().length;i+=2)check(calls(node,target.getValue()[i],target.getValue()[i+1]),"actual hook applied: "+target.getKey()+" / "+target.getValue()[i+1]);
            if(target.getKey().endsWith("EntityUtil")) {
                check(node.methods.stream().filter(m->m.name.equals("findEntitiesOnPath")||m.name.equals("findEntityOnPath"))
                    .allMatch(m->Arrays.stream(m.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode c&&c.name.contains("gd656$"))),"both penetrating and ordinary collision paths observed");
            }
            if(target.getKey().endsWith("EntityKineticBullet"))check(node.methods.stream().filter(m->m.name.equals("gd656$pierce")).anyMatch(m->Arrays.stream(m.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("pierce"))),"actual TACZ pierce accessor applied");
            if(target.getKey().endsWith("GameRenderer") && side==MixinEnvironment.Side.CLIENT) {
                check(node.methods.stream().filter(m->m.name.equals("m_172767_")).anyMatch(m->callsInMethod(m,"reload")),"suppression resources released by actual shader reload");
                check(node.methods.stream().filter(m->m.name.equals("close")).anyMatch(m->callsInMethod(m,"release")),"suppression resources released by actual renderer close");
                check(calls(node,"ClientBoundaryState","renderWorld"),"boundary and suppression renderer hooks coexist");
            }
            if(args.length>1){var file=Path.of(args[1],node.name+".class");Files.createDirectories(file.getParent());Files.write(file,result);}
        }
        System.out.println("PASS actual support / suppression mixins: "+side+" / TACZ absent="+Boolean.getBoolean("gd.isolation.absent")+" / "+checks);
    }
}
