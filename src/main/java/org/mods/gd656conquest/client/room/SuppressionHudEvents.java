package org.mods.gd656conquest.client.room;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SuppressionHudEvents {
    private SuppressionHudEvents(){}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){ClientSuppressionState.reset();}
}
