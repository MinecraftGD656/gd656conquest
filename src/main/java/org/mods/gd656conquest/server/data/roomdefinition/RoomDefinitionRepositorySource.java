package org.mods.gd656conquest.server.data.roomdefinition;

import org.mods.gd656conquest.server.room.bootstrap.RoomDefinitionSource;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.List;
import java.util.Objects;

public final class RoomDefinitionRepositorySource implements RoomDefinitionSource {
    private final RoomDefinitionRepository roomDefinitionRepository;

    public RoomDefinitionRepositorySource(RoomDefinitionRepository roomDefinitionRepository) {
        this.roomDefinitionRepository = Objects.requireNonNull(roomDefinitionRepository, "roomDefinitionRepository");
    }

    @Override
    public List<RoomDefinition> loadAll() {
        return roomDefinitionRepository.getAllDefinitions();
    }
}
