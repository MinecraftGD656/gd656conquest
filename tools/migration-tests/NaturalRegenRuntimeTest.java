package tests;

import java.lang.reflect.Method;
import java.util.*;
import com.google.gson.Gson;
import org.mods.gd656conquest.server.room.model.RoomCommonConfig;
import org.mods.gd656conquest.server.data.serverconfig.ServerConfigDataModel;
import org.objectweb.asm.*;
import tests.SupportRuntimeFixtureTest.*;

/** Executes shipped resolver, passive timeline and food redirect handlers with engine adapters. */
public final class NaturalRegenRuntimeTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static class Rules {
        public boolean enabled;
        public boolean m_46207_(net.minecraft.world.level.GameRules.Key<?> key){return enabled;}
    }
    static void config(Server server,String json){server.room.config=new Gson().fromJson(json,RoomCommonConfig.class);}
    static void tick(Method tick,Server server,Player player,int now)throws Exception {server.now=now;tick.invoke(null,player,(long)now,0,1);}
    public static void main(String[] args)throws Exception {
        var loader=new AmmoReserveFixtureTest.Loader();var map=SupportRuntimeFixtureTest.mappings();SupportRuntimeFixtureTest.addRegen(loader,map);
        for(String name:new ArrayList<>(map.keySet()))if(name.startsWith(SupportRuntimeFixtureTest.BASE+"server/support/"))AmmoReserveFixtureTest.add(loader,AmmoReserveFixtureTest.read(name),map);
        var nativeMixin=AmmoReserveFixtureTest.read(SupportRuntimeFixtureTest.BASE+"mixin/server/NaturalFoodRegenMixin");
        nativeMixin.access &= ~Opcodes.ACC_ABSTRACT;
        nativeMixin.visibleAnnotations=null;nativeMixin.invisibleAnnotations=null;
        map.put(nativeMixin.name,"tests/supply/FoodPolicy");map.put("net/minecraft/world/level/GameRules",Type.getInternalName(Rules.class));
        AmmoReserveFixtureTest.add(loader,nativeMixin,map);
        var resolver=loader.loadClass("tests.supply.NaturalRegenService");var passive=loader.loadClass("tests.supply.PassiveRegenService");
        var enabled=resolver.getMethod("enabled",Player.class);var amount=resolver.getMethod("amount",Player.class);var vanilla=resolver.getMethod("vanillaAllowed",Player.class,boolean.class);
        var tick=passive.getMethod("onPlayerTick",Player.class,long.class,int.class,int.class);var damaged=passive.getMethod("markPlayerDamaged",Player.class,long.class);var reset=passive.getMethod("resetPlayer",Player.class);
        var server=new Server();var p=new Player(server);
        check(!(boolean)enabled.invoke(null,new Object[]{null}),"null player cannot enter regen");
        var global=new ServerConfigDataModel();global.setBoolPassiveRegeneration(false);global.setBoolNaturalRegeneration(true);global.setPassiveRegenStartDelaySeconds(3);global.setPassiveRegenIntervalTicks(7);global.setPassiveRegenHealAmount(1.25);
        Data.stores.put(server,NaturalRegenConfigTest.store(global));
        check((boolean)enabled.invoke(null,p)&&(float)amount.invoke(null,p)==.5F&&!(boolean)vanilla.invoke(null,p,true),"default actual room overrides global disabled, amount and vanilla gamerule");
        damaged.invoke(null,p,100L);
        for(int now=101;now<220;now++){tick(tick,server,p,now);check(p.health==10&&p.cues.isEmpty(),"full six-second injury delay without sound");}
        tick(tick,server,p,220);check(p.health==10.5F&&p.cues.size()==1,"first 0.5HP heal at exact room deadline, private start sound");
        for(int now=221;now<230;now++){tick(tick,server,p,now);check(p.health==10.5F&&p.cues.size()==1,"ten-tick interval without repeated startup sound");}
        tick(tick,server,p,230);check(p.health==11&&p.cues.size()==1,"next half-HP heal at exact interval");
        damaged.invoke(null,p,231L);tick(tick,server,p,350);check(p.health==11,"new damage restarts full delay");tick(tick,server,p,351);check(p.health==11.5F&&p.cues.size()==2,"new damage allows fresh successful startup cue");
        config(server,"{\"naturalRegenConfig\":{\"mode\":\"全局\"}}");check(!(boolean)enabled.invoke(null,p)&&(boolean)vanilla.invoke(null,p,false),"global room source follows independent global switches");
        tick(tick,server,p,500);check(p.health==11.5F,"global passive switch off blocks heal");
        global.setBoolPassiveRegeneration(true);check((float)amount.invoke(null,p)==1.25F,"global source resolves actual stored fractional amount");
        reset.invoke(null,p);p.health=10;damaged.invoke(null,p,500L);tick(tick,server,p,559);check(p.health==10,"global delay enforced");tick(tick,server,p,560);check(p.health==11.25F,"global heal amount applied");tick(tick,server,p,566);check(p.health==11.25F,"global interval retained");tick(tick,server,p,567);check(p.health==12.5F,"global seven-tick second heal");
        config(server,"{\"naturalRegenConfig\":{\"passiveEnabled\":false,\"vanillaEnabled\":true}}");check(!(boolean)enabled.invoke(null,p)&&(boolean)vanilla.invoke(null,p,false),"room switches independent in opposite direction");
        config(server,"{\"naturalRegenConfig\":{\"mode\":\"禁用\",\"passiveEnabled\":true,\"vanillaEnabled\":true}}");check(!(boolean)enabled.invoke(null,p)&&!(boolean)vanilla.invoke(null,p,true),"actual resolver preserves legacy disabled semantics");
        config(server,"{\"naturalRegenConfig\":{\"healAmount\":0}}");check(!(boolean)enabled.invoke(null,p),"zero HP disables passive transactions");
        server.bound=false;check((float)amount.invoke(null,p)==1.25F&&(boolean)vanilla.invoke(null,p,true)&&!(boolean)vanilla.invoke(null,p,false),"outside room uses global passive policy and original vanilla gamerule");
        server.bound=true;config(server,"{\"naturalRegenConfig\":{\"startDelaySeconds\":0,\"intervalTicks\":1,\"healAmount\":0.5,\"vanillaEnabled\":true}}");reset.invoke(null,p);p.health=10;damaged.invoke(null,p,600L);int cues=p.cues.size();
        p.acceptHeal=false;tick(tick,server,p,600);check(p.health==10&&p.cues.size()==cues,"canceled heal emits no successful cue");p.acceptHeal=true;tick(tick,server,p,601);check(p.health==10.5F&&p.cues.size()==cues+1,"retry performs actual heal and emits first success");
        p.health=20;tick(tick,server,p,602);check(p.cues.size()==cues+1,"full health does not emit another cue");
        p.health=10;p.spectator=true;tick(tick,server,p,603);check(p.health==10,"spectator skipped");p.spectator=false;p.alive=false;tick(tick,server,p,604);check(p.health==10,"dead player skipped");p.alive=true;
        var food=loader.loadClass("tests.supply.FoodPolicy");var constructor=food.getDeclaredConstructor();constructor.setAccessible(true);var instance=constructor.newInstance();
        var canHeal=food.getDeclaredMethod("gd656$canHeal",Player.class);canHeal.setAccessible(true);
        var foodRule=food.getDeclaredMethod("gd656$roomFoodRule",Rules.class,net.minecraft.world.level.GameRules.Key.class,Player.class);foodRule.setAccessible(true);var rules=new Rules();
        check((boolean)canHeal.invoke(instance,p),"food handler permits hurt player without treatment");p.health=20;check(!(boolean)canHeal.invoke(instance,p),"food handler preserves native full-health guard");p.health=10;
        check((boolean)foodRule.invoke(instance,rules,null,p),"room food on overrides world gamerule off");rules.enabled=true;config(server,"{}");check(!(boolean)foodRule.invoke(instance,rules,null,p),"room food off overrides world gamerule on");
        server.bound=false;check((boolean)foodRule.invoke(instance,rules,null,p),"food handler outside room preserves actual world rule");server.bound=true;
        var runtime=loader.loadClass("tests.supply.SupportRuntime");var of=runtime.getMethod("of",Server.class);var state=runtime.getMethod("recipient",Player.class);Object recipient=state.invoke(of.invoke(null,server),p);
        var range=recipient.getClass().getDeclaredField("medicalRangeUntil");range.setAccessible(true);range.setLong(recipient,700L);server.now=650;reset.invoke(null,p);recipient=state.invoke(of.invoke(null,server),p);range.setLong(recipient,700L);damaged.invoke(null,p,650L);
        check(!(boolean)canHeal.invoke(instance,p),"food healing pauses during actual active medical range");tick(tick,server,p,700);check(p.health==10,"passive healing pauses at inclusive medical range deadline");
        config(server,"{\"naturalRegenConfig\":{\"startDelaySeconds\":0,\"intervalTicks\":1}}");tick(tick,server,p,701);check(p.health==10.5F&&(boolean)canHeal.invoke(instance,p),"both healing guards resume after treatment window");
        System.out.println("PASS actual resolver/passive timeline/food handler/treatment interactions: "+checks);
    }
}
