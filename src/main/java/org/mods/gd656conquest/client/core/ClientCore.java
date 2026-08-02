package org.mods.gd656conquest.client.core;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.ModList;
import net.minecraft.resources.ResourceLocation;
import org.mods.gd656conquest.Gd656conquest;
import org.mods.gd656conquest.client.animation.ClientPlayerAnimatorDebugState;
import org.mods.gd656conquest.client.command.ClientCommandRegistrar;
import org.mods.gd656conquest.client.render.entity.AmmoBoxEntityRenderer;
import org.mods.gd656conquest.client.render.entity.GroundSensorEntityRenderer;
import org.mods.gd656conquest.client.render.entity.MedicalBoxEntityRenderer;
import org.mods.gd656conquest.client.event.listener.ClientEventListener;
import org.mods.gd656conquest.client.event.listener.ClientHudVisibilityCycleListener;
import org.mods.gd656conquest.client.hud.ClientBaseEffectsOverlay;
import org.mods.gd656conquest.client.hud.ClientMainHudOverlay;
import org.mods.gd656conquest.client.input.ClientKeyMappings;
import org.mods.gd656conquest.client.loadout.screen.LoadoutConfigScreen;
import org.mods.gd656conquest.client.log.ClientLogger;
import org.mods.gd656conquest.client.render.entity.SquadDeployBeaconEntityRenderer;
import org.mods.gd656conquest.common.entity.ConquestEntities;

public final class ClientCore {
    private static boolean initialized;

    private ClientCore() {
    }

    public static void init(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        modEventBus.addListener(ClientCore::onClientSetup);
        modEventBus.addListener(ClientCore::onRegisterRenderers);
        modEventBus.addListener(ClientCore::onRegisterKeyMappings);
        modEventBus.addListener(ClientCore::onRegisterGuiOverlays);
        MinecraftForge.EVENT_BUS.register(new ClientEventListener());
        MinecraftForge.EVENT_BUS.register(new ClientHudVisibilityCycleListener());
        MinecraftForge.EVENT_BUS.register(new ClientCommandRegistrar());
        ClientLogger.success("客户端核心框架已初始化");
    }

    @SuppressWarnings("removal")
    private static void onClientSetup(FMLClientSetupEvent event) {
        ClientPlayerAnimatorDebugState.init();
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new LoadoutConfigScreen(parent))
        );
        ClientLogger.info("客户端生命周期已进入 setup 阶段");
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ConquestEntities.GROUND_SENSOR.get(), GroundSensorEntityRenderer::new);
        event.registerEntityRenderer(ConquestEntities.SQUAD_DEPLOY_BEACON.get(), SquadDeployBeaconEntityRenderer::new);
        event.registerEntityRenderer(ConquestEntities.MEDICAL_BOX.get(), MedicalBoxEntityRenderer::new);
        event.registerEntityRenderer(ConquestEntities.AMMO_BOX.get(), AmmoBoxEntityRenderer::new);
        event.registerEntityRenderer(ConquestEntities.SHELLING_SMOKE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ClientKeyMappings.register(event);
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        String baseOverlayId = Gd656conquest.MODID + "_" + ClientBaseEffectsOverlay.ID;
        String mainOverlayId = Gd656conquest.MODID + "_" + ClientMainHudOverlay.ID;
        if (ModList.get().isLoaded("superbwarfare")) {
            ResourceLocation killMessage = new ResourceLocation("superbwarfare", "superbwarfare_kill_message");
            event.registerBelow(killMessage, baseOverlayId, new ClientBaseEffectsOverlay());
            event.registerAbove(new ResourceLocation(Gd656conquest.MODID, baseOverlayId), mainOverlayId, new ClientMainHudOverlay());
            return;
        }
        event.registerBelowAll(baseOverlayId, new ClientBaseEffectsOverlay());
        event.registerAbove(new ResourceLocation(Gd656conquest.MODID, baseOverlayId), mainOverlayId, new ClientMainHudOverlay());
    }
}
