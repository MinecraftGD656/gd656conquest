package org.mods.gd656conquest.client.event.listener;

import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.mods.gd656conquest.client.event.handler.ClientEventHandler;

public final class ClientEventListener {
    private final ClientEventHandler eventHandler = ClientEventHandler.getInstance();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        eventHandler.handleClientTick(event);
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiOverlayEvent.Post event) {
        boolean isTargetOverlay = event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id());
        eventHandler.handleHudRender(event, isTargetOverlay);
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        eventHandler.handleRenderHand(event);
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        eventHandler.handleClientLogout(event);
    }

    @SubscribeEvent
    public void onClientDimensionChange(ClientPlayerNetworkEvent.Clone event) {
        eventHandler.handleClientDimensionChange(event);
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        eventHandler.handleRenderLevel(event);
    }

    @SubscribeEvent
    public void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        eventHandler.handleCameraSetup(event);
    }

    @SubscribeEvent
    public void onBlockHighlight(RenderHighlightEvent.Block event) {
        eventHandler.handleBlockHighlight(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyInput(InputEvent.Key event) {
        eventHandler.handleClientKeyInput(event);
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseButton event) {
        eventHandler.handleMouseInput(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        eventHandler.handleMouseScroll(event);
    }

    @SubscribeEvent
    public void onMovementInput(MovementInputUpdateEvent event) {
        eventHandler.handleMovementInput(event);
    }

    @SubscribeEvent
    public void onEntitySize(EntityEvent.Size event) {
        eventHandler.handleEntitySize(event);
    }

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        eventHandler.handleScreenOpening(event);
    }

    @SubscribeEvent
    public void onScreenCharTyped(ScreenEvent.CharacterTyped event) {
        eventHandler.handleScreenCharTyped(event);
    }
}
