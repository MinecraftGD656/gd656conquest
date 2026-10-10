package org.mods.gd656conquest.mixin.client;

import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the actual playback channel instead of the engine's delayed sound bookkeeping. */
@Mixin(SoundEngine.class)
public interface AmmoSupplySoundAccessor {
    @Accessor("instanceToChannel")
    Map<SoundInstance,ChannelAccess.ChannelHandle> gd656$ammoSoundChannels();
}
