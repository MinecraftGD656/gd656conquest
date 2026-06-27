package org.mods.gd656conquest.mixin.compat.ywzjvehicle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.ywzj.vehicle.item.RepairToolItem;

@Mixin(RepairToolItem.class)
public abstract class RepairToolItemMixin {
    @ModifyConstant(method = "onUseTick", constant = @Constant(floatValue = 2.0F, ordinal = 0))
    private float gd656conquest$increaseVehicleRepairPerTick(float original) {
        return 6.0F;
    }
}
