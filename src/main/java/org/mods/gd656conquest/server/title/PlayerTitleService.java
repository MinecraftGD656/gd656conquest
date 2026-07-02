package org.mods.gd656conquest.server.title;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.player.level.PlayerLevelService;
import org.mods.gd656conquest.server.shared.contract.ClickActionSpec;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;
import org.mods.gd656conquest.server.shared.text.ServerText;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;

public final class PlayerTitleService {
    public static final String DEFAULT_TITLE_ID = "NEWCOMER";
    public static final String DEFAULT_TITLE_TEXT = "&7\u521d\u6765\u4e4d\u5230";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, PlayerTitleService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final PlayerTitleRegistryStore registryStore;

    private PlayerTitleService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.registryStore = ConquestDataManager.of(server).playerTitles();
        ensureDefaultDefinitionRegistered();
    }

    public static PlayerTitleService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, PlayerTitleService::new);
        }
    }

    public static void forget(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    public static String normalizeTitleId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String normalizeTitleText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replace('\u00a7', '&');
    }

    public void initializePlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ensurePlayerDefaults(player.getUUID(), player.getScoreboardName());
    }

    public Optional<PlayerTitleRegistryStore.TitleDefinition> findTitle(String titleId) {
        return registryStore.find(titleId);
    }

    public List<String> suggestTitleIds() {
        return registryStore.getAll().keySet().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    public List<String> suggestKnownPlayerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.getScoreboardName() != null && !player.getScoreboardName().isBlank()) {
                names.add(player.getScoreboardName());
            }
        }
        for (PlayerDataSnapshot snapshot : readAllPlayerSnapshots()) {
            if (snapshot.lastKnownPlayerName != null && !snapshot.lastKnownPlayerName.isBlank()) {
                names.add(snapshot.lastKnownPlayerName);
            }
        }
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> suggestImportFileNames() {
        Path directory = ServerDataPaths.getFilesDirectory(server);
        ensureDirectory(directory);
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public boolean addTitleDefinition(String titleId, String rawText) {
        ensureDefaultDefinitionRegistered();
        return registryStore.saveTitle(normalizeTitleId(titleId), normalizeTitleText(rawText));
    }

    public boolean editTitleDefinition(String titleId, String rawText) {
        ensureDefaultDefinitionRegistered();
        return registryStore.updateTitle(normalizeTitleId(titleId), normalizeTitleText(rawText));
    }

    public GrantResult giveTitle(String titleId, String playerNameOrUuid, String giverName) {
        String normalizedTitleId = normalizeTitleId(titleId);
        PlayerTitleRegistryStore.TitleDefinition definition = registryStore.find(normalizedTitleId).orElse(null);
        if (definition == null) {
            return new GrantResult(false, normalizedTitleId, playerNameOrUuid, null, "未找到称号: " + normalizedTitleId);
        }
        ResolvedPlayer resolved = resolvePlayer(playerNameOrUuid);
        if (resolved == null) {
            return new GrantResult(false, normalizedTitleId, playerNameOrUuid, null, "未找到玩家: " + playerNameOrUuid);
        }
        PlayerDataModel data = ensurePlayerDefaults(resolved.playerId(), resolved.playerName());
        boolean changed = grantTitleToPlayer(resolved.playerId(), resolved.playerName(), data, definition, giverName);
        return new GrantResult(true, normalizedTitleId, resolved.playerName(), resolved.playerId(), changed ? "已给予称号" : "该玩家已拥有该称号");
    }

    public GrantResult giveTitleDirect(UUID playerId, String preferredName, String titleId, String giverName) {
        String normalizedTitleId = normalizeTitleId(titleId);
        PlayerTitleRegistryStore.TitleDefinition definition = registryStore.find(normalizedTitleId).orElse(null);
        if (definition == null) {
            return new GrantResult(false, normalizedTitleId, preferredName == null ? "" : preferredName, playerId, "未找到称号: " + normalizedTitleId);
        }
        if (playerId == null) {
            return new GrantResult(false, normalizedTitleId, preferredName == null ? "" : preferredName, null, "玩家不存在");
        }
        String resolvedName = preferredName == null || preferredName.isBlank() ? playerId.toString() : preferredName.trim();
        PlayerDataModel data = ensurePlayerDefaults(playerId, resolvedName);
        boolean changed = grantTitleToPlayer(playerId, resolvedName, data, definition, giverName);
        return new GrantResult(true, normalizedTitleId, resolvedName, playerId, changed ? "已给予称号" : "该玩家已拥有该称号");
    }

    public RemoveResult removeAllTitles(String playerNameOrUuid) {
        ResolvedPlayer resolved = resolvePlayer(playerNameOrUuid);
        if (resolved == null) {
            return new RemoveResult(false, playerNameOrUuid, null, "未找到玩家: " + playerNameOrUuid);
        }
        PlayerDataModel data = ensurePlayerDefaults(resolved.playerId(), resolved.playerName());
        boolean changed = false;
        Set<String> retained = new LinkedHashSet<>();
        retained.add(DEFAULT_TITLE_ID);
        if (!retained.equals(data.getOwnedTitleIds())) {
            data.setOwnedTitleIds(retained);
            changed = true;
        }
        if (!DEFAULT_TITLE_ID.equals(data.getEquippedTitleId())) {
            data.setEquippedTitleId(DEFAULT_TITLE_ID);
            changed = true;
        }
        changed |= removePendingNoticesByFilter(data, null);
        if (changed) {
            savePlayerData(resolved.playerId(), data);
        }
        return new RemoveResult(true, resolved.playerName(), resolved.playerId(), changed ? "已清空该玩家的自定义称号并恢复默认称号" : "该玩家当前仅保留默认称号");
    }

    public RemoveResult removeSingleTitle(String playerNameOrUuid, String titleId) {
        String normalizedTitleId = normalizeTitleId(titleId);
        if (DEFAULT_TITLE_ID.equals(normalizedTitleId)) {
            return new RemoveResult(false, playerNameOrUuid, null, "默认称号不可删除");
        }
        ResolvedPlayer resolved = resolvePlayer(playerNameOrUuid);
        if (resolved == null) {
            return new RemoveResult(false, playerNameOrUuid, null, "未找到玩家: " + playerNameOrUuid);
        }
        PlayerDataModel data = ensurePlayerDefaults(resolved.playerId(), resolved.playerName());
        boolean changed = data.getOwnedTitleIds().remove(normalizedTitleId);
        if (normalizedTitleId.equals(data.getEquippedTitleId())) {
            data.setEquippedTitleId(DEFAULT_TITLE_ID);
            changed = true;
        }
        changed |= removePendingNoticesByFilter(data, normalizedTitleId);
        if (changed) {
            savePlayerData(resolved.playerId(), data);
        }
        return new RemoveResult(true, resolved.playerName(), resolved.playerId(), changed ? "已删除指定称号" : "该玩家未持有该称号");
    }

    public RemoveResult removeSingleTitleDirect(UUID playerId, String preferredName, String titleId) {
        String normalizedTitleId = normalizeTitleId(titleId);
        if (DEFAULT_TITLE_ID.equals(normalizedTitleId)) {
            return new RemoveResult(false, preferredName == null ? "" : preferredName, playerId, "默认称号不可删除");
        }
        if (playerId == null) {
            return new RemoveResult(false, preferredName == null ? "" : preferredName, null, "玩家不存在");
        }
        String resolvedName = preferredName == null || preferredName.isBlank() ? playerId.toString() : preferredName.trim();
        PlayerDataModel data = ensurePlayerDefaults(playerId, resolvedName);
        boolean changed = data.getOwnedTitleIds().remove(normalizedTitleId);
        if (normalizedTitleId.equals(data.getEquippedTitleId())) {
            data.setEquippedTitleId(DEFAULT_TITLE_ID);
            changed = true;
        }
        changed |= removePendingNoticesByFilter(data, normalizedTitleId);
        if (changed) {
            savePlayerData(playerId, data);
        }
        return new RemoveResult(true, resolvedName, playerId, changed ? "已删除指定称号" : "该玩家未持有该称号");
    }

    public WearResult wearTitle(ServerPlayer player, String titleId) {
        if (player == null) {
            return new WearResult(false, "玩家不存在");
        }
        String normalizedTitleId = normalizeTitleId(titleId);
        PlayerTitleRegistryStore.TitleDefinition definition = registryStore.find(normalizedTitleId).orElse(null);
        if (definition == null) {
            return new WearResult(false, "未找到称号: " + normalizedTitleId);
        }
        PlayerDataModel data = ensurePlayerDefaults(player.getUUID(), player.getScoreboardName());
        if (!data.getOwnedTitleIds().contains(normalizedTitleId)) {
            return new WearResult(false, "你尚未拥有该称号");
        }
        if (normalizedTitleId.equals(data.getEquippedTitleId())) {
            return new WearResult(false, "该称号已佩戴");
        }
        data.setEquippedTitleId(normalizedTitleId);
        savePlayerData(player.getUUID(), data);
        return new WearResult(true, "已佩戴称号");
    }

    public WearResult clearEquippedTitle(ServerPlayer player) {
        if (player == null) {
            return new WearResult(false, "玩家不存在");
        }
        PlayerDataModel data = ensurePlayerDefaults(player.getUUID(), player.getScoreboardName());
        if (DEFAULT_TITLE_ID.equals(data.getEquippedTitleId())) {
            return new WearResult(false, "当前已佩戴默认称号");
        }
        data.setEquippedTitleId(DEFAULT_TITLE_ID);
        savePlayerData(player.getUUID(), data);
        return new WearResult(true, "已恢复默认称号");
    }

    public void sendTitleList(CommandSourceStack source) {
        Map<String, Integer> counts = countOwnedPlayersByTitle();
        CommandFeedbacks.line(source, Component.literal("========== 称号列表 ==========").withStyle(ChatFormatting.GOLD));
        CommandFeedbacks.line(
            source,
            Component.literal("称号ID").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("称号").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("玩家数").withStyle(ChatFormatting.WHITE))
        );
        for (PlayerTitleRegistryStore.TitleDefinition definition : registryStore.getAll().values().stream()
            .sorted(Comparator.comparing(PlayerTitleRegistryStore.TitleDefinition::id, String.CASE_INSENSITIVE_ORDER))
            .toList()) {
            CommandFeedbacks.line(
                source,
                Component.empty()
                    .append(Component.literal(definition.id()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                    .append(parseLegacyText(definition.text()))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(String.valueOf(counts.getOrDefault(definition.id(), 0))).withStyle(ChatFormatting.WHITE))
            );
        }
        CommandFeedbacks.line(source, Component.literal("共 " + registryStore.getAll().size() + " 个称号").withStyle(ChatFormatting.GRAY));
    }

    public void sendMyTitleList(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerDataModel data = ensurePlayerDefaults(player.getUUID(), player.getScoreboardName());
        List<PlayerTitleRegistryStore.TitleDefinition> titles = resolveOwnedTitles(data);
        player.sendSystemMessage(Component.literal("========== 我的称号 ==========").withStyle(ChatFormatting.GOLD));
        for (PlayerTitleRegistryStore.TitleDefinition title : titles) {
            boolean equipped = title.id().equals(data.getEquippedTitleId());
            MutableComponent line = Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(parseLegacyText(title.text()))
                .append(Component.literal(" "));
            if (equipped) {
                line.append(Component.literal("[已佩戴]").withStyle(ChatFormatting.GRAY));
            } else {
                line.append(ServerText.action(new ClickActionSpec(
                    "[佩戴称号]",
                    ChatFormatting.GOLD,
                    "/gdc action weartitle " + title.id(),
                    "点击佩戴该称号"
                )));
            }
            player.sendSystemMessage(line);
        }
        player.sendSystemMessage(
            Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(ServerText.action(new ClickActionSpec(
                    "[取消称号佩戴]",
                    ChatFormatting.RED,
                    "/gdc action cleartitle",
                    "点击恢复默认称号"
                )))
        );
    }

    public void deliverPendingNotices(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerDataModel data = ensurePlayerDefaults(player.getUUID(), player.getScoreboardName());
        List<PlayerDataModel.PendingTitleNotice> notices = new ArrayList<>(data.getPendingTitleNotices());
        if (notices.isEmpty()) {
            return;
        }
        for (PlayerDataModel.PendingTitleNotice notice : notices) {
            if (notice == null || notice.getTitleId().isBlank() || notice.getTitleText().isBlank()) {
                continue;
            }
            String resolvedText = findTitle(notice.getTitleId())
                .map(PlayerTitleRegistryStore.TitleDefinition::text)
                .orElse(notice.getTitleText());
            sendGrantedTitleNotification(player, notice.getGiverName(), notice.getTitleId(), resolvedText);
        }
        data.getPendingTitleNotices().clear();
        savePlayerData(player.getUUID(), data);
    }

    public MutableComponent buildChatLevelPrefix(ServerPlayer player, int level) {
        int levelColor = PlayerLevelService.resolveLevelColorRgb(level);
        MutableComponent prefix = Component.literal("[").withStyle(style -> style.withColor(levelColor));
        String equippedTitleId = ensurePlayerDefaults(player.getUUID(), player.getScoreboardName()).getEquippedTitleId();
        PlayerTitleRegistryStore.TitleDefinition equippedTitle = registryStore.find(equippedTitleId).orElse(null);
        if (equippedTitle == null || equippedTitle.text() == null || equippedTitle.text().isBlank()) {
            prefix.append(Component.literal(String.valueOf(level)).withStyle(style -> style.withColor(levelColor)));
        } else {
            prefix.append(parseLegacyText(equippedTitle.text()));
            prefix.append(Component.literal("-").withStyle(ChatFormatting.GRAY));
            prefix.append(Component.literal(String.valueOf(level)).withStyle(style -> style.withColor(levelColor)));
        }
        prefix.append(Component.literal("] ").withStyle(style -> style.withColor(levelColor)));
        return prefix;
    }

    public ExampleResult writeExampleFile(String rawName) {
        String fileStem = sanitizeFileStem(rawName);
        Path directory = ServerDataPaths.getFilesDirectory(server);
        ensureDirectory(directory);
        Path file = directory.resolve(fileStem + ".json");
        List<String> playerNames = server.getPlayerList().getPlayers().stream()
            .map(ServerPlayer::getScoreboardName)
            .filter(Objects::nonNull)
            .filter(name -> !name.isBlank())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        ExamplePayload payload = new ExamplePayload(
            Instant.now().toString(),
            playerNames.isEmpty() ? List.of("PlayerOne", "PlayerTwo") : playerNames
        );
        writeJson(file, payload);
        return new ExampleResult(file, payload.playerNames().size());
    }

    public ImportResult importPlayersFromFile(String rawFileName, String titleId, String giverName) {
        String normalizedTitleId = normalizeTitleId(titleId);
        PlayerTitleRegistryStore.TitleDefinition definition = registryStore.find(normalizedTitleId).orElse(null);
        if (definition == null) {
            return new ImportResult(false, normalizedTitleId, null, 0, 0, List.of(), "未找到称号: " + normalizedTitleId);
        }
        String fileName = normalizeJsonFileName(rawFileName);
        Path file = ServerDataPaths.getFilesDirectory(server).resolve(fileName);
        if (Files.notExists(file)) {
            return new ImportResult(false, normalizedTitleId, file, 0, 0, List.of(), "未找到文件: " + fileName);
        }
        List<String> playerNames = readImportPlayerNames(file);
        if (playerNames.isEmpty()) {
            return new ImportResult(false, normalizedTitleId, file, 0, 0, List.of(), "文件中未找到可导入的玩家名");
        }
        int granted = 0;
        int skipped = 0;
        List<String> unresolved = new ArrayList<>();
        for (String playerName : playerNames) {
            ResolvedPlayer resolved = resolvePlayer(playerName);
            if (resolved == null) {
                unresolved.add(playerName);
                continue;
            }
            PlayerDataModel data = ensurePlayerDefaults(resolved.playerId(), resolved.playerName());
            if (grantTitleToPlayer(resolved.playerId(), resolved.playerName(), data, definition, giverName)) {
                granted++;
            } else {
                skipped++;
            }
        }
        return new ImportResult(true, normalizedTitleId, file, granted, skipped, List.copyOf(unresolved), "称号批量导入完成");
    }

    private boolean grantTitleToPlayer(
        UUID playerId,
        String playerName,
        PlayerDataModel data,
        PlayerTitleRegistryStore.TitleDefinition definition,
        String giverName
    ) {
        if (playerId == null || data == null || definition == null) {
            return false;
        }
        boolean changed = data.getOwnedTitleIds().add(definition.id());
        if (!changed) {
            return false;
        }
        String resolvedGiverName = giverName == null || giverName.isBlank() ? "系统" : giverName.trim();
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
        if (onlinePlayer != null) {
            sendGrantedTitleNotification(onlinePlayer, resolvedGiverName, definition.id(), definition.text());
        } else {
            PlayerDataModel.PendingTitleNotice notice = new PlayerDataModel.PendingTitleNotice();
            notice.setGiverName(resolvedGiverName);
            notice.setTitleId(definition.id());
            notice.setTitleText(definition.text());
            data.getPendingTitleNotices().add(notice);
        }
        savePlayerData(playerId, data);
        return true;
    }

    private void sendGrantedTitleNotification(ServerPlayer player, String giverName, String titleId, String titleText) {
        if (player == null || titleId == null || titleId.isBlank() || titleText == null || titleText.isBlank()) {
            return;
        }
        String resolvedGiverName = giverName == null || giverName.isBlank() ? "系统" : giverName.trim();
        MutableComponent message = Component.literal("您收到了来自 ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(resolvedGiverName).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" 的新称号 ").withStyle(ChatFormatting.AQUA))
            .append(parseLegacyText(titleText))
            .append(Component.literal("！").withStyle(ChatFormatting.AQUA))
            .append(ServerText.action(new ClickActionSpec(
                "[点击佩戴]",
                ChatFormatting.GOLD,
                "/gdc action weartitle " + titleId,
                "点击立即佩戴该称号"
            )));
        ServerMessageSender.system(player, "称号", message);
    }

    private boolean removePendingNoticesByFilter(PlayerDataModel data, String titleId) {
        if (data == null || data.getPendingTitleNotices().isEmpty()) {
            return false;
        }
        int before = data.getPendingTitleNotices().size();
        if (titleId == null || titleId.isBlank()) {
            data.getPendingTitleNotices().clear();
        } else {
            data.getPendingTitleNotices().removeIf(notice -> notice != null && titleId.equalsIgnoreCase(notice.getTitleId()));
        }
        return before != data.getPendingTitleNotices().size();
    }

    private void ensureDefaultDefinitionRegistered() {
        if (registryStore.find(DEFAULT_TITLE_ID).isPresent()) {
            return;
        }
        registryStore.saveTitle(DEFAULT_TITLE_ID, DEFAULT_TITLE_TEXT);
    }

    private PlayerDataModel ensurePlayerDefaults(UUID playerId, String preferredName) {
        PlayerDataStore store = ConquestDataManager.of(server).playerData();
        PlayerDataModel data = store.getPlayerData(playerId);
        boolean changed = false;
        if (preferredName != null && !preferredName.isBlank() && !preferredName.equals(data.getLastKnownPlayerName())) {
            data.setLastKnownPlayerName(preferredName);
            changed = true;
        }
        if (!data.getOwnedTitleIds().contains(DEFAULT_TITLE_ID)) {
            data.getOwnedTitleIds().add(DEFAULT_TITLE_ID);
            changed = true;
        }
        if (data.getEquippedTitleId().isBlank() || !data.getOwnedTitleIds().contains(data.getEquippedTitleId())) {
            data.setEquippedTitleId(DEFAULT_TITLE_ID);
            changed = true;
        }
        if (changed) {
            savePlayerData(playerId, data);
        }
        return data;
    }

    private void savePlayerData(UUID playerId, PlayerDataModel data) {
        ConquestDataManager.of(server).playerData().savePlayerData(playerId, data);
    }

    private List<PlayerTitleRegistryStore.TitleDefinition> resolveOwnedTitles(PlayerDataModel data) {
        List<PlayerTitleRegistryStore.TitleDefinition> titles = new ArrayList<>();
        if (data == null) {
            return titles;
        }
        for (String titleId : data.getOwnedTitleIds()) {
            PlayerTitleRegistryStore.TitleDefinition definition = registryStore.find(titleId).orElse(null);
            if (definition != null) {
                titles.add(definition);
            }
        }
        titles.sort(Comparator.comparing(PlayerTitleRegistryStore.TitleDefinition::id, String.CASE_INSENSITIVE_ORDER));
        return titles;
    }

    private Map<String, Integer> countOwnedPlayersByTitle() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String titleId : registryStore.getAll().keySet()) {
            counts.put(titleId, 0);
        }
        for (PlayerDataSnapshot snapshot : readAllPlayerSnapshots()) {
            Set<String> owned = normalizeOwnedTitles(snapshot.ownedTitleIds);
            if (owned.isEmpty()) {
                owned.add(DEFAULT_TITLE_ID);
            }
            for (String titleId : owned) {
                counts.merge(titleId, 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<PlayerDataSnapshot> readAllPlayerSnapshots() {
        Path directory = ServerDataPaths.getPlayerDataDirectory(server);
        ensureDirectory(directory);
        List<PlayerDataSnapshot> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(path -> {
                    PlayerDataSnapshot snapshot = readPlayerSnapshot(path);
                    if (snapshot != null) {
                        result.add(snapshot);
                    }
                });
        } catch (Exception ignored) {
        }
        return result;
    }

    private PlayerDataSnapshot readPlayerSnapshot(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            PlayerDataModel data = GSON.fromJson(reader, PlayerDataModel.class);
            if (data == null) {
                data = new PlayerDataModel();
            }
            String fileName = path.getFileName().toString();
            String uuidText = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
            UUID playerId = UUID.fromString(uuidText);
            return new PlayerDataSnapshot(
                playerId,
                data.getLastKnownPlayerName(),
                normalizeOwnedTitles(data.getOwnedTitleIds()),
                normalizeTitleId(data.getEquippedTitleId())
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private Set<String> normalizeOwnedTitles(Collection<String> rawTitleIds) {
        Set<String> result = new LinkedHashSet<>();
        if (rawTitleIds == null) {
            return result;
        }
        for (String rawTitleId : rawTitleIds) {
            String normalized = normalizeTitleId(rawTitleId);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private ResolvedPlayer resolvePlayer(String playerNameOrUuid) {
        if (playerNameOrUuid == null || playerNameOrUuid.isBlank()) {
            return null;
        }
        String normalizedInput = playerNameOrUuid.trim();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getScoreboardName().equalsIgnoreCase(normalizedInput)) {
                return new ResolvedPlayer(player.getUUID(), player.getScoreboardName());
            }
        }
        try {
            UUID uuid = UUID.fromString(normalizedInput);
            return new ResolvedPlayer(uuid, resolvePlayerName(uuid).orElse(uuid.toString()));
        } catch (Exception ignored) {
        }
        try {
            Optional<GameProfile> cachedProfile = server.getProfileCache().get(normalizedInput);
            if (cachedProfile.isPresent() && cachedProfile.get().getId() != null) {
                return new ResolvedPlayer(cachedProfile.get().getId(), cachedProfile.get().getName());
            }
        } catch (Exception ignored) {
        }
        for (PlayerDataSnapshot snapshot : readAllPlayerSnapshots()) {
            if (snapshot.lastKnownPlayerName != null && snapshot.lastKnownPlayerName.equalsIgnoreCase(normalizedInput)) {
                return new ResolvedPlayer(snapshot.playerId, snapshot.lastKnownPlayerName);
            }
        }
        return null;
    }

    private Optional<String> resolvePlayerName(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null && online.getScoreboardName() != null && !online.getScoreboardName().isBlank()) {
            return Optional.of(online.getScoreboardName());
        }
        try {
            Optional<GameProfile> profile = server.getProfileCache().get(playerId);
            if (profile.isPresent() && profile.get().getName() != null && !profile.get().getName().isBlank()) {
                return Optional.of(profile.get().getName());
            }
        } catch (Exception ignored) {
        }
        for (PlayerDataSnapshot snapshot : readAllPlayerSnapshots()) {
            if (playerId.equals(snapshot.playerId) && snapshot.lastKnownPlayerName != null && !snapshot.lastKnownPlayerName.isBlank()) {
                return Optional.of(snapshot.lastKnownPlayerName);
            }
        }
        return Optional.empty();
    }

    private List<String> readImportPlayerNames(Path file) {
        Set<String> result = new LinkedHashSet<>();
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                return List.of();
            }
            JsonObject object = root.getAsJsonObject();
            appendStringArray(result, object.getAsJsonArray("playerNames"));
            appendStringArray(result, object.getAsJsonArray("players"));
            JsonArray entries = object.getAsJsonArray("entries");
            if (entries != null) {
                for (JsonElement entryElement : entries) {
                    if (entryElement == null || !entryElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject entry = entryElement.getAsJsonObject();
                    appendStringValue(result, entry.get("playerName"));
                    appendStringValue(result, entry.get("onlineName"));
                }
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return result.stream().filter(name -> !name.isBlank()).toList();
    }

    private void appendStringArray(Set<String> target, JsonArray array) {
        if (target == null || array == null) {
            return;
        }
        for (JsonElement element : array) {
            appendStringValue(target, element);
        }
    }

    private void appendStringValue(Set<String> target, JsonElement element) {
        if (target == null || element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return;
        }
        String value = element.getAsString().trim();
        if (!value.isBlank()) {
            target.add(value);
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建目录: " + directory, exception);
        }
    }

    private void writeJson(Path file, Object payload) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                ensureDirectory(parent);
            }
            try (Writer writer = Files.newBufferedWriter(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                GSON.toJson(payload, writer);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入文件: " + file, exception);
        }
    }

    private String normalizeJsonFileName(String rawFileName) {
        String normalized = rawFileName == null ? "" : rawFileName.trim();
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT).endsWith(".json") ? normalized : normalized + ".json";
    }

    private String sanitizeFileStem(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim();
        if (normalized.isBlank()) {
            return "title_player_list_example";
        }
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|]+", "_");
        normalized = normalized.replaceAll("\\s+", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "title_player_list_example" : normalized;
    }

    public static MutableComponent parseLegacyText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (containsMarker(raw, "GRA")) {
            List<StyledGlyph> glyphs = parseGlyphs(raw, true);
            int[] endpointColors = resolveGradientEndpointColors(raw);
            return buildGradientComponent(glyphs, endpointColors[0], endpointColors[1]);
        }
        return buildGlyphComponent(parseGlyphs(raw, false));
    }

    private static MutableComponent buildGlyphComponent(List<StyledGlyph> glyphs) {
        MutableComponent result = Component.empty();
        if (glyphs == null || glyphs.isEmpty()) {
            return result;
        }
        StringBuilder buffer = new StringBuilder();
        Style currentStyle = null;
        for (StyledGlyph glyph : glyphs) {
            if (glyph == null || glyph.text() == null || glyph.text().isEmpty()) {
                continue;
            }
            if (currentStyle == null || Objects.equals(currentStyle, glyph.style())) {
                buffer.append(glyph.text());
                currentStyle = glyph.style();
                continue;
            }
            appendStyledSegment(result, buffer, currentStyle == null ? Style.EMPTY : currentStyle);
            buffer.append(glyph.text());
            currentStyle = glyph.style();
        }
        appendStyledSegment(result, buffer, currentStyle == null ? Style.EMPTY : currentStyle);
        return result;
    }

    private static MutableComponent buildGradientComponent(List<StyledGlyph> glyphs, int startColor, int endColor) {
        MutableComponent result = Component.empty();
        if (glyphs == null || glyphs.isEmpty()) {
            return result;
        }
        int visibleCount = glyphs.size();
        for (int index = 0; index < glyphs.size(); index++) {
            StyledGlyph glyph = glyphs.get(index);
            if (glyph == null || glyph.text() == null || glyph.text().isEmpty()) {
                continue;
            }
            float progress = visibleCount <= 1 ? 0.0F : (float) index / (float) (visibleCount - 1);
            int rgb = interpolateRgb(startColor, endColor, progress);
            result.append(Component.literal(glyph.text()).setStyle(glyph.style().withColor(rgb)));
        }
        return result;
    }

    private static List<StyledGlyph> parseGlyphs(String raw, boolean ignoreColorCodes) {
        List<StyledGlyph> glyphs = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return glyphs;
        }
        Style currentStyle = Style.EMPTY;
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (current == '/') {
                if (matchesControlKeyword(raw, index, "GRA")) {
                    index += 3;
                    continue;
                }
                HexColorToken hexToken = readHexColorToken(raw, index);
                if (hexToken != null) {
                    if (!ignoreColorCodes) {
                        currentStyle = currentStyle.withColor(hexToken.rgb());
                    }
                    index = hexToken.endIndex();
                    continue;
                }
            }
            if ((current == '\u00a7' || current == '&') && index + 1 < raw.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(index + 1));
                if (formatting != null) {
                    if (formatting == ChatFormatting.RESET) {
                        currentStyle = Style.EMPTY;
                    } else if (!formatting.isColor() || !ignoreColorCodes) {
                        currentStyle = currentStyle.applyFormat(formatting);
                    }
                    index++;
                    continue;
                }
            }
            glyphs.add(new StyledGlyph(String.valueOf(current), currentStyle));
        }
        return glyphs;
    }

    private static boolean containsMarker(String raw, String marker) {
        if (raw == null || marker == null || marker.isBlank()) {
            return false;
        }
        return raw.toUpperCase(Locale.ROOT).contains("/" + marker.toUpperCase(Locale.ROOT));
    }

    private static boolean matchesControlKeyword(String raw, int index, String keyword) {
        if (raw == null || keyword == null || keyword.isBlank() || index < 0 || index >= raw.length() || raw.charAt(index) != '/') {
            return false;
        }
        if (index + keyword.length() >= raw.length()) {
            return false;
        }
        return raw.regionMatches(true, index + 1, keyword, 0, keyword.length());
    }

    private static int[] resolveGradientEndpointColors(String raw) {
        List<Integer> colors = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new int[]{0xFFFFFF, 0xFFFFFF};
        }
        for (int index = 0; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (current == '/') {
                HexColorToken hexToken = readHexColorToken(raw, index);
                if (hexToken != null) {
                    colors.add(hexToken.rgb());
                    index = hexToken.endIndex();
                    continue;
                }
            }
            if ((current == '\u00a7' || current == '&') && index + 1 < raw.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(raw.charAt(index + 1));
                if (formatting != null && formatting.isColor() && formatting.getColor() != null) {
                    colors.add(formatting.getColor());
                    index++;
                }
            }
        }
        if (colors.isEmpty()) {
            return new int[]{0xFFFFFF, 0xFFFFFF};
        }
        if (colors.size() == 1) {
            return new int[]{colors.get(0), colors.get(0)};
        }
        return new int[]{colors.get(0), colors.get(colors.size() - 1)};
    }

    private static HexColorToken readHexColorToken(String raw, int slashIndex) {
        if (raw == null || slashIndex < 0 || slashIndex >= raw.length() || raw.charAt(slashIndex) != '/') {
            return null;
        }
        int sixDigitStart = slashIndex + 1;
        if (sixDigitStart + 6 <= raw.length()) {
            String candidate = raw.substring(sixDigitStart, sixDigitStart + 6);
            if (isHex(candidate)) {
                return new HexColorToken(Integer.parseInt(candidate, 16), slashIndex + 6);
            }
        }
        return null;
    }

    private static boolean isHex(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean digit = current >= '0' && current <= '9';
            boolean lower = current >= 'a' && current <= 'f';
            boolean upper = current >= 'A' && current <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static int interpolateRgb(int startColor, int endColor, float progress) {
        int startRed = (startColor >> 16) & 0xFF;
        int startGreen = (startColor >> 8) & 0xFF;
        int startBlue = startColor & 0xFF;
        int endRed = (endColor >> 16) & 0xFF;
        int endGreen = (endColor >> 8) & 0xFF;
        int endBlue = endColor & 0xFF;
        int red = Math.round(startRed + (endRed - startRed) * progress);
        int green = Math.round(startGreen + (endGreen - startGreen) * progress);
        int blue = Math.round(startBlue + (endBlue - startBlue) * progress);
        return (red << 16) | (green << 8) | blue;
    }

    private static void appendStyledSegment(MutableComponent result, StringBuilder buffer, Style style) {
        if (result == null || buffer == null || buffer.isEmpty()) {
            return;
        }
        result.append(Component.literal(buffer.toString()).setStyle(style));
        buffer.setLength(0);
    }

    public record GrantResult(boolean success, String titleId, String playerName, UUID playerId, String message) {
    }

    public record RemoveResult(boolean success, String playerName, UUID playerId, String message) {
    }

    public record WearResult(boolean success, String message) {
    }

    public record ExampleResult(Path file, int suggestedPlayerCount) {
    }

    public record ImportResult(
        boolean success,
        String titleId,
        Path file,
        int grantedCount,
        int skippedCount,
        List<String> unresolvedPlayers,
        String message
    ) {
    }

    private record ResolvedPlayer(UUID playerId, String playerName) {
    }

    private record PlayerDataSnapshot(UUID playerId, String lastKnownPlayerName, Set<String> ownedTitleIds, String equippedTitleId) {
    }

    private record ExamplePayload(String generatedAt, List<String> playerNames) {
    }

    private record StyledGlyph(String text, Style style) {
    }

    private record HexColorToken(int rgb, int endIndex) {
    }
}
