package tests;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.service.MixinService;

/** Transform authentic vehicle dependencies; verify snapshots precede damage/ejection. */
public final class RealBoundaryVehicleMixinTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("isolation-boundary-vehicles.mixins.json");
        IMixinTransformer transformer=((IsolationMixinService)MixinService.getService()).factory().factory().createTransformer();
        for(String name:List.of("org.ywzj.vehicle.entity.vehicle.AbstractVehicle","com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity")) {
            byte[] original;
            try(var in=RealBoundaryVehicleMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){original=in.readAllBytes();}
            byte[] changed=transformer.transformClassBytes(name,name,original);
            if(Boolean.getBoolean("gd.isolation.vehiclesAbsent")) {
                check(Arrays.equals(original,changed),"optional absent vehicle mixin excluded");continue;
            }
            var type=new ClassNode();new ClassReader(changed).accept(type,0);
            boolean ywzj=name.startsWith("org.ywzj");
            var method=type.methods.stream().filter(m->m.name.equals(ywzj?"m_6469_":"destroy")).findFirst().orElseThrow();
            int begin=-1,finish=-1,first=-1,last=-1,index=0;
            for(var instruction:method.instructions) {
                if(instruction instanceof MethodInsnNode call) {
                    if(call.name.contains("beginBoundaryExplosion"))begin=index;
                    if(call.name.contains("finishBoundaryExplosion"))finish=index;
                    if(ywzj?call.owner.equals("org/ywzj/vehicle/util/VehicleExplosion")&&call.name.equals("explode")
                        :Set.of("crashPassengers","explodePassengers","explode","m_146870_").contains(call.name)) {
                        if(first<0)first=index;last=index;
                    }
                }
                index++;
            }
            check(begin>=0&&begin<first,"snapshot and deserter markers precede first native passenger damage");
            check(finish>last,"survivor cleanup follows native explosion/ejection/removal");
            check(type.methods.stream().anyMatch(m->m.instructions.iterator().hasNext()
                &&Arrays.stream(m.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode c&&c.name.equals("beginNativeDestruction"))),"real service entry wired");
        }
        System.out.println("PASS authentic YWZJ/Superb Warfare native explosion hooks / optionalAbsent="+Boolean.getBoolean("gd.isolation.vehiclesAbsent"));
    }
}
