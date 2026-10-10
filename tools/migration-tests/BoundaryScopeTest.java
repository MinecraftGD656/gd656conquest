package tests;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Compare the previous release to the current boundary fix, protecting unrelated behavior. */
public final class BoundaryScopeTest {
    static int checks, configCopies;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static ClassNode read(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return n;}
    static String trace(MethodNode m){var t=new Textifier();m.accept(new TraceMethodVisitor(t));var s=new StringWriter();t.print(new PrintWriter(s));return s.toString();}
    static boolean calls(MethodNode m,String name){for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.name.equals(name))return true;return false;}
    static void normalizeConfigCopies(MethodNode method) {
        for(var ins:method.instructions.toArray())if(ins instanceof MethodInsnNode ctor&&ctor.name.equals("<init>")
            &&ctor.owner.endsWith("/RoomCommonConfig")&&ctor.desc.endsWith("Ljava/lang/Integer;Ljava/lang/Double;)V")) {
            configCopies++;
            for(String getter:List.of("flightBoundaryPaddingBlocks","flightVehicleOutsideBoundaryDeathSeconds")) {
                AbstractInsnNode tail=ctor.getPrevious();
                check(tail instanceof MethodInsnNode c&&c.name.equals(getter),"copy retains "+getter);
                method.instructions.remove(tail);
                while(true) {
                    AbstractInsnNode receiver=ctor.getPrevious();
                    boolean load=receiver instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD;
                    check(load || receiver instanceof MethodInsnNode,"simple config receiver");
                    method.instructions.remove(receiver);
                    if(load)break;
                }
            }
            ctor.desc=ctor.desc.replace("Ljava/lang/Integer;Ljava/lang/Double;)V",")V");
        }
    }
    public static void main(String[] args)throws Exception {
        String root="org/mods/gd656conquest/";
        Set<String> replaced=Set.of(root+"server/room/model/RoomCommonConfig.class",root+"server/room/runtime/RoomDeathBoundaryQuery.class");
        Map<String,Set<String>> allowed=Map.of(
            root+"server/room/config/RoomConfigDescriptorRegistry.class",Set.of("buildDescriptors"),
            root+"server/data/roomdefinition/RoomConfigValidator.class",Set.of("validateCommon"),
            root+"server/room/entry/RoomDeathCauseService.class",Set.of("clear","tick"),
            root+"server/room/runtime/RoomRuleRuntimeService.class",Set.of("processBoundaryRule","clearBoundaryPenalty","lambda$processBoundaryRule$0"),
            root+"server/room/vehicle/VehicleBoundaryPenaltyService.class",Set.of("processAircraftBoundaryTimeouts","updateAircraftBoundaryState","handleBoundaryTimeout"),
            root+"server/room/config/RoomDefinitionScalarConfigEditor.class",Set.of("parseNumberValue"),
            root+"client/room/ClientDeathFlowState.class",Set.of("setBoundaryPenaltyActive","clear"));
        Set<String> resources=Set.of("META-INF/MANIFEST.MF","META-INF/mods.toml","gd656conquest.mixins.json",
            "gd656conquest.refmap.json","assets/gd656conquest/sounds.json");
        try(var before=new JarFile(args[0]);var after=new JarFile(args[1])) {
            for(JarEntry old:Collections.list(before.entries())) {
                var next=after.getJarEntry(old.getName());check(next!=null,"entry retained: "+old.getName());
                if(old.isDirectory())continue;
                byte[] a=before.getInputStream(old).readAllBytes(),b=after.getInputStream(next).readAllBytes();
                if(!old.getName().endsWith(".class")) {
                    if(!resources.contains(old.getName()))check(Arrays.equals(a,b),"existing resource unchanged: "+old.getName());
                    continue;
                }
                if(replaced.contains(old.getName()))continue;
                var original=read(a);var changed=read(b);
                for(MethodNode m:original.methods) {
                    if(allowed.getOrDefault(old.getName(),Set.of()).contains(m.name))continue;
                    if(old.getName().endsWith("/RoomDeathFlowPacket.class")&&m.name.startsWith("lambda$handle$"))continue;
                    var updated=changed.methods.stream().filter(n->n.desc.equals(m.desc)&&n.name.equals(m.name)).findFirst().orElseThrow(()->new AssertionError("Missing protected method: "+original.name+"."+m.name+m.desc));
                    normalizeConfigCopies(m);normalizeConfigCopies(updated);
                    if(old.getName().endsWith("/PlayerClassType.class")&&m.name.equals("<clinit>")
                        &&Arrays.stream(m.instructions.toArray()).noneMatch(i->i instanceof LdcInsnNode l&&"医护兵".equals(l.cst))) {
                        int renamed=0;
                        for(var instruction:updated.instructions)if(instruction instanceof LdcInsnNode literal) {
                            if("医护兵".equals(literal.cst)) {literal.cst="突击兵";renamed++;}
                            else if("突击兵".equals(literal.cst)) {literal.cst="工程兵";renamed++;}
                        }
                        check(renamed==2,"exactly two display labels changed; class keys and input parsing preserved");
                    }
                    // The two appended constructor arguments increase stack capacity, not behavior.
                    updated.maxStack=m.maxStack;updated.maxLocals=m.maxLocals;
                    String newTrace=trace(updated);
                    if(old.getName().endsWith("/NetworkManager.class"))newTrace=newTrace.replace("bf1-0.0.8","bf1-0.0.7");
                    check(trace(m).equals(newTrace),"protected method unchanged: "+original.name+"."+m.name);
                }
            }
            check(configCopies==4,"all four original config copies carry both fields");
            var network=read(after.getInputStream(after.getJarEntry(root+"network/NetworkManager.class")).readAllBytes());
            check(network.fields.stream().anyMatch(f->f.name.equals("PROTOCOL_VERSION")&&"bf1-0.0.8".equals(f.value)),"strict boundary protocol semantic version");
            var service=read(after.getInputStream(after.getJarEntry(root+"server/room/runtime/UnifiedBoundaryService.class")).readAllBytes());
            var process=service.methods.stream().filter(m->m.name.equals("process")).findFirst().orElseThrow();
            check(calls(process,"tighten")&&calls(process,"killDeserter")&&calls(process,"destroy")&&calls(process,"isOutside"),"one tightening/death/vehicle path");
            check(!calls(process,"updateAircraftBoundaryState")&&!calls(process,"processAircraftBoundaryTimeouts"),"no aircraft timer calls");
            var destruction=read(after.getInputStream(after.getJarEntry(root+"server/compat/vehicle/BoundaryVehicleDestruction.class")).readAllBytes());
            check(destruction.methods.stream().noneMatch(m->calls(m,"m_146870_")||calls(m,"m_142687_")),"boundary destruction never removes/discards directly");
            var packet=read(after.getInputStream(after.getJarEntry(root+"network/packet/RoomDeathFlowPacket.class")).readAllBytes());
            check(packet.methods.stream().anyMatch(m->calls(m,"accept")),"boundary wire update reaches new client state");
        }
        System.out.println("PASS latest release protected camera/rescue/physics/shaders/HUD and unified path: "+checks);
    }
}
