package org.mods.gd656conquest.server.facility.support;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.common.support.SupportHeroKitTypeRegistry;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientShortSoundPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.herokit.HeroKitDataModel;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.room.model.RoomTeamId;
import org.mods.gd656conquest.server.shared.inventory.PlayerInventorySanitizer;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.shared.util.ItemStackSnbtCodec;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class HeroKitSupportService {
    private static final int RESERVED_HOTBAR_SLOT_7 = 6;
    private static final int RESERVED_HOTBAR_SLOT_8 = 7;
    private static final int RESERVED_HOTBAR_SLOT_9 = 8;

    private HeroKitSupportService() {
    }

    public static void handleDeployRequest(ServerPlayer player, String heroKitId) {
        if (player == null || player.server == null) {
            return;
        }
        executeDeploy(player, heroKitId);
    }

    public static boolean isHeroKitActive(RunningRuntimeState state, UUID playerId) {
        return state != null
            && playerId != null
            && state.getActiveHeroKitIdsByPlayer().containsKey(playerId);
    }

    public static void clearHeroKitState(RunningRuntimeState state, UUID playerId) {
        if (state == null || playerId == null) {
            return;
        }
        state.getActiveHeroKitIdsByPlayer().remove(playerId);
        state.getActiveHeroKitTypesByPlayer().remove(playerId);
    }

    public static void clearHeroKitStateOnRespawn(RunningRuntimeState state, UUID playerId) {
        clearHeroKitState(state, playerId);
    }

    public static boolean canRefillFromAmmoBox(RunningRuntimeState state, UUID playerId) {
        return !isHeroKitActive(state, playerId);
    }

    private static void executeDeploy(ServerPlayer player, String rawHeroKitId) {
        RoomCoreRuntimeManager roomManager = RoomCoreRuntimeManager.of(player.server);
        RunningRuntimeState state = resolveRunningState(roomManager, player);
        RoomDefinition room = resolveRoom(roomManager, player);
        RoomTeamId teamId = roomManager.findCurrentTeam(player.getUUID()).orElse(null);
        if (state == null || room == null || teamId == null || room.lifecycleState() != RoomLifecycleState.RUNNING) {
            ServerMessageSender.system(player, "阵地支援", "当前无法部署精英兵", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || state.isInRespawnFlow(player.getUUID()) || state.isPlayerDowned(player.getUUID())) {
            ServerMessageSender.system(player, "阵地支援", "当前状态无法部署精英兵", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        if (state.getActiveHeroKitIdsByPlayer().containsKey(player.getUUID())) {
            ServerMessageSender.system(player, "阵地支援", "您当前已经是精英兵状态，无法再次购买", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        String heroKitId = normalizeHeroKitId(rawHeroKitId);
        if (heroKitId.isBlank()) {
            ServerMessageSender.system(player, "阵地支援", "无效的精英兵 ID", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        List<String> allowedIds = resolveAllowedHeroKitIds(room, teamId);
        if (allowedIds.stream().noneMatch(heroKitId::equalsIgnoreCase)) {
            ServerMessageSender.system(player, "阵地支援", "当前阵营未开放该精英兵", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        HeroKitDataModel.HeroKitEntry entry = ConquestDataManager.of(player.server)
            .heroKits()
            .get()
            .getHeroKitsById()
            .get(heroKitId);
        if (entry == null) {
            ServerMessageSender.system(player, "阵地支援", "精英兵数据不存在", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        SupportScoreService.ConsumptionResult result = roomManager.tryConsumePlayerSupportScore(player.getUUID(), Math.max(0, entry.getCost()));
        if (!result.success()) {
            ServerMessageSender.system(player, "阵地支援", "剩余分数不足", ChatFormatting.RED);
            SupportUiService.sendSnapshot(player);
            return;
        }
        applyHeroKitInventory(player, entry);
        state.getActiveHeroKitIdsByPlayer().put(player.getUUID(), entry.getId());
        state.getActiveHeroKitTypesByPlayer().put(player.getUUID(), entry.getType());
        SupportHudSubtitleService.broadcastHeroKitDeployed(state, teamId, player, resolveDisplayName(entry));
        playDeployVoice(player, entry.getType());
        ServerMessageSender.system(
            player,
            "阵地支援",
            "已部署精英兵: " + resolveDisplayName(entry) + "，剩余分数 " + result.remainingScore(),
            ChatFormatting.GREEN
        );
        SupportUiService.sendSnapshot(player);
    }

    private static RunningRuntimeState resolveRunningState(RoomCoreRuntimeManager roomManager, ServerPlayer player) {
        String roomId = roomManager.findPlayerRoomId(player.getUUID()).orElse("");
        return roomId.isBlank() ? null : roomManager.findRunningState(roomId).orElse(null);
    }

    private static RoomDefinition resolveRoom(RoomCoreRuntimeManager roomManager, ServerPlayer player) {
        String roomId = roomManager.findPlayerRoomId(player.getUUID()).orElse("");
        return roomId.isBlank() ? null : roomManager.findRoom(roomId).orElse(null);
    }

    private static List<String> resolveAllowedHeroKitIds(RoomDefinition room, RoomTeamId teamId) {
        if (room == null || teamId == null) {
            return List.of();
        }
        List<String> rawValues = teamId == RoomTeamId.CAMP_A
            ? room.commonConfig().supportCampAHeroKits()
            : room.commonConfig().supportCampBHeroKits();
        return rawValues == null ? List.of() : rawValues.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .sorted(Comparator.comparing(String::toLowerCase))
            .toList();
    }

    private static String normalizeHeroKitId(String rawHeroKitId) {
        return rawHeroKitId == null ? "" : rawHeroKitId.trim().toLowerCase(Locale.ROOT);
    }

    private static void applyHeroKitInventory(ServerPlayer player, HeroKitDataModel.HeroKitEntry entry) {
        if (player == null || entry == null) {
            return;
        }
        ItemStack reservedSlot7 = snapshotHotbarSlot(player, RESERVED_HOTBAR_SLOT_7);
        ItemStack reservedSlot8 = snapshotHotbarSlot(player, RESERVED_HOTBAR_SLOT_8);
        ItemStack reservedSlot9 = snapshotHotbarSlot(player, RESERVED_HOTBAR_SLOT_9);
        PlayerInventorySanitizer.clearAllSlots(player);
        applyItems(player, entry.getHotbarBySlot(), 0);
        applyItems(player, entry.getInventoryBySlot(), 9);
        restoreHotbarSlot(player, RESERVED_HOTBAR_SLOT_7, reservedSlot7);
        restoreHotbarSlot(player, RESERVED_HOTBAR_SLOT_8, reservedSlot8);
        restoreHotbarSlot(player, RESERVED_HOTBAR_SLOT_9, reservedSlot9);
        HeroKitDataModel.ArmorLoadout armor = entry.getArmor();
        player.setItemSlot(EquipmentSlot.HEAD, ItemStackSnbtCodec.deserialize(armor == null ? "" : armor.getHeadItemSnbt()));
        player.setItemSlot(EquipmentSlot.CHEST, ItemStackSnbtCodec.deserialize(armor == null ? "" : armor.getChestItemSnbt()));
        player.setItemSlot(EquipmentSlot.LEGS, ItemStackSnbtCodec.deserialize(armor == null ? "" : armor.getLegsItemSnbt()));
        player.setItemSlot(EquipmentSlot.FEET, ItemStackSnbtCodec.deserialize(armor == null ? "" : armor.getFeetItemSnbt()));
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStackSnbtCodec.deserialize(entry.getOffhandItemSnbt()));
        player.getInventory().selected = 0;
        PlayerInventorySanitizer.flush(player);
    }

    private static ItemStack snapshotHotbarSlot(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot >= player.getInventory().items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private static void restoreHotbarSlot(ServerPlayer player, int slot, ItemStack stack) {
        if (player == null || slot < 0 || slot >= player.getInventory().items.size()) {
            return;
        }
        player.getInventory().setItem(slot, stack == null ? ItemStack.EMPTY : stack.copy());
    }

    private static void applyItems(ServerPlayer player, Map<String, String> bySlot, int slotOffset) {
        if (player == null || bySlot == null || bySlot.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : bySlot.entrySet()) {
            int logicalSlot;
            try {
                logicalSlot = Integer.parseInt(entry.getKey());
            } catch (Exception ignored) {
                continue;
            }
            int actualSlot = logicalSlot + slotOffset;
            if (actualSlot < 0 || actualSlot >= player.getInventory().items.size()) {
                continue;
            }
            player.getInventory().setItem(actualSlot, ItemStackSnbtCodec.deserialize(entry.getValue()));
        }
    }

    private static String resolveDisplayName(HeroKitDataModel.HeroKitEntry entry) {
        if (entry == null) {
            return "";
        }
        String displayName = entry.getDisplayName();
        return displayName == null || displayName.isBlank() ? entry.getId() : displayName;
    }

    private static void playDeployVoice(ServerPlayer player, String type) {
        if (player == null) {
            return;
        }
        String soundId = SupportHeroKitTypeRegistry.soundId(type);
        if (soundId.isBlank()) {
            return;
        }
        NetworkManager.sendToPlayer(player, ClientShortSoundPacket.oneShot(soundId, 1.0F, 1.0F));
    }
}
