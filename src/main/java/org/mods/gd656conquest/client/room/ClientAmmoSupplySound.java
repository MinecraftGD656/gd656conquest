package org.mods.gd656conquest.client.room;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.mixin.client.AmmoSupplySoundAccessor;

/** One playback per cue per listener; ammo and healing have independent playback state. */
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAmmoSupplySound {
    private static SoundEngine engine;
    private static final Map<String,SoundInstance> playing=new HashMap<>();
    private static final Map<String,Long> acceptedAt=new HashMap<>();
    private ClientAmmoSupplySound() {}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void play(PlaySoundEvent event) { filter(event,System.nanoTime()); }

    private static void filter(PlaySoundEvent event,long now) {
        SoundInstance next=event.getSound();
        if(next==null)return;
        String cue=next.getLocation().toString();
        if(!"gd656conquest:sfx.ammo_bag_pickup".equals(cue) && !"gd656conquest:sfx.player_heal".equals(cue))return;
        if(engine!=event.getEngine()) {reset();engine=event.getEngine();}
        SoundInstance current=playing.get(cue);
        if(current!=null) {
            var handle=((AmmoSupplySoundAccessor)engine).gd656$ammoSoundChannels().get(current);
            boolean busy=handle!=null ? !handle.isStopped() : engine.isActive(current);
            // Cover the short gap before an accepted sound obtains its playback channel.
            if(busy || now-acceptedAt.get(cue)<50_000_000L) {event.setSound(null);return;}
        }
        playing.put(cue,next);acceptedAt.put(cue,now);
    }

    public static void reset() {engine=null;playing.clear();acceptedAt.clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {reset();}
    @SubscribeEvent public static void reload(SoundEngineLoadEvent event) {reset();}
}
