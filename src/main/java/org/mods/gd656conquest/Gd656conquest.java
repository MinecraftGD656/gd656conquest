package org.mods.gd656conquest;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.mods.gd656conquest.client.core.ClientCore;
import org.mods.gd656conquest.common.block.ConquestBlocks;
import org.mods.gd656conquest.common.entity.ConquestEntities;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.server.core.ServerCore;
import software.bernie.geckolib.GeckoLib;

@Mod(Gd656conquest.MODID)
public class Gd656conquest {
    public static final String MODID = "gd656conquest";

    @SuppressWarnings("removal")
    public Gd656conquest() {
        GeckoLib.initialize();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        ConquestBlocks.register(modEventBus);
        ConquestEntities.register(modEventBus);
        ConquestItems.register(modEventBus);

        NetworkManager.init();
        ServerCore.init(modEventBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCore.init(modEventBus));
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
    }
}
