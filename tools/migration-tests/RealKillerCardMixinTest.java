package tests;

import java.util.Arrays;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

public final class RealKillerCardMixinTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.valueOf(args[0]));
        Mixins.addConfiguration("migration-killer-card.mixins.json");
        var service=(IsolationMixinService)MixinService.getService();IMixinTransformer transformer=service.factory().factory().createTransformer();
        String name="net.minecraft.client.gui.components.ChatComponent";
        byte[] original;
        try(var in=RealKillerCardMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){original=in.readAllBytes();}
        byte[] transformed=transformer.transformClass(MixinEnvironment.getDefaultEnvironment(),name,original);
        if(args[0].equals("SERVER")){check(Arrays.equals(original,transformed),"Client accessor must not apply server-side");}
        else {
            var node=new ClassNode();new ClassReader(transformed).accept(node,0);
            check(node.interfaces.contains("org/mods/gd656conquest/mixin/client/KillerCardChatAccessor"),"Chat bounds interface applied");
            var lines=node.methods.stream().filter(m->m.name.equals("gd656conquest$chatLines")).findFirst().orElseThrow();
            check(Arrays.stream(lines.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.name.equals("f_93761_")),"Accessor reads actual wrapped chat lines");
            var height=node.methods.stream().filter(m->m.name.equals("gd656conquest$chatLineHeight")).findFirst().orElseThrow();
            check(Arrays.stream(height.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode m&&m.name.equals("m_240691_")),"Invoker uses native configured chat line height");
            check(node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).anyMatch(i->i instanceof MethodInsnNode m&&m.owner.contains("ClientRoomChatState")),"Native render and mouse coordinates use room layout");
            for(String method:new String[]{"m_280165_","m_93813_","m_93816_","m_240491_","m_240485_","m_93772_"}) {
                var m=node.methods.stream().filter(v->v.name.equals(method)).findFirst().orElseThrow();
                check(Arrays.stream(m.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode c&&c.owner.equals(node.name)&&c.name.contains("gd656conquest$")),"Room hook actually applied: "+method);
            }
        }
        for(String target:new String[]{"net.minecraft.client.gui.screens.ChatScreen","net.minecraft.client.gui.components.CommandSuggestions"}) {
            byte[] source;
            try(var in=RealKillerCardMixinTest.class.getClassLoader().getResourceAsStream(target.replace('.','/')+".class")){source=in.readAllBytes();}
            byte[] changed=transformer.transformClass(MixinEnvironment.getDefaultEnvironment(),target,source);
            if(args[0].equals("SERVER"))check(Arrays.equals(source,changed),"Room chat UI cannot load on dedicated server: "+target);
            else {
                var node=new ClassNode();new ClassReader(changed).accept(node,0);
                check(node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).anyMatch(i->i instanceof MethodInsnNode m&&m.owner.contains("ClientRoomChatState")),"Input/suggestion native target wired: "+target);
            }
        }
        System.out.println("PASS real killer-card chat Mixin / "+args[0]+" isolation");
    }
}
