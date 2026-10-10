package tests;

import java.util.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Compares actual 0.0.10/0.0.11 archives and protects unrelated gameplay/resources/network. */
public final class NaturalRegenScopeTest {
    static int checks;
    static final String ROOT="org/mods/gd656conquest/",COMMON=ROOT+"server/room/model/RoomCommonConfig",REGEN=ROOT+"server/player/regen/NaturalRegenService",COMMANDS=ROOT+"server/core/command/NaturalRegenCommands";
    static final Map<String,Set<String>> CHANGED=Map.ofEntries(
        Map.entry("server/data/roomdefinition/RoomDefinitionRepository",Set.of("normalizeDefinition")),
        Map.entry("server/room/config/RoomDefinitionGroupConfigEditor",Set.of("replaceCommonStringGroup")),
        Map.entry("server/room/entry/RoomAdminConfigTemplateService",Set.of("buildCopiedDefinition")),
        Map.entry("server/room/entry/RoomCommands",Set.of("executeMergeWeaponWhitelist")),
        Map.entry("server/room/config/RoomConfigDescriptorRegistry",Set.of("buildDescriptors")),
        Map.entry("server/room/config/RoomDefinitionScalarConfigEditor",Set.of("parseNumberValue")),
        Map.entry("server/room/config/BoundaryConfigNumbers",Set.of("parse")),
        Map.entry("server/data/serverconfig/ServerConfigDataModel",Set.of("<init>","getBoolPassiveRegeneration","setBoolPassiveRegeneration","getPassiveRegenHealAmount","setPassiveRegenHealAmount")),
        Map.entry("server/data/serverconfig/ServerConfigStore",Set.of("normalize","getPassiveRegenIntervalTicks","isPassiveRegenerationEnabled","getPassiveRegenHealAmount")),
        Map.entry("server/core/command/ConquestServerCommands",Set.of("<clinit>","executeConfigGetAll","executeConfigGetSingle","executeConfigSet","suggestServerConfigValues","normalizeServerConfigIdSafe","readConfigValue")),
        Map.entry("server/player/regen/PassiveRegenService",Set.of("onPlayerTick")),
        Map.entry("server/room/model/RoomCommonConfig",Set.of()));
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static ClassNode node(byte[] bytes){return AmmoReserveScopeTest.read(bytes);}
    static MethodNode method(ClassNode n,String name){return AmmoReserveScopeTest.method(n,name);}
    static int calls(MethodNode m,String owner,String name){return AmmoReserveScopeTest.calls(m,owner,name);}
    static JsonObject json(Map<String,byte[]> entries,String name){return JsonParser.parseString(new String(entries.get(name),StandardCharsets.UTF_8)).getAsJsonObject();}
    static void retained(JsonObject old,JsonObject next,String path){for(var e:old.entrySet()){check(next.has(e.getKey()),"retained JSON key "+path+e.getKey());if(e.getValue().isJsonObject())retained(e.getValue().getAsJsonObject(),next.getAsJsonObject(e.getKey()),path+e.getKey()+"/");else check(e.getValue().equals(next.get(e.getKey())),"retained JSON value "+path+e.getKey());}}
    public static void main(String[] args)throws Exception {
        var old=AmmoReserveScopeTest.entries(args[0]);var next=AmmoReserveScopeTest.entries(args[1]);
        for(var e:old.entrySet()){
            String name=e.getKey();check(next.containsKey(name),"retained entry "+name);
            if(Set.of("META-INF/MANIFEST.MF","META-INF/mods.toml","gd656conquest.mixins.json","gd656conquest.refmap.json").contains(name))continue;
            if(name.startsWith(ROOT)&&name.endsWith(".class")){
                String relative=name.substring(ROOT.length(),name.length()-6),outer=relative.split("\\$",2)[0];
                if(CHANGED.containsKey(outer)){
                    if(relative.equals("server/room/model/RoomCommonConfig"))continue;
                    var before=node(e.getValue());var after=node(next.get(name));
                    for(var n:List.of(before,after)){
                        n.methods.removeIf(m->CHANGED.get(outer).contains(m.name));n.innerClasses.removeIf(i->!Objects.equals(i.outerName,n.name));
                        if(relative.equals("server/data/serverconfig/ServerConfigDataModel"))n.fields.removeIf(f->Set.of("boolPassiveRegeneration","passiveRegenHealAmount").contains(f.name));
                        if(relative.equals("server/player/regen/PassiveRegenService"))n.fields.removeIf(f->f.name.equals("HEAL_AMOUNT"));
                    }
                    check(SupportScopeTest.trace(before).equals(SupportScopeTest.trace(after)),"remaining original methods/fields/annotations preserved "+name);continue;
                }
            }
            check(Arrays.equals(e.getValue(),next.get(name)),"unchanged gameplay/resource/network "+name);
        }
        Set<String> added=Set.of("server/room/model/RoomNaturalRegenConfig","server/room/model/RoomNaturalRegenConfig$Settings","server/player/regen/NaturalRegenService","server/core/command/NaturalRegenCommands","mixin/server/NaturalFoodRegenMixin");
        for(var name:next.keySet())if(!old.containsKey(name))check(name.startsWith(ROOT)&&name.endsWith(".class")&&added.contains(name.substring(ROOT.length(),name.length()-6)),"only intended new class "+name);
        for(var name:added)check(next.containsKey(ROOT+name+".class"),"required new production class present "+name);
        var common=node(next.get(COMMON+".class"));check(common.recordComponents.size()==54,"five migrated configuration extensions");
        check(common.methods.stream().filter(m->m.name.equals("<init>")).map(m->Type.getArgumentTypes(m.desc).length).collect(java.util.stream.Collectors.toSet()).equals(Set.of(49,51,53,54)),"original and intermediate constructor APIs retained");
        for(var copy:List.of("server/data/roomdefinition/RoomDefinitionRepository","server/room/config/RoomDefinitionGroupConfigEditor","server/room/entry/RoomAdminConfigTemplateService","server/room/entry/RoomCommands")){
            int count=0;for(var m:node(next.get(ROOT+copy+".class")).methods)if(CHANGED.get(copy).contains(m.name))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(COMMON)&&c.name.equals("<init>")){
                count++;check(Type.getArgumentTypes(c.desc).length==54,"actual copy calls current canonical constructor "+copy);
                for(String field:List.of("flightVehicleOutsideBoundaryDeathSeconds","flightBoundaryPaddingBlocks","allowEnemySupportResupply","medicalBoxDamageCooldownSeconds","naturalRegenConfig"))check(calls(m,COMMON,field)==1,"actual copy preserves migrated field "+copy+"/"+field);
            }check(count==1,"one actual copy audited "+copy);
        }
        var passive=method(node(next.get(ROOT+"server/player/regen/PassiveRegenService.class")),"onPlayerTick");
        for(String name:List.of("enabled","startDelay","interval","amount"))check(calls(passive,REGEN,name)==1,"actual passive hook "+name);
        check(calls(passive,ROOT+"server/support/SupportRuntime","pausePassive")==1,"shared treatment guard retained");check(calls(passive,"net/minecraft/server/level/ServerPlayer","m_6330_")==1,"successful startup remains private audio");
        var commands=node(next.get(ROOT+"server/core/command/ConquestServerCommands.class"));
        for(var hook:Map.of("<clinit>","keys","executeConfigGetAll","showAdded","executeConfigGetSingle","get","executeConfigSet","set","suggestServerConfigValues","suggest","normalizeServerConfigIdSafe","normalize","readConfigValue","read").entrySet())check(calls(method(commands,hook.getKey()),COMMANDS,hook.getValue())==1,"actual native command delegates "+hook.getKey());
        var oldMixins=json(old,"gd656conquest.mixins.json");var newMixins=json(next,"gd656conquest.mixins.json");var oldCommon=oldMixins.getAsJsonArray("mixins");var newCommon=newMixins.getAsJsonArray("mixins");
        check(newCommon.size()==oldCommon.size()+1,"exactly one common Mixin added");check(newCommon.contains(new JsonPrimitive("server.NaturalFoodRegenMixin")),"food Mixin registered");
        // Insertion position follows the existing support mixin; compare without the new entry.
        var filtered=json(next,"gd656conquest.mixins.json");filtered.getAsJsonArray("mixins").remove(new JsonPrimitive("server.NaturalFoodRegenMixin"));check(oldMixins.equals(filtered),"all old Mixin entries/order/settings retained");
        var oldRef=json(old,"gd656conquest.refmap.json");var newRef=json(next,"gd656conquest.refmap.json");retained(oldRef,newRef,"");
        var mappings=newRef.getAsJsonObject("mappings");check(mappings.size()==oldRef.getAsJsonObject("mappings").size()+1&&mappings.has(ROOT+"mixin/server/NaturalFoodRegenMixin"),"one new official-to-SRG refmap target");
        for(var e:next.entrySet()){
            check(!e.getKey().startsWith("tests/")&&!e.getKey().startsWith("tools/")&&!e.getKey().startsWith("net/minecraft/")&&!e.getKey().startsWith("com/tacz/"),"no fixtures or third-party classes packaged");
            if(e.getKey().endsWith(".class"))check(new ClassReader(e.getValue()).readUnsignedShort(6)==61,"Java 17 class "+e.getKey());
        }
        for(String name:List.of("META-INF/MANIFEST.MF","META-INF/mods.toml"))check(new String(next.get(name),StandardCharsets.UTF_8).contains("0.9.3-bf1-0.0.11"),"correct packaged version "+name);
        System.out.println("PASS 0.0.10 -> 0.0.11 original scope/network/config copies/refmap/food registration: "+checks);
    }
}
