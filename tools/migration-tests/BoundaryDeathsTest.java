package tests;

import java.util.*;
import java.lang.reflect.Method;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.mods.gd656conquest.common.player.DeathCause;

/** Execute shipped service bytecode with small server/vehicle doubles, without a game boot. */
public final class BoundaryDeathsTest {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static class Source {}
    public static class Sources {public Source m_287172_(){return new Source();}}
    public static class Players {
        final Map<UUID,Player> online=new HashMap<>();
        public Player m_11259_(UUID id){return online.get(id);}
    }
    public static class Server {final Players players=new Players();public Players m_6846_(){return players;}}
    public static class Room {public String roomId(){return "test";}}
    public static class State {}
    public static class Manager {
        static State running;
        public static Manager of(Server server){return new Manager();}
        public Optional<State> findRunningState(String id){return Optional.ofNullable(running);}
    }
    public static class Entity {
        final Server server;boolean removed;final List<Entity> passengers=new ArrayList<>();
        Entity(Server server){this.server=server;}
        public boolean m_213877_(){return removed;}
        public Server m_20194_(){return server;}
        public List<Entity> m_20197_(){return passengers;}
        public Sources m_269291_(){return new Sources();}
        public boolean m_6469_(Source source,float damage){return false;}
    }
    public static class Player extends Entity {
        public final Server f_8924_;final UUID id=UUID.randomUUID();
        int marked,killed;boolean deployment,outside;
        Player(Server server){super(server);f_8924_=server;server.players.online.put(id,this);}
        public UUID m_20148_(){return id;}
    }
    public static class Vehicle extends Entity {
        float health=100;boolean destroyed;
        Runnable explosion=()->{};
        Vehicle(Server server){super(server);}
        public float getHealth(){return health;}
        public void setHealth(float value){health=value;}
        public boolean isDestroyed(){return destroyed;}
        void nativeDestroy() {
            invoke(begin,this);
            explosion.run();
            passengers.clear();destroyed=true;removed=true;
            invoke(finish,this);
        }
    }
    public static class YwzjVehicle extends Vehicle {
        YwzjVehicle(Server server){super(server);}
        @Override public boolean m_6469_(Source source,float damage){nativeDestroy();return true;}
    }
    public static class Facade {public static boolean isVehicleEntity(Entity entity){return entity instanceof Vehicle;}}
    public static class Rules {
        public static void markDeserter(Player player){player.marked++;}
        public static void killDeserter(Player player){if(!player.deployment){player.marked++;player.killed++;player.deployment=true;}}
        public static boolean isOutside(Player player){return player.outside;}
    }
    public static class Fire {public static Player resolve(Player player,Source source){return null;}}
    static Method begin,finish;
    static void invoke(Method method,Object value){try{method.invoke(null,value);}catch(ReflectiveOperationException error){throw new RuntimeException(error);}}
    static byte[] bytes(String name)throws Exception {
        try(var in=BoundaryDeathsTest.class.getClassLoader().getResourceAsStream(name+".class")){return in.readAllBytes();}
    }
    static class Loader extends ClassLoader {
        final Map<String,byte[]> types=new HashMap<>();
        Loader(){super(BoundaryDeathsTest.class.getClassLoader());}
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            byte[] content=types.get(name);if(content==null)throw new ClassNotFoundException(name);
            return defineClass(name,content,0,content.length);
        }
    }
    static void mapped(Loader loader,String source,Map<String,String> mapping,Set<String> methods)throws Exception {
        var type=new ClassNode();new ClassReader(bytes(source)).accept(type,0);
        if(methods!=null)type.methods.removeIf(m->!methods.contains(m.name));
        // Match the YWZJ base-class name used by the shipped reflection bridge.
        for(var method:type.methods)for(var instruction:method.instructions)if(instruction instanceof LdcInsnNode literal
            &&"org.ywzj.vehicle.entity.vehicle.AbstractVehicle".equals(literal.cst))literal.cst=YwzjVehicle.class.getName();
        var writer=new ClassWriter(0);type.accept(new ClassRemapper(writer,new SimpleRemapper(mapping)));
        loader.types.put(mapping.get(source).replace('/','.'),writer.toByteArray());
    }
    static String internal(Class<?> type){return org.objectweb.asm.Type.getInternalName(type);}
    public static void main(String[] args)throws Exception {
        String root="org/mods/gd656conquest/",nativeService=root+"server/compat/vehicle/BoundaryVehicleDestruction",
            causeService=root+"server/room/entry/RoomDeathCauseService";
        Map<String,String> mapping=new HashMap<>();
        mapping.put(nativeService,"tests/runtime/BoundaryVehicleDestruction");mapping.put(nativeService+"$Request","tests/runtime/BoundaryVehicleDestruction$Request");
        mapping.put(causeService,"tests/runtime/RoomDeathCauseService");
        for(String name:List.of("Death","FireOwner"))mapping.put(causeService+"$"+name,"tests/runtime/RoomDeathCauseService$"+name);
        mapping.put("net/minecraft/world/entity/Entity",internal(Entity.class));
        mapping.put("net/minecraft/server/MinecraftServer",internal(Server.class));
        mapping.put("net/minecraft/server/players/PlayerList",internal(Players.class));
        mapping.put("net/minecraft/server/level/ServerPlayer",internal(Player.class));
        mapping.put("net/minecraft/world/damagesource/DamageSource",internal(Source.class));
        mapping.put("net/minecraft/world/damagesource/DamageSources",internal(Sources.class));
        mapping.put(root+"server/room/model/RoomDefinition",internal(Room.class));
        mapping.put(root+"server/room/core/RunningRuntimeState",internal(State.class));
        mapping.put(root+"server/room/entry/RoomCoreRuntimeManager",internal(Manager.class));
        mapping.put(root+"server/compat/vehicle/VehicleCompatFacade",internal(Facade.class));
        mapping.put(root+"server/room/runtime/UnifiedBoundaryService",internal(Rules.class));
        mapping.put(root+"server/room/entry/KilliconFireOwnership",internal(Fire.class));
        var loader=new Loader();
        mapped(loader,nativeService,mapping,null);mapped(loader,nativeService+"$Request",mapping,null);
        var service=loader.loadClass("tests.runtime.BoundaryVehicleDestruction");
        begin=service.getMethod("beginNativeDestruction",Entity.class);finish=service.getMethod("finishNativeDestruction",Entity.class);
        var destroy=service.getMethod("destroy",Entity.class,Room.class,State.class);
        var clear=service.getMethod("clear",Server.class);var tick=service.getMethod("tick",Server.class);
        var server=new Server();Manager.running=new State();var room=new Room();
        var driver=new Player(server);var oldPassenger=new Player(server);var newcomer=new Player(server);
        var vehicle=new Vehicle(server);vehicle.passengers.addAll(List.of(driver,oldPassenger));
        check((boolean)destroy.invoke(null,vehicle,room,Manager.running),"SBW schedules health-zero destruction");
        check(driver.marked==0&&oldPassenger.marked==0,"no snapshot/marks at health assignment");
        check(vehicle.getHealth()==0&&!vehicle.removed,"native deferred destruction, no direct discard");
        vehicle.passengers.remove(oldPassenger);vehicle.passengers.add(newcomer);
        vehicle.explosion=()->{
            check(driver.marked>0&&newcomer.marked>0,"every current passenger marked before blast");
            check(oldPassenger.marked==0,"passenger leaving before blast excluded");
            driver.deployment=true; // Native blast already killed this player; room proxy may be healed.
            vehicle.passengers.clear(); // Native blast ejects survivors before completion.
        };
        vehicle.nativeDestroy();
        check(newcomer.killed==1&&driver.killed==0&&oldPassenger.killed==0,"survivor killed once; completed death not repeated; early exit survives");
        invoke(finish,vehicle);check(newcomer.killed==1,"duplicate destruction callback has no effect");
        var nested=new Entity(server);var seatPassenger=new Player(server);nested.passengers.add(seatPassenger);
        var ywzj=new YwzjVehicle(server);ywzj.passengers.add(nested);
        ywzj.explosion=()->check(seatPassenger.marked>0&&seatPassenger.killed==0,"nested seat passenger marked before synchronous YWZJ blast");
        check((boolean)destroy.invoke(null,ywzj,room,Manager.running),"YWZJ native hurt/explosion path succeeds");
        check(seatPassenger.killed==1,"nested passenger killed after YWZJ blast");
        var normal=new Vehicle(server);var normalPassenger=new Player(server);normal.passengers.add(normalPassenger);normal.nativeDestroy();
        check(normalPassenger.marked==0&&normalPassenger.killed==0,"ordinary combat vehicle destruction unaffected");
        var canceled=new Vehicle(server);var escaped=new Player(server);canceled.passengers.add(escaped);
        destroy.invoke(null,canceled,room,Manager.running);clear.invoke(null,server);canceled.nativeDestroy();
        check(escaped.killed==0,"server cleanup releases pending vehicle context");
        var stale=new Vehicle(server);stale.passengers.add(escaped);destroy.invoke(null,stale,room,Manager.running);
        Manager.running=new State();tick.invoke(null,server);stale.nativeDestroy();check(escaped.killed==0,"room replacement clears stale pending destruction");
        System.out.println("PASS shipped native request lifecycle with server/vehicle fixtures: "+checks);
    }
}
