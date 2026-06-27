package org.mods.gd656conquest.server.core;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.mods.gd656conquest.server.core.bootstrap.ServerFrameworkBootstrap;
import org.mods.gd656conquest.server.core.command.ServerCommandEntrypoint;
import org.mods.gd656conquest.server.core.event.ServerEventEntrypoint;
import org.mods.gd656conquest.server.core.lifecycle.ServerLifecycleCoordinator;

public final class ServerCore {
    private static boolean initialized;

    private ServerCore() {
    }

    public static void init(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerLifecycleCoordinator lifecycleCoordinator = ServerFrameworkBootstrap.bootstrapDefault();
        MinecraftForge.EVENT_BUS.register(new ServerEventEntrypoint(lifecycleCoordinator));
        MinecraftForge.EVENT_BUS.register(new ServerCommandEntrypoint(lifecycleCoordinator));
    }
}
