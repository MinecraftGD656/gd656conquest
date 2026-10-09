package org.mods.gd656conquest.server.player.identity;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;

public final class PlayerIdentityService {
    private static final Map<MinecraftServer, PlayerIdentityService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final PlayerDataStore playerDataStore;

    private PlayerIdentityService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerDataStore = ConquestDataManager.of(server).playerData();
    }

    public static PlayerIdentityService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, PlayerIdentityService::new);
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

    public void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        rememberPlayerName(player.getUUID(), player.getScoreboardName());
    }

    public void rememberPlayerName(UUID playerId, String playerName) {
        playerDataStore.updateLastKnownPlayerName(playerId, playerName);
    }

    public Optional<String> resolvePlayerName(UUID playerId) {
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
        return playerDataStore.findLastKnownPlayerName(playerId);
    }

    public ResolvedPlayer resolvePlayer(String playerNameOrUuid) {
        if (playerNameOrUuid == null || playerNameOrUuid.isBlank()) {
            return null;
        }
        String normalizedInput = playerNameOrUuid.trim();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.getScoreboardName() != null && player.getScoreboardName().equalsIgnoreCase(normalizedInput)) {
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
        for (UUID knownPlayerId : playerDataStore.listKnownPlayerIds()) {
            Optional<String> knownName = playerDataStore.findLastKnownPlayerName(knownPlayerId);
            if (knownName.isPresent() && knownName.get().equalsIgnoreCase(normalizedInput)) {
                return new ResolvedPlayer(knownPlayerId, knownName.get());
            }
        }
        return null;
    }

    public List<String> suggestKnownPlayerNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.getScoreboardName() != null && !player.getScoreboardName().isBlank()) {
                names.add(player.getScoreboardName());
            }
        }
        for (UUID knownPlayerId : playerDataStore.listKnownPlayerIds()) {
            playerDataStore.findLastKnownPlayerName(knownPlayerId).ifPresent(names::add);
        }
        List<String> result = new ArrayList<>(names);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public String resolvePlayerNameOrUuid(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        return resolvePlayerName(playerId).orElse(playerId.toString().toLowerCase(Locale.ROOT));
    }

    public record ResolvedPlayer(UUID playerId, String playerName) {
    }
}
