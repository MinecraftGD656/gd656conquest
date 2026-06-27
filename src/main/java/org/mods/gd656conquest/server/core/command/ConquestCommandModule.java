package org.mods.gd656conquest.server.core.command;

import net.minecraftforge.event.RegisterCommandsEvent;
import org.mods.gd656conquest.server.core.bootstrap.ServerModule;

public final class ConquestCommandModule implements ServerModule {
    @Override
    public void registerCommands(RegisterCommandsEvent event) {
        ConquestServerCommands.register(event.getDispatcher());
    }
}
