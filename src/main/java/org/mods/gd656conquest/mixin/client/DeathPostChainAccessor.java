package org.mods.gd656conquest.mixin.client;

import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PostChain.class)
public interface DeathPostChainAccessor {
    @Accessor("passes") List<PostPass> gd656conquest$passes();
}
