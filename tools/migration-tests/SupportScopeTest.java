package tests;

import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Actual JAR preservation and support call-site audit, independent of engine fixtures. */
public final class SupportScopeTest {
    static int checks;
    static final String ROOT="org/mods/gd656conquest/",RT=ROOT+"server/support/SupportRuntime";
    static final Map<String,Set<String>> CHANGED=Map.ofEntries(
        Map.entry("common/item/AmmoBagItem",Set.of("m_7203_","m_6880_")),
        Map.entry("common/item/MedicalBagItem",Set.of("m_7203_","m_6880_","applyHealEffect")),
        Map.entry("server/data/roomdefinition/RoomDefinitionRepository",Set.of("normalizeDefinition")),
        Map.entry("server/player/regen/PassiveRegenService",Set.of("onPlayerTick","resetPlayer")),
        Map.entry("server/player/state/PlayerItemInteractionService",Set.of("handlePlayerItemToss","handleEntityItemPickup","handleMedicalBagPickup")),
        Map.entry("server/room/config/BoundaryConfigNumbers",Set.of("parse")),
        Map.entry("server/room/config/RoomConfigDescriptorRegistry",Set.of("buildDescriptors")),
        Map.entry("server/room/config/RoomDefinitionGroupConfigEditor",Set.of("replaceCommonStringGroup")),
        Map.entry("server/room/entry/RoomAdminConfigTemplateService",Set.of("buildCopiedDefinition")),
        Map.entry("server/room/entry/RoomCommands",Set.of("executeMergeWeaponWhitelist")),
        Map.entry("server/room/model/RoomCommonConfig",Set.of()),
        Map.entry("server/room/runtime/RoomDeathSupportDropService",Set.of("handlePlayerKillSupportDrop")),
        Map.entry("server/room/runtime/RoomItemReplenishService",Set.of("replenishConfiguredItem")),
        Map.entry("server/room/runtime/RoomPlayerLoadoutService",Set.of("placeLoadoutItem","trySupplyAmmoByBag")),
        Map.entry("server/room/runtime/RoomSupportBoxService",Set.of("spawnPermanentSupportBoxes","tryUseAmmoBoxItem","tryUseMedicalBoxItem","spawnAmmoBoxFromThrownItem","spawnMedicalBoxFromThrownItem","processAmmoBoxAura","processMedicalBoxAura","tryUseNearbyAmmoBox","tryTriggerMedicalBoxForPlayer","<clinit>")),
        Map.entry("server/support/SupportAmmo",Set.of("bag")));
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static Map<String,byte[]> read(String file)throws Exception{return AmmoReserveScopeTest.entries(file);}
    static ClassNode node(byte[] bytes){return AmmoReserveScopeTest.read(bytes);}
    static String trace(ClassNode node){
        AmmoReserveScopeTest.normalize(node);
        Map<String,String> names=new HashMap<>();Map<String,Integer> indices=new HashMap<>();
        node.methods.stream().filter(m->m.name.startsWith("lambda$")).sorted(Comparator.comparingInt(m->Integer.parseInt(m.name.substring(m.name.lastIndexOf('$')+1))))
            .forEach(m->{String stem=m.name.substring(0,m.name.lastIndexOf('$')+1);int index=indices.merge(stem,1,Integer::sum)-1;names.put(m.name,stem+index);});
        var writer=new ClassWriter(0);
        node.accept(new org.objectweb.asm.commons.ClassRemapper(writer,new org.objectweb.asm.commons.Remapper(){
            @Override public String mapMethodName(String owner,String name,String descriptor){return owner.equals(node.name)?names.getOrDefault(name,name):name;}
        }));
        var normalized=node(writer.toByteArray());normalized.methods.sort(Comparator.comparing((MethodNode m)->m.name).thenComparing(m->m.desc));
        return AmmoReserveScopeTest.trace(normalized);
    }
    static MethodNode method(ClassNode node,String name){return AmmoReserveScopeTest.method(node,name);}
    static int calls(MethodNode method,String owner,String name){return AmmoReserveScopeTest.calls(method,owner,name);}
    static void hook(Map<String,byte[]> jar,String owner,String method,String name){check(calls(method(node(jar.get(ROOT+owner+".class")),method),RT,name)==1,"actual hook: "+owner+"."+method+" -> "+name);}
    public static void main(String[] args)throws Exception {
        var old=read(args[0]);var next=read(args[1]);
        for(var e:old.entrySet()) {
            String name=e.getKey();check(next.containsKey(name),"retained entry: "+name);
            if(Set.of("META-INF/MANIFEST.MF","META-INF/mods.toml","gd656conquest.mixins.json","gd656conquest.refmap.json").contains(name))continue;
            if(name.startsWith(ROOT)&&name.endsWith(".class")) {
                String relative=name.substring(ROOT.length(),name.length()-6),outer=relative.split("\\$",2)[0];
                if(CHANGED.containsKey(outer)) {
                    if(relative.equals("server/room/model/RoomCommonConfig"))continue;
                    var before=node(e.getValue());var after=node(next.get(name));
                    var methods=CHANGED.get(outer);
                    for(var n:List.of(before,after)) {
                        n.methods.removeIf(m->methods.contains(m.name)||methods.stream().anyMatch(s->m.name.startsWith("lambda$"+s+"$")));
                        // javac emits references for foreign inner types used only by the replaced bodies.
                        n.innerClasses.removeIf(i->!Objects.equals(i.outerName,n.name));
                    }
                    if(relative.equals("server/room/runtime/RoomSupportBoxService")) {
                        for(var n:List.of(before,after))for(var field:n.fields)if(field.name.equals("AMMO_BOX_MAX_USE_COUNT")||field.name.equals("MEDICAL_BOX_MAX_USE_COUNT"))field.value=0;
                    }
                    if(!trace(before).equals(trace(after))) {
                        java.nio.file.Files.writeString(java.nio.file.Path.of("build/migration-verification/support/scope-prior.txt"),trace(before));
                        java.nio.file.Files.writeString(java.nio.file.Path.of("build/migration-verification/support/scope-current.txt"),trace(after));
                    }
                    check(trace(before).equals(trace(after)),"remaining original class code preserved: "+name);continue;
                }
            }
            check(Arrays.equals(e.getValue(),next.get(name)),"unchanged existing class/resource: "+name);
        }
        Set<String> allowed=Set.of("server/support/SupportContext","server/support/SupportRules","server/support/SupportRules$Progress","server/support/SupportRuntime","server/support/SupportRuntime$Recipient","server/support/SupportRuntime$MedicalTreatment","server/support/SupportDropGuard","client/room/ClientAmmoSupplySound","mixin/client/AmmoSupplySoundAccessor","mixin/server/SupportDropMixin");
        for(var name:next.keySet())if(!old.containsKey(name))check(name.startsWith(ROOT)&&name.endsWith(".class")&&allowed.contains(name.substring(ROOT.length(),name.length()-6)),"intended new production class: "+name);
        hook(next,"common/item/AmmoBagItem","m_7203_","useBag");hook(next,"common/item/AmmoBagItem","m_6880_","interactBag");
        hook(next,"common/item/MedicalBagItem","m_7203_","useBag");hook(next,"common/item/MedicalBagItem","m_6880_","interactBag");
        hook(next,"server/player/state/PlayerItemInteractionService","handlePlayerItemToss","toss");hook(next,"server/player/state/PlayerItemInteractionService","handleEntityItemPickup","pickup");
        hook(next,"server/room/runtime/RoomPlayerLoadoutService","placeLoadoutItem","prepareLoadoutStack");
        hook(next,"server/room/runtime/RoomDeathSupportDropService","handlePlayerKillSupportDrop","markDeathReward");
        hook(next,"server/room/runtime/RoomItemReplenishService","replenishConfiguredItem","recharge");
        hook(next,"server/room/runtime/RoomSupportBoxService","processAmmoBoxAura","boxes");hook(next,"server/room/runtime/RoomSupportBoxService","processMedicalBoxAura","boxes");
        hook(next,"server/player/regen/PassiveRegenService","onPlayerTick","pausePassive");hook(next,"server/player/regen/PassiveRegenService","resetPlayer","reset");
        for(String type:List.of("Ammo","Medical")) {
            hook(next,"server/room/runtime/RoomSupportBoxService","tryUse"+type+"BoxItem","stampBoxThrow");
            hook(next,"server/room/runtime/RoomSupportBoxService","spawn"+type+"BoxFromThrownItem","initializeBox");
        }
        var common=node(next.get(ROOT+"server/room/model/RoomCommonConfig.class"));
        check(common.recordComponents.size()==53,"actual canonical record has four migrated extensions");
        for(String copy:List.of("server/data/roomdefinition/RoomDefinitionRepository","server/room/config/RoomDefinitionGroupConfigEditor","server/room/entry/RoomAdminConfigTemplateService","server/room/entry/RoomCommands")) {
            var n=node(next.get(ROOT+copy+".class"));int constructors=0;
            for(var m:n.methods)if(CHANGED.get(copy).contains(m.name))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(ROOT+"server/room/model/RoomCommonConfig")&&c.name.equals("<init>")) {
                constructors++;check(Type.getArgumentTypes(c.desc).length==53,"real copy calls expanded constructor: "+copy);
                for(String field:List.of("flightVehicleOutsideBoundaryDeathSeconds","flightBoundaryPaddingBlocks","allowEnemySupportResupply","medicalBoxDamageCooldownSeconds"))check(calls(m,ROOT+"server/room/model/RoomCommonConfig",field)==1,"real copy carries extension: "+copy+" / "+field);
            }
            check(constructors==1,"one audited common copy in "+copy);
        }
        for(String owner:List.of("common/item/MedicalBagItem","server/player/state/PlayerItemInteractionService"))check(!node(next.get(ROOT+owner+".class")).methods.stream().anyMatch(m->m.name.equals("applyHealEffect")||m.name.equals("handleMedicalBagPickup")),"old medical potion entry removed");
        for(String method:List.of("tryUseNearbyAmmoBox","tryTriggerMedicalBoxForPlayer")) {
            var m=method(node(next.get(ROOT+"server/room/runtime/RoomSupportBoxService.class")),method);
            check(m.instructions.size()==2&&m.instructions.getFirst().getOpcode()==Opcodes.ICONST_0,"legacy interaction/potion trigger disabled");
        }
        for(String name:next.keySet())check(!name.startsWith("tests/")&&!name.startsWith("tools/")&&!name.startsWith("net/minecraft/"),"no test/engine fixture packaged: "+name);
        System.out.println("PASS 0.0.9 -> 0.0.10 production methods/resources/support hooks/four config copies: "+checks);
    }
}
