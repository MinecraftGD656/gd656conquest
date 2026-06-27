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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

public class MedicalBagItem extends ConquestGeoItem {
    private static final int ITEM_COOLDOWN_TICKS = 5 * 20;
    private static final int EFFECT_DURATION_TICKS = 3 * 20;
    private static final int EFFECT_AMPLIFIER = 4;
    private static final ResourceLocation AMMO_BAG_THROW_SOUND_ID = ResourceLocation.fromNamespaceAndPath("gd656conquest", "sfx.ammo_bag_throw");

    public MedicalBagItem(Properties properties, ResourceLocation geoModel, ResourceLocation texture, ResourceLocation animation) {
        super(properties, geoModel, texture, animation);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
            }
            applyHealEffect(serverPlayer);
            serverPlayer.serverLevel().playSound(
                    null,
                    serverPlayer.getX(),
                    serverPlayer.getY(),
                    serverPlayer.getZ(),
                    SoundEvent.createVariableRangeEvent(AMMO_BAG_THROW_SOUND_ID),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F
            );
            serverPlayer.getCooldowns().addCooldown(this, ITEM_COOLDOWN_TICKS);
            serverPlayer.displayClientMessage(Component.literal("你使用了一个医疗包").withStyle(ChatFormatting.GREEN), true);
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
        if (receiver.getHealth() >= receiver.getMaxHealth() - 0.001F) {
            return InteractionResult.FAIL;
        }
        if (!giver.getAbilities().instabuild) {
            stack.shrink(1);
        }
        applyHealEffect(receiver);
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
        giver.getCooldowns().addCooldown(this, ITEM_COOLDOWN_TICKS);
        giver.swing(hand, true);
        MutableComponent actionbar = Component.literal(giver.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" 给了你一个医疗包").withStyle(ChatFormatting.AQUA));
        receiver.displayClientMessage(actionbar, true);
        giver.displayClientMessage(
                Component.literal("你给了 ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(receiver.getGameProfile().getName()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" 一个医疗包").withStyle(ChatFormatting.GREEN)),
                true
        );
        FrontlineVoiceBridge.playCue(giver, RoomThirdPersonAudioCueRegistry.PLAYER_THROW_MEDKIT);
        KilliconScoreboardBridge.tryAwardConquestBonus(giver, "HEALING", 1.0F, "");
        return InteractionResult.SUCCESS;
    }

    private static void applyHealEffect(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_DURATION_TICKS, EFFECT_AMPLIFIER, false, true, true));
    }

    private static boolean isSameCampPlayer(ServerPlayer giver, ServerPlayer receiver) {
        if (giver == null || giver.server == null || receiver == null) {
            return false;
        }
        return RoomCoreRuntimeManager.of(giver.server).isSameCampPlayer(giver, receiver);
    }
}
