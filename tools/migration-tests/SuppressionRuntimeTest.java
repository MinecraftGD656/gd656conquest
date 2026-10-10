package tests;

import java.lang.reflect.*;
import java.util.*;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.support.SupportRules;
import tests.SupportRuntimeFixtureTest.*;

/** Executes actual TACZ path/grouping and shared runtime bytecode with engine/metadata adapters. */
public final class SuppressionRuntimeTest {
    static final String ROOT=SupportRuntimeFixtureTest.BASE;
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static class Projectile extends Entity {public Entity owner;public Entity m_19749_(){return owner;}}
    public interface Pierce {int gd656$pierce();}
    public static class Bullet extends Projectile implements Pierce {
        public double damage=20;public int pierce=1;public Vec3 sampled;
        public Bullet(Server server,Player shooter){level=server.level;owner=shooter;}
        public float getDamage(Vec3 point){sampled=point;return (float)damage;}public int gd656$pierce(){return pierce;}
    }
    public record Hit(Vec3 point){public Vec3 getHitPos(){return point;}}
    public static class Heads {public static AABB configured;public static AABB getAABB(ResourceLocation id){return configured;}}
    public interface Registry {ResourceLocation getKey(Object type);}
    public static class Registries {public static final Registry ENTITY_TYPES=type->ResourceLocation.fromNamespaceAndPath("minecraft","player");}
    static Object field(Object object,String name)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    static double strength(Object recipient,Server server)throws Exception{return ((SupportRules.Suppression)field(recipient,"suppression")).value(server.now,server.room.config.suppressionConfig());}
    static void config(Server server,String suppression){server.room.config=new Gson().fromJson("{\"medicalBoxDamageCooldownSeconds\":0,\"naturalRegenConfig\":{\"startDelaySeconds\":0,\"intervalTicks\":1},\"suppressionConfig\":"+suppression+"}",RoomCommonConfig.class);}
    public static void main(String[] args)throws Exception {
        var loader=new AmmoReserveFixtureTest.Loader();var map=SupportRuntimeFixtureTest.mappings();SupportRuntimeFixtureTest.addRegen(loader,map);
        map.put(ROOT+"server/support/TaczSuppression","tests/supply/TaczSuppression");
        for(var e:Map.ofEntries(Map.entry("net/minecraft/world/entity/projectile/Projectile",Projectile.class),Map.entry("net/minecraft/world/entity/EntityType",EntityType.class),Map.entry("com/tacz/guns/entity/EntityKineticBullet",Bullet.class),Map.entry("com/tacz/guns/entity/EntityKineticBullet$EntityResult",Hit.class),Map.entry("com/tacz/guns/config/util/HeadShotAABBConfigRead",Heads.class),Map.entry("net/minecraftforge/registries/ForgeRegistries",Registries.class),Map.entry("net/minecraftforge/registries/IForgeRegistry",Registry.class),Map.entry(ROOT+"mixin/compat/tacz/KineticBulletSupportAccessor",Pierce.class)).entrySet())map.put(e.getKey(),org.objectweb.asm.Type.getInternalName(e.getValue()));
        for(String name:new ArrayList<>(map.keySet()))if(name.startsWith(ROOT+"server/support/")&&!name.endsWith("SuppressionNetwork"))AmmoReserveFixtureTest.add(loader,AmmoReserveFixtureTest.read(name),map);
        var hook=AmmoReserveFixtureTest.read(ROOT+"mixin/compat/tacz/SupportSuppressionMixin");hook.access &= ~Opcodes.ACC_ABSTRACT;hook.visibleAnnotations=null;hook.invisibleAnnotations=null;map.put(hook.name,"tests/supply/PathHooks");AmmoReserveFixtureTest.add(loader,hook,map);
        var tacz=loader.loadClass("tests.supply.TaczSuppression");var runtime=loader.loadClass("tests.supply.SupportRuntime");var passive=loader.loadClass("tests.supply.PassiveRegenService");
        var path=tacz.getMethod("path",Projectile.class,Vec3.class,Vec3.class);var tag=tacz.getMethod("tag",Bullet.class);var begin=tacz.getMethod("beginShot");var end=tacz.getMethod("endShot");
        var of=runtime.getMethod("of",Server.class);var recipient=runtime.getMethod("recipient",Player.class);var reset=runtime.getMethod("reset",Player.class);var blocked=runtime.getMethod("suppressionBlocks",Player.class);var pause=runtime.getMethod("pausePassive",Player.class);var tick=runtime.getDeclaredMethod("tick");tick.setAccessible(true);
        var server=new Server();server.now=100;config(server,"{}");var shooter=new Player(server);var victim=new Player(server);victim.x=5;server.state.teams.put(victim.id,RoomTeamId.CAMP_B);var ally=new Player(server);ally.x=5;
        Vec3 start=new Vec3(0,1.6,0),finish=new Vec3(10,1.6,0);Object rt=of.invoke(null,server);Object state=recipient.invoke(rt,victim);
        begin.invoke(null);var one=new Bullet(server,shooter);var two=new Bullet(server,shooter);tag.invoke(null,one);tag.invoke(null,two);end.invoke(null);
        check(one.data.m_128342_("gd656_suppression_shot").equals(two.data.m_128342_("gd656_suppression_shot")),"all pellets from one shootOnce share one ID");
        path.invoke(null,one,start,finish);check(strength(state,server)==40,"high damage capped at 40 for a shot");path.invoke(null,two,start,finish);check(strength(state,server)==40,"second pellet cannot exceed same-shot cap");path.invoke(null,one,start,finish);check(strength(state,server)==40,"same bullet cannot repeat a recipient across segments");
        check(strength(recipient.invoke(rt,ally),server)==0&&strength(recipient.invoke(rt,shooter),server)==0,"same-camp and shooter excluded");check(SuppressionTransport.messages.isEmpty(),"below threshold no visual transition");
        server.now=110;var three=new Bullet(server,shooter);tag.invoke(null,three);check(three.data.m_128342_("gd656_suppression_shot").equals(three.id),"unscoped projectile uses own UUID");path.invoke(null,three,start,finish);
        check(strength(state,server)==80&&(boolean)blocked.invoke(null,victim)&&(boolean)pause.invoke(null,victim),"second shot crosses threshold and shared healing guard");
        check(SuppressionTransport.messages.size()==1&&SuppressionTransport.messages.get(0).player()==victim&&SuppressionTransport.messages.get(0).active(),"only suppressed recipient receives entry state");
        var medical=SupportRuntimeFixtureTest.bag(server,"medic_bag",1);runtime.getMethod("markBag",ItemEntity.class,Player.class).invoke(null,medical,shooter);var event=new Pickup(victim,medical);runtime.getMethod("pickup",Pickup.class).invoke(null,event);
        check(victim.health==12.5F&&!medical.alive,"medical bag remains usable while suppressed");runtime.getMethod("damaged",Damage.class).invoke(null,new Damage(victim));victim.health=10;
        shooter.health=ally.health=20;
        var box=new MedicalBox();box.level=server.level;box.x=victim.x;box.owner=shooter.id;box.capacity=100;server.level.entities.put(box.id,box);runtime.getMethod("initializeBox",Entity.class,UUID.class,Object.class).invoke(null,box,shooter.id,server.room);
        var boxes=runtime.getMethod("boxes",Server.class,Set.class,long.class,boolean.class);boxes.invoke(null,server,Set.of(box.id),110L,true);check(victim.health==10&&box.capacity==100,"suppression blocks actual medical-box transaction");
        passive.getMethod("markPlayerDamaged",Player.class,long.class).invoke(null,victim,100L);var healTick=passive.getMethod("onPlayerTick",Player.class,long.class,int.class,int.class);healTick.invoke(null,victim,110L,0,1);check(victim.health==10,"suppression blocks actual passive heal");
        server.now=140;tick.invoke(rt);check((boolean)blocked.invoke(null,victim)&&strength(state,server)==60,"exact threshold remains suppressed");server.now=141;tick.invoke(rt);check((boolean)blocked.invoke(null,victim)&&SuppressionTransport.messages.size()==2&&!SuppressionTransport.messages.get(1).active(),"exit immediately clears visual but starts recovery wait");
        server.now=180;check((boolean)blocked.invoke(null,victim),"entire recovery wait retained before exact two-second deadline");boxes.invoke(null,server,Set.of(box.id),180L,true);healTick.invoke(null,victim,180L,0,1);check(victim.health==10,"both actual heal paths stay blocked during exit recovery");
        server.now=181;check(!(boolean)blocked.invoke(null,victim),"recovery expires exactly 40 ticks after actual exit");healTick.invoke(null,victim,181L,0,1);check(victim.health==10.5F,"passive heals after recovery");server.now=190;boxes.invoke(null,server,Set.of(box.id),190L,true);check(victim.health==11.5F&&box.capacity==99,"box resumes and debits actual healing");
        reset.invoke(null,victim);state=recipient.invoke(rt,victim);server.now=200;victim.z=2;path.invoke(null,new Bullet(server,shooter),start,finish);check(Math.abs(strength(state,server)-40)<.00001,"radius edge keeps one-third damage weight");
        reset.invoke(null,victim);state=recipient.invoke(rt,victim);victim.z=2.01;path.invoke(null,new Bullet(server,shooter),start,finish);check(strength(state,server)==0,"outside radius never gains suppression");victim.z=0;
        Heads.configured=new AABB(-.1,5,-.1,.1,5.2,.1);path.invoke(null,new Bullet(server,shooter),start,finish);check(strength(state,server)==0,"configured TACZ head center used instead of eye fallback");Heads.configured=null;
        path.invoke(null,new Bullet(server,shooter),new Vec3(Double.NaN,0,0),finish);check(strength(state,server)==0,"nonfinite path rejected");var invalid=new Bullet(server,shooter);invalid.damage=Double.NaN;path.invoke(null,invalid,start,finish);check(strength(state,server)==0,"nonfinite damage rejected");
        server.level.f_46443_=true;var client=new Bullet(server,shooter);tag.invoke(null,client);path.invoke(null,client,start,finish);check(!client.data.m_128403_("gd656_suppression_shot")&&strength(state,server)==0,"client projectile never tags or modifies server state");server.level.f_46443_=false;
        var otherRoom=new Room();otherRoom.id="other";server.extraRooms.put("other",otherRoom);server.roomIds.put(victim.id,"other");path.invoke(null,new Bullet(server,shooter),start,finish);check(strength(state,server)==0,"cross-room enemy excluded");server.roomIds.clear();
        victim.spectator=true;path.invoke(null,new Bullet(server,shooter),start,finish);check(strength(state,server)==0,"spectator excluded");victim.spectator=false;server.state.downed=true;path.invoke(null,new Bullet(server,shooter),start,finish);check(strength(state,server)==0,"downed player excluded");server.state.downed=false;
        config(server,"{\"enabled\":false}");path.invoke(null,new Bullet(server,shooter),start,finish);check(strength(state,server)==0&&!(boolean)blocked.invoke(null,victim),"room suppression off disables accumulation and guard");config(server,"{}");
        var hooks=loader.loadClass("tests.supply.PathHooks");var single=hooks.getDeclaredMethod("gd656$single",Projectile.class,Vec3.class,Vec3.class,CallbackInfoReturnable.class);single.setAccessible(true);var multiple=hooks.getDeclaredMethod("gd656$multiple",Projectile.class,Vec3.class,Vec3.class,CallbackInfoReturnable.class);multiple.setAccessible(true);
        single.invoke(null,new Bullet(server,shooter),start,finish,new CallbackInfoReturnable<Hit>("findEntityOnPath",false,new Hit(new Vec3(2,1.6,0))));check(strength(state,server)==0,"ordinary path ends at returned collision; no effect behind collision");
        single.invoke(null,new Bullet(server,shooter),start,finish,new CallbackInfoReturnable<Hit>("findEntityOnPath",false,(Hit)null));check(strength(state,server)==40,"unblocked ordinary path reaches target");reset.invoke(null,victim);state=recipient.invoke(rt,victim);
        var penetrate=new Bullet(server,shooter);penetrate.pierce=1;List<Hit> hits=List.of(new Hit(new Vec3(9,1.6,0)),new Hit(new Vec3(2,1.6,0)));
        multiple.invoke(null,penetrate,start,finish,new CallbackInfoReturnable<List<Hit>>("findEntitiesOnPath",false,hits));check(strength(state,server)==0,"pierce cap sorts hits and clips at consumed first collision");
        penetrate=new Bullet(server,shooter);penetrate.pierce=2;multiple.invoke(null,penetrate,start,finish,new CallbackInfoReturnable<List<Hit>>("findEntitiesOnPath",false,hits));check(strength(state,server)==40,"second consumed pierce collision allows path through target");
        reset.invoke(null,victim);state=recipient.invoke(rt,victim);path.invoke(null,new Bullet(server,shooter),new Vec3(5,1.6,0),new Vec3(5,1.6,0));check(strength(state,server)==40,"zero-length finite path handled without division overflow");
        begin.invoke(null);var outer=new Bullet(server,shooter);tag.invoke(null,outer);begin.invoke(null);var inner=new Bullet(server,shooter);tag.invoke(null,inner);end.invoke(null);var restored=new Bullet(server,shooter);tag.invoke(null,restored);end.invoke(null);
        check(!outer.data.m_128342_("gd656_suppression_shot").equals(inner.data.m_128342_("gd656_suppression_shot"))&&outer.data.m_128342_("gd656_suppression_shot").equals(restored.data.m_128342_("gd656_suppression_shot")),"nested shot scope restores outer ID");
        server.now=801;tick.invoke(rt);check(((Map<?,?>)field(state,"shotGains")).isEmpty()&&((Map<?,?>)field(state,"shotTimes")).isEmpty(),"shot grouping cache expires after 600 ticks");
        path.invoke(null,new Bullet(server,shooter),start,finish);path.invoke(null,new Bullet(server,shooter),start,finish);int messages=SuppressionTransport.messages.size();victim.spectator=true;tick.invoke(rt);check(SuppressionTransport.messages.size()==messages+1&&!SuppressionTransport.messages.get(messages).active(),"unavailable recipient clears active visual through real reset");
        check(!(boolean)blocked.invoke(null,victim),"reset removes suppression and recovery state");
        System.out.println("PASS actual TACZ path/grouping/pierce/shared healing/recovery/cleanup fixture: "+checks);
    }
}
