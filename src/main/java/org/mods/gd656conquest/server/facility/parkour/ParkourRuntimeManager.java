package org.mods.gd656conquest.server.facility.parkour;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ParkourLeaderboardSnapshotPacket;
import org.mods.gd656conquest.network.packet.ParkourSessionSyncPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.parkour.ParkourDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.facility.range.RangeInventorySnapshotCodec;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.player.state.HubDimensionService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.shared.inventory.PlayerInventorySanitizer;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.social.chat.ServerActionBarService;
import org.mods.gd656conquest.server.title.PlayerTitleService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ParkourRuntimeManager {
    private static final String INVENTORY_SNAPSHOT_TAG = "gd656conquest_parkour_inventory_snapshot";
    private static final String ACTIONBAR_PROMPT_SOURCE = "parkour_prompt";
    private static final String ACTIONBAR_STATUS_SOURCE = "parkour_status";
    private static final int PARKOUR_LEAVE_SLOT = 8;
    private static final long MAX_ACCEPTED_TIME_MILLIS = 60L * 60L * 1000L;
    private static final Map<MinecraftServer, ParkourRuntimeManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, ParkourPlayerSession> playerSessions = new LinkedHashMap<>();
    private final Set<UUID> pendingRespawnPlayers = ConcurrentHashMap.newKeySet();

    private ParkourRuntimeManager(MinecraftServer server) {
        this.server = server;
    }

    public static ParkourRuntimeManager of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, ParkourRuntimeManager::new);
        }
    }

    public void initialize() {
        reconcileConfiguredTitles();
    }

    public void shutdown() {
        for (UUID uuid : new ArrayList<>(playerSessions.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                leaveParkourInternal(player, false, false);
            }
        }
        playerSessions.clear();
        pendingRespawnPlayers.clear();
    }

    public void onServerTick(long gameTick) {
    }

    public void onPlayerTick(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        ParkourPlayerSession session = playerSessions.get(player.getUUID());
        if (!HubDimensionService.isHubDimension(player.level())) {
            ServerActionBarService.clearSource(player, ACTIONBAR_PROMPT_SOURCE);
            return;
        }
        if (session != null && session.challengeActive()) {
            ServerActionBarService.clearSource(player, ACTIONBAR_PROMPT_SOURCE);
            return;
        }
        if (session != null && server.getTickCount() < session.statusUntilTick()) {
            ServerActionBarService.clearSource(player, ACTIONBAR_PROMPT_SOURCE);
            return;
        }
        if (shouldShowStartPrompt(player, session)) {
            ServerActionBarService.showBuilder(player, ACTIONBAR_PROMPT_SOURCE, buildStartPromptActionbar(), 30);
        } else {
            ServerActionBarService.clearSource(player, ACTIONBAR_PROMPT_SOURCE);
        }
    }

    public boolean isInParkour(UUID uuid) {
        return uuid != null && playerSessions.containsKey(uuid);
    }

    public boolean isChallengeActive(UUID uuid) {
        ParkourPlayerSession session = uuid == null ? null : playerSessions.get(uuid);
        return session != null && session.challengeActive();
    }

    public boolean canQuickJoin(ServerPlayer player) {
        if (player == null || player.server == null || !HubDimensionService.isHubDimension(player.level())) {
            return false;
        }
        UUID uuid = player.getUUID();
        if (RoomCoreRuntimeManager.of(server).findPlayerRoomId(uuid).isPresent()) {
            return false;
        }
        if (RangeRuntimeManager.of(server).isInRange(uuid)) {
            return false;
        }
        return !playerSessions.containsKey(uuid);
    }

    public void handleQuickJoinRequest(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        String result = joinParkour(player);
        ServerMessageSender.system(player, "跑酷", result, canQuickJoin(player) ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
        sendLeaderboardSnapshot(player, false);
    }

    public String joinParkour(ServerPlayer player) {
        if (player == null || player.server == null) {
            return "加入跑酷失败：玩家不存在";
        }
        UUID uuid = player.getUUID();
        if (!HubDimensionService.isHubDimension(player.level())) {
            return "加入跑酷失败：仅可在主城使用";
        }
        if (RoomCoreRuntimeManager.of(server).findPlayerRoomId(uuid).isPresent()) {
            return "加入跑酷失败：请先离开当前对局";
        }
        if (RangeRuntimeManager.of(server).isInRange(uuid)) {
            return "加入跑酷失败：请先离开靶场";
        }
        ParkourPlayerSession existing = playerSessions.get(uuid);
        if (existing != null) {
            existing.setChallengeActive(false);
            existing.setFinishSubmitted(false);
            existing.setStatusUntilTick(0L);
            teleportToStart(player);
            enforceParkourHotbarLayout(player);
            syncClientState(player);
            QuickAccessItemService.refreshHotbar(player);
            sendLeaderboardSnapshot(player, false);
            return "你已在跑酷中，已返回起点";
        }
        backupInventory(player);
        clearPlayerInventory(player);
        ParkourPlayerSession session = new ParkourPlayerSession(requireSnapshot(player));
        playerSessions.put(uuid, session);
        enforceParkourHotbarLayout(player);
        teleportToStart(player);
        resetPostReturnState(player);
        syncClientState(player);
        QuickAccessItemService.refreshHotbar(player);
        sendLeaderboardSnapshot(player, false);
        return "已加入跑酷";
    }

    public String leaveParkour(ServerPlayer player) {
        if (player == null) {
            return "离开跑酷失败：玩家不存在";
        }
        UUID uuid = player.getUUID();
        if (!playerSessions.containsKey(uuid) && !hasPersistentSnapshot(player)) {
            return "当前不在跑酷中";
        }
        leaveParkourInternal(player, true, true);
        QuickAccessItemService.refreshHotbar(player);
        sendLeaderboardSnapshot(player, false);
        return "已离开跑酷";
    }

    public void leaveParkourForTransfer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        leaveParkourInternal(player, false, false);
        QuickAccessItemService.refreshHotbar(player);
        sendLeaderboardSnapshot(player, false);
    }

    public void onPlayerRespawn(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!pendingRespawnPlayers.remove(uuid)) {
            return;
        }
        if (!playerSessions.containsKey(uuid) && !hasPersistentSnapshot(player)) {
            return;
        }
        leaveParkourInternal(player, true, true);
        QuickAccessItemService.refreshHotbar(player);
        sendLeaderboardSnapshot(player, false);
    }

    public void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        pendingRespawnPlayers.remove(player.getUUID());
        leaveParkourInternal(player, false, false);
    }

    public void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        pendingRespawnPlayers.remove(player.getUUID());
        playerSessions.remove(player.getUUID());
        if (!hasPersistentSnapshot(player)) {
            syncClientState(player);
            sendLeaderboardSnapshot(player, false);
            return;
        }
        tryRestoreFromPersistent(player);
        teleportToLeaveSpawn(player);
        syncClientState(player);
        QuickAccessItemService.refreshHotbar(player);
        sendLeaderboardSnapshot(player, false);
    }

    public void onPlayerChangedDimension(ServerPlayer player) {
        if (player == null || !playerSessions.containsKey(player.getUUID())) {
            return;
        }
        if (pendingRespawnPlayers.contains(player.getUUID())) {
            return;
        }
        leaveParkourInternal(player, false, false);
        QuickAccessItemService.refreshHotbar(player);
        sendLeaderboardSnapshot(player, false);
    }

    public void onPlayerDeath(ServerPlayer player) {
        if (player == null || !playerSessions.containsKey(player.getUUID())) {
            return;
        }
        ParkourPlayerSession session = playerSessions.get(player.getUUID());
        if (session != null) {
            session.setChallengeActive(false);
            session.setFinishSubmitted(false);
        }
        pendingRespawnPlayers.add(player.getUUID());
        NetworkManager.sendToPlayer(player, buildClearedSyncPacket());
    }

    public boolean tryHandleCommonUse(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }
        if (!HubDimensionService.isHubDimension(player.level())) {
            return false;
        }
        ParkourPlayerSession session = playerSessions.get(player.getUUID());
        if (session == null) {
            if (!canQuickJoin(player) || !isInsideStartArea(player)) {
                return false;
            }
            joinParkour(player);
            session = playerSessions.get(player.getUUID());
        }
        if (session == null || session.challengeActive()) {
            return false;
        }
        if (!isInsideStartArea(player)) {
            return false;
        }
        session.beginNewRun(server.getTickCount());
        ServerActionBarService.clearSource(player, ACTIONBAR_PROMPT_SOURCE);
        ServerActionBarService.showComponent(
            player,
            ACTIONBAR_STATUS_SOURCE,
            Component.literal("挑战已开始").withStyle(ChatFormatting.GOLD),
            40
        );
        syncClientState(player);
        return true;
    }

    public void handleFinishSubmission(ServerPlayer player, long elapsedMillis, int runNonce) {
        if (player == null || player.server == null) {
            return;
        }
        ParkourPlayerSession session = playerSessions.get(player.getUUID());
        if (session == null || !session.challengeActive() || session.finishSubmitted()) {
            return;
        }
        if (session.runNonce() != runNonce) {
            return;
        }
        if (elapsedMillis <= 0L || elapsedMillis > MAX_ACCEPTED_TIME_MILLIS) {
            return;
        }
        if (!isInsideEndArea(player)) {
            return;
        }
        session.setFinishSubmitted(true);
        session.setChallengeActive(false);
        session.setStatusUntilTick(server.getTickCount() + 120L);
        SubmissionResult result = submitResult(player, elapsedMillis);
        ServerActionBarService.showComponent(player, ACTIONBAR_STATUS_SOURCE, buildCompletionActionbar(elapsedMillis, result.rank()), 120);
        completeParkourAfterFinish(player, session);
        sendLeaderboardSnapshot(player, false);
    }

    public void sendLeaderboardSnapshot(ServerPlayer player, boolean openPanel) {
        if (player == null || player.server == null) {
            return;
        }
        List<RankedRecord> sorted = sortedRecords();
        UUID playerId = player.getUUID();
        RankedRecord holder = sorted.isEmpty() ? null : sorted.get(0);
        PlayerStats stats = resolvePlayerStats(playerId, player.getScoreboardName(), sorted);
        List<ParkourLeaderboardSnapshotPacket.EntryPayload> entries = sorted.stream()
            .limit(25)
            .map(record -> new ParkourLeaderboardSnapshotPacket.EntryPayload(
                record.rank(),
                record.playerName(),
                record.timeMillis(),
                record.playerId().equals(playerId)
            ))
            .toList();
        NetworkManager.sendToPlayer(player, new ParkourLeaderboardSnapshotPacket(
            openPanel,
            canQuickJoin(player),
            isInParkour(playerId),
            stats.rank(),
            stats.bestTimeMillis(),
            stats.lastTimeMillis(),
            holder == null ? "" : holder.playerName(),
            holder == null ? 0L : holder.timeMillis(),
            entries
        ));
    }

    public void resyncOnlineState() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncClientState(player);
            sendLeaderboardSnapshot(player, false);
        }
    }

    public void reconcileConfiguredTitles() {
        ServerConfigStore config = serverConfig();
        String completionTitleId = config.getParkourCompletionTitleId();
        String bestTitleId = config.getParkourBestTitleId();
        PlayerTitleService titleService = PlayerTitleService.of(server);
        List<RankedRecord> sorted = sortedRecords();
        PlayerDataStore playerDataStore = ConquestDataManager.of(server).playerData();
        if (!completionTitleId.isBlank()) {
            for (RankedRecord record : sorted) {
                titleService.giveTitleDirect(completionTitleId == null ? null : record.playerId(), record.playerName(), completionTitleId, "跑酷");
            }
        }
        if (!bestTitleId.isBlank()) {
            UUID championId = sorted.isEmpty() ? null : sorted.get(0).playerId();
            Collection<UUID> knownPlayers = playerDataStore.listKnownPlayerIds();
            for (UUID uuid : knownPlayers) {
                if (uuid == null) {
                    continue;
                }
                String preferredName = playerDataStore.getPlayerData(uuid).getLastKnownPlayerName();
                if (uuid.equals(championId)) {
                    titleService.giveTitleDirect(uuid, preferredName, bestTitleId, "跑酷");
                } else {
                    titleService.removeSingleTitleDirect(uuid, preferredName, bestTitleId);
                }
            }
        }
    }

    public List<String> suggestKnownPlayers() {
        return PlayerTitleService.of(server).suggestKnownPlayerNames();
    }

    public RemoveRecordResult removeRecord(String playerName) {
        ResolvedPlayer resolved = resolvePlayerByName(playerName);
        if (resolved == null) {
            return new RemoveRecordResult(false, playerName == null ? "" : playerName, "未找到玩家: " + playerName);
        }
        ParkourDataModel model = ConquestDataManager.of(server).parkourData().get();
        ParkourDataModel.PlayerRecord removed = model.getPlayerRecords().remove(resolved.playerId().toString());
        if (removed == null) {
            return new RemoveRecordResult(false, resolved.playerName(), "该玩家没有跑酷记录");
        }
        ConquestDataManager.of(server).parkourData().save(model);
        String bestTitleId = serverConfig().getParkourBestTitleId();
        if (!bestTitleId.isBlank()) {
            PlayerTitleService.of(server).removeSingleTitleDirect(resolved.playerId(), resolved.playerName(), bestTitleId);
        }
        reconcileConfiguredTitles();
        resyncOnlineState();
        return new RemoveRecordResult(true, resolved.playerName(), "已删除该玩家的跑酷记录");
    }

    private SubmissionResult submitResult(ServerPlayer player, long elapsedMillis) {
        String completionTitleId = serverConfig().getParkourCompletionTitleId();
        String bestTitleId = serverConfig().getParkourBestTitleId();
        PlayerTitleService titleService = PlayerTitleService.of(server);
        List<RankedRecord> before = sortedRecords();
        UUID previousChampion = before.isEmpty() ? null : before.get(0).playerId();
        String playerName = player.getScoreboardName();
        UUID playerId = player.getUUID();
        ParkourDataModel model = ConquestDataManager.of(server).parkourData().get();
        Map<String, ParkourDataModel.PlayerRecord> records = model.getPlayerRecords();
        ParkourDataModel.PlayerRecord record = records.computeIfAbsent(playerId.toString(), ignored -> new ParkourDataModel.PlayerRecord());
        long now = System.currentTimeMillis();
        record.setPlayerName(playerName);
        record.setLastTimeMillis(elapsedMillis);
        record.setLastUpdatedAtEpochMs(now);
        long bestTime = record.getBestTimeMillis() == null ? 0L : record.getBestTimeMillis();
        if (bestTime <= 0L || elapsedMillis < bestTime) {
            record.setBestTimeMillis(elapsedMillis);
            record.setBestUpdatedAtEpochMs(now);
        }
        ConquestDataManager.of(server).parkourData().save(model);
        if (!completionTitleId.isBlank()) {
            titleService.giveTitleDirect(playerId, playerName, completionTitleId, "跑酷");
        }
        List<RankedRecord> after = sortedRecords();
        RankedRecord currentChampion = after.isEmpty() ? null : after.get(0);
        if (!bestTitleId.isBlank()) {
            if (previousChampion != null && (currentChampion == null || !previousChampion.equals(currentChampion.playerId()))) {
                titleService.removeSingleTitleDirect(previousChampion, before.isEmpty() ? previousChampion.toString() : before.get(0).playerName(), bestTitleId);
            }
            if (currentChampion != null) {
                titleService.giveTitleDirect(currentChampion.playerId(), currentChampion.playerName(), bestTitleId, "跑酷");
            }
        }
        int rank = 0;
        for (RankedRecord ranked : after) {
            if (ranked.playerId().equals(playerId)) {
                rank = ranked.rank();
                break;
            }
        }
        return new SubmissionResult(rank);
    }

    private PlayerStats resolvePlayerStats(UUID playerId, String fallbackName, List<RankedRecord> sorted) {
        ParkourDataModel.PlayerRecord record = ConquestDataManager.of(server).parkourData().get().getPlayerRecords().get(playerId.toString());
        if (record != null && (record.getPlayerName() == null || record.getPlayerName().isBlank()) && fallbackName != null) {
            record.setPlayerName(fallbackName);
        }
        int rank = 0;
        for (RankedRecord rankedRecord : sorted) {
            if (rankedRecord.playerId().equals(playerId)) {
                rank = rankedRecord.rank();
                break;
            }
        }
        return new PlayerStats(
            rank,
            record == null || record.getBestTimeMillis() == null ? 0L : record.getBestTimeMillis(),
            record == null || record.getLastTimeMillis() == null ? 0L : record.getLastTimeMillis()
        );
    }

    private List<RankedRecord> sortedRecords() {
        List<RecordRow> rows = new ArrayList<>();
        for (Map.Entry<String, ParkourDataModel.PlayerRecord> entry : ConquestDataManager.of(server).parkourData().get().getPlayerRecords().entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (Exception ignored) {
                continue;
            }
            ParkourDataModel.PlayerRecord record = entry.getValue();
            if (record == null || record.getBestTimeMillis() == null || record.getBestTimeMillis() <= 0L) {
                continue;
            }
            rows.add(new RecordRow(
                uuid,
                record.getPlayerName() == null || record.getPlayerName().isBlank() ? uuid.toString() : record.getPlayerName(),
                record.getBestTimeMillis(),
                Math.max(0L, record.getBestUpdatedAtEpochMs() == null ? 0L : record.getBestUpdatedAtEpochMs())
            ));
        }
        rows.sort(Comparator
            .comparingLong(RecordRow::bestTimeMillis)
            .thenComparingLong(RecordRow::bestUpdatedAtEpochMs)
            .thenComparing(RecordRow::playerName, String.CASE_INSENSITIVE_ORDER));
        List<RankedRecord> ranked = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            RecordRow row = rows.get(index);
            ranked.add(new RankedRecord(index + 1, row.playerId(), row.playerName(), row.bestTimeMillis()));
        }
        return ranked;
    }

    private void leaveParkourInternal(ServerPlayer player, boolean teleportToLeaveSpawn, boolean announceClear) {
        UUID uuid = player.getUUID();
        pendingRespawnPlayers.remove(uuid);
        ParkourPlayerSession session = playerSessions.remove(uuid);
        ServerActionBarService.clearSource(player, ACTIONBAR_PROMPT_SOURCE);
        ServerActionBarService.clearSource(player, ACTIONBAR_STATUS_SOURCE);
        restoreInventory(player, session == null ? null : session.inventorySnapshot());
        if (teleportToLeaveSpawn) {
            teleportToLeaveSpawn(player);
        }
        if (announceClear) {
            syncClientState(player);
        } else {
            NetworkManager.sendToPlayer(player, buildClearedSyncPacket());
        }
    }

    private void backupInventory(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!playerSessions.containsKey(uuid)) {
            String snapshot = RangeInventorySnapshotCodec.snapshot(player);
            if (!snapshot.isBlank()) {
                player.getPersistentData().putString(INVENTORY_SNAPSHOT_TAG, snapshot);
            }
        }
    }

    private String requireSnapshot(ServerPlayer player) {
        String snapshot = player.getPersistentData().getString(INVENTORY_SNAPSHOT_TAG);
        return snapshot == null ? "" : snapshot;
    }

    private void clearPlayerInventory(ServerPlayer player) {
        PlayerInventorySanitizer.clearAllSlots(player);
    }

    private void restoreInventory(ServerPlayer player, String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = pullPersistentSnapshot(player);
        } else {
            player.getPersistentData().remove(INVENTORY_SNAPSHOT_TAG);
        }
        if (snapshot == null || snapshot.isBlank()) {
            return;
        }
        RangeInventorySnapshotCodec.restore(player, snapshot);
    }

    private void tryRestoreFromPersistent(ServerPlayer player) {
        String snapshot = pullPersistentSnapshot(player);
        if (snapshot == null || snapshot.isBlank()) {
            return;
        }
        RangeInventorySnapshotCodec.restore(player, snapshot);
    }

    private String pullPersistentSnapshot(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(INVENTORY_SNAPSHOT_TAG, Tag.TAG_STRING)) {
            return null;
        }
        String snapshot = persistent.getString(INVENTORY_SNAPSHOT_TAG);
        persistent.remove(INVENTORY_SNAPSHOT_TAG);
        return snapshot;
    }

    private boolean hasPersistentSnapshot(ServerPlayer player) {
        return player != null && player.getPersistentData().contains(INVENTORY_SNAPSHOT_TAG, Tag.TAG_STRING);
    }

    private void enforceParkourHotbarLayout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        for (int slot = 0; slot < 8; slot++) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        player.getInventory().setItem(PARKOUR_LEAVE_SLOT, new ItemStack(ConquestItems.LEAVE_ITEM.get()));
        player.getInventory().selected = PARKOUR_LEAVE_SLOT;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void teleportToStart(ServerPlayer player) {
        RoomPosition position = serverConfig().getParkourStartPosFallbackShared();
        player.teleportTo(HubDimensionService.resolveHubLevel(server), position.x() + 0.5D, position.y(), position.z() + 0.5D, player.getYRot(), player.getXRot());
    }

    private void teleportToLeaveSpawn(ServerPlayer player) {
        RoomPosition position = serverConfig().getOverworldSpawnFallbackShared();
        player.teleportTo(HubDimensionService.resolveHubLevel(server), position.x() + 0.5D, position.y(), position.z() + 0.5D, player.getYRot(), player.getXRot());
    }

    private void resetPostReturnState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        float maxHealth = player.getMaxHealth();
        if (maxHealth > 0.0F) {
            player.setHealth(maxHealth);
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.clearFire();
    }

    private boolean shouldShowStartPrompt(ServerPlayer player, ParkourPlayerSession session) {
        if (player == null || !isInsideStartArea(player)) {
            return false;
        }
        return session != null || canQuickJoin(player);
    }

    private boolean isInsideStartArea(ServerPlayer player) {
        return isInsideTargetArea(player, serverConfig().getParkourStartPosFallbackShared(), 3.5D);
    }

    private boolean isInsideEndArea(ServerPlayer player) {
        return isInsideTargetArea(player, serverConfig().getParkourEndPosFallbackShared(), 2.5D);
    }

    private boolean isInsideTargetArea(ServerPlayer player, RoomPosition position, double verticalTolerance) {
        if (player == null || position == null) {
            return false;
        }
        return Math.abs(player.getBlockX() - position.x()) <= 1
            && Math.abs(player.getBlockZ() - position.z()) <= 1
            && Math.abs(player.getY() - position.y()) <= verticalTolerance;
    }

    private void completeParkourAfterFinish(ServerPlayer player, ParkourPlayerSession session) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        pendingRespawnPlayers.remove(uuid);
        playerSessions.remove(uuid);
        ServerActionBarService.clearSource(player, ACTIONBAR_PROMPT_SOURCE);
        restoreInventory(player, session == null ? null : session.inventorySnapshot());
        resetPostReturnState(player);
        syncClientState(player);
        QuickAccessItemService.refreshHotbar(player);
    }

    private void syncClientState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ParkourPlayerSession session = playerSessions.get(player.getUUID());
        if (session == null) {
            NetworkManager.sendToPlayer(player, buildClearedSyncPacket());
            return;
        }
        RoomPosition start = serverConfig().getParkourStartPosFallbackShared();
        RoomPosition end = serverConfig().getParkourEndPosFallbackShared();
        NetworkManager.sendToPlayer(player, new ParkourSessionSyncPacket(
            true,
            session.challengeActive(),
            session.runNonce(),
            start.x(),
            start.y(),
            start.z(),
            end.x(),
            end.y(),
            end.z()
        ));
    }

    private ParkourSessionSyncPacket buildClearedSyncPacket() {
        return new ParkourSessionSyncPacket(false, false, 0, 0, 0.0D, 0, 0, 0.0D, 0);
    }

    private ServerConfigStore serverConfig() {
        return ConquestDataManager.of(server).serverConfig();
    }

    private ServerActionBarService.Builder buildStartPromptActionbar() {
        return ServerActionBarService.builder()
            .append(Component.literal("按下").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("[F]").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("开始跑酷挑战").withStyle(ChatFormatting.WHITE));
    }

    private MutableComponent buildCompletionActionbar(long elapsedMillis, int rank) {
        ChatFormatting rankColor = resolveRankColor(rank);
        return Component.empty()
            .append(Component.literal("已完成跑酷！").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("用时").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(formatElapsed(elapsedMillis)).withStyle(rankColor))
            .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("全服排名 ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(String.valueOf(Math.max(1, rank))).withStyle(rankColor));
    }

    private ChatFormatting resolveRankColor(int rank) {
        return switch (rank) {
            case 1 -> ChatFormatting.RED;
            case 2 -> ChatFormatting.GOLD;
            case 3 -> ChatFormatting.YELLOW;
            default -> ChatFormatting.BLUE;
        };
    }

    private String formatElapsed(long elapsedMillis) {
        long totalSeconds = elapsedMillis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long millis = elapsedMillis % 1000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private ResolvedPlayer resolvePlayerByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        String normalized = playerName.trim();
        ServerPlayer online = server.getPlayerList().getPlayerByName(normalized);
        if (online != null) {
            return new ResolvedPlayer(online.getUUID(), online.getScoreboardName());
        }
        for (UUID uuid : ConquestDataManager.of(server).playerData().listKnownPlayerIds()) {
            if (uuid == null) {
                continue;
            }
            String knownName = ConquestDataManager.of(server).playerData().getPlayerData(uuid).getLastKnownPlayerName();
            if (knownName != null && knownName.equalsIgnoreCase(normalized)) {
                return new ResolvedPlayer(uuid, knownName);
            }
        }
        return null;
    }

    private record RecordRow(UUID playerId, String playerName, long bestTimeMillis, long bestUpdatedAtEpochMs) {
    }

    private record RankedRecord(int rank, UUID playerId, String playerName, long timeMillis) {
    }

    private record PlayerStats(int rank, long bestTimeMillis, long lastTimeMillis) {
    }

    private record SubmissionResult(int rank) {
    }

    public record RemoveRecordResult(boolean success, String playerName, String message) {
    }

    private record ResolvedPlayer(UUID playerId, String playerName) {
    }

    private static final class ParkourPlayerSession {
        private final String inventorySnapshot;
        private boolean challengeActive;
        private boolean finishSubmitted;
        private long statusUntilTick;
        private int runNonce;

        private ParkourPlayerSession(String inventorySnapshot) {
            this.inventorySnapshot = inventorySnapshot == null ? "" : inventorySnapshot;
        }

        public String inventorySnapshot() {
            return inventorySnapshot;
        }

        public boolean challengeActive() {
            return challengeActive;
        }

        public void setChallengeActive(boolean challengeActive) {
            this.challengeActive = challengeActive;
        }

        public boolean finishSubmitted() {
            return finishSubmitted;
        }

        public void setFinishSubmitted(boolean finishSubmitted) {
            this.finishSubmitted = finishSubmitted;
        }

        public long statusUntilTick() {
            return statusUntilTick;
        }

        public void setStatusUntilTick(long statusUntilTick) {
            this.statusUntilTick = statusUntilTick;
        }

        public int runNonce() {
            return runNonce;
        }

        public void beginNewRun(long currentTick) {
            runNonce++;
            challengeActive = true;
            finishSubmitted = false;
            statusUntilTick = currentTick + 40L;
        }
    }
}
