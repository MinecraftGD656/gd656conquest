package org.mods.gd656conquest.server.data.playerdata;

import net.minecraft.server.MinecraftServer;
import org.mods.gd656conquest.server.data.ServerDataPaths;
import org.mods.gd656conquest.server.data.util.DelayedJsonWriteService;
import org.mods.gd656conquest.server.data.util.JsonDataStoreSupport;
import org.mods.gd656conquest.server.loadout.domain.PlayerClassType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

public final class PlayerDataStore extends JsonDataStoreSupport {
    private final MinecraftServer server;
    private final DelayedJsonWriteService delayedJsonWriteService;
    private final Map<UUID, PlayerDataModel> cache = new LinkedHashMap<>();

    public PlayerDataStore(MinecraftServer server, DelayedJsonWriteService delayedJsonWriteService) {
        this.server = Objects.requireNonNull(server, "server");
        this.delayedJsonWriteService = Objects.requireNonNull(delayedJsonWriteService, "delayedJsonWriteService");
    }

    public synchronized void initialize() {
        ensureDirectory(ServerDataPaths.getPlayerDataDirectory(server));
    }

    public synchronized PlayerDataModel getPlayerData(UUID uuid) {
        PlayerDataModel cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        Path file = ServerDataPaths.getPlayerDataFile(server, uuid.toString());
        PlayerDataModel data = read(file, PlayerDataModel.class, PlayerDataModel::new);
        boolean changed = false;
        if (data.getLevel() < 0) {
            data.setLevel(0);
            changed = true;
        }
        if (data.getLevelExperience() < 0) {
            data.setLevelExperience(0);
            changed = true;
        }
        if (data.getUnacknowledgedTeamChannelMessageCount() < 0) {
            data.setUnacknowledgedTeamChannelMessageCount(0);
            changed = true;
        }
        Set<String> normalizedOwnedTitleIds = new LinkedHashSet<>();
        for (String titleId : data.getOwnedTitleIds()) {
            if (titleId == null || titleId.isBlank()) {
                continue;
            }
            normalizedOwnedTitleIds.add(titleId.trim().toUpperCase(Locale.ROOT));
        }
        if (!normalizedOwnedTitleIds.equals(data.getOwnedTitleIds())) {
            data.setOwnedTitleIds(normalizedOwnedTitleIds);
            changed = true;
        }
        String equippedTitleId = data.getEquippedTitleId().trim().toUpperCase(Locale.ROOT);
        if (!equippedTitleId.equals(data.getEquippedTitleId())) {
            data.setEquippedTitleId(equippedTitleId);
            changed = true;
        }
        String lastKnownPlayerName = data.getLastKnownPlayerName().trim();
        if (!lastKnownPlayerName.equals(data.getLastKnownPlayerName())) {
            data.setLastKnownPlayerName(lastKnownPlayerName);
            changed = true;
        }
        List<PlayerDataModel.PendingTitleNotice> normalizedPendingNotices = new ArrayList<>();
        for (PlayerDataModel.PendingTitleNotice notice : data.getPendingTitleNotices()) {
            if (notice == null) {
                continue;
            }
            String giverName = notice.getGiverName().trim();
            String titleId = notice.getTitleId().trim().toUpperCase(Locale.ROOT);
            String titleText = notice.getTitleText().trim().replace('\u00a7', '&');
            if (titleId.isBlank() || titleText.isBlank()) {
                continue;
            }
            PlayerDataModel.PendingTitleNotice normalizedNotice = new PlayerDataModel.PendingTitleNotice();
            normalizedNotice.setGiverName(giverName);
            normalizedNotice.setTitleId(titleId);
            normalizedNotice.setTitleText(titleText);
            normalizedPendingNotices.add(normalizedNotice);
        }
        if (!hasSamePendingNotices(data.getPendingTitleNotices(), normalizedPendingNotices)) {
            data.setPendingTitleNotices(normalizedPendingNotices);
            changed = true;
        }
        PlayerClassType resolvedClassType = PlayerClassType.fromInput(data.getCurrentClassType());
        if (resolvedClassType == null) {
            data.setCurrentClassType(PlayerClassType.defaultType().getKey());
            changed = true;
        } else if (!resolvedClassType.getKey().equals(data.getCurrentClassType())) {
            data.setCurrentClassType(resolvedClassType.getKey());
            changed = true;
        }
        if (changed) {
            savePlayerData(uuid, data);
        } else {
            cache.put(uuid, data);
        }
        return data;
    }

    public synchronized void savePlayerData(UUID uuid, PlayerDataModel data) {
        PlayerDataModel resolved = data == null ? new PlayerDataModel() : data;
        cache.put(uuid, resolved);
        delayedJsonWriteService.schedule(ServerDataPaths.getPlayerDataFile(server, uuid.toString()), resolved, server.getTickCount());
    }

    public synchronized Optional<String> findLastKnownPlayerName(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        PlayerDataModel cached = cache.get(uuid);
        if (cached != null) {
            String cachedName = cached.getLastKnownPlayerName().trim();
            return cachedName.isBlank() ? Optional.empty() : Optional.of(cachedName);
        }
        Path file = ServerDataPaths.getPlayerDataFile(server, uuid.toString());
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String lastKnownPlayerName = getPlayerData(uuid).getLastKnownPlayerName().trim();
        return lastKnownPlayerName.isBlank() ? Optional.empty() : Optional.of(lastKnownPlayerName);
    }

    public synchronized void updateLastKnownPlayerName(UUID uuid, String playerName) {
        if (uuid == null) {
            return;
        }
        String normalizedName = playerName == null ? "" : playerName.trim();
        if (normalizedName.isBlank()) {
            return;
        }
        PlayerDataModel data = getPlayerData(uuid);
        if (normalizedName.equals(data.getLastKnownPlayerName())) {
            return;
        }
        data.setLastKnownPlayerName(normalizedName);
        savePlayerData(uuid, data);
    }

    public synchronized List<UUID> listKnownPlayerIds() {
        LinkedHashSet<UUID> result = new LinkedHashSet<>(cache.keySet());
        Path directory = ServerDataPaths.getPlayerDataDirectory(server);
        ensureDirectory(directory);
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path != null && path.getFileName() != null)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".json"))
                .map(name -> name.substring(0, name.length() - 5))
                .forEach(rawUuid -> {
                    try {
                        result.add(UUID.fromString(rawUuid));
                    } catch (Exception ignored) {
                    }
                });
        } catch (Exception ignored) {
        }
        return new ArrayList<>(result);
    }

    private boolean hasSamePendingNotices(List<PlayerDataModel.PendingTitleNotice> current, List<PlayerDataModel.PendingTitleNotice> normalized) {
        if (current == null) {
            return normalized == null || normalized.isEmpty();
        }
        if (normalized == null) {
            return current.isEmpty();
        }
        if (current.size() != normalized.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            PlayerDataModel.PendingTitleNotice left = current.get(index);
            PlayerDataModel.PendingTitleNotice right = normalized.get(index);
            if (left == null || right == null) {
                if (left != right) {
                    return false;
                }
                continue;
            }
            if (!left.getGiverName().equals(right.getGiverName())
                || !left.getTitleId().equals(right.getTitleId())
                || !left.getTitleText().equals(right.getTitleText())) {
                return false;
            }
        }
        return true;
    }
}
