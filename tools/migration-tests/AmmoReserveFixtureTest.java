package tests;

import java.lang.reflect.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.room.model.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Execute shipped grant/target/refill methods with inventory/world adapters and real Minecraft NBT. */
public final class AmmoReserveFixtureTest {
    static int checks;
    static final String BASE="org/mods/gd656conquest/", RUN="tests/reserve/";
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public record Meta(ResourceLocation ammoId,int stackSize) {}
    public static final class Item {
        public final String id; public Item(String id){this.id=id;}
    }
    public static final class Stack {
        public static final Map<String,Item> ITEMS=new HashMap<>();
        public static final Stack EMPTY=new Stack("",0,0);
        public Item item; public int count,limit; public boolean gun,sbw; public String ammo;
        public CompoundTag tag;
        public Stack(String id,int count,int limit){item=ITEMS.computeIfAbsent(id,Item::new);this.count=count;this.limit=limit;}
        public boolean m_41619_(){return count<=0;}
        public int m_41613_(){return count;}
        public int m_41741_(){return limit;}
        public Item m_41720_(){return item;}
        public void m_41764_(int n){count=n;}
        public void m_41769_(int n){count+=n;}
        public CompoundTag m_41783_(){return tag;}
        public CompoundTag m_41784_(){if(tag==null)tag=new CompoundTag();return tag;}
        public Stack m_41777_(){
            Stack copy=new Stack(item.id,count,limit);copy.gun=gun;copy.sbw=sbw;copy.ammo=ammo;
            try{copy.tag=tag==null?null:TagParser.m_129359_(tag.toString());}catch(Exception e){throw new RuntimeException(e);}return copy;
        }
        public static boolean m_150942_(Stack a,Stack b){return a.item==b.item&&Objects.equals(a.tag,b.tag);}
    }
    public static final class StackList extends ArrayList<Stack> {
        public StackList(){super(Collections.nCopies(36,Stack.EMPTY));}
    }
    public static final class Inventory {
        public final StackList f_35974_=new StackList();
        public int f_35977_,updates;
        public Stack m_8020_(int slot){return f_35974_.get(slot);}
        public void m_6836_(int slot,Stack stack){f_35974_.set(slot,stack);}
        public void m_6596_(){updates++;}
    }
    public static final class Menu {public int updates;public void m_38946_(){updates++;}}
    public static final class Player {
        public final Server f_8924_;public final UUID id=UUID.randomUUID();public final Inventory inventory=new Inventory();
        public final Menu f_36096_=new Menu();public boolean alive=true,spectator;
        public final Map<CustomLoadoutSlot,Resolved> configured=new EnumMap<>(CustomLoadoutSlot.class);
        public Player(Server server){f_8924_=server;if(server!=null)server.state.teams.put(id,RoomTeamId.CAMP_A);}
        public Inventory m_150109_(){return inventory;}
        public UUID m_20148_(){return id;}
        public boolean m_6084_(){return alive;}
        public boolean m_5833_(){return spectator;}
    }
    public static final class Server {public int limit=64,group=64;public State state=new State();public Room room=new Room();public boolean bound=true;}
    public static final class State {
        public Map<UUID,RoomTeamId> teams=new HashMap<>();public boolean downed,respawning;
        public Map<UUID,RoomTeamId> getRoomTeams(){return teams;}
        public boolean isInRespawnFlow(UUID id){return respawning;}
    }
    public static final class Room {
        public RoomLifecycleState lifecycle=RoomLifecycleState.RUNNING;public RoomGameplayId gameplay=RoomGameplayId.CONQUEST;
        public RoomLifecycleState lifecycleState(){return lifecycle;}
        public RoomGameplayId gameplayId(){return gameplay;}
    }
    public static final class Manager {
        public final Server server;public Manager(Server server){this.server=server;}
        public static Manager of(Server server){return new Manager(server);}
        public Optional<String> findPlayerRoomId(UUID id){return server.bound?Optional.of("room"):Optional.empty();}
        public Optional<Room> findRoom(String id){return Optional.ofNullable(server.room);}
        public Optional<State> findRunningState(String id){return Optional.ofNullable(server.state);}
        public boolean isPlayerDowned(UUID id){return server.state.downed;}
    }
    public static final class Entry {
        public boolean enabled=true;public int configured;public String id="entry";
        public boolean isAmmoBoxRefillEnabled(){return enabled;}
        public int getExtraData(){return configured;}
        public int getReplenishSeconds(){return 15;}
        public String getId(){return id;}
    }
    public record Resolved(CustomLoadoutSlot slot,int hotbarSlot,Entry entry,Stack configuredStack) {}
    public static final class Loadout {
        public Loadout(Server server){}
        public Map<CustomLoadoutSlot,Resolved> resolveConfiguredLoadoutItems(Player p){return p.configured;}
        public boolean isAmmoManagedGunItem(Stack stack){return stack.gun;}
    }
    public static final class Grant {
        public final Server server;public Grant(Server server){this.server=server;}
        public Meta resolveTaczAmmoMeta(Stack stack){return stack.gun&&!stack.sbw?new Meta(new ResourceLocation("test",stack.ammo),server.group):null;}
        public Stack createTaczAmmoStack(ResourceLocation id,int count){return count<=0?Stack.EMPTY:new Stack("ammo:"+id,count,server.limit);}
        // Replaced below with the actual production helper before this class is defined.
        public int addTaczAmmoToPreferredSlots(Player p,Meta meta,Stack sample,int count,int[] slots){throw new AssertionError("production helper not installed");}
    }
    public static final class Bridge {
        public static boolean isGunItem(Stack stack){return stack!=null&&!stack.m_41619_()&&stack.sbw;}
        public static boolean applyReserveAmmo(Stack stack,int count){stack.m_41784_().m_128405_("virtualAmmo",count);return true;}
        public static int getReserveAmmo(Stack stack){return stack.m_41784_().m_128451_("virtualAmmo");}
    }
    public static final class Mods {public static Mods get(){return new Mods();}public boolean isLoaded(String id){return false;}}
    public static final class Holder {public final Item item;public Holder(String id){item=new Stack(id,1,1).item;}public Object get(){return item;}}
    public static final class Items {
        public static final Holder AMMO_BAG=new Holder("ammo_bag"),MEDIC_BAG=new Holder("medic_bag"),AMMO_BOX=new Holder("ammo_box"),MED_BOX=new Holder("medical_box");
    }
    static byte[] bytes(String name)throws Exception{try(var in=AmmoReserveFixtureTest.class.getClassLoader().getResourceAsStream(name+".class")){return in.readAllBytes();}}
    static class Loader extends ClassLoader {
        final Map<String,byte[]> types=new HashMap<>();Loader(){super(AmmoReserveFixtureTest.class.getClassLoader());}
        protected Class<?> findClass(String name)throws ClassNotFoundException{byte[] b=types.get(name);if(b==null)throw new ClassNotFoundException(name);return defineClass(name,b,0,b.length);}
    }
    static String internal(Class<?> type){return org.objectweb.asm.Type.getInternalName(type);}
    static void add(Loader loader,ClassNode node,Map<String,String> mappings){var writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(mappings)));loader.types.put(mappings.get(node.name).replace('/','.'),writer.toByteArray());}
    static ClassNode read(String name)throws Exception{var node=new ClassNode();new ClassReader(bytes(name)).accept(node,0);return node;}
    static Stack gun(String name,String ammo,boolean sw){Stack g=new Stack("gun:"+name,1,1);g.gun=true;g.sbw=sw;g.ammo=ammo;g.m_41784_().m_128405_("loadedRounds",7);return g;}
    static void configure(Player p,CustomLoadoutSlot slot,Stack gun,int configured){
        Entry e=new Entry();e.configured=configured;int hotbar=slot.getIndex()-1;
        p.inventory.m_6836_(hotbar,gun);p.configured.put(slot,new Resolved(slot,hotbar,e,gun.m_41777_()));
    }
    static int[] range(int a,int b){return java.util.stream.IntStream.rangeClosed(a,b).toArray();}
    static int reserve(Player p,String ammo){return p.inventory.f_35974_.stream().filter(s->s.item.id.equals("ammo:test:"+ammo)).mapToInt(s->s.count).sum();}
    static int cap(Stack g){return g.m_41784_().m_128451_("gd656_support_reserve_cap");}
    public static void main(String[] args)throws Exception {
        Map<String,String> map=new HashMap<>();
        for(String type:List.of("SupportAmmoGrant","SupportAmmoGrant$Slots","SupportAmmoGrant$1","SupportAmmo","SupportAmmo$Target","SupportAmmo$Tacz","SupportAmmoRefill"))map.put(BASE+"server/support/"+type,RUN+type);
        for(var entry:Map.of("net/minecraft/server/level/ServerPlayer",Player.class,"net/minecraft/server/MinecraftServer",Server.class,
            "net/minecraft/world/entity/player/Inventory",Inventory.class,"net/minecraft/world/inventory/AbstractContainerMenu",Menu.class,
            "net/minecraft/world/item/ItemStack",Stack.class,"net/minecraft/world/item/Item",Item.class,
            "net/minecraftforge/fml/ModList",Mods.class,"net/minecraftforge/registries/RegistryObject",Holder.class,
            "net/minecraft/core/NonNullList",StackList.class).entrySet())map.put(entry.getKey(),internal(entry.getValue()));
        for(var entry:Map.of("server/room/runtime/RoomPlayerLoadoutService",Loadout.class,"server/room/runtime/RoomPlayerLoadoutService$ResolvedLoadoutItem",Resolved.class,
            "server/room/runtime/RoomLoadoutGrantService$TaczAmmoMeta",Meta.class,"server/data/equipment/EquipmentDataModel$EquipmentEntry",Entry.class,
            "server/compat/superbwarfare/SuperbWarfareCompatBridge",Bridge.class,"common/item/ConquestItems",Items.class,
            "server/room/entry/RoomCoreRuntimeManager",Manager.class,"server/room/model/RoomDefinition",Room.class,"server/room/core/RunningRuntimeState",State.class).entrySet())map.put(BASE+entry.getKey(),internal(entry.getValue()));
        map.put(BASE+"server/room/runtime/RoomLoadoutGrantService",RUN+"Grant");map.put(internal(Grant.class),RUN+"Grant");
        var loader=new Loader();
        for(String name:new ArrayList<>(map.keySet()))if(name.startsWith(BASE+"server/support/"))add(loader,read(name),map);
        var adapter=read(internal(Grant.class));adapter.methods.removeIf(m->m.name.equals("addTaczAmmoToPreferredSlots"));
        adapter.methods.add(read(BASE+"server/room/runtime/RoomLoadoutGrantService").methods.stream().filter(m->m.name.equals("addTaczAmmoToPreferredSlots")).findFirst().orElseThrow());
        add(loader,adapter,map);
        Class<?> service=loader.loadClass((RUN+"SupportAmmoGrant").replace('/','.')),grantType=loader.loadClass((RUN+"Grant").replace('/','.'));
        Method grant=service.getMethod("grant",grantType,Player.class,Stack.class,int.class,int[].class),finish=service.getMethod("finishLoadout",Player.class);
        Class<?> ammo=loader.loadClass((RUN+"SupportAmmo").replace('/','.')),refill=loader.loadClass((RUN+"SupportAmmoRefill").replace('/','.'));
        Method targets=ammo.getMethod("targets",Player.class),bag=refill.getMethod("bag",Player.class,int.class),box=refill.getMethod("box",RoomTeamId.class,Player.class);
        Server server=new Server();server.limit=10000;Player p=new Player(server);Stack primary=gun("primary","light",false);configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,primary,1000);
        Object actualGrant=grantType.getConstructor(Server.class).newInstance(server);
        grant.invoke(null,actualGrant,p,primary,1000,range(18,26));
        check(reserve(p,"light")==1000&&p.inventory.m_8020_(18).count==488&&p.inventory.m_8020_(26).count==64,"actual initial grant spreads gun-pack groups, then fills expanded stacks");
        check(cap(primary)==1000&&primary.tag.m_128451_("loadedRounds")==7,"actual grant records ceiling without changing loaded rounds");
        server.limit=64;p=new Player(server);primary=gun("primary","light",false);Stack secondary=gun("secondary","light",false);
        configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,primary,1000);configure(p,CustomLoadoutSlot.SECONDARY_WEAPON,secondary,1000);
        grant.invoke(null,actualGrant,p,primary,1000,range(18,26));grant.invoke(null,actualGrant,p,secondary,1000,range(29,35));
        check(cap(primary)==576&&cap(secondary)==448&&reserve(p,"light")==1024,"actual finite inventory records partial grants");
        p.inventory.m_6836_(35,new Stack("unrelated",1,64));finish.invoke(null,p);
        check(cap(primary)==576&&cap(secondary)==384&&reserve(p,"light")==960,"final equipment overwrite reduces actual deployed caps");
        for(Object t:(List<?>)targets.invoke(null,p)){Field field=t.getClass().getDeclaredField("cap");field.setAccessible(true);check(field.getInt(t)==960,"same ammo pool merges both deployed caps");}
        p.inventory.m_8020_(18).count-=20;
        check((int)bag.invoke(null,p,50)==20&&reserve(p,"light")==960,"bag obeys actual shared cap, without extra overflow pass");
        int updates=p.inventory.updates;check((int)bag.invoke(null,p,50)==0&&p.inventory.updates==updates,"full reserves produce no supply or inventory sync");
        p.configured.get(CustomLoadoutSlot.PRIMARY_WEAPON).entry.configured=10000;
        p.inventory.m_8020_(18).count-=10;check((int)bag.invoke(null,p,50)==10,"configuration edits cannot raise an existing deployed cap");
        check(!(boolean)box.invoke(null,RoomTeamId.CAMP_B,p),"wrong camp rejected by actual refill bridge");
        for(boolean unavailable:new boolean[]{true,false}) {
            server.state.respawning=unavailable;
            if(unavailable)check((int)bag.invoke(null,p,50)==0&&!(boolean)box.invoke(null,RoomTeamId.CAMP_A,p),"deployment flow blocks both transactions");
        }
        server.state.downed=true;check((int)bag.invoke(null,p,50)==0,"downed player cannot refill");server.state.downed=false;
        check((int)bag.invoke(null,null,50)==0,"null player rejected");
        p.alive=false;check((int)bag.invoke(null,p,50)==0,"dead player rejected");p.alive=true;
        p.spectator=true;check(!(boolean)box.invoke(null,RoomTeamId.CAMP_A,p),"spectator cannot refill");p.spectator=false;
        server.room.lifecycle=RoomLifecycleState.IDLE;
        check((int)bag.invoke(null,p,50)==0,"non-running room rejected");server.room.lifecycle=RoomLifecycleState.RUNNING;
        server.bound=false;check((int)bag.invoke(null,p,50)==0,"outside a room cannot refill");server.bound=true;
        server.room.gameplay=RoomGameplayId.GUNMASTER;check((int)bag.invoke(null,p,50)==0,"gunmaster cannot refill");server.room.gameplay=RoomGameplayId.CONQUEST;
        Stack gadget=new Stack("gadget",3,16);configure(p,CustomLoadoutSlot.GADGET_1,gadget,0);p.inventory.m_6836_(3,Stack.EMPTY);
        check((int)bag.invoke(null,p,50)==0&&p.inventory.m_8020_(3).m_41619_(),"bag leaves slow equipment replenishment to boxes");
        check((boolean)box.invoke(null,RoomTeamId.CAMP_A,p)&&p.inventory.m_8020_(3).count==3,"box restores configured gadget amount");
        check(!(boolean)box.invoke(null,RoomTeamId.CAMP_A,p),"box has no successful transaction when everything is full");
        check(primary.tag.m_128451_("loadedRounds")==7&&p.inventory.m_8020_(35).item.id.equals("unrelated"),"reserve supply preserves loaded rounds and unrelated inventory");
        Stack throwable=new Stack("throwable",2,16);configure(p,CustomLoadoutSlot.THROWABLE,throwable,0);p.inventory.m_6836_(5,Stack.EMPTY);
        check((int)bag.invoke(null,p,50)==0,"bag does not replenish throwables");
        check((boolean)box.invoke(null,RoomTeamId.CAMP_A,p)&&p.inventory.m_8020_(5).count==2,"box restores configured throwable amount");
        Stack supportBag=new Stack("ammo_bag",2,16);configure(p,CustomLoadoutSlot.GADGET_2,supportBag,0);p.inventory.m_6836_(4,Stack.EMPTY);
        check(!(boolean)box.invoke(null,RoomTeamId.CAMP_A,p)&&p.inventory.m_8020_(4).m_41619_(),"supply bags excluded from ordinary equipment targets");
        server.limit=16;p=new Player(server);primary=gun("small","light",false);configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,primary,1000);
        grant.invoke(null,actualGrant,p,primary,1000,new int[]{18,19});check(cap(primary)==32,"initial grant observes actual smaller stack limit");
        p.inventory.m_6836_(18,Stack.EMPTY);p.inventory.m_6836_(19,Stack.EMPTY);
        check((int)bag.invoke(null,p,50)==32&&p.inventory.m_8020_(18).count==16&&p.inventory.m_8020_(19).count==16,"refill empty slots also observes actual smaller stack limit");
        p.inventory.m_8020_(18).count=0;p.configured.get(CustomLoadoutSlot.PRIMARY_WEAPON).entry.enabled=false;
        check((int)bag.invoke(null,p,50)==0,"disabled refill entry remains disabled");p.configured.get(CustomLoadoutSlot.PRIMARY_WEAPON).entry.enabled=true;
        p.inventory.m_6836_(0,new Stack("unrelated",1,64));check((int)bag.invoke(null,p,50)==0,"different held weapon does not receive configured reserves");
        p.inventory.m_6836_(0,primary);for(int slot:range(18,26))p.inventory.m_6836_(slot,new Stack("blocked",1,64));
        check((int)bag.invoke(null,p,50)==0,"occupied destination slots never spill into other rows");
        p=new Player(server);primary=gun("none","light",false);configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,primary,1000);
        grant.invoke(null,actualGrant,p,primary,1000,new int[]{-1,99});check(cap(primary)==0&&(int)bag.invoke(null,p,50)==0,"zero actual deployment keeps zero reserve allowance");
        server.limit=10000;grant.invoke(null,actualGrant,p,primary,1000,range(18,26));
        check(cap(primary)==1000,"redeployment replaces the previous zero allowance");
        server.limit=64;p=new Player(server);primary=gun("sw","unused",true);configure(p,CustomLoadoutSlot.PRIMARY_WEAPON,primary,100);
        grant.invoke(null,actualGrant,p,primary,100,new int[]{18});primary.tag.m_128405_("virtualAmmo",60);
        check((int)bag.invoke(null,p,50)==40&&Bridge.getReserveAmmo(primary)==100&&primary.tag.m_128451_("loadedRounds")==7,"SBW refills only missing virtual reserve, retaining magazine");
        System.out.println("PASS shipped grants/final caps/shared targets/refills with inventory/world adapters and real NBT: "+checks);
    }
}
