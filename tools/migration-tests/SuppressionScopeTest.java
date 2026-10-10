package tests;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Actual archive comparison, six copy chains, append-only S2C registration and isolated client gates. */
public final class SuppressionScopeTest {
    static int checks;
    static final String ROOT="org/mods/gd656conquest/",COMMON=ROOT+"server/room/model/RoomCommonConfig",RT=ROOT+"server/support/SupportRuntime",NET=ROOT+"server/support/SuppressionNetwork";
    static final Map<String,Set<String>> CHANGED=Map.ofEntries(
        Map.entry("server/data/roomdefinition/RoomDefinitionRepository",Set.of("normalizeDefinition")),
        Map.entry("server/room/config/RoomDefinitionGroupConfigEditor",Set.of("replaceCommonStringGroup")),
        Map.entry("server/room/entry/RoomAdminConfigTemplateService",Set.of("buildCopiedDefinition")),
        Map.entry("server/room/entry/RoomCommands",Set.of("executeMergeWeaponWhitelist")),
        Map.entry("server/room/config/RoomConfigDescriptorRegistry",Set.of("buildDescriptors")),
        Map.entry("server/room/config/BoundaryConfigNumbers",Set.of("parse")),
        Map.entry("server/room/model/RoomCommonConfig",Set.of()),
        Map.entry("server/support/SupportRules",Set.of("nearGain")),
        Map.entry("server/support/SupportRuntime",Set.of("reset","suppressionBlocks","pausePassive","tick","boxes")),
        Map.entry("client/room/ClientBoundaryState",Set.of("renderWorld")),
        Map.entry("network/NetworkManager",Set.of()));
    static final Set<String> ADDED=Set.of("server/room/model/RoomSuppressionConfig","server/support/TaczSuppression","server/support/SuppressionNetwork","server/support/SupportRules$Suppression","network/packet/SuppressionPacket","client/room/ClientSuppressionState","client/room/SuppressionHudEvents","mixin/client/SuppressionWorldMixin","mixin/compat/tacz/SupportSuppressionMixin","mixin/compat/tacz/SupportShotMixin","mixin/compat/tacz/SupportBulletMixin","mixin/compat/tacz/KineticBulletSupportAccessor");
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static ClassNode node(byte[] bytes){return AmmoReserveScopeTest.read(bytes);}
    static MethodNode method(ClassNode n,String name){return AmmoReserveScopeTest.method(n,name);}
    static int calls(MethodNode m,String owner,String name){return AmmoReserveScopeTest.calls(m,owner,name);}
    static JsonObject json(Map<String,byte[]> jar,String path){return JsonParser.parseString(new String(jar.get(path),StandardCharsets.UTF_8)).getAsJsonObject();}
    static void retain(JsonObject old,JsonObject next){for(var e:old.entrySet()){check(next.has(e.getKey()),"retained refmap key "+e.getKey());if(e.getValue().isJsonObject())retain(e.getValue().getAsJsonObject(),next.getAsJsonObject(e.getKey()));else check(e.getValue().equals(next.get(e.getKey())),"retained refmap mapping "+e.getKey());}}
    static void network(ClassNode n,boolean next){
        var protocol=n.fields.stream().filter(f->f.name.equals("PROTOCOL_VERSION")).findFirst().orElseThrow();check(protocol.value.equals(next?"bf1-0.0.12":"bf1-0.0.8"),"strict protocol deliberately upgraded");protocol.value="bf1-0.0.8";
        for(var m:n.methods)for(var i:m.instructions.toArray())if(i instanceof LdcInsnNode ldc&&"bf1-0.0.12".equals(ldc.cst))ldc.cst="bf1-0.0.8";
        if(next){var init=method(n,"init");int count=0;for(var i:init.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals(NET)&&c.name.equals("register")){
            count++;var first=i.getPrevious().getPrevious();var last=i.getNext();check(first instanceof FieldInsnNode f&&f.name.equals("CHANNEL")&&i.getPrevious() instanceof FieldInsnNode id&&id.name.equals("packetId")&&last instanceof FieldInsnNode put&&put.name.equals("packetId"),"real append registration reads channel/id and writes next ID");
            check(last.getNext().getOpcode()==Opcodes.RETURN,"new registration appended after every existing ID");var previous=first.getPrevious();check(previous instanceof FieldInsnNode put&&put.name.equals("packetId"),"original final registration retained before append");
            var cursor=first;while(true){var following=cursor.getNext();init.instructions.remove(cursor);if(cursor==last)break;cursor=following;}
        }check(count==1,"one suppression registration");}
    }
    public static void main(String[] args)throws Exception {
        var old=AmmoReserveScopeTest.entries(args[0]);var next=AmmoReserveScopeTest.entries(args[1]);
        for(var e:old.entrySet()){
            String name=e.getKey();check(next.containsKey(name),"retained archive entry "+name);
            if(Set.of("META-INF/MANIFEST.MF","META-INF/mods.toml","gd656conquest.mixins.json","gd656conquest.refmap.json").contains(name))continue;
            if(name.startsWith(ROOT)&&name.endsWith(".class")){
                String relative=name.substring(ROOT.length(),name.length()-6),outer=relative.split("\\$",2)[0];
                if(CHANGED.containsKey(outer)){
                    if(relative.equals("server/room/model/RoomCommonConfig"))continue;
                    var before=node(e.getValue());var after=node(next.get(name));
                    if(relative.equals("network/NetworkManager")){network(before,false);network(after,true);}
                    for(var n:List.of(before,after)){
                        n.methods.removeIf(m->CHANGED.get(outer).contains(m.name)||CHANGED.get(outer).stream().anyMatch(s->m.name.startsWith("lambda$"+s+"$")));
                        n.innerClasses.removeIf(i->!Objects.equals(i.outerName,n.name)||i.name.equals(ROOT+"server/support/SupportRules$Suppression"));
                        if(n.nestMembers!=null)n.nestMembers.removeIf(member->member.equals(ROOT+"server/support/SupportRules$Suppression"));
                        if(relative.equals("server/support/SupportRuntime$Recipient")){n.methods.removeIf(m->m.name.equals("<init>"));n.fields.removeIf(f->Set.of("suppression","shotGains","shotTimes","visual").contains(f.name));}
                    }
                    String a=SupportScopeTest.trace(before),b=SupportScopeTest.trace(after);
                    if(!a.equals(b)){Files.writeString(Path.of("build/migration-verification/suppression/scope-prior.txt"),a);Files.writeString(Path.of("build/migration-verification/suppression/scope-current.txt"),b);}
                    check(a.equals(b),"all remaining original code/fields/annotations retained "+name);continue;
                }
            }
            check(Arrays.equals(e.getValue(),next.get(name)),"unchanged gameplay/resource/old-packet "+name);
        }
        Set<String> resources=Set.of("assets/gd656conquest/shaders/post/suppression_world.json","assets/gd656conquest/shaders/program/suppression_world.json","assets/gd656conquest/shaders/program/suppression_world.fsh");
        for(var name:next.keySet())if(!old.containsKey(name))check(resources.contains(name)||(name.startsWith(ROOT)&&name.endsWith(".class")&&ADDED.contains(name.substring(ROOT.length(),name.length()-6))),"only intended new classes/resources "+name);
        for(String name:ADDED)check(next.containsKey(ROOT+name+".class"),"required production class "+name);
        var common=node(next.get(COMMON+".class"));check(common.recordComponents.size()==55,"all six configuration extensions migrated");
        check(common.methods.stream().filter(m->m.name.equals("<init>")).map(m->Type.getArgumentTypes(m.desc).length).collect(java.util.stream.Collectors.toSet()).equals(Set.of(49,51,53,54,55)),"all preceding constructor APIs retained");
        for(String copy:List.of("server/data/roomdefinition/RoomDefinitionRepository","server/room/config/RoomDefinitionGroupConfigEditor","server/room/entry/RoomAdminConfigTemplateService","server/room/entry/RoomCommands")){
            int count=0;for(var m:node(next.get(ROOT+copy+".class")).methods)if(CHANGED.get(copy).contains(m.name))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(COMMON)&&c.name.equals("<init>")){
                count++;check(Type.getArgumentTypes(c.desc).length==55,"actual copy calls six-field constructor "+copy);for(String field:List.of("flightVehicleOutsideBoundaryDeathSeconds","flightBoundaryPaddingBlocks","allowEnemySupportResupply","medicalBoxDamageCooldownSeconds","naturalRegenConfig","suppressionConfig"))check(calls(m,COMMON,field)==1,"copy carries actual field "+copy+"/"+field);
            }check(count==1,"one actual common copy "+copy);
        }
        var runtime=node(next.get(RT+".class"));check(calls(method(runtime,"pausePassive"),RT,"suppressionBlocks")==1&&calls(method(runtime,"boxes"),RT,"suppressionBlocks")==1,"both actual shared passive and medical-box suppression guards");
        var boundary=method(node(next.get(ROOT+"client/room/ClientBoundaryState.class")),"renderWorld");check(calls(boundary,ROOT+"client/room/ClientSuppressionState","opacity")==1,"boundary shader uses actual shared opacity");
        var client=node(next.get(ROOT+"client/room/ClientSuppressionState.class"));check(calls(method(client,"renderWorld"),ROOT+"client/room/ClientBoundaryState","hasWorldEffect")==1,"standalone vignette skipped during combined boundary pass");
        var registration=method(node(next.get(NET+".class")),"register");check(Arrays.stream(registration.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.owner.equals("net/minecraftforge/network/NetworkDirection")&&f.name.equals("PLAY_TO_CLIENT")),"new packet registration restricted to client-bound direction");
        var handler=method(node(next.get(ROOT+"network/packet/SuppressionPacket.class")),"handle");check(Arrays.stream(handler.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("PLAY_TO_CLIENT")),"handler independently checks direction");
        check(calls(handler,"net/minecraftforge/network/NetworkEvent$Context","enqueueWork")==1&&calls(handler,"net/minecraftforge/network/NetworkEvent$Context","setPacketHandled")==1,"packet queues correct-side work and marks handled");
        var packet=node(next.get(ROOT+"network/packet/SuppressionPacket.class"));check(packet.methods.stream().anyMatch(m->calls(m,"net/minecraftforge/fml/DistExecutor","unsafeRunWhenOn")==1),"client class access isolated behind distribution gate");
        var oldConfig=json(old,"gd656conquest.mixins.json");var config=json(next,"gd656conquest.mixins.json");
        for(String name:List.of("compat.tacz.SupportSuppressionMixin","compat.tacz.SupportShotMixin","compat.tacz.SupportBulletMixin","compat.tacz.KineticBulletSupportAccessor"))check(config.getAsJsonArray("mixins").remove(new JsonPrimitive(name)),"new TACZ hook registered "+name);
        check(config.getAsJsonArray("client").remove(new JsonPrimitive("client.SuppressionWorldMixin")),"new client render hook registered");check(config.equals(oldConfig),"existing Mixin order/settings preserved");
        var oldRef=json(old,"gd656conquest.refmap.json");var ref=json(next,"gd656conquest.refmap.json");retain(oldRef,ref);
        check(ref.getAsJsonObject("mappings").size()==oldRef.getAsJsonObject("mappings").size()+1&&ref.getAsJsonObject("mappings").has(ROOT+"mixin/client/SuppressionWorldMixin"),"only new vanilla renderer target added to refmap");
        for(var e:next.entrySet()){
            check(!e.getKey().startsWith("tests/")&&!e.getKey().startsWith("tools/")&&!e.getKey().startsWith("net/minecraft/")&&!e.getKey().startsWith("com/tacz/"),"no fixture or third-party class packaged");if(e.getKey().endsWith(".class"))check(new ClassReader(e.getValue()).readUnsignedShort(6)==61,"Java 17 class "+e.getKey());
        }
        for(String name:List.of("META-INF/MANIFEST.MF","META-INF/mods.toml"))check(new String(next.get(name),StandardCharsets.UTF_8).contains("0.9.3-bf1-0.0.12"),"packaged version "+name);
        System.out.println("PASS 0.0.11 -> 0.0.12 production scope/six-field copies/append-only protocol/refmap: "+checks);
    }
}
