package org.mods.gd656conquest.server.room.config;

public enum RoomConfigGroupOperationMode {
    NONE,
    LIST_ONLY,
    ADD_DEL_LIST;

    public boolean supportsList() {
        return this == LIST_ONLY || this == ADD_DEL_LIST;
    }

    public boolean supportsMutation() {
        return this == ADD_DEL_LIST;
    }
}
