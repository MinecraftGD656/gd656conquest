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
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceGdcqCompatApi;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

import java.util.Locale;

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
        return org.mods.gd656conquest.server.support.SupportRuntime.useBag(level, player, hand);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
        return org.mods.gd656conquest.server.support.SupportRuntime.interactBag(stack, user, target, hand);
    }



    private static boolean playGiveMedkitVoice(ServerPlayer giver) {
        String voicePack = FrontlineVoiceGdcqCompatApi.resolveVoicePack(giver);
        if (voicePack == null || voicePack.isBlank()) {
            return false;
        }
        String soundId = "tp." + voicePack.toLowerCase(Locale.ROOT) + ".throw_medkit";
        return FrontlineVoiceBridge.playThirdPersonSound(giver, soundId, 1.0F, 1.0F, 24.0D);
    }

    private static boolean isSameCampPlayer(ServerPlayer giver, ServerPlayer receiver) {
        if (giver == null || giver.server == null || receiver == null) {
            return false;
        }
        return RoomCoreRuntimeManager.of(giver.server).isSameCampPlayer(giver, receiver);
    }
}
