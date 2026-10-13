package org.mods.gd656conquest.client.audio;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="gd656conquest", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class OperationMusicResources {
    private static final Map<String, CompletableFuture<Double>> durations = new ConcurrentHashMap<>();
    private OperationMusicResources() {}
    static boolean playable(String id) {
        var mc = Minecraft.getInstance();
        if (mc.getSoundManager().getSoundEvent(ResourceLocation.parse(id)) == null) return false;
        var probe = new TimelineSound(id, 1, 0, 1);
        probe.resolve(mc.getSoundManager());
        var definition = probe.getSound();
        return definition != net.minecraft.client.sounds.SoundManager.EMPTY_SOUND
            && definition != net.minecraft.client.sounds.SoundManager.INTENTIONALLY_EMPTY_SOUND
            && mc.getResourceManager().getResource(definition.getPath()).isPresent();
    }
    static CompletableFuture<Double> duration(String id) {
        return durations.computeIfAbsent(id, key -> TimelineAudioDuration.readTicks(key).thenApply(ticks -> ticks / 20.0));
    }
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> durations.clear());
    }
}
