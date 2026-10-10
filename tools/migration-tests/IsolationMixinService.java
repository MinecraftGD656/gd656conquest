package tests;

import java.io.*;
import java.net.URL;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.*;

/** Minimal launcher for applying the real Mixin engine to authentic dependency bytecode. */
public final class IsolationMixinService extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    public String getName() { return "GDIsolation"; }
    public boolean isValid() { return true; }
    public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
    public IClassProvider getClassProvider() { return this; }
    public IClassBytecodeProvider getBytecodeProvider() { return this; }
    public ITransformerProvider getTransformerProvider() { return this; }
    public IClassTracker getClassTracker() { return this; }
    public IMixinAuditTrail getAuditTrail() { return null; }
    public Collection<String> getPlatformAgents() { return List.of(); }
    public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("GDIsolation"); }
    public InputStream getResourceAsStream(String name) {
        if (Boolean.getBoolean("gd.isolation.vehiclesAbsent") && (name.startsWith("org/ywzj/vehicle/") || name.startsWith("com/atsuishio/superbwarfare/"))) return null;
        if (Boolean.getBoolean("gd.isolation.absent") && (name.startsWith("com/raiiiden/ragdollified/") || name.startsWith("com/tacz/guns/"))) return null;
        return getClass().getClassLoader().getResourceAsStream(name);
    }
    public URL[] getClassPath() { return new URL[0]; }
    public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        if (Boolean.getBoolean("gd.isolation.vehiclesAbsent") && (name.startsWith("org.ywzj.vehicle.") || name.startsWith("com.atsuishio.superbwarfare."))) throw new ClassNotFoundException(name);
        if (Boolean.getBoolean("gd.isolation.absent") && (name.startsWith("com.raiiiden.ragdollified.") || name.startsWith("com.tacz.guns."))) throw new ClassNotFoundException(name);
        return Class.forName(name, initialize, getClass().getClassLoader());
    }
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
    public ClassNode getClassNode(String name) throws IOException, ClassNotFoundException { return getClassNode(name, false); }
    public ClassNode getClassNode(String name, boolean transform) throws IOException, ClassNotFoundException {
        try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) throw new ClassNotFoundException(name);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.EXPAND_FRAMES); return node;
        }
    }
    public Collection<ITransformer> getTransformers() { return List.of(); }
    public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
    public void addTransformerExclusion(String name) {}
    public void registerInvalidClass(String name) {}
    public boolean isClassLoaded(String name) { return false; }
    public String getClassRestrictions(String name) { return ""; }
    public IMixinTransformerFactoryAccess factory() { return new IMixinTransformerFactoryAccess(getInternal(org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory.class)); }
    public record IMixinTransformerFactoryAccess(org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory factory) {}
}
