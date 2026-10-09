package org.mods.gd656conquest.server.player.state;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FireExtinguishService {
    private static final int HOLD_TICKS_REQUIRED = 3 * 20;
    private static final int HINT_INTERVAL_TICKS = 20;
    private static final ResourceLocation LRTACTICAL_FLAMMABLE_ID = ResourceLocation.fromNamespaceAndPath("lrtactical", "flammable");
    private static final Map<UUID, Long> HOLD_START_TICK_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Integer> LAST_COUNTDOWN_SECOND_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT_TICK_BY_PLAYER = new HashMap<>();
    private static final Set<UUID> EXTINGUISH_SUCCESS_PLAYERS = new HashSet<>();

    private FireExtinguishService() {
    }

    public static void onPlayerTick(ServerPlayer player, long gameTick) {
        if (player == null || gameTick <= 0L) {
            return;
        }
        UUID uuid = player.getUUID();
        if (player.isSpectator()) {
            clearHoldState(uuid);
            return;
        }

        boolean burning = isBurning(player);
        if (!burning) {
            clearHoldState(uuid);
            return;
        }

        if (!player.isShiftKeyDown()) {
            clearHoldState(uuid);
            if (!EXTINGUISH_SUCCESS_PLAYERS.contains(uuid)) {
                long lastHintTick = LAST_HINT_TICK_BY_PLAYER.getOrDefault(uuid, Long.MIN_VALUE);
                if (gameTick - lastHintTick >= HINT_INTERVAL_TICKS) {
                    ServerMessageSender.actionBar(player, "状态", Component.literal("下蹲即可灭火（长按3秒）").withStyle(ChatFormatting.RED));
                    LAST_HINT_TICK_BY_PLAYER.put(uuid, gameTick);
                }
            }
            return;
        }

        long startTick = HOLD_START_TICK_BY_PLAYER.computeIfAbsent(uuid, ignored -> gameTick);
        int elapsedTicks = (int) Math.max(0L, gameTick - startTick + 1L);
        if (elapsedTicks >= HOLD_TICKS_REQUIRED) {
            extinguish(player);
            clearHoldState(uuid);
            LAST_HINT_TICK_BY_PLAYER.put(uuid, gameTick);
            EXTINGUISH_SUCCESS_PLAYERS.add(uuid);
            ServerMessageSender.actionBar(player, "状态", Component.literal("灭火成功").withStyle(ChatFormatting.GREEN));
            return;
        }

        int remainSeconds = Math.max(1, (int) Math.ceil((HOLD_TICKS_REQUIRED - elapsedTicks) / 20.0D));
        int lastShown = LAST_COUNTDOWN_SECOND_BY_PLAYER.getOrDefault(uuid, Integer.MIN_VALUE);
        if (remainSeconds != lastShown) {
            MutableComponent actionbar = Component.literal("长按下蹲灭火: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.valueOf(remainSeconds)).withStyle(resolveCountdownNumberColor(remainSeconds)))
                .append(Component.literal(" 秒").withStyle(ChatFormatting.YELLOW));
            ServerMessageSender.actionBar(player, "状态", actionbar);
            LAST_COUNTDOWN_SECOND_BY_PLAYER.put(uuid, remainSeconds);
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        clearHoldState(uuid);
        LAST_HINT_TICK_BY_PLAYER.remove(uuid);
        EXTINGUISH_SUCCESS_PLAYERS.remove(uuid);
    }

    public static void clearAll() {
        HOLD_START_TICK_BY_PLAYER.clear();
        LAST_COUNTDOWN_SECOND_BY_PLAYER.clear();
        LAST_HINT_TICK_BY_PLAYER.clear();
        EXTINGUISH_SUCCESS_PLAYERS.clear();
    }

    private static void clearHoldState(UUID uuid) {
        HOLD_START_TICK_BY_PLAYER.remove(uuid);
        LAST_COUNTDOWN_SECOND_BY_PLAYER.remove(uuid);
    }

    private static boolean isBurning(ServerPlayer player) {
        return player.isOnFire() || hasLrTacticalFlammableEffect(player);
    }

    private static boolean hasLrTacticalFlammableEffect(ServerPlayer player) {
        MobEffect flammable = ForgeRegistries.MOB_EFFECTS.getValue(LRTACTICAL_FLAMMABLE_ID);
        return flammable != null && player.hasEffect(flammable);
    }

    private static void extinguish(ServerPlayer player) {
        player.clearFire();
        player.setRemainingFireTicks(0);
        MobEffect flammable = ForgeRegistries.MOB_EFFECTS.getValue(LRTACTICAL_FLAMMABLE_ID);
        if (flammable != null) {
            player.removeEffect(flammable);
        }
    }

    private static ChatFormatting resolveCountdownNumberColor(int remainSeconds) {
        if (remainSeconds >= 3) {
            return ChatFormatting.RED;
        }
        if (remainSeconds == 2) {
            return ChatFormatting.GOLD;
        }
        return ChatFormatting.YELLOW;
    }
}
