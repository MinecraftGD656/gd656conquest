package org.mods.gd656conquest.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface DownedCameraAccessor {
    @Invoker("setPosition") void gd656conquest$setPosition(Vec3 position);
    @Invoker("setRotation") void gd656conquest$setRotation(float yaw, float pitch);
    @Accessor("detached") void gd656conquest$setDetached(boolean detached);
}
