package org.mods.gd656conquest.client.room;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoundaryHudEvents {
    private BoundaryHudEvents() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { ClientBoundaryState.tick(); ClientRetreatState.tick(); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void overlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && ClientBoundaryState.hidePlayerCrosshair())
            event.setCanceled(true);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) { ClientBoundaryState.renderWarning(event.getGuiGraphics()); ClientRetreatState.renderWarning(event.getGuiGraphics()); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientBoundaryState.reset(); ClientRetreatState.reset(); }
}
