package org.mods.gd656conquest.server.core.command;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.mods.gd656conquest.server.core.lifecycle.ServerLifecycleCoordinator;

import java.util.Objects;

public final class ServerCommandEntrypoint {
    private final ServerLifecycleCoordinator lifecycleCoordinator;

    public ServerCommandEntrypoint(ServerLifecycleCoordinator lifecycleCoordinator) {
        this.lifecycleCoordinator = Objects.requireNonNull(lifecycleCoordinator, "lifecycleCoordinator");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        lifecycleCoordinator.registerCommands(event);
    }
}
