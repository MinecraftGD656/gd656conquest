package tests;

import java.util.*;
import java.lang.reflect.Method;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.*;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.commons.*;
import tests.AmmoReserveFixtureTest.Stack;
import tests.AmmoReserveFixtureTest.Item;
import tests.AmmoReserveFixtureTest.Entry;
import tests.AmmoReserveFixtureTest.Resolved;
import tests.AmmoReserveFixtureTest.Meta;

/** Executes actual shipped support methods; isolates engine, inventory, metadata and transport. */
public final class SupportRuntimeFixtureTest {
    static final String BASE="org/mods/gd656conquest/",RUN="tests/supply/";
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static class Slots<T> extends ArrayList<T>{}
    public static class Inventory {
        public final Slots<Stack> f_35974_=new Slots<>();public int f_35977_,updates;
        public Inventory(){f_35974_.addAll(Collections.nCopies(36,Stack.EMPTY));}
        public Stack m_8020_(int slot){return f_35974_.get(slot);}
        public void m_6836_(int slot,Stack stack){f_35974_.set(slot,stack);}
        public void m_6596_(){updates++;}
        public boolean m_36054_(Stack stack){
            for(int i=0;i<36;i++)if(m_8020_(i).m_41619_()){m_6836_(i,stack.m_41777_());stack.count=0;return true;}return false;
        }
    }
    public static class Slot {public Stack stack=Stack.EMPTY;public Stack m_7993_(){return stack;}}
    public static class Menu {
        public final Slots<Slot> f_38839_=new Slots<>();public Stack carried=Stack.EMPTY;public int updates;
        public void m_38946_(){updates++;}public void m_182423_(){updates++;}public Stack m_142621_(){return carried;}
    }
    public static class Abilities {public boolean f_35937_;}
    public static class Entity {
        public final UUID id=UUID.randomUUID();public final CompoundTag data=new CompoundTag();public Level level;public double x,y,z;public boolean alive=true;
        public UUID m_20148_(){return id;}public CompoundTag getPersistentData(){return data;}public Level m_9236_(){return level;}
        public boolean m_6084_(){return alive;}public void m_146870_(){alive=false;}
        public double m_20185_(){return x;}public double m_20186_(){return y;}public double m_20189_(){return z;}public double m_20188_(){return y+1.6;}
        public Vec3 m_20154_(){return new Vec3(0,0,1);}public Vec3 m_20184_(){return new Vec3(0,0,0);}public void m_20256_(Vec3 value){}
        public AABB m_20191_(){return new AABB(x,y,z,x+1,y+1,z+1);}public double m_20280_(Entity other){return (x-other.x)*(x-other.x)+(y-other.y)*(y-other.y)+(z-other.z)*(z-other.z);}
        public Vec3 m_20182_(){return new Vec3(x,y,z);}public Vec3 m_146892_(){return new Vec3(x,y+1.6,z);}
        public EntityType m_6095_(){return new EntityType();}
    }
    public static class EntityType {}
    public static class Player extends Entity {
        public final Server f_8924_;public final Inventory inventory=new Inventory();public final Menu f_36096_=new Menu();public final Abilities abilities=new Abilities();
        public boolean spectator,acceptHeal=true;public float health=10,maximum=20;public final List<String> cues=new ArrayList<>();public int bonuses;
        public final Map<CustomLoadoutSlot,Resolved> configured=new EnumMap<>(CustomLoadoutSlot.class);
        public Player(Server server){f_8924_=server;level=server.level;server.players.put(id,this);server.state.teams.put(id,RoomTeamId.CAMP_A);level.entities.put(id,this);}
        public Inventory m_150109_(){return inventory;}public boolean m_5833_(){return spectator;}public Abilities m_150110_(){return abilities;}
        public float m_21223_(){return health;}public float m_21233_(){return maximum;}public void m_5634_(float amount){if(acceptHeal)health=Math.min(maximum,health+amount);}
        public boolean m_36325_(){return health<maximum;}
        public Level m_284548_(){return level;}
        public void m_6330_(Sound sound,SoundSource source,float volume,float pitch){cues.add(sound.id.toString());}
        public Stack m_21120_(InteractionHand hand){return inventory.m_8020_(inventory.f_35977_);}public Stack m_21205_(){return m_21120_(InteractionHand.MAIN_HAND);}
        public static Method dropToss;
        public boolean m_182294_(boolean all){
            Stack held=m_21205_(),copy=held.m_41777_();copy.count=all?held.count:1;held.m_41774_(copy.count);
            var item=new ItemEntity(level,x,y,z,copy);var event=new Toss(this,item);
            try{dropToss.invoke(null,event);}catch(Exception e){throw new RuntimeException(e);}
            if(!event.canceled)level.m_7967_(item);return !event.canceled;
        }
    }
    public static class Server {
        public int now,limit=64,group=64;public final Map<UUID,Player> players=new HashMap<>();public State state=new State();public Room room=new Room();public boolean bound=true;
        public final Map<UUID,String> roomIds=new HashMap<>();public final Map<String,Room> extraRooms=new HashMap<>();
        public final Level level=new Level(this);public int m_129921_(){return now;}public Players m_6846_(){return new Players(this);}public Iterable<Level> m_129785_(){return List.of(level);}
    }
    public static class Players {public final Server server;public Players(Server s){server=s;}public Player m_11259_(UUID id){return server.players.get(id);}}
    public static class Level {
        public final Server server;public boolean f_46443_,spawn=true;public final Map<UUID,Entity> entities=new LinkedHashMap<>();
        public Level(Server server){this.server=server;}public Server m_7654_(){return server;}public Entity m_8791_(UUID id){return entities.get(id);}
        public boolean m_7967_(Entity entity){if(spawn)entities.put(entity.id,entity);return spawn;}
        public <T> List<T> m_6443_(Class<T> type,AABB bounds,java.util.function.Predicate<T> predicate){return entities.values().stream().filter(type::isInstance).map(type::cast).filter(predicate).toList();}
    }
    public static class ItemEntity extends Entity {
        public Stack stack;public int lifespan,pickup;
        public ItemEntity(Level level,double x,double y,double z,Stack stack){this.level=level;this.x=x;this.y=y;this.z=z;this.stack=stack;}
        public Stack m_32055_(){return stack;}public void m_32045_(Stack stack){this.stack=stack;}public void m_32010_(int delay){pickup=delay;}
    }
    public static class Box extends Entity {public UUID owner;public boolean permanent;public int capacity;public UUID getOwnerUuid(){return owner;}public boolean isPermanentSupport(){return permanent;}public int getUseRemaining(){return capacity;}public void setUseRemaining(int n){capacity=n;}}
    public static class AmmoBox extends Box {}
    public static class MedicalBox extends Box {}
    public static class State {public Map<UUID,RoomTeamId> teams=new HashMap<>();public boolean downed,respawning,hero;
        public Map<UUID,RoomTeamId> getRoomTeams(){return teams;}public boolean isInRespawnFlow(UUID id){return respawning;}}
    public static class Room {public String id="room";public RoomLifecycleState lifecycle=RoomLifecycleState.RUNNING;public RoomGameplayId gameplay=RoomGameplayId.CONQUEST;public RoomCommonConfig config=RoomCommonConfig.defaults();
        public String roomId(){return id;}public RoomCommonConfig commonConfig(){return config;}public RoomLifecycleState lifecycleState(){return lifecycle;}public RoomGameplayId gameplayId(){return gameplay;}}
    public static class Manager {public final Server s;public Manager(Server s){this.s=s;}public static Manager of(Server s){return new Manager(s);}public Optional<String> findPlayerRoomId(UUID id){return s.bound?Optional.of(s.roomIds.getOrDefault(id,s.room.id)):Optional.empty();}public Optional<Room> findRoom(String id){return Optional.ofNullable(id.equals(s.room.id)?s.room:s.extraRooms.get(id));}public Optional<State> findRunningState(String id){return Optional.of(s.state);}public boolean isPlayerDowned(UUID id){return s.state.downed;}}
    public static class SuppressionTransport {
        public record Message(Player player,boolean active,float opacity){}
        public static final List<Message> messages=new ArrayList<>();
        public static void send(Player p,boolean active,float opacity){messages.add(new Message(p,active,opacity));}
    }
    public static class Data {
        public static final Map<Server,org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore> stores=new IdentityHashMap<>();
        public final Server server;public Data(Server server){this.server=server;}public static Data of(Server server){return new Data(server);}
        public org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore serverConfig(){
            return stores.computeIfAbsent(server,s->{try{return NaturalRegenConfigTest.store(new org.mods.gd656conquest.server.data.serverconfig.ServerConfigDataModel());}catch(Exception e){throw new RuntimeException(e);}});
        }
    }
    public static class Loadout {public Loadout(Server s){}public Map<CustomLoadoutSlot,Resolved> resolveConfiguredLoadoutItems(Player p){return p.configured;}public boolean isAmmoManagedGunItem(Stack stack){return stack.gun;}}
    public static class Grant {
        public final Server server;public Grant(Server server){this.server=server;}
        public Meta resolveTaczAmmoMeta(Stack stack){return stack.gun&&!stack.sbw?new Meta(ResourceLocation.fromNamespaceAndPath("test",stack.ammo),server.group):null;}
        public Stack createTaczAmmoStack(ResourceLocation id,int count){return count<=0?Stack.EMPTY:new Stack("ammo:"+id,count,server.limit);}
        public int addTaczAmmoToPreferredSlots(Player p,Meta meta,Stack sample,int count,int[] slots){throw new AssertionError("actual helper not installed");}
    }
    public static class Sound {public final ResourceLocation id;public Sound(ResourceLocation id){this.id=id;}public static Sound m_262824_(ResourceLocation id){return new Sound(id);}}
    public static class Hero {public static boolean canRefillFromAmmoBox(State s,UUID id){return !s.hero;}}
    public static class Voice {public static boolean playCue(Player p,String cue){return true;}}
    public static class Score {public static void tryAwardConquestBonus(Player p,String bonus,float amount,String extra){p.bonuses++;}}
    public static class Death {
        public static boolean isDeathSupportDrop(ItemEntity e){return e.data.m_128471_("reward");}
        public static boolean isPickupBlocked(Player p,ItemEntity e){return isDeathSupportDrop(e)&&!p.id.equals(e.data.m_128342_("rewardOwner"));}
    }
    public static class Pickup {public final Player player;public final ItemEntity item;public boolean canceled;public Pickup(Player p,ItemEntity i){player=p;item=i;}public Player getEntity(){return player;}public ItemEntity getItem(){return item;}public boolean isCanceled(){return canceled;}public void setCanceled(boolean b){canceled=b;}}
    public static class Toss {public final Player p;public final ItemEntity e;public boolean canceled;public Toss(Player p,ItemEntity e){this.p=p;this.e=e;}public Player getPlayer(){return p;}public ItemEntity getEntity(){return e;}public boolean isCanceled(){return canceled;}public void setCanceled(boolean b){canceled=b;}}
    public static class Damage {public final Player p;public float amount=1;public Damage(Player p){this.p=p;}public Entity getEntity(){return p;}public boolean isCanceled(){return false;}public float getAmount(){return amount;}}
    public static class Click {public int slot;public ClickType type;public int m_133962_(){return slot;}public ClickType m_133966_(){return type;}}
    static Map<String,String> mappings(){
        Map<String,String> m=new HashMap<>();
        for(String s:List.of("SupportAmmoGrant","SupportAmmoGrant$Slots","SupportAmmoGrant$1","SupportContext","SupportRuntime","SupportRuntime$Recipient","SupportRuntime$MedicalTreatment","SupportAmmo","SupportAmmo$Target","SupportAmmo$Tacz","SupportDropGuard"))m.put(BASE+"server/support/"+s,RUN+s);
        for(var e:Map.ofEntries(Map.entry("net/minecraft/server/level/ServerPlayer",Player.class),Map.entry("net/minecraft/world/entity/player/Player",Player.class),Map.entry("net/minecraft/world/entity/LivingEntity",Entity.class),Map.entry("net/minecraft/world/entity/Entity",Entity.class),Map.entry("net/minecraft/world/entity/item/ItemEntity",ItemEntity.class),Map.entry("net/minecraft/server/MinecraftServer",Server.class),Map.entry("net/minecraft/server/players/PlayerList",Players.class),Map.entry("net/minecraft/world/level/Level",Level.class),Map.entry("net/minecraft/server/level/ServerLevel",Level.class),Map.entry("net/minecraft/world/entity/player/Inventory",Inventory.class),Map.entry("net/minecraft/world/inventory/AbstractContainerMenu",Menu.class),Map.entry("net/minecraft/world/inventory/Slot",Slot.class),Map.entry("net/minecraft/world/entity/player/Abilities",Abilities.class),Map.entry("net/minecraft/core/NonNullList",Slots.class),Map.entry("net/minecraft/world/item/ItemStack",Stack.class),Map.entry("net/minecraft/world/item/Item",Item.class),Map.entry("net/minecraft/sounds/SoundEvent",Sound.class),Map.entry("net/minecraftforge/fml/ModList",AmmoReserveFixtureTest.Mods.class),Map.entry("net/minecraftforge/registries/RegistryObject",AmmoReserveFixtureTest.Holder.class),Map.entry("net/minecraftforge/event/entity/player/EntityItemPickupEvent",Pickup.class),Map.entry("net/minecraftforge/event/entity/item/ItemTossEvent",Toss.class),Map.entry("net/minecraftforge/event/entity/living/LivingDamageEvent",Damage.class),Map.entry("net/minecraft/network/protocol/game/ServerboundContainerClickPacket",Click.class)).entrySet())m.put(e.getKey(),Type.getInternalName(e.getValue()));
        for(var e:Map.ofEntries(Map.entry("server/room/entry/RoomCoreRuntimeManager",Manager.class),Map.entry("server/room/model/RoomDefinition",Room.class),Map.entry("server/room/core/RunningRuntimeState",State.class),Map.entry("server/room/runtime/RoomPlayerLoadoutService",Loadout.class),Map.entry("server/room/runtime/RoomPlayerLoadoutService$ResolvedLoadoutItem",Resolved.class),Map.entry("server/room/runtime/RoomLoadoutGrantService$TaczAmmoMeta",Meta.class),Map.entry("server/data/equipment/EquipmentDataModel$EquipmentEntry",Entry.class),Map.entry("server/compat/superbwarfare/SuperbWarfareCompatBridge",AmmoReserveFixtureTest.Bridge.class),Map.entry("common/item/ConquestItems",AmmoReserveFixtureTest.Items.class),Map.entry("common/entity/AmmoBoxEntity",AmmoBox.class),Map.entry("common/entity/MedicalBoxEntity",MedicalBox.class),Map.entry("server/facility/support/HeroKitSupportService",Hero.class),Map.entry("server/compat/frontlinevoice/FrontlineVoiceBridge",Voice.class),Map.entry("server/integration/KilliconScoreboardBridge",Score.class),Map.entry("server/room/runtime/RoomDeathSupportDropService",Death.class)).entrySet())m.put(BASE+e.getKey(),Type.getInternalName(e.getValue()));
        m.put(BASE+"server/room/runtime/RoomLoadoutGrantService",RUN+"Grant");m.put(Type.getInternalName(Grant.class),RUN+"Grant");
        m.put(BASE+"server/support/SuppressionNetwork",Type.getInternalName(SuppressionTransport.class));return m;
    }
    static void addRegen(AmmoReserveFixtureTest.Loader loader,Map<String,String> map)throws Exception {
        map.put(BASE+"server/data/ConquestDataManager",Type.getInternalName(Data.class));
        for(String name:List.of("NaturalRegenService","PassiveRegenService"))map.put(BASE+"server/player/regen/"+name,RUN+name);
        for(String name:List.of("NaturalRegenService","PassiveRegenService"))AmmoReserveFixtureTest.add(loader,AmmoReserveFixtureTest.read(BASE+"server/player/regen/"+name),map);
    }
    static ItemEntity bag(Server s,String type,int count){return new ItemEntity(s.level,0,0,0,new Stack(type,count,64));}
    static void configure(Player p,CustomLoadoutSlot slot,Stack stack,int cap){Entry entry=new Entry();entry.configured=cap;int hotbar=slot.getIndex()-1;p.inventory.m_6836_(hotbar,stack);p.configured.put(slot,new Resolved(slot,hotbar,entry,stack.m_41777_()));}
    static int reserve(Player p){return p.inventory.f_35974_.stream().filter(s->s.item.id.equals("ammo:test:light")).mapToInt(s->s.count).sum();}
    public static void main(String[] args)throws Exception {
        var loader=new AmmoReserveFixtureTest.Loader();var map=mappings();
        addRegen(loader,map);
        for(String name:new ArrayList<>(map.keySet()))if(name.startsWith(BASE+"server/support/")&&!name.endsWith("SuppressionNetwork"))AmmoReserveFixtureTest.add(loader,AmmoReserveFixtureTest.read(name),map);
        AmmoReserveFixtureTest.add(loader,AmmoReserveFixtureTest.read(BASE+"server/player/regen/PassiveRegenService"),map);
        var adapter=AmmoReserveFixtureTest.read(Type.getInternalName(Grant.class));adapter.methods.removeIf(m->m.name.equals("addTaczAmmoToPreferredSlots"));adapter.methods.add(AmmoReserveFixtureTest.read(BASE+"server/room/runtime/RoomLoadoutGrantService").methods.stream().filter(m->m.name.equals("addTaczAmmoToPreferredSlots")).findFirst().orElseThrow());AmmoReserveFixtureTest.add(loader,adapter,map);
        Class<?> runtime=loader.loadClass("tests.supply.SupportRuntime"),context=loader.loadClass("tests.supply.SupportContext"),guard=loader.loadClass("tests.supply.SupportDropGuard"),ammo=loader.loadClass("tests.supply.SupportAmmo");
        Method pickup=runtime.getMethod("pickup",Pickup.class),mark=runtime.getMethod("markBag",ItemEntity.class,Player.class),reset=runtime.getMethod("reset",Player.class),boxes=runtime.getMethod("boxes",Server.class,Set.class,long.class,boolean.class),pause=runtime.getMethod("pausePassive",Player.class),heal=runtime.getMethod("heal",Player.class,float.class,UUID.class,String.class),tick=runtime.getDeclaredMethod("tick");tick.setAccessible(true);
        Method of=runtime.getMethod("of",Server.class),allowed=context.getMethod("allowed",Player.class,Entity.class,UUID.class,boolean.class);
        Server server=new Server();Player owner=new Player(server),p=new Player(server);server.state.teams.put(p.id,RoomTeamId.CAMP_B);
        server.room.config=new com.google.gson.Gson().fromJson("{\"allowEnemySupportResupply\":false}",RoomCommonConfig.class);
        ItemEntity med=bag(server,"medic_bag",1);mark.invoke(null,med,owner);
        check(med.lifespan==600&&med.stack.tag.m_128403_("gd656_support_bag_id"),"real mark preserves 30s life and unique bag NBT");
        check(!(boolean)allowed.invoke(null,p,med,owner.id,false),"enemy support false rejects enemy");
        check((boolean)allowed.invoke(null,owner,med,owner.id,false),"owner always allowed within same room");
        check((boolean)allowed.invoke(null,p,med,null,true),"permanent supplies public within stamped room");
        med.data.m_128359_("gd656_support_room","other");check(!(boolean)allowed.invoke(null,owner,med,owner.id,false),"cross-room supply denied even for owner/permanent");med.data.m_128359_("gd656_support_room","room");
        server.room.config=RoomCommonConfig.defaults();check((boolean)allowed.invoke(null,p,med,owner.id,false),"default enemy support true allows enemy");
        Pickup event=new Pickup(owner,med);pickup.invoke(null,event);check(event.canceled&&med.stack.count==1&&owner.health==10,"thrower 0.5s grace before use");
        owner.abilities.f_35937_=true;event=new Pickup(owner,med);pickup.invoke(null,event);check(event.canceled,"creative thrower also observes grace");
        server.now=10;event=new Pickup(owner,med);pickup.invoke(null,event);check(!event.canceled&&med.stack.count==1&&!med.stack.tag.m_128403_("gd656_support_bag_id"),"creative later pickup returns item without automatic use");owner.abilities.f_35937_=false;
        server.now=11;med=bag(server,"medic_bag",1);mark.invoke(null,med,owner);event=new Pickup(p,med);pickup.invoke(null,event);
        check(event.canceled&&!med.alive&&p.health==12.5F&&p.cues.size()==1,"medical pickup consumes one bag after actual immediate 2.5 HP");
        check((boolean)pause.invoke(null,p),"active medical treatment pauses passive regeneration");
        server.now=30;tick.invoke(of.invoke(null,server));check(p.health==12.5F,"medical treatment waits full second");
        server.now=31;tick.invoke(of.invoke(null,server));check(p.health==15&&p.cues.size()==2,"subsequent medical heal uses same cue after one second");
        runtime.getMethod("damaged",Damage.class).invoke(null,new Damage(p));server.now=60;tick.invoke(of.invoke(null,server));check(p.health==15&&!(boolean)pause.invoke(null,p),"actual injury terminates bag treatment");
        p.health=20;med=bag(server,"medic_bag",1);mark.invoke(null,med,owner);event=new Pickup(p,med);pickup.invoke(null,event);check(med.stack.count==1&&med.alive,"full health does not consume medical bag");
        p.health=19;int cues=p.cues.size();heal.invoke(null,p,2.5F,null,"");heal.invoke(null,p,1F,null,"");check(p.health==20&&p.cues.size()==cues+1,"only actual HP increase emits healing sound");
        Stack gun=AmmoReserveFixtureTest.gun("primary","light",false);gun.tag.m_128405_("gd656_support_reserve_cap",100);gun.tag.m_128359_("gd656_support_reserve_pool","tacz:test:light");configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,gun,100);
        check((int)ammo.getMethod("bag",Player.class).invoke(null,p)==4&&reserve(p)==4,"single-shot ammo bag supplies max two magazines/four rounds");
        server.state.hero=true;check((int)ammo.getMethod("bag",Player.class).invoke(null,p)==0,"hero kit blocks ammo bag");server.state.hero=false;
        AmmoBox box=new AmmoBox();box.level=server.level;box.owner=owner.id;box.capacity=20;server.level.entities.put(box.id,box);runtime.getMethod("initializeBox",Entity.class,UUID.class,Object.class).invoke(null,box,owner.id,bag(server,"ammo_bag",1));context.getMethod("stamp",Entity.class,Player.class).invoke(null,box,owner);
        var ids=new HashSet<UUID>(Set.of(box.id));server.now=70;boxes.invoke(null,server,ids,70L,false);check(reserve(p)==6&&box.capacity==19,"ammo aura consumes one successful burst");
        server.now=80;boxes.invoke(null,server,ids,80L,false);check(reserve(p)==6&&box.capacity==19,"ammo aura cannot accelerate before one second");
        server.now=90;boxes.invoke(null,server,ids,90L,false);check(reserve(p)==8&&box.capacity==18,"next ammo aura burst after one second");
        Stack gadget=new Stack("gadget",2,16);configure(p,CustomLoadoutSlot.GADGET_1,gadget,0);p.inventory.m_6836_(3,Stack.EMPTY);
        for(int time=100;time<200;time+=10){server.now=time;boxes.invoke(null,server,ids,(long)time,false);}check(p.inventory.m_8020_(3).m_41619_(),"gadget waits five seconds of eligible range progress");
        server.now=200;boxes.invoke(null,server,ids,200L,false);check(p.inventory.m_8020_(3).count==1,"gadget receives one after five seconds");
        owner.health=20;MedicalBox medical=new MedicalBox();medical.level=server.level;medical.owner=owner.id;medical.capacity=100;server.level.entities.put(medical.id,medical);context.getMethod("stamp",Entity.class,Player.class).invoke(null,medical,owner);runtime.getMethod("initializeBox",Entity.class,UUID.class,Object.class).invoke(null,medical,owner.id,new Room());
        var mids=new HashSet<UUID>(Set.of(medical.id));p.health=19.5F;server.now=210;boxes.invoke(null,server,mids,210L,true);check(p.health==20&&medical.data.m_128457_("gd656_medical_pool")==99.5F,"medical aura debits actual HP, not requested HP");
        p.health=10;runtime.getMethod("damaged",Damage.class).invoke(null,new Damage(p));server.now=260;boxes.invoke(null,server,mids,260L,true);check(p.health==10,"medical aura honors damage cooldown");
        server.now=270;boxes.invoke(null,server,mids,270L,true);check(p.health==11,"medical aura resumes at exact three-second boundary");
        check(owner.bonuses==0,"enemy medical/ammo supplies never award giver");
        server.state.teams.put(p.id,RoomTeamId.CAMP_A);heal.invoke(null,p,1F,owner.id,"CAMP_A");check(owner.bonuses==1,"actual friendly healing awards giver");
        heal.invoke(null,p,1F,p.id,"CAMP_A");check(p.bonuses==0,"self healing never awards self");
        reset.invoke(null,p);check(!(boolean)pause.invoke(null,p),"reset clears medical state immediately");
        ItemEntity lifetime=bag(server,"ammo_bag",1);mark.invoke(null,lifetime,owner);server.now=870;tick.invoke(of.invoke(null,server));check(!lifetime.alive,"tracked bag removed at 30 second lifetime");
        Stack recharge=new Stack("ammo_bag",2,64);configure(p,CustomLoadoutSlot.GADGET_2,recharge,0);p.inventory.m_6836_(4,Stack.EMPTY);var times=new HashMap<Integer,Long>();Method charged=runtime.getMethod("recharge",Player.class,Resolved.class,long.class,Map.class);
        check(!(boolean)charged.invoke(null,p,p.configured.get(CustomLoadoutSlot.GADGET_2),0L,times)&&!(boolean)charged.invoke(null,p,p.configured.get(CustomLoadoutSlot.GADGET_2),119L,times),"bag recharge waits six seconds");
        check((boolean)charged.invoke(null,p,p.configured.get(CustomLoadoutSlot.GADGET_2),120L,times)&&p.inventory.m_8020_(4).count==1,"bag recharge restores one");
        check(!(boolean)charged.invoke(null,p,p.configured.get(CustomLoadoutSlot.GADGET_1),240L,times),"ordinary equipment does not auto-recharge");
        Click click=new Click();click.slot=-999;p.f_36096_.carried=recharge;check((boolean)guard.getMethod("blockClick",Player.class,Click.class).invoke(null,p,click),"container outside throw of carried bag blocked");
        configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,recharge,0);check(!(boolean)guard.getMethod("drop",Player.class,boolean.class).invoke(null,p,true)&&p.f_36096_.updates>0,"Ctrl-Q blocked and container resynchronized");
        ItemEntity tossed=bag(server,"ammo_bag",1);Toss toss=new Toss(p,tossed);runtime.getMethod("toss",Toss.class).invoke(null,toss);check(toss.canceled,"unstamped Q context rejects direct container toss");
        Player.dropToss=runtime.getMethod("toss",Toss.class);
        server.now=900;configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,new Stack("ammo_bag",2,64),0);
        check((boolean)guard.getMethod("drop",Player.class,boolean.class).invoke(null,p,false),"single Q enters actual toss context");
        check(!(boolean)guard.getMethod("isQ",Player.class).invoke(null,p),"Q context restored after toss");
        check(!(boolean)guard.getMethod("drop",Player.class,boolean.class).invoke(null,p,false)&&p.inventory.m_8020_(0).count==1,"Q cooldown blocks duplicate without consuming stock");
        server.now=910;check((boolean)guard.getMethod("drop",Player.class,boolean.class).invoke(null,p,false),"Q interval expires at half-second boundary");
        p.abilities.f_35937_=true;configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,new Stack("medic_bag",2,64),0);
        runtime.getMethod("useBag",Level.class,Player.class,InteractionHand.class).invoke(null,server.level,p,InteractionHand.MAIN_HAND);
        check(p.inventory.m_8020_(0).count==2,"creative right-click throw preserves inventory");
        p.abilities.f_35937_=false;server.level.spawn=false;server.now=930;int before=p.inventory.m_8020_(0).count;
        runtime.getMethod("useBag",Level.class,Player.class,InteractionHand.class).invoke(null,server.level,p,InteractionHand.MAIN_HAND);
        check(p.inventory.m_8020_(0).count==before,"failed world insertion does not consume thrown bag");server.level.spawn=true;
        server.room.config=new com.google.gson.Gson().fromJson("{\"naturalRegenConfig\":{\"startDelaySeconds\":0,\"intervalTicks\":1,\"healAmount\":2}}",RoomCommonConfig.class);
        Class<?> passive=loader.loadClass("tests.supply.PassiveRegenService");p.health=10;p.acceptHeal=false;cues=p.cues.size();
        passive.getMethod("markPlayerDamaged",Player.class,long.class).invoke(null,p,990L);
        passive.getMethod("onPlayerTick",Player.class,long.class,int.class,int.class).invoke(null,p,1000L,0,1);
        check(p.health==10&&p.cues.size()==cues,"canceled passive healing does not emit a success cue");
        p.acceptHeal=true;passive.getMethod("onPlayerTick",Player.class,long.class,int.class,int.class).invoke(null,p,1001L,0,1);
        check(p.health==12&&p.cues.size()==cues+1,"actual passive healing emits one private startup cue");
        System.out.println("PASS shipped support permissions/pickup/treatment/aura/lifetime/recharge/drop fixture: "+checks);
    }
}
