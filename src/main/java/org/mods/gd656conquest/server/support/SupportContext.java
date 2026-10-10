package org.mods.gd656conquest.server.support;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.*;
import java.util.UUID;

public record SupportContext(RoomDefinition room, RunningRuntimeState state, RoomTeamId camp) {
    public static SupportContext of(ServerPlayer p) {
        if (p == null || p.server == null) return null;
        var manager=RoomCoreRuntimeManager.of(p.server);
        String id=manager.findPlayerRoomId(p.getUUID()).orElse(null);
        if (id==null) return null;
        var room=manager.findRoom(id).orElse(null); var state=manager.findRunningState(id).orElse(null);
        if (room==null || state==null || room.lifecycleState()!=RoomLifecycleState.RUNNING) return null;
        return new SupportContext(room,state,state.getRoomTeams().get(p.getUUID()));
    }
    public static boolean available(ServerPlayer p) {
        var c=of(p);
        return c!=null && c.camp()!=null && p.isAlive() && !p.isSpectator()
            && !c.state().isInRespawnFlow(p.getUUID())
            && !RoomCoreRuntimeManager.of(p.server).isPlayerDowned(p.getUUID());
    }
    public static void stamp(Entity e, ServerPlayer owner) {
        var c=of(owner); if(c==null)return;
        var d=e.getPersistentData();
        d.putString("gd656_support_room",c.room().roomId());
        d.putString("gd656_support_camp",c.camp()==null ? "" : c.camp().name());
        d.putUUID("gd656_support_owner",owner.getUUID());
    }
    public static boolean allowed(ServerPlayer p, Entity source, UUID owner, boolean permanent) {
        var c=of(p); if(c==null || !available(p))return false;
        var d=source.getPersistentData();
        String room=d.getString("gd656_support_room");
        if(!room.isEmpty() && !room.equals(c.room().roomId()))return false;
        if(permanent)return true;
        if(owner==null)return false;
        if(room.isEmpty())return false; // Unattributed deployed supplies must not cross rooms.
        return owner.equals(p.getUUID()) || c.room().commonConfig().allowEnemySupportResupply()
            || c.camp().name().equals(d.getString("gd656_support_camp"));
    }
}
