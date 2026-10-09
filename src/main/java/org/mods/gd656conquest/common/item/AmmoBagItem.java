package org.mods.gd656conquest.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

public class AmmoBagItem extends ConquestGeoItem {
    private static final int ITEM_COOLDOWN_TICKS = 5 * 20;
    private static final int MAX_AMMO_PER_USE = 50;
    private static final ResourceLocation AMMO_BAG_THROW_SOUND_ID = ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.ammo_bag_throw");

    public AmmoBagItem(Properties properties, ResourceLocation geoModel, ResourceLocation texture, ResourceLocation animation) {
        super(properties, geoModel, texture, animation);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RoomPlayerLoadoutService.AmmoSupplyResult supplyResult = resolveAmmoSupply(serverPlayer, serverPlayer);
            if (supplyResult.totalAdded() <= 0) {
                serverPlayer.displayClientMessage(Component.literal("弹药包使用失败：当前无可补给弹药").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
            }
            serverPlayer.getCooldowns().addCooldown(this, ITEM_COOLDOWN_TICKS);
            serverPlayer.displayClientMessage(buildSelfUseMessage(supplyResult), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
        if (!(user instanceof ServerPlayer giver) || !(target instanceof ServerPlayer receiver)) {
            return InteractionResult.PASS;
        }
        if (giver.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (!isSameCampPlayer(giver, receiver)) {
            return InteractionResult.PASS;
        }
        RoomPlayerLoadoutService.AmmoSupplyResult supplyResult = resolveAmmoSupply(giver, receiver);
        if (supplyResult.totalAdded() <= 0) {
            giver.displayClientMessage(Component.literal("目标当前无可补给弹药").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!giver.getAbilities().instabuild) {
            stack.shrink(1);
        }
        giver.getCooldowns().addCooldown(this, ITEM_COOLDOWN_TICKS);
        giver.swing(hand, true);
        giver.serverLevel().playSound(
                null,
                giver.getX(),
                giver.getY(),
                giver.getZ(),
                SoundEvent.createVariableRangeEvent(AMMO_BAG_THROW_SOUND_ID),
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        receiver.displayClientMessage(buildReceiverMessage(giver, supplyResult), true);
        giver.displayClientMessage(buildGiverMessage(receiver, supplyResult), true);
        FrontlineVoiceBridge.playCue(giver, RoomThirdPersonAudioCueRegistry.PLAYER_THROW_AMMO);
        FrontlineVoiceBridge.playCue(receiver, RoomThirdPersonAudioCueRegistry.PLAYER_THANKS);
        KilliconScoreboardBridge.tryAwardConquestBonus(giver, "AMMO_SUPPLY", 1.0F, "");
        return InteractionResult.SUCCESS;
    }

    private static RoomPlayerLoadoutService.AmmoSupplyResult resolveAmmoSupply(ServerPlayer contextPlayer, ServerPlayer targetPlayer) {
        if (contextPlayer == null || contextPlayer.server == null || targetPlayer == null) {
            return RoomPlayerLoadoutService.AmmoSupplyResult.EMPTY;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(contextPlayer.server);
        if (coreManager.findPlayerRoomId(contextPlayer.getUUID()).isEmpty()) {
            return RoomPlayerLoadoutService.AmmoSupplyResult.EMPTY;
        }
        return coreManager.trySupplyAmmoByBag(targetPlayer, MAX_AMMO_PER_USE);
    }

    private static boolean isSameCampPlayer(ServerPlayer giver, ServerPlayer receiver) {
        if (giver == null || giver.server == null || receiver == null) {
            return false;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(giver.server);
        return coreManager.isSameCampPlayer(giver, receiver);
    }

    private static MutableComponent buildSelfUseMessage(RoomPlayerLoadoutService.AmmoSupplyResult supplyResult) {
        return Component.literal("弹药已得到补给").withStyle(ChatFormatting.GREEN);
    }

    private static MutableComponent buildReceiverMessage(ServerPlayer giver, RoomPlayerLoadoutService.AmmoSupplyResult supplyResult) {
        return Component.literal(giver.getGameProfile().getName()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" 给了你一个弹药包，弹药已得到补给").withStyle(ChatFormatting.AQUA));
    }

    private static MutableComponent buildGiverMessage(ServerPlayer receiver, RoomPlayerLoadoutService.AmmoSupplyResult supplyResult) {
        return Component.literal("你给了 ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(receiver.getGameProfile().getName()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" 一个弹药包，弹药已得到补给").withStyle(ChatFormatting.GREEN));
    }
}
