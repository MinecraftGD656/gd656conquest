package org.mods.gd656conquest.server.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.common.pack.PackAuditConstants;
import org.mods.gd656conquest.common.pack.PackAuditEntry;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientboundPackAuditRequestPacket;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.pack.PackAuditLogEntry;
import org.mods.gd656conquest.server.data.pack.PackAuditLogStore;
import org.mods.gd656conquest.server.data.pack.PackWhitelistStore;
import org.mods.gd656conquest.server.shared.log.ServerLogger;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

import net.minecraft.commands.CommandSourceStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PackValidationService {
    private static final long LOGIN_TIMEOUT_TICKS = 20L * 20L;
    private static final long MANUAL_UPDATE_TIMEOUT_TICKS = 20L * 20L;
    private static final Map<MinecraftServer, PackValidationService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, Long> pendingLoginDeadlines = new LinkedHashMap<>();
    private final Map<String, PendingManualUpdateRequest> pendingManualUpdateRequests = new LinkedHashMap<>();

    private PackValidationService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static PackValidationService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, PackValidationService::new);
        }
    }

    public static void forget(MinecraftServer server) {
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public void onPlayerLogin(ServerPlayer player) {
        if (player == null || !isValidationEnabled() || shouldBypassValidation(player)) {
            return;
        }
        pendingLoginDeadlines.put(player.getUUID(), server.getTickCount() + LOGIN_TIMEOUT_TICKS);
    }

    public void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        pendingLoginDeadlines.remove(player.getUUID());
        pendingManualUpdateRequests.entrySet().removeIf(entry -> entry.getValue().requesterId().equals(player.getUUID()));
    }

    public void onServerTick(long gameTick) {
        handleLoginTimeouts(gameTick);
        handleManualUpdateTimeouts(gameTick);
    }

    public int requestManualWhitelistUpdate(CommandSourceStack source, ServerPlayer requester) {
        if (source == null || requester == null) {
            return CommandFeedbacks.failure(source, "只有在线玩家可以执行 pack update");
        }
        String requestId = UUID.randomUUID().toString();
        pendingManualUpdateRequests.put(requestId, new PendingManualUpdateRequest(
            requestId,
            requester.getUUID(),
            requester.getScoreboardName(),
            server.getTickCount() + MANUAL_UPDATE_TIMEOUT_TICKS
        ));
        NetworkManager.sendToPlayer(requester, new ClientboundPackAuditRequestPacket(requestId, PackAuditConstants.REASON_MANUAL_UPDATE));
        return CommandFeedbacks.info(source, "已开始采集当前客户端安装的材质包与光影包，请稍候...");
    }

    public int listWhitelist(CommandSourceStack source) {
        List<PackAuditEntry> entries = whitelistStore().get().getEntries();
        if (entries.isEmpty()) {
            return CommandFeedbacks.info(source, "当前白名单为空");
        }
        CommandFeedbacks.line(source, Component.literal("========== Pack 白名单 =========="));
        int index = 1;
        for (PackAuditEntry entry : entries) {
            String line = index + ". [" + entry.getType() + "] " + entry.getFileName()
                + " | sha256=" + entry.getSha256()
                + " | size=" + entry.getSize()
                + " | source=" + entry.getSourceKind();
            CommandFeedbacks.line(source, Component.literal(line));
            index++;
        }
        return CommandFeedbacks.info(source, "白名单数量: " + entries.size());
    }

    public int deleteWhitelistEntry(CommandSourceStack source, String fileName) {
        List<PackAuditEntry> removed = whitelistStore().removeByFileName(fileName);
        if (removed.isEmpty()) {
            return CommandFeedbacks.failure(source, "未找到白名单项: " + fileName);
        }
        return CommandFeedbacks.info(source, "已删除白名单项 " + removed.size() + " 条: " + fileName);
    }

    public List<String> suggestWhitelistFileNames() {
        return whitelistStore().suggestFileNames();
    }

    public void handleClientAuditReport(
        ServerPlayer player,
        String requestId,
        String reason,
        boolean success,
        String message,
        List<PackAuditEntry> entries
    ) {
        if (player == null) {
            return;
        }
        if (shouldBypassValidation(player)) {
            pendingLoginDeadlines.remove(player.getUUID());
            if (requestId != null && !requestId.isBlank()) {
                pendingManualUpdateRequests.remove(requestId);
            }
            return;
        }
        PendingManualUpdateRequest manualRequest = requestId == null || requestId.isBlank()
            ? null
            : pendingManualUpdateRequests.remove(requestId);
        if (manualRequest != null) {
            handleManualUpdateReport(player, manualRequest, success, message, entries);
            return;
        }

        boolean hadPendingLogin = pendingLoginDeadlines.remove(player.getUUID()) != null;
        if (!success) {
            if (hadPendingLogin || isValidationEnabled()) {
                kickForViolation(player, reason, "客户端上报失败: " + normalizeMessage(message), List.of(), List.of());
            }
            return;
        }

        List<PackAuditEntry> normalizedEntries = normalizeEntries(entries);
        if (!isValidationEnabled()) {
            return;
        }

        ValidationResult result = validateAgainstWhitelist(normalizedEntries);
        if (!result.illegalEntries().isEmpty()) {
            kickForViolation(player, reason, "检测到未在白名单内的安装包", result.illegalEntries(), result.missingEntries());
        }
    }

    private void handleManualUpdateReport(
        ServerPlayer player,
        PendingManualUpdateRequest request,
        boolean success,
        String message,
        List<PackAuditEntry> entries
    ) {
        if (!request.requesterId().equals(player.getUUID())) {
            return;
        }
        if (!success) {
            CommandFeedbacks.failure(player.createCommandSourceStack(), "采集失败: " + normalizeMessage(message));
            return;
        }
        List<PackAuditEntry> normalizedEntries = normalizeEntries(entries);
        PackWhitelistStore.AppendResult result = whitelistStore().appendEntries(normalizedEntries);
        CommandFeedbacks.info(
            player.createCommandSourceStack(),
            "白名单已更新: 新增=" + result.addedCount() + "，已存在=" + result.existingCount() + "，总数=" + result.totalCount()
        );
    }

    private void handleLoginTimeouts(long gameTick) {
        if (pendingLoginDeadlines.isEmpty()) {
            return;
        }
        List<UUID> expiredPlayers = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : pendingLoginDeadlines.entrySet()) {
            if (gameTick >= entry.getValue()) {
                expiredPlayers.add(entry.getKey());
            }
        }
        for (UUID uuid : expiredPlayers) {
            pendingLoginDeadlines.remove(uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && !shouldBypassValidation(player)) {
                kickForViolation(player, PackAuditConstants.REASON_LOGIN, "客户端未按时上报资源包校验数据", List.of(), List.of());
            }
        }
    }

    private void handleManualUpdateTimeouts(long gameTick) {
        if (pendingManualUpdateRequests.isEmpty()) {
            return;
        }
        List<String> expiredRequestIds = new ArrayList<>();
        for (Map.Entry<String, PendingManualUpdateRequest> entry : pendingManualUpdateRequests.entrySet()) {
            if (gameTick >= entry.getValue().deadlineTick()) {
                expiredRequestIds.add(entry.getKey());
            }
        }
        for (String requestId : expiredRequestIds) {
            PendingManualUpdateRequest request = pendingManualUpdateRequests.remove(requestId);
            if (request == null) {
                continue;
            }
            ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId());
            if (requester != null) {
                CommandFeedbacks.failure(requester.createCommandSourceStack(), "pack update 超时，未收到客户端回传");
            } else {
                ServerLogger.failure("pack update 超时: %s", request.requesterName());
            }
        }
    }

    private ValidationResult validateAgainstWhitelist(List<PackAuditEntry> entries) {
        Set<String> whitelistKeys = new LinkedHashSet<>();
        List<PackAuditEntry> whitelistEntries = whitelistStore().get().getEntries();
        for (PackAuditEntry entry : whitelistEntries) {
            if (entry != null && entry.isValid()) {
                whitelistKeys.add(entry.identityKey());
            }
        }

        List<PackAuditEntry> illegalEntries = new ArrayList<>();
        for (PackAuditEntry entry : entries) {
            if (entry == null || !entry.isValid()) {
                continue;
            }
            if (!whitelistKeys.contains(entry.identityKey())) {
                illegalEntries.add(entry);
            }
        }

        Set<String> installedKeys = new LinkedHashSet<>();
        for (PackAuditEntry entry : entries) {
            if (entry != null && entry.isValid()) {
                installedKeys.add(entry.identityKey());
            }
        }
        List<PackAuditEntry> missingEntries = new ArrayList<>();
        for (PackAuditEntry entry : whitelistEntries) {
            if (entry != null && entry.isValid() && !installedKeys.contains(entry.identityKey())) {
                missingEntries.add(entry);
            }
        }
        return new ValidationResult(illegalEntries, missingEntries);
    }

    private void kickForViolation(
        ServerPlayer player,
        String trigger,
        String reason,
        List<PackAuditEntry> illegalEntries,
        List<PackAuditEntry> missingEntries
    ) {
        if (player == null || player.connection == null) {
            return;
        }
        String playerName = player.getScoreboardName();
        String kickTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        ServerLogger.failure(
            "[PACK][KICK] 玩家=%s 时间=%s 触发=%s 原因=%s 违规包=%s 缺少白名单包=%s",
            playerName,
            kickTime,
            blankToDash(trigger),
            reason,
            summarizeEntries(illegalEntries),
            summarizeEntries(missingEntries)
        );

        PackAuditLogEntry logEntry = new PackAuditLogEntry();
        logEntry.setPlayerName(playerName);
        logEntry.setPlayerUuid(player.getUUID().toString());
        logEntry.setKickTime(kickTime);
        logEntry.setTrigger(trigger);
        logEntry.setReason(reason);
        logEntry.setDisconnectMessage(PackAuditConstants.DISCONNECT_MESSAGE);
        logEntry.setIllegalEntries(illegalEntries);
        logEntry.setMissingWhitelistEntries(missingEntries);
        auditLogStore().append(logEntry);

        player.connection.disconnect(Component.literal(PackAuditConstants.DISCONNECT_MESSAGE));
    }

    private List<PackAuditEntry> normalizeEntries(List<PackAuditEntry> entries) {
        Map<String, PackAuditEntry> normalized = new LinkedHashMap<>();
        for (PackAuditEntry entry : entries == null ? List.<PackAuditEntry>of() : entries) {
            if (entry == null) {
                continue;
            }
            PackAuditEntry cleaned = new PackAuditEntry(
                entry.getType(),
                entry.getFileName(),
                entry.getSha256(),
                entry.getSize(),
                entry.getSourceKind()
            );
            if (!cleaned.isValid()) {
                continue;
            }
            normalized.putIfAbsent(cleaned.identityKey(), cleaned);
        }
        return new ArrayList<>(normalized.values());
    }

    private boolean isValidationEnabled() {
        return ConquestDataManager.of(server).serverConfig().isPackValidationEnabled();
    }

    private boolean shouldBypassValidation(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    private PackWhitelistStore whitelistStore() {
        return ConquestDataManager.of(server).packWhitelist();
    }

    private PackAuditLogStore auditLogStore() {
        return ConquestDataManager.of(server).packAuditLog();
    }

    private static String summarizeEntries(List<PackAuditEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (PackAuditEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            parts.add(entry.getType() + ":" + entry.getFileName() + ":" + entry.getSha256());
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    private static String normalizeMessage(String message) {
        return message == null || message.isBlank() ? "未知错误" : message;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record PendingManualUpdateRequest(
        String requestId,
        UUID requesterId,
        String requesterName,
        long deadlineTick
    ) {
    }

    private record ValidationResult(List<PackAuditEntry> illegalEntries, List<PackAuditEntry> missingEntries) {
    }
}
