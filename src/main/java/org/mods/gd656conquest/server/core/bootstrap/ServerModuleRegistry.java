package org.mods.gd656conquest.server.core.bootstrap;

import java.util.List;
import java.util.Objects;

public record ServerModuleRegistry(List<ServerModule> modules) {
    public ServerModuleRegistry {
        Objects.requireNonNull(modules, "modules");
        modules = List.copyOf(modules);
    }
}
