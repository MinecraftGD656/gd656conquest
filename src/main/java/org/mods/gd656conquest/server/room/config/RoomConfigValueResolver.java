package org.mods.gd656conquest.server.room.config;

import org.mods.gd656conquest.server.room.model.RoomDefinition;

public final class RoomConfigValueResolver {
    private RoomConfigValueResolver() {
    }

    public static Object resolve(RoomDefinition definition, RoomConfigDescriptor descriptor) {
        if (definition == null || descriptor == null) {
            return null;
        }
        return RoomConfigBindingAccess.resolve(definition, descriptor.newPath());
    }
}
