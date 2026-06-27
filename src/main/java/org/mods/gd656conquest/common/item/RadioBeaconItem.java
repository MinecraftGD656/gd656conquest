package org.mods.gd656conquest.common.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;

public class RadioBeaconItem extends ConquestGeoItem {
    public RadioBeaconItem(Properties properties, ResourceLocation geoModel, ResourceLocation texture, ResourceLocation animation) {
        super(properties, geoModel, texture, animation);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        boolean used = RoomCoreRuntimeManager.of(serverPlayer.server).tryUseSquadBeaconItem(serverPlayer, hand);
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
        InteractionHand hand = context.getHand();
        boolean used = RoomCoreRuntimeManager.of(serverPlayer.server).tryUseSquadBeaconItem(serverPlayer, hand);
        return used ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}

