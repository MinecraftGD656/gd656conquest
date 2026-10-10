package tests;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSyncScope;

/** Executes the real replacement and sender methods. Only runtime gates, engine creation,
 * destruction and network transport are replaced; map/counter decisions stay native. */
public final class RealReplacementPipelineTest {
    static final String BASE="com.raiiiden.ragdollified.client.", MANAGER=BASE+"ClientRagdollManager", BODY=BASE+"ClientRagdoll";
    public static final List<Object> SENT=new ArrayList<>();
    private static int checks;
    public static void send(Object packet){SENT.add(packet);}
    public static void send(Object unused,Object packet){SENT.add(packet);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static void set(Object body,String name,Object value)throws Exception{field(body.getClass(),name).set(body,value);}
    public static void initialize(Object body,Object data){
        try{
            int id=field(data.getClass(),"originalEntityId").getInt(data);
            set(body,"originalEntityId",id);set(body,"isPlayer",true);set(body,"playerUUID",new UUID(0,id));
            set(body,"streamPoses",new ArrayDeque<>());set(body,"lastStreamSequence",Integer.MIN_VALUE);
            set(body,"ragdollParts",new ArrayList<>());set(body,"settledGroundSupportBlocks",new HashSet<>());
            set(body,"settledSupportRagdolls",new HashSet<>());snapshot(body,1);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static void snapshot(Object body,int sample)throws Exception{
        Class<?> type=body.getClass().getClassLoader().loadClass(BODY+"$TransformSnapshot");
        Constructor<?> constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Class<?>[] types=constructor.getParameterTypes();Object[] args=new Object[types.length];int ints=0,bools=0;
        for(int i=0;i<types.length;i++){
            if(types[i]==int.class){args[i]=ints++==1?sample:1;}
            else if(types[i]==boolean.class){args[i]=bools++==0;}
            else if(types[i].isArray()){
                Object array=Array.newInstance(types[i].componentType(),1);
                Object value=types[i].componentType()==javax.vecmath.Quat4f.class?new javax.vecmath.Quat4f(0,0,0,1):new javax.vecmath.Vector3f();
                Array.set(array,0,value);args[i]=array;
            }else args[i]=new javax.vecmath.Vector3f();
        }
        set(body,"publishedSnapshot",constructor.newInstance(args));
    }
    static final class Loader extends URLClassLoader{
        final Path transformed;
        Loader(Path path,Path dependency)throws Exception{super(new URL[]{dependency.toUri().toURL()},RealReplacementPipelineTest.class.getClassLoader());transformed=path;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            synchronized(getClassLoadingLock(name)){
                Class<?> type=findLoadedClass(name);
                if(type==null&&name.startsWith("com.raiiiden.ragdollified."))try{type=findClass(name);}catch(ClassNotFoundException error){if(name.equals(MANAGER)||name.equals(BODY))throw error;}
                if(type==null)type=super.loadClass(name,false);if(resolve)resolveClass(type);return type;
            }
        }
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
            if(!name.equals(MANAGER)&&!name.equals(BODY)){
                Path p=transformed.resolve(name.replace('.','/')+".class");
                if(Files.exists(p))try{byte[] b=Files.readAllBytes(p);return defineClass(name,b,0,b.length);}catch(IOException e){throw new ClassNotFoundException(name,e);}
                return super.findClass(name);
            }
            try{
                byte[] bytes=Files.readAllBytes(transformed.resolve(name.replace('.','/')+".class"));
                ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);node.methods.removeIf(m->m.name.equals("<clinit>"));
                for(FieldNode f:node.fields)if((f.access&Opcodes.ACC_STATIC)!=0)f.access&=~Opcodes.ACC_FINAL;
                for(MethodNode method:node.methods){
                    if(name.equals(BODY)){
                        if(method.name.equals("<init>")&&method.desc.startsWith("(Lcom/raiiiden/ragdollified/client/ClientRagdoll$SpawnData;")){
                            reset(method);method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"tests/RealReplacementPipelineTest","initialize","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
                            method.instructions.add(new InsnNode(Opcodes.RETURN));
                        }
                        if(method.name.equals("destroy")){
                            reset(method);method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                            method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,name.replace('.','/'),"destroyed","Z"));method.instructions.add(new InsnNode(Opcodes.RETURN));
                        }
                        if(method.name.equals("consumeStationaryHardSyncRequest")){reset(method);method.instructions.add(new InsnNode(Opcodes.ICONST_0));method.instructions.add(new InsnNode(Opcodes.IRETURN));}
                        if(Set.of("updateCachedTransforms","publishSnapshot","freezeBodies").contains(method.name)){reset(method);method.instructions.add(new InsnNode(Opcodes.RETURN));}
                    }else{
                        if(method.name.equals("isSupportedSpawn")){reset(method);method.instructions.add(new InsnNode(Opcodes.ICONST_1));method.instructions.add(new InsnNode(Opcodes.IRETURN));}
                        if(Set.of("enforceMaxRagdolls","enforceMaxRagdollsPerPlayer").contains(method.name)){reset(method);method.instructions.add(new InsnNode(Opcodes.RETURN));}
                        if(Set.of("tickRagdollStreamClient","tickRagdollSyncClient").contains(method.name)){
                            AbstractInsnNode start=null;
                            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETSTATIC
                                && (f.name.equals("streamOwnership")||f.name.equals("ragdolls")) && i.getNext() instanceof MethodInsnNode call
                                && (call.name.equals("entrySet")||call.name.equals("values"))){start=i;break;}
                            if(start==null)throw new AssertionError("No real sender loop");
                            while(method.instructions.getFirst()!=start)method.instructions.remove(method.instructions.getFirst());
                            method.localVariables=null;
                            for(AbstractInsnNode i:method.instructions.toArray()){
                                if(i instanceof FieldInsnNode f && f.owner.equals("com/raiiiden/ragdollified/network/ModNetwork") && f.name.equals("CHANNEL"))method.instructions.set(i,new InsnNode(Opcodes.ACONST_NULL));
                                if(i instanceof MethodInsnNode call && (call.name.equals("sendToServer")||call.name.contains("traceSentPose")))
                                    method.instructions.set(i,new MethodInsnNode(Opcodes.INVOKESTATIC,"tests/RealReplacementPipelineTest","send","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
                            }
                        }
                        if(method.name.equals("processSpawnQueue"))for(AbstractInsnNode i:method.instructions.toArray()){
                            if(i instanceof FieldInsnNode f && f.owner.equals("com/raiiiden/ragdollified/config/RagdollifiedConfig")){
                                AbstractInsnNode call=i.getNext();
                                if(call instanceof MethodInsnNode m && m.name.equals("get")){
                                    method.instructions.remove(call);method.instructions.set(i,new IntInsnNode(Opcodes.BIPUSH,8));
                                }
                            }
                        }
                    }
                }
                ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
                for(MethodNode m:node.methods)if(m.name.equals("tickRagdollStreamClient"))
                    new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicInterpreter()).analyze(node.name,m);
                writer.visit(node.version,node.access,node.name,node.signature,node.superName,node.interfaces.toArray(String[]::new));
                for(FieldNode f:node.fields)f.accept(writer);
                for(MethodNode m:node.methods)try{m.accept(writer);}catch(RuntimeException e){throw new IllegalStateException("Test isolation method: "+m.name,e);}
                writer.visitEnd();bytes=writer.toByteArray();return defineClass(name,bytes,0,bytes.length);
            }catch(Exception e){throw new ClassNotFoundException(name,e);}
        }
        static void reset(MethodNode method){method.instructions.clear();method.tryCatchBlocks.clear();method.localVariables=null;}
    }
    static Object allocate(Class<?> type)throws Exception{
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field singleton=unsafe.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(singleton.get(null),type);
    }
    @SuppressWarnings("unchecked") static <T> T map(Class<?> manager,String name)throws Exception{return (T)field(manager,name).get(null);}
    static Object spawn(Loader loader,Class<?> manager,int id)throws Exception{
        Class<?> dataType=loader.loadClass(BODY+"$SpawnData");Object data=allocate(dataType);set(data,"originalEntityId",id);set(data,"isPlayer",true);
        ((Map<Integer,Object>)map(manager,"pendingSpawns")).put(id,data);((Queue<Integer>)map(manager,"spawnOrder")).offer(id);
        ((Set<Integer>)map(manager,"coordinatedSpawnIds")).add(id);
        Method spawn=Arrays.stream(manager.getDeclaredMethods()).filter(m->m.getName().equals("processSpawnQueue")).findFirst().orElseThrow();spawn.setAccessible(true);spawn.invoke(null,new Object[]{null});
        return ((Map<?,?>)map(manager,"ragdolls")).get(id);
    }
    public static void main(String[] args)throws Exception{
        boolean fixed=args.length>2&&args[2].equals("fixed");
        try(Loader loader=new Loader(Path.of(args[0]),Path.of(args[1]))){
            Class<?> manager=loader.loadClass(MANAGER);
            for(Field f:manager.getDeclaredFields())if(Modifier.isStatic(f.getModifiers()) && Set.of("ragdolls","streamOwnership",
                    "streamSendSequences","lastStreamSampleTicks","pendingSpawns","spawnOrder","coordinatedSpawnIds",
                    "forcedSpawnIds","persistentRagdollIds","authoritativeStates","pendingStreamPoses","streamPoseQueue",
                    "impulseQueue","severQueue").contains(f.getName())){
                f.setAccessible(true);
                if(Map.class.isAssignableFrom(f.getType()))f.set(null,new ConcurrentHashMap<>());
                else if(Queue.class.isAssignableFrom(f.getType()))f.set(null,new ConcurrentLinkedQueue<>());
                else if(Set.class.isAssignableFrom(f.getType()))f.set(null,ConcurrentHashMap.newKeySet());
            }
            int id=42;manager.getMethod("enqueueStreamOwnership",int.class,boolean.class).invoke(null,id,true);
            Object old=spawn(loader,manager,id);snapshot(old,200);manager.getMethod("tickRagdollStreamClient").invoke(null);
            check(SENT.size()==1,"real old ordinary body sends sample 200");
            Object fresh=spawn(loader,manager,id);check(fresh!=old,"real worker creates a new physical object");
            check((Boolean)old.getClass().getMethod("isDestroyed").invoke(old),"real worker retires old object");
            check(fixed ? ((Map<?,?>)map(manager,"lastStreamSampleTicks")).get(id)==null
                : ((Map<?,?>)map(manager,"lastStreamSampleTicks")).get(id).equals(200),
                fixed ? "ordinary replacement clears sender sample without GD binding" : "ragdoll.8 ordinary replacement retains old sender sample");
            if(fixed){
                ((Map<Integer,Integer>)map(manager,"lastStreamSampleTicks")).put(id,200);
                ((Map<Integer,Integer>)map(manager,"streamSendSequences")).put(id,80);
            }
            for(int i=1;i<=60;i++){snapshot(fresh,i);manager.getMethod("tickRagdollStreamClient").invoke(null);}
            check(SENT.size()==(fixed?61:1),fixed ? "all 60 initial ordinary motion frames send, including after late old-counter write"
                : "real sender suppresses all 60 initial motion samples after ordinary replacement");
            check(fresh.getClass().getMethod("getSnapshot").invoke(fresh)!=null,"self still has a visible new local body");
            set(fresh,"settled",true);manager.getMethod("tickRagdollSyncClient").invoke(null);
            Object terminal=SENT.get(SENT.size()-1);
            check(SENT.size()==(fixed?62:2)&&terminal.getClass().getSimpleName().equals("RagdollStatePacket"),"final state still uses original settlement report path");
            Class<?> dataType=loader.loadClass(BODY+"$SpawnData");Object data=allocate(dataType);set(data,"originalEntityId",id);
            Object observer=allocate(loader.loadClass(BODY));initialize(observer,data);set(observer,"replicated",true);set(observer,"bodiesFrozen",true);
            check(observer.getClass().getMethod("getSnapshot").invoke(observer)==null,"native observer has no visible snapshot before a received pose");
            Object transforms=field(terminal.getClass(),"transforms").get(terminal);
            observer.getClass().getMethod("applyAuthoritativeState",transforms.getClass(),int.class,boolean.class).invoke(observer,transforms,60,true);
            check(observer.getClass().getMethod("getSnapshot").invoke(observer)!=null,"terminal state alone makes native observer visible");
            RagdollSyncScope.beginClient(id,1,Runnable::run);SENT.clear();Object downed=spawn(loader,manager,id);
            check(((Map<?,?>)map(manager,"lastStreamSampleTicks")).get(id)==null,"already-bound GD replacement actually resets native sample counter");
            snapshot(downed,1);manager.getMethod("tickRagdollStreamClient").invoke(null);
            check(SENT.size()==1,"nominal already-bound GD replacement sends its first motion sample");
            if(fixed){
                snapshot(downed,2);manager.getMethod("tickRagdollStreamClient").invoke(null);
                check(SENT.size()==2&&field(SENT.get(1).getClass(),"sequence").getInt(SENT.get(1))==2,
                    "sender identity check keeps consecutive sequence numbers on the same GD body");
            }
            RagdollSyncScope.resetClient();
        }
        System.out.println("PASS real replacement/sender/observer audit "+(fixed?"fixed":"ragdoll.8 baseline")+": "+checks);
    }
}
