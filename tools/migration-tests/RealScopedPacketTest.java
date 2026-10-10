package tests;

import io.netty.buffer.Unpooled;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import org.mods.gd656conquest.server.compat.ragdoll.*;

/** Executes real Mixin-transformed dependency classes and native packet codecs. */
public final class RealScopedPacketTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static final class DependencyLoader extends URLClassLoader {
        DependencyLoader(URL[] urls) { super(urls, RealScopedPacketTest.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null && name.startsWith("com.raiiiden.ragdollified.")) {
                    try { type = findClass(name); } catch (ClassNotFoundException ignored) {}
                }
                if (type == null) type = super.loadClass(name, false);
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
    private static Object makePacket(ClassLoader loader, String kind, int id) throws Exception {
        Class<?> packet = loader.loadClass("com.raiiiden.ragdollified.network.Ragdoll"+kind+"Packet");
        if (kind.equals("StreamOwner")) return packet.getConstructor(int.class, boolean.class).newInstance(id, true);
        if (kind.equals("Impulse")) return packet.getConstructor(int.class,int.class,float.class,float.class,float.class).newInstance(id,-3,1F,2F,3F);
        if (kind.equals("Sever")) {
            Constructor<?> constructor=packet.getDeclaredConstructor(int.class,byte.class,int.class,int.class,float.class,float.class,float.class);
            constructor.setAccessible(true);return constructor.newInstance(id,(byte)1,99,200,1F,2F,3F);
        }
        Class<?> transform = loader.loadClass("com.raiiiden.ragdollified.RagdollTransform");
        Class<?> transforms = Array.newInstance(transform, 0).getClass();
        Object pose = Array.newInstance(transform, 10);
        Array.set(pose, 0, transform.getConstructor(int.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class)
            .newInstance(0,1F,2F,3F,0F,0F,0F,1F));
        if (kind.equals("Stream")) return packet.getConstructor(int.class,int.class,int.class,boolean.class,transforms)
            .newInstance(id,1,1,false,pose);
        return packet.getConstructor(int.class,transforms,int.class,int.class,boolean.class).newInstance(id,pose,0,0,true);
    }
    private static void codecs(ClassLoader loader) throws Exception {
        int id = 42;
        RagdollSyncScope.beginClient(id, 9876543210123L, Runnable::run);
        RagdollSyncScope.commitSpawn(id, new Object());
        for (String kind : List.of("StreamOwner", "Stream", "State", "Impulse", "Sever")) {
            Object packet = makePacket(loader, kind, id);
            Class<?> type = packet.getClass();
            check(((RagdollPacketScope)packet).gd656conquest$generation() == 9876543210123L, "real constructor tags " + kind);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                type.getMethod("encode", type, FriendlyByteBuf.class).invoke(null, packet, buffer);
                int length = buffer.readableBytes();
                Object decoded = type.getMethod("decode", FriendlyByteBuf.class).invoke(null, buffer);
                check(((RagdollPacketScope)decoded).gd656conquest$generation() == 9876543210123L, "64-bit generation codec " + kind);
                check(buffer.readableBytes() == 0, "complete extended packet consumed " + kind);
                if (kind.equals("Stream") || kind.equals("State")) {
                    Field field = type.getDeclaredField("transforms"); field.setAccessible(true);
                    Object decodedPart = Array.get(field.get(decoded), 0);
                    Object position = decodedPart.getClass().getField("position").get(decodedPart);
                    check(position.getClass().getField("x").getFloat(position) == 1F,
                        "native pose payload survives extended " + kind + " codec");
                }
                buffer.setIndex(0, length - 8);
                Object legacy = type.getMethod("decode", FriendlyByteBuf.class).invoke(null, buffer);
                check(((RagdollPacketScope)legacy).gd656conquest$generation() == 0, "untagged packet defaults to ordinary " + kind);
            } finally { buffer.release(); }
        }
        RagdollSyncScope.resetClient();
    }
    private static void playback(ClassLoader loader) throws Exception {
        Class<?> type = loader.loadClass("com.raiiiden.ragdollified.client.RenderPlayback");
        Class<?> segment = loader.loadClass("com.raiiiden.ragdollified.client.RenderPlayback$Segment");
        Object pose = java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{segment},(proxy,method,args) -> 1);
        Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        Object playback=constructor.newInstance();
        Method offer=type.getDeclaredMethod("offer",segment), advance=type.getDeclaredMethod("advance",long.class), phase=type.getDeclaredMethod("phase");
        for(Method method:List.of(offer,advance,phase))method.setAccessible(true);
        offer.invoke(playback,pose);advance.invoke(playback,0L);
        advance.invoke(playback,50_000_000L);check((Float)phase.invoke(playback)==1F,"completed segment reaches final pose");
        for(long millis:new long[]{201,251,402,1000,10000}){
            check(advance.invoke(playback,millis*1_000_000L)==pose,"stale snapshot remains same object at "+millis);
            check((Float)phase.invoke(playback)==1F,"completed interpolation never rewinds at "+millis);
        }
        Object successor=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{segment},(proxy,method,args)->1);
        offer.invoke(playback,successor);
        check(advance.invoke(playback,10_001_000_000L)==successor,"new snapshot resumes playback");
        check((Float)phase.invoke(playback)==0F,"new snapshot starts interpolation");
        advance.invoke(playback,10_051_000_000L);check((Float)phase.invoke(playback)==1F,"new snapshot completes");
    }
    public static void main(String[] args) throws Exception {
        try(DependencyLoader loader=new DependencyLoader(new URL[]{Path.of(args[0]).toUri().toURL(),Path.of(args[1]).toUri().toURL()})){
            codecs(loader);playback(loader);
            for(String kind:List.of("Spawn","Stream","State","StreamOwner","Impulse","Sever")){
                Class<?> packet=loader.loadClass("com.raiiiden.ragdollified.network.Ragdoll"+kind+"Packet");
                packet.getDeclaredMethods();packet.getDeclaredFields();check(RagdollPacketScope.class.isAssignableFrom(packet),"transformed packet loads: "+kind);
            }
            Class<?> manager=loader.loadClass("com.raiiiden.ragdollified.client.ClientRagdollManager");
            manager.getDeclaredMethods();manager.getDeclaredFields();check(true,"transformed worker/transmission methods pass JVM verification");
        }
        System.out.println("PASS real transformed packet codecs / playback / class loading: "+checks);
    }
}
