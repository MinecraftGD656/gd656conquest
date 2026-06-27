package org.mods.gd656conquest.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(PlayerTeam.class)
public abstract class PlayerTeamColorMixin {
    @Inject(method = "getFormattedName", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$getFormattedName(Component component, CallbackInfoReturnable<MutableComponent> cir) {
        PlayerTeam playerTeam = (PlayerTeam) (Object) this;
        MutableComponent mapped = mapFormattedName(playerTeam, component);
        if (mapped == null) {
            return;
        }
        cir.setReturnValue(mapped);
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$getColor(CallbackInfoReturnable<ChatFormatting> cir) {
        PlayerTeam playerTeam = (PlayerTeam) (Object) this;
        ChatFormatting relationColor = resolveRelationColor(playerTeam);
        if (relationColor != null) {
            cir.setReturnValue(relationColor);
        }
    }

    @Inject(method = "formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gd656conquest$formatNameForTeam(Team team, Component component, CallbackInfoReturnable<MutableComponent> cir) {
        if (!(team instanceof PlayerTeam playerTeam)) {
            return;
        }
        MutableComponent mapped = mapFormattedName(playerTeam, component);
        if (mapped != null) {
            cir.setReturnValue(mapped);
        }
    }

    private static MutableComponent mapFormattedName(PlayerTeam playerTeam, Component component) {
        ChatFormatting relationColor = resolveRelationColor(playerTeam);
        if (relationColor == null) {
            return null;
        }
        MutableComponent base = component.copy();
        return playerTeam.getPlayerPrefix().copy()
                .append(base)
                .append(playerTeam.getPlayerSuffix())
                .withStyle(style -> style.withColor(relationColor));
    }

    private static ChatFormatting resolveRelationColor(PlayerTeam team) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        if (!(minecraft.player.getTeam() instanceof PlayerTeam selfTeam)) {
            return null;
        }
        String selfName = normalize(selfTeam.getName());
        String targetName = normalize(team.getName());
        String selfPrefix = resolveRoomPrefix(selfName);
        String targetPrefix = resolveRoomPrefix(targetName);
        int selfCamp = resolveCampIndex(selfName);
        int targetCamp = resolveCampIndex(targetName);
        if (selfPrefix == null || targetPrefix == null || selfCamp <= 0 || targetCamp <= 0) {
            return null;
        }
        if (!selfPrefix.equals(targetPrefix)) {
            return null;
        }
        if (selfCamp == targetCamp) {
            return ChatFormatting.BLUE;
        }
        return ChatFormatting.RED;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveRoomPrefix(String teamName) {
        if (teamName.isEmpty()) {
            return null;
        }
        if (teamName.endsWith("_camp_a")) {
            return teamName.substring(0, teamName.length() - "_camp_a".length());
        }
        if (teamName.endsWith("_camp_b")) {
            return teamName.substring(0, teamName.length() - "_camp_b".length());
        }
        if (teamName.endsWith("_campa")) {
            return teamName.substring(0, teamName.length() - "_campa".length());
        }
        if (teamName.endsWith("_campb")) {
            return teamName.substring(0, teamName.length() - "_campb".length());
        }
        if (teamName.endsWith("_taiga")) {
            return teamName.substring(0, teamName.length() - "_taiga".length());
        }
        if (teamName.endsWith("_savanna")) {
            return teamName.substring(0, teamName.length() - "_savanna".length());
        }
        if (teamName.endsWith("_t")) {
            return teamName.substring(0, teamName.length() - "_t".length());
        }
        if (teamName.endsWith("_s")) {
            return teamName.substring(0, teamName.length() - "_s".length());
        }
        return null;
    }

    private static int resolveCampIndex(String teamName) {
        if (teamName.isEmpty()) {
            return -1;
        }
        if (isCampAScoreboardName(teamName)) {
            return 1;
        }
        if (isCampBScoreboardName(teamName)) {
            return 2;
        }
        return -1;
    }

    private static boolean isCampAScoreboardName(String teamName) {
        return teamName.endsWith("_camp_a")
                || teamName.endsWith("_campa")
                || teamName.endsWith("_taiga")
                || teamName.endsWith("_t");
    }

    private static boolean isCampBScoreboardName(String teamName) {
        return teamName.endsWith("_camp_b")
                || teamName.endsWith("_campb")
                || teamName.endsWith("_savanna")
                || teamName.endsWith("_s");
    }
}
