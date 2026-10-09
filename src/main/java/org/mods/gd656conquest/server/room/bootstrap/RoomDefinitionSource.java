package org.mods.gd656conquest.server.room.bootstrap;

import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.List;

public interface RoomDefinitionSource {
    List<RoomDefinition> loadAll();
}
