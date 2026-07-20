package org.mods.gd656conquest.mixin.compat.lrtactical;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.mods.gd656conquest.client.compat.lrtactical.LrThrowableOwnerSyncAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Pseudo
@Mixin(targets = "me.xjqsh.lrtactical.entity.ThrowableItemEntity", remap = false)
public abstract class ThrowableItemEntityMixin implements LrThrowableOwnerSyncAccess {
    @Unique
    private UUID gd656conquest$syncedOwnerUuid;

    @Inject(method = "writeSpawnData", at = @At("TAIL"), require = 0, remap = false)
    private void gd656conquest$appendOwnerUuid(FriendlyByteBuf buffer, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        UUID ownerUuid = null;
        if (entity instanceof Projectile projectile && projectile.getOwner() != null) {
            ownerUuid = projectile.getOwner().getUUID();
        }
        buffer.writeBoolean(ownerUuid != null);
        if (ownerUuid != null) {
            buffer.writeUUID(ownerUuid);
        }
    }

    @Inject(method = "readSpawnData", at = @At("TAIL"), require = 0, remap = false)
    private void gd656conquest$readOwnerUuid(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!buffer.readBoolean()) {
            gd656conquest$syncedOwnerUuid = null;
            return;
        }
        gd656conquest$syncedOwnerUuid = buffer.readUUID();
    }

    @Override
    public UUID gd656conquest$getSyncedOwnerUuid() {
        return gd656conquest$syncedOwnerUuid;
    }
}
