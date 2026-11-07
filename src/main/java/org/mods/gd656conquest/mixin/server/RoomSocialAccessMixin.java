package org.mods.gd656conquest.mixin.server;

import java.util.Optional;
import org.mods.gd656conquest.server.room.core.RoomCoreService;
import org.mods.gd656conquest.server.room.core.RoomRuntimeRepository;
import org.mods.gd656conquest.server.room.core.RoomRuntimeState;
import org.mods.gd656conquest.social.RoomSocialAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Adds one internal access method while the original room lifecycle methods remain intact. */
@Mixin(value=RoomCoreService.class,remap=false)
public abstract class RoomSocialAccessMixin implements RoomSocialAccess {
    @Shadow(remap=false) @Final private RoomRuntimeRepository<RoomRuntimeState> runtimeRepository;
    @Override public Optional<RoomRuntimeState> socialRuntime(String roomId){return runtimeRepository.findRuntime(roomId);}
}
