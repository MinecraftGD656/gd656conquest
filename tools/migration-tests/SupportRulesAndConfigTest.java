package tests;

import java.util.*;
import java.lang.reflect.*;
import com.google.gson.Gson;
import org.mods.gd656conquest.server.support.SupportRules;
import org.mods.gd656conquest.server.room.config.*;
import org.mods.gd656conquest.server.room.model.*;

public final class SupportRulesAndConfigTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static RoomDefinition room(RoomCommonConfig common){return new RoomDefinition("support-test","test",RoomGameplayId.DEATHMATCH,RoomLifecycleState.DISABLED,common,RoomDeathmatchConfig.defaults());}
    static void preserved(RoomDefinition value){check(!value.commonConfig().allowEnemySupportResupply()&&value.commonConfig().medicalBoxDamageCooldownSeconds()==4.5&&value.commonConfig().flightBoundaryPaddingBlocks()==125.5,"all four migrated fields retained by actual copy");}
    public static void main(String[] args)throws Exception {
        for(long tick=100;tick<=161;tick++)check(SupportRules.damageBlocksHealing(tick,100,3)==(tick<160),"damage cooldown exact tick boundary");
        check(!SupportRules.damageBlocksHealing(100,-1,3)&&!SupportRules.damageBlocksHealing(100,100,0),"no injury and zero cooldown");
        check(SupportRules.damageBlocksHealing(102,100,.125)&&!SupportRules.damageBlocksHealing(103,100,.125),"fractional cooldown rounds up");
        for(int size:new int[]{-1,0,1,2,30,64,Integer.MAX_VALUE}) {
            check(SupportRules.ammoBurst(size,true)==Math.min(Integer.MAX_VALUE,Math.max(4,Math.max(1,(long)size)*2)),"one bag burst observes magazine and saturation");
            check(SupportRules.ammoBurst(size,false)==Math.max(2,size),"one box burst observes magazine");
        }
        var progress=new SupportRules.Progress();
        check(!progress.ready(0,true,true,100)&&!progress.ready(40,true,true,100),"initial equipment progress");
        check(!progress.ready(50,true,false,100)&&!progress.ready(500,true,false,100),"outside range pauses progress");
        check(!progress.ready(510,true,true,100)&&!progress.ready(560,true,true,100),"re-entering/switching boxes keeps earned progress");
        check(progress.ready(570,true,true,100)&&progress.ready(580,true,true,100),"failed insertion retains completed progress");
        progress.supplied();check(!progress.ready(590,true,true,100),"success resets progress");
        check(!progress.ready(600,false,true,100)&&!progress.ready(610,true,true,100)&&progress.ready(710,true,true,100),"full resets, later shortage starts fresh");
        var gson=new Gson();var old=gson.fromJson("{}",RoomCommonConfig.class);
        check(old.allowEnemySupportResupply()&&old.medicalBoxDamageCooldownSeconds()==3D,"old room defaults true / 3s");
        var common=gson.fromJson("{\"allowEnemySupportResupply\":false,\"medicalBoxDamageCooldownSeconds\":4.5,\"flightVehicleOutsideBoundaryDeathSeconds\":60,\"flightBoundaryPaddingBlocks\":125.5}",RoomCommonConfig.class);
        check(gson.fromJson(gson.toJson(common),RoomCommonConfig.class).equals(common),"real Gson roundtrip retains false and fraction");
        for(String input:List.of("0","0.125","60"))check(((Double)BoundaryConfigNumbers.parse("medicalBoxDamageCooldownSeconds",input))==Double.parseDouble(input),"valid editor cooldown");
        for(String input:List.of("-1","60.1","NaN","Infinity","abc")) {
            try{BoundaryConfigNumbers.parse("medicalBoxDamageCooldownSeconds",input);throw new AssertionError("invalid cooldown accepted");}catch(IllegalArgumentException expected){check(true,"invalid editor cooldown rejected");}
        }
        var edited=RoomDefinitionScalarConfigEditor.apply(room(old),RoomConfigDescriptorRegistry.find("allowEnemySupportResupply").orElseThrow(),"false",null);
        edited=RoomDefinitionScalarConfigEditor.apply(edited,RoomConfigDescriptorRegistry.find("medicalBoxDamageCooldownSeconds").orElseThrow(),"4.5",null);
        edited=RoomDefinitionScalarConfigEditor.apply(edited,RoomConfigDescriptorRegistry.find("flightBoundaryPaddingBlocks").orElseThrow(),"125.5",null);
        preserved(edited);
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var repository=Class.forName("org.mods.gd656conquest.server.data.roomdefinition.RoomDefinitionRepository");
        var normalize=repository.getDeclaredMethod("normalizeDefinition",RoomDefinition.class);normalize.setAccessible(true);
        preserved((RoomDefinition)normalize.invoke(unsafe.allocateInstance(repository),edited));
        var templates=Class.forName("org.mods.gd656conquest.server.room.entry.RoomAdminConfigTemplateService");
        var copy=templates.getDeclaredMethod("buildCopiedDefinition",RoomDefinition.class,RoomDefinition.class);copy.setAccessible(true);
        preserved((RoomDefinition)copy.invoke(unsafe.allocateInstance(templates),edited,room(old)));
        var group=RoomDefinitionGroupConfigEditor.class.getDeclaredMethod("replaceCommonStringGroup",RoomDefinition.class,String.class,List.class);group.setAccessible(true);
        preserved((RoomDefinition)group.invoke(null,edited,"supportcampaherokits",List.of("kit")));
        check(gson.fromJson("{\"medicalBoxDamageCooldownSeconds\":100}",RoomCommonConfig.class).medicalBoxDamageCooldownSeconds()==60,"stored excessive cooldown clamped");
        check(gson.fromJson("{\"medicalBoxDamageCooldownSeconds\":-1}",RoomCommonConfig.class).medicalBoxDamageCooldownSeconds()==0,"stored negative cooldown clamped");
        System.out.println("PASS support timing/bursts/progress and actual config/editor/repository/template/group: "+checks);
    }
}
