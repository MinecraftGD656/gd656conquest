package org.mods.gd656conquest.mixin.compat.tacz;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo @Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public interface KineticBulletSupportAccessor { @Accessor("pierce") int gd656$pierce(); }
