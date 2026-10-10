package tests;

import com.google.gson.Gson;
import org.mods.gd656conquest.server.support.SupportRules;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.room.config.*;

/** Checks temporal behavior and migration, rather than duplicating the implementation. */
public final class SupportRulesTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static void equal(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-8,message+": "+actual);}
    public static void main(String[] args)throws Exception {
        var cfg=RoomSuppressionConfig.defaults();
        check(!SupportRules.damageBlocksHealing(100,-1,3),"no medical-box cooldown before any injury");
        check(SupportRules.damageBlocksHealing(159,100,3) && !SupportRules.damageBlocksHealing(160,100,3),"medical-box injury cooldown expires at three seconds");
        check(SupportRules.damageBlocksHealing(169,110,3),"another injury restarts the box wait");
        check(!SupportRules.damageBlocksHealing(100,100,0),"zero disables the box injury wait");
        check(SupportRules.damageBlocksHealing(109,100,.5) && !SupportRules.damageBlocksHealing(110,100,.5),"fractional box cooldown has exact tick boundary");
        var globalPolicy=new RoomNaturalRegenConfig("全局",null,null,null,null,null).resolve(false,true,12,7,.5);
        check(!globalPolicy.passiveEnabled() && globalPolicy.vanillaEnabled() && globalPolicy.startDelaySeconds()==12
            && globalPolicy.intervalTicks()==7 && globalPolicy.healAmount()==.5F,"global mode uses all global settings");
        var roomPolicy=new RoomNaturalRegenConfig("房间",true,true,4,20,1.5).resolve(false,false,12,7,.5);
        check(roomPolicy.passiveEnabled() && roomPolicy.vanillaEnabled() && roomPolicy.startDelaySeconds()==4
            && roomPolicy.intervalTicks()==20 && roomPolicy.healAmount()==1.5F,"room mode uses its own settings");
        var legacyDisabled=new RoomNaturalRegenConfig("禁用",true,true,4,20,1.5);
        var disabled=legacyDisabled.resolve(true,true,6,3,2);
        check(!disabled.passiveEnabled() && !disabled.vanillaEnabled(),"legacy disabled mode remains disabled after migration");
        check(legacyDisabled.mode().equals("房间") && !legacyDisabled.passiveEnabled() && !legacyDisabled.vanillaEnabled()
            && legacyDisabled.startDelaySeconds()==4 && legacyDisabled.intervalTicks()==20 && legacyDisabled.healAmount()==1.5,
            "legacy disabled mode becomes room source with both switches off and parameters retained");
        check(new RoomNaturalRegenConfig("ROOM",null,null,null,null,null).mode().equals("房间"),"English mode aliases normalize");
        try {new RoomNaturalRegenConfig("other",null,null,null,null,null);throw new AssertionError("invalid mode accepted");}
        catch(IllegalArgumentException expected){check(true,"invalid room mode rejected");}
        var commands=org.mods.gd656conquest.server.core.command.NaturalRegenCommands.class;
        check(org.mods.gd656conquest.server.core.command.NaturalRegenCommands.normalize("PASSIVEREGENAMOUNT").equals("passiveRegenHealAmount"),"new global command aliases");
        check(org.mods.gd656conquest.server.core.command.NaturalRegenCommands.keys(java.util.List.of("boolNaturalRegeneration")).size()==3,"old global command key retained and two added");
        equal((Double)org.mods.gd656conquest.server.core.command.NaturalRegenCommands.parse("passiveRegenHealAmount","1.5"),1.5,"global heal command accepts fractions");
        check(Boolean.FALSE.equals(org.mods.gd656conquest.server.core.command.NaturalRegenCommands.parse("boolPassiveRegeneration","false")),"global passive off command");
        for(String invalid:new String[]{"-1","NaN","Infinity","1025"}) {
            try {org.mods.gd656conquest.server.core.command.NaturalRegenCommands.parse("passiveRegenHealAmount",invalid);throw new AssertionError("invalid global amount accepted");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){check(true,"invalid heal amount rejected before saving");}
        }

        check(cfg.recoverySeconds()==2,"suppression recovery defaults to two seconds");
        check(SupportRules.ammoBurst(1,true)==4,"single-shot gun gets four rounds per bag");
        check(SupportRules.ammoBurst(1,false)==2,"single-shot gun gets two rounds per box burst");
        check(SupportRules.ammoBurst(30,true)==60 && SupportRules.ammoBurst(30,false)==30,"magazine-sized resupply");
        check(SupportRules.ammoBurst(Integer.MAX_VALUE,true)==Integer.MAX_VALUE,"no integer overflow");
        equal(SupportRules.nearGain(10,0,cfg),60,"damage contributes before the per-shot cap");
        equal(SupportRules.nearGain(10,2,cfg),20,"far edge still has one third weight");
        check(SupportRules.nearGain(10,2.01,cfg)==0 && SupportRules.nearGain(Double.NaN,1,cfg)==0,"reject outside and invalid damage");
        var s=new SupportRules.Suppression();
        s.add(40,100,cfg);check(!s.active(100,cfg) && !s.blocksHealing(100,cfg),"one capped shot does not suppress");
        s.add(20,110,cfg);check(s.active(110,cfg),"threshold is inclusive");
        equal(s.value(130,cfg),60,"no decay during the quiet delay");
        check(!s.active(131,cfg) && s.blocksHealing(131,cfg),"falling below the threshold starts healing cooldown");
        equal(s.value(160,cfg),0,"quiet strength decays to zero");
        check(s.blocksHealing(170,cfg) && !s.blocksHealing(171,cfg),"full two second cooldown starts at suppression exit and repeated checks do not extend it");
        s.add(1,172,cfg);check(!s.blocksHealing(172,cfg),"below-threshold near misses do not restart healing cooldown");
        s.add(1000,180,cfg);equal(s.value(180,cfg),100,"strength clamps at 100");
        s=new SupportRules.Suppression();s.add(60,180,cfg);
        s.add(20,200,cfg);check(s.active(230,cfg),"maintained suppression still includes the exact threshold");
        check(!s.active(231,cfg) && s.blocksHealing(231,cfg),"maintained suppression starts cooldown only after exit");
        check(s.blocksHealing(270,cfg) && !s.blocksHealing(271,cfg),"maintained suppression has a full two second recovery wait");
        s=new SupportRules.Suppression();s.add(100,100,cfg);
        check(s.active(140,cfg) && !s.active(141,cfg) && s.blocksHealing(141,cfg)
            && s.blocksHealing(180,cfg) && !s.blocksHealing(181,cfg),"maximum strength cannot exhaust recovery cooldown while still suppressed");
        s=new SupportRules.Suppression();s.add(60,110,cfg);s.active(131,cfg);s.add(60,140,cfg);
        check(s.active(180,cfg) && !s.active(181,cfg) && s.blocksHealing(220,cfg) && !s.blocksHealing(221,cfg),
            "suppression during recovery restarts the full wait at the next exit");
        s=new SupportRules.Suppression();s.add(60,110,cfg);s.active(131,cfg);s.add(1,140,cfg);
        check(s.blocksHealing(170,cfg) && !s.blocksHealing(171,cfg),"sub-threshold contributions during recovery leave its deadline unchanged");
        var zeroRecovery=new RoomSuppressionConfig(null,null,null,null,null,null,null,0D,null);
        s=new SupportRules.Suppression();s.add(60,10,zeroRecovery);
        check(s.active(30,zeroRecovery) && !s.active(31,zeroRecovery) && !s.blocksHealing(31,zeroRecovery),
            "zero recovery delay allows healing immediately after suppression exit");
        var fractionalRecovery=new RoomSuppressionConfig(null,null,null,null,null,null,null,.125D,null);
        s=new SupportRules.Suppression();s.add(60,10,fractionalRecovery);
        check(!s.active(31,fractionalRecovery) && s.blocksHealing(33,fractionalRecovery) && !s.blocksHealing(34,fractionalRecovery),
            "fractional recovery waits round up to whole ticks after exit");
        var p=new SupportRules.Progress();
        check(!p.ready(0,true,true,100) && !p.ready(40,true,true,100),"partial gadget recharge");
        check(!p.ready(50,true,false,100) && !p.ready(500,true,false,100),"progress pauses outside range");
        check(!p.ready(510,true,true,100) && !p.ready(560,true,true,100),"switching boxes preserves earned progress");
        check(p.ready(570,true,true,100) && p.ready(580,true,true,100),"failed inventory insertion keeps ready progress");
        p.supplied();check(!p.ready(590,true,true,100),"successful supply restarts progress");
        check(!p.ready(600,false,true,100) && !p.ready(610,true,true,100),"full reserve resets progress");
        check(p.ready(700,true,true,100),"continuous shortage resumes normal timing");
        var gson=new Gson();var old=gson.fromJson("{}",RoomCommonConfig.class);
        var legacyRoom=gson.fromJson("{\"naturalRegenConfig\":{\"mode\":\"禁用\",\"passiveEnabled\":true,\"vanillaEnabled\":true,\"healAmount\":0.5}}",RoomCommonConfig.class);
        var migrated=legacyRoom.naturalRegenConfig();
        check(migrated.mode().equals("房间") && !migrated.passiveEnabled() && !migrated.vanillaEnabled() && migrated.healAmount()==.5,
            "actual old room JSON migration does not re-enable either healing mechanism");
        String migratedJson=gson.toJson(legacyRoom);
        check(!migratedJson.contains("禁用") && gson.fromJson(migratedJson,RoomCommonConfig.class).equals(legacyRoom),
            "saved migrated room only contains the new source and reloads losslessly");
        var defaultRegen=old.naturalRegenConfig();
        check(old.medicalBoxDamageCooldownSeconds()==3 && defaultRegen.mode().equals("房间")
            && defaultRegen.passiveEnabled() && !defaultRegen.vanillaEnabled()
            && defaultRegen.startDelaySeconds()==6 && defaultRegen.intervalTicks()==10 && defaultRegen.healAmount()==.5,
            "missing room config uses enabled room regeneration, disabled food healing and 6s/10tick/0.5HP defaults");
        var migratedGlobal=gson.fromJson("{\"passiveRegenStartDelaySeconds\":9,\"passiveRegenIntervalTicks\":20}",org.mods.gd656conquest.server.data.serverconfig.ServerConfigDataModel.class);
        check(migratedGlobal.getBoolPassiveRegeneration() && migratedGlobal.getPassiveRegenHealAmount()==.5
            && migratedGlobal.getPassiveRegenStartDelaySeconds()==9 && migratedGlobal.getPassiveRegenIntervalTicks()==20,"new global fields migrate without changing old values");
        check(old.allowEnemySupportResupply() && old.suppressionConfig().equals(cfg),"old room files get safe defaults");
        var configured=gson.fromJson("{\"allowEnemySupportResupply\":false,\"suppressionConfig\":{\"threshold\":75,\"recoverySeconds\":4}}",RoomCommonConfig.class);
        check(!configured.allowEnemySupportResupply() && configured.suppressionConfig().threshold()==75,"room overrides preserved");
        check(configured.suppressionConfig().radiusBlocks()==2 && configured.suppressionConfig().recoverySeconds()==4,"partial nested configs migrate");
        check(gson.fromJson(gson.toJson(configured),RoomCommonConfig.class).equals(configured),"new room config serializes losslessly");
        equal((Double)BoundaryConfigNumbers.parse("suppressionDamageScale","2.5"),2.5,"room editor supports fractional parameters");
        var room=new RoomDefinition("support-test","test",RoomGameplayId.DEATHMATCH,RoomLifecycleState.DISABLED,configured,RoomDeathmatchConfig.defaults());
        var descriptor=new RoomConfigDescriptor("suppressionThreshold",RoomConfigScope.COMMON,RoomConfigCategory.RULES,"threshold",
            "commonConfig.suppressionConfig.threshold",RoomConfigValueType.NUMBER,java.util.List.of(),java.util.List.of(),RoomConfigGroupOperationMode.NONE,null,true);
        var edited=RoomDefinitionScalarConfigEditor.apply(room,descriptor,"65.5",null);
        check(edited.commonConfig().suppressionConfig().threshold()==65.5 && !edited.commonConfig().allowEnemySupportResupply(),"native editor changes nested suppression and preserves permissions");
        var mode=RoomConfigDescriptorRegistry.find("naturalRegenMode").orElseThrow();
        check(mode.displayName().equals("自然回血配置来源") && mode.suggestionValues().equals(java.util.List.of("全局","房间")),
            "room editor offers only global and room configuration sources");
        edited=RoomDefinitionScalarConfigEditor.apply(edited,mode,"房间",null);
        edited=RoomDefinitionScalarConfigEditor.apply(edited,RoomConfigDescriptorRegistry.find("passiveRegenHealAmount").orElseThrow(),"1.5",null);
        edited=RoomDefinitionScalarConfigEditor.apply(edited,RoomConfigDescriptorRegistry.find("medicalBoxDamageCooldownSeconds").orElseThrow(),"4.5",null);
        check(edited.commonConfig().naturalRegenConfig().mode().equals("房间") && edited.commonConfig().naturalRegenConfig().healAmount()==1.5
            && edited.commonConfig().medicalBoxDamageCooldownSeconds()==4.5,"native room editor accepts mode, fractional healing and separate box wait");
        var roomOff=RoomDefinitionScalarConfigEditor.apply(edited,RoomConfigDescriptorRegistry.find("roomPassiveRegenEnabled").orElseThrow(),"false",null);
        roomOff=RoomDefinitionScalarConfigEditor.apply(roomOff,RoomConfigDescriptorRegistry.find("roomVanillaRegenEnabled").orElseThrow(),"false",null);
        var switchesOff=roomOff.commonConfig().naturalRegenConfig().resolve(true,true,6,3,2);
        check(!switchesOff.passiveEnabled() && !switchesOff.vanillaEnabled(),"room switches can disable both mechanisms while global healing stays enabled");
        var regular=new RoomConfigDescriptor("outsideBoundaryDeathSeconds",RoomConfigScope.COMMON,RoomConfigCategory.RULES,"outside",
            "commonConfig.outsideBoundaryDeathSeconds",RoomConfigValueType.NUMBER,java.util.List.of(),java.util.List.of(),RoomConfigGroupOperationMode.NONE,null,true);
        var other=RoomDefinitionScalarConfigEditor.apply(edited,regular,"20",null);
        check(other.commonConfig().suppressionConfig().equals(edited.commonConfig().suppressionConfig()),"unrelated room edits preserve suppression settings");
        var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);var unsafe=(sun.misc.Unsafe)uf.get(null);
        var repo=Class.forName("org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepository");
        var normalize=repo.getDeclaredMethod("normalizeDefinition",RoomDefinition.class);normalize.setAccessible(true);
        var normalized=(RoomDefinition)normalize.invoke(unsafe.allocateInstance(repo),other);
        check(normalized.commonConfig().suppressionConfig().equals(other.commonConfig().suppressionConfig()) && !normalized.commonConfig().allowEnemySupportResupply(),"repository reload preserves new settings");
        check(normalized.commonConfig().naturalRegenConfig().equals(other.commonConfig().naturalRegenConfig())
            && normalized.commonConfig().medicalBoxDamageCooldownSeconds().equals(other.commonConfig().medicalBoxDamageCooldownSeconds()),"repository preserves both new healing configurations");
        var templates=Class.forName("org.mods.gd656conquest.server.room.entry.RoomAdminConfigTemplateService");
        var copy=templates.getDeclaredMethod("buildCopiedDefinition",RoomDefinition.class,RoomDefinition.class);copy.setAccessible(true);
        var copied=(RoomDefinition)copy.invoke(unsafe.allocateInstance(templates),other,room);
        check(copied.commonConfig().suppressionConfig().equals(other.commonConfig().suppressionConfig()),"template copy retains suppression settings");
        check(copied.commonConfig().naturalRegenConfig().equals(other.commonConfig().naturalRegenConfig())
            && copied.commonConfig().medicalBoxDamageCooldownSeconds().equals(other.commonConfig().medicalBoxDamageCooldownSeconds()),"template copy preserves both new healing configurations");
        System.out.println("PASS support timing, damage weighting and room migration: "+checks);
    }
}
