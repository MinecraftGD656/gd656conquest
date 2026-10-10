package tests;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

/** Protect the previous release, and verify necessary original-source hooks in the actual JAR. */
public final class AmmoReserveScopeTest {
    static int checks;
    static final String ROOT="org/mods/gd656conquest/", RUNTIME=ROOT+"server/room/runtime/", SUPPORT=ROOT+"server/support/";
    static final Set<String> ORIGINAL=Set.of("RoomLoadoutGrantService", "RoomPlayerLoadoutService");
    static final Set<String> NEW=Set.of("SupportAmmoGrant", "SupportAmmoGrant$Slots", "SupportAmmoGrant$1", "SupportAmmo", "SupportAmmo$Target", "SupportAmmo$Tacz", "SupportAmmoRefill");
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static Map<String,byte[]> entries(String file)throws Exception {
        Map<String,byte[]> result=new TreeMap<>();
        try(var zip=new ZipFile(file)){for(var e:Collections.list(zip.entries()))if(!e.isDirectory())result.put(e.getName(),zip.getInputStream(e).readAllBytes());}return result;
    }
    static ClassNode read(byte[] bytes){var node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return node;}
    static String trace(ClassNode node){var text=new StringWriter();node.accept(new TraceClassVisitor(new PrintWriter(text)));return text.toString();}
    static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    static int calls(MethodNode method,String owner,String name){int n=0;for(var i:method.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))n++;return n;}
    static int index(MethodNode method,String owner,String name){for(var i:method.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))return method.instructions.indexOf(c);return -1;}
    static void normalize(ClassNode node){node.innerClasses.sort(Comparator.comparing(i->i.name));for(var m:node.methods){m.maxStack=0;m.maxLocals=0;}}
    public static void main(String[] args)throws Exception {
        var prior=entries(args[0]);var current=entries(args[1]);
        for(var e:prior.entrySet()) {
            String name=e.getKey();check(current.containsKey(name),"retained entry: "+name);
            if(name.equals("META-INF/MANIFEST.MF")||name.equals("META-INF/mods.toml"))continue;
            String owner=name.endsWith(".class")?name.substring(0,name.length()-6):"";
            String simple=owner.startsWith(RUNTIME)?owner.substring(RUNTIME.length()):"";
            String outer=simple.split("\\$",2)[0];
            if(ORIGINAL.contains(outer)) {
                var old=read(e.getValue());var next=read(current.get(name));
                if(simple.equals("RoomLoadoutGrantService")) {
                    var grant=method(next,"grantConfiguredWeaponAmmoByExtraData");
                    check(calls(grant,SUPPORT+"SupportAmmoGrant","grant")==1,"initial grant delegates once");
                    old.methods.removeIf(m->Set.of("grantConfiguredWeaponAmmoByExtraData","addTaczAmmoToPreferredSlots").contains(m.name));
                    next.methods.removeIf(m->Set.of("grantConfiguredWeaponAmmoByExtraData","addTaczAmmoToPreferredSlots").contains(m.name));
                }
                if(simple.equals("RoomPlayerLoadoutService")) {
                    var deploy=method(next,"applyLoadout");
                    check(calls(deploy,SUPPORT+"SupportAmmoGrant","finishLoadout")==1,"one final cap reconciliation");
                    int optional=index(deploy,RUNTIME+"RoomLoadoutGrantService","applyOptionalRoomItems");
                    int saved=index(deploy,ROOT+"server/data/playerdata/PlayerDataStore","savePlayerData");
                    int synced=index(deploy,"net/minecraft/world/entity/player/Inventory","m_6596_");
                    int finalCap=index(deploy,SUPPORT+"SupportAmmoGrant","finishLoadout");
                    check(optional>=0&&finalCap>optional,"final caps follow optional equipment placement");
                    check(saved>=0&&finalCap>saved,"final caps follow selection persistence");
                    check(synced>finalCap,"final caps precede final inventory synchronization");
                    var finish=Arrays.stream(deploy.instructions.toArray()).filter(i->i instanceof MethodInsnNode c&&c.owner.equals(SUPPORT+"SupportAmmoGrant")&&c.name.equals("finishLoadout")).findFirst().orElseThrow();
                    var player=finish.getPrevious();check(player instanceof VarInsnNode v&&v.getOpcode()==Opcodes.ALOAD&&v.var==4,"final caps use deployed player");
                    deploy.instructions.remove(player);deploy.instructions.remove(finish);
                    MethodNode box=method(next,"tryRefillLoadoutByAmmoBox"),bag=method(next,"trySupplyAmmoByBag");
                    check(calls(box,SUPPORT+"SupportAmmoRefill","box")==1&&calls(box,RUNTIME+"RoomPlayerLoadoutService","applyLoadout")==0,"box refills reserves without replacing deployed weapons");
                    check(calls(bag,SUPPORT+"SupportAmmoRefill","bag")==1,"bag delegates to bounded targets");
                    old.methods.removeIf(m->Set.of("tryRefillLoadoutByAmmoBox","trySupplyAmmoByBag").contains(m.name));
                    next.methods.removeIf(m->Set.of("tryRefillLoadoutByAmmoBox","trySupplyAmmoByBag").contains(m.name));
                }
                normalize(old);normalize(next);
                if(!trace(old).equals(trace(next))) {
                    var diagnostics=Path.of("build/migration-verification/ammo-reserves");
                    Files.writeString(diagnostics.resolve("scope-prior.txt"),trace(old));
                    Files.writeString(diagnostics.resolve("scope-current.txt"),trace(next));
                }
                check(trace(old).equals(trace(next)),"all remaining original code/fields/annotations preserved: "+name);
            } else check(Arrays.equals(e.getValue(),current.get(name)),"unchanged production class/resource: "+name);
        }
        for(var name:current.keySet())if(!prior.containsKey(name)) {
            check(name.startsWith(SUPPORT)&&name.endsWith(".class")&&NEW.contains(name.substring(SUPPORT.length(),name.length()-6)),"only intended new production classes: "+name);
        }
        for(var name:NEW){byte[] bytes=current.get(SUPPORT+name+".class");check(bytes!=null,"new class exists: "+name);check(new ClassReader(bytes).readUnsignedShort(6)==61,"Java 17: "+name);}
        for(var name:current.keySet())check(!name.startsWith("tests/")&&!name.startsWith("tools/")&&!name.startsWith("com/tacz/")&&!name.startsWith("com/atsuishio/")&&!name.startsWith("net/minecraft/"),"no fixture or third-party class packaged: "+name);
        String mods=new String(current.get("META-INF/mods.toml"),java.nio.charset.StandardCharsets.UTF_8);
        check(mods.contains("0.9.3-bf1-0.0.9"),"packaged migration version");
        String manifest=new String(current.get("META-INF/MANIFEST.MF"),java.nio.charset.StandardCharsets.UTF_8);
        check(manifest.contains("0.9.3-bf1-0.0.9"),"manifest migration version");
        System.out.println("PASS 0.0.8 -> 0.0.9 production scope/resources/registration/original hooks: "+checks);
    }
}
