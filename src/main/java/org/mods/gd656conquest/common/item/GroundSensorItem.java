package org.mods.gd656conquest.common.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

public class GroundSensorItem extends ConquestGeoItem {
    public GroundSensorItem(Properties properties, ResourceLocation geoModel, ResourceLocation texture, ResourceLocation animation) {
        super(properties, geoModel, texture, animation);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(serverPlayer.server);
        boolean used = coreManager.findPlayerRoomId(serverPlayer.getUUID()).isPresent()
            && coreManager.tryUseGroundSensorItem(serverPlayer, hand);
        if (used) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(serverPlayer.server);
        boolean used = coreManager.findPlayerRoomId(serverPlayer.getUUID()).isPresent()
            && coreManager.tryUseGroundSensorItem(serverPlayer, context.getHand());
        return used ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
