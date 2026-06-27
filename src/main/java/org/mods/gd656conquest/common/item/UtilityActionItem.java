package org.mods.gd656conquest.common.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;

public class UtilityActionItem extends Item {
    private static final int LOADOUT_UI_COOLDOWN_TICKS = 8;
    private final QuickAccessItemService.Action action;

    public UtilityActionItem(Properties properties, QuickAccessItemService.Action action) {
        super(properties);
        this.action = action;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (action == QuickAccessItemService.Action.LOADOUT_GUIDE && serverPlayer.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }
        boolean success = QuickAccessItemService.handleUse(serverPlayer, action);
        if (success && action == QuickAccessItemService.Action.LOADOUT_GUIDE) {
            serverPlayer.getCooldowns().addCooldown(stack.getItem(), LOADOUT_UI_COOLDOWN_TICKS);
        }
        return success ? InteractionResultHolder.sidedSuccess(stack, false) : InteractionResultHolder.fail(stack);
    }
}
