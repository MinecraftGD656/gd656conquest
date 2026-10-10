package tests;

import java.lang.reflect.*;
import java.util.*;
import com.google.gson.Gson;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.mods.gd656conquest.server.room.config.*;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.data.serverconfig.*;
import org.mods.gd656conquest.server.core.command.NaturalRegenCommands;

/** Exercises shipped Gson records, native editors/copies, store migration and command parsing. */
public final class NaturalRegenConfigTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static sun.misc.Unsafe unsafe() throws Exception {
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);return (sun.misc.Unsafe)field.get(null);
    }
    static ServerConfigStore store(ServerConfigDataModel model) throws Exception {
        var store=(ServerConfigStore)unsafe().allocateInstance(ServerConfigStore.class);
        var field=ServerConfigStore.class.getDeclaredField("cache");field.setAccessible(true);field.set(store,model);return store;
    }
    static RoomDefinition room(RoomCommonConfig config){return new RoomDefinition("regen-test","test",RoomGameplayId.DEATHMATCH,RoomLifecycleState.DISABLED,config,RoomDeathmatchConfig.defaults());}
    static RoomDefinition edit(RoomDefinition room,String id,String value){return RoomDefinitionScalarConfigEditor.apply(room,RoomConfigDescriptorRegistry.find(id).orElseThrow(),value,null);}
    static void preserved(RoomDefinition actual,RoomCommonConfig expected){
        check(actual.commonConfig().naturalRegenConfig().equals(expected.naturalRegenConfig()),"real copy preserves every regeneration option");
        check(!actual.commonConfig().allowEnemySupportResupply()&&actual.commonConfig().medicalBoxDamageCooldownSeconds()==4.5
            &&actual.commonConfig().flightVehicleOutsideBoundaryDeathSeconds()==60&&actual.commonConfig().flightBoundaryPaddingBlocks()==125.5,"real copy preserves all preceding extensions");
    }
    public static void main(String[] args) throws Exception {
        var gson=new Gson();var config=gson.fromJson("{}",RoomCommonConfig.class);var defaults=config.naturalRegenConfig();
        check(defaults.mode().equals("房间")&&defaults.passiveEnabled()&&!defaults.vanillaEnabled()
            &&defaults.startDelaySeconds()==6&&defaults.intervalTicks()==10&&defaults.healAmount()==.5,"old room receives final 6s/10tick/0.5HP defaults");
        check(gson.fromJson(gson.toJson(config),RoomCommonConfig.class).equals(config),"default record saves/reloads losslessly");
        for(String disabled:List.of("禁用","disabled","disable","off"," OFF ")) {
            var old=gson.fromJson("{\"naturalRegenConfig\":{\"mode\":\""+disabled+"\",\"passiveEnabled\":true,\"vanillaEnabled\":true,\"startDelaySeconds\":4,\"intervalTicks\":20,\"healAmount\":1.5}}",RoomCommonConfig.class);
            var policy=old.naturalRegenConfig();
            check(policy.mode().equals("房间")&&!policy.passiveEnabled()&&!policy.vanillaEnabled()&&policy.startDelaySeconds()==4&&policy.intervalTicks()==20&&policy.healAmount()==1.5,"disabled migration retains timing and amount, disables both");
            check(gson.fromJson(gson.toJson(old),RoomCommonConfig.class).equals(old),"disabled migration persists normalized room policy");
        }
        var global=new RoomNaturalRegenConfig("global",true,false,1,1,2D).resolve(false,true,12,7,1.25);
        check(!global.passiveEnabled()&&global.vanillaEnabled()&&global.startDelaySeconds()==12&&global.intervalTicks()==7&&global.healAmount()==1.25F,"global source overrides all local parameters");
        var local=new RoomNaturalRegenConfig("ROOM",true,true,4,20,1.5).resolve(false,false,12,7,2);
        check(local.passiveEnabled()&&local.vanillaEnabled()&&local.startDelaySeconds()==4&&local.intervalTicks()==20&&local.healAmount()==1.5F,"room source overrides all global parameters");
        for(Double amount:Arrays.asList(null,Double.NaN,Double.POSITIVE_INFINITY,-1D))check(RoomNaturalRegenConfig.amount(amount)==.5,"invalid stored amount falls back to 0.5");
        check(RoomNaturalRegenConfig.amount(0D)==0&&RoomNaturalRegenConfig.amount(2000D)==1024,"stored zero retained, excessive amount clamped");
        var model=gson.fromJson("{\"passiveRegenStartDelaySeconds\":9,\"passiveRegenIntervalTicks\":3,\"boolNaturalRegeneration\":true}",ServerConfigDataModel.class);
        check(model.getPassiveRegenStartDelaySeconds()==9&&model.getPassiveRegenIntervalTicks()==3&&model.getBoolNaturalRegeneration()
            &&model.getBoolPassiveRegeneration()&&model.getPassiveRegenHealAmount()==.5,"old global explicit values retained; new keys default");
        var store=store(model);var normalize=ServerConfigStore.class.getDeclaredMethod("normalize",ServerConfigDataModel.class);normalize.setAccessible(true);normalize.invoke(store,model);
        check(store.getPassiveRegenIntervalTicks()==3&&store.getPassiveRegenStartDelaySeconds()==9,"normalization preserves explicit old interval/delay");
        model.setBoolPassiveRegeneration(null);model.setPassiveRegenHealAmount(Double.NaN);model.setPassiveRegenIntervalTicks(null);model.setPassiveRegenStartDelaySeconds(-1);normalize.invoke(store,model);
        check(store.isPassiveRegenerationEnabled()&&store.getPassiveRegenHealAmount()==.5&&store.getPassiveRegenIntervalTicks()==10&&store.getPassiveRegenStartDelaySeconds()==6,"null/invalid global fields normalized and getters agree");
        model.setBoolPassiveRegeneration(false);model.setPassiveRegenHealAmount(0D);normalize.invoke(store,model);
        check(!store.isPassiveRegenerationEnabled()&&store.getPassiveRegenHealAmount()==0,"explicit global disabled and zero preserved");
        check(gson.fromJson(gson.toJson(model),ServerConfigDataModel.class).getPassiveRegenHealAmount()==0,"global zero persists");
        var mode=RoomConfigDescriptorRegistry.find("naturalRegenMode").orElseThrow();
        check(mode.suggestionValues().equals(List.of("全局","房间")),"editor offers only current sources");
        var room=room(gson.fromJson("{\"allowEnemySupportResupply\":false,\"medicalBoxDamageCooldownSeconds\":4.5,\"flightVehicleOutsideBoundaryDeathSeconds\":60,\"flightBoundaryPaddingBlocks\":125.5}",RoomCommonConfig.class));
        room=edit(room,"naturalRegenMode","global");room=edit(room,"roomPassiveRegenEnabled","false");room=edit(room,"roomVanillaRegenEnabled","true");
        room=edit(room,"passiveRegenStartDelaySeconds","0");room=edit(room,"passiveRegenIntervalTicks","7");room=edit(room,"passiveRegenHealAmount","1.25");
        var edited=room.commonConfig().naturalRegenConfig();check(edited.mode().equals("全局")&&!edited.passiveEnabled()&&edited.vanillaEnabled()&&edited.startDelaySeconds()==0&&edited.intervalTicks()==7&&edited.healAmount()==1.25,"all six settings editable through actual native binding");
        for(String[] invalid:new String[][]{{"naturalRegenMode","other"},{"passiveRegenStartDelaySeconds","-1"},{"passiveRegenIntervalTicks","0"},{"passiveRegenIntervalTicks","1.5"},{"passiveRegenHealAmount","-1"},{"passiveRegenHealAmount","NaN"},{"passiveRegenHealAmount","Infinity"},{"passiveRegenHealAmount","1025"}}){
            try{edit(room,invalid[0],invalid[1]);throw new AssertionError("invalid editor setting accepted");}catch(IllegalArgumentException expected){check(true,"invalid editor value rejected");}
        }
        check(edit(room,"passiveRegenHealAmount","del").commonConfig().naturalRegenConfig().healAmount()==.5,"deleting setting restores actual room default");
        var repository=Class.forName("org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepository");var reload=repository.getDeclaredMethod("normalizeDefinition",RoomDefinition.class);reload.setAccessible(true);
        preserved((RoomDefinition)reload.invoke(unsafe().allocateInstance(repository),room),room.commonConfig());
        var templates=Class.forName("org.mods.gd656conquest.server.room.entry.RoomAdminConfigTemplateService");var copy=templates.getDeclaredMethod("buildCopiedDefinition",RoomDefinition.class,RoomDefinition.class);copy.setAccessible(true);
        preserved((RoomDefinition)copy.invoke(unsafe().allocateInstance(templates),room,room(config)),room.commonConfig());
        var group=RoomDefinitionGroupConfigEditor.class.getDeclaredMethod("replaceCommonStringGroup",RoomDefinition.class,String.class,List.class);group.setAccessible(true);
        preserved((RoomDefinition)group.invoke(null,room,"supportcampaherokits",List.of("kit")),room.commonConfig());
        check(NaturalRegenCommands.keys(List.of("boolNaturalRegeneration","boolPassiveRegeneration")).equals(List.of("boolNaturalRegeneration","boolPassiveRegeneration","passiveRegenHealAmount")),"keys deduplicate and retain original order");
        check(NaturalRegenCommands.normalize("PASSIVEREGENAMOUNT").equals("passiveRegenHealAmount")&&NaturalRegenCommands.normalize("passiveregenenabled").equals("boolPassiveRegeneration"),"new command aliases");
        check(NaturalRegenCommands.normalize("boolNaturalRegeneration")==null&&NaturalRegenCommands.normalize(null)==null&&NaturalRegenCommands.read(store,"old-key")==null,"unknown command IDs defer to native path");
        check(NaturalRegenCommands.read(store,"boolPassiveRegeneration").equals("false")&&NaturalRegenCommands.read(store,"passiveRegenHealAmount").equals("0.0"),"reads use actual global store");
        check(NaturalRegenCommands.parse("boolPassiveRegeneration","FALSE").equals(false)&&NaturalRegenCommands.parse("passiveRegenHealAmount","1.25").equals(1.25),"new command accepts strict boolean and fractional HP");
        for(String amount:List.of("-1","NaN","Infinity","1025","abc")){try{NaturalRegenCommands.parse("passiveRegenHealAmount",amount);throw new AssertionError("invalid command amount accepted");}catch(CommandSyntaxException expected){check(true,"invalid command amount rejected before save");}}
        try{NaturalRegenCommands.parse("boolPassiveRegeneration","yes");throw new AssertionError("invalid boolean accepted");}catch(CommandSyntaxException expected){check(true,"strict global boolean");}
        System.out.println("PASS natural regeneration defaults/migration/editor/copies/store/commands: "+checks);
    }
}
