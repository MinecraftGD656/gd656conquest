package org.mods.gd656conquest.server.integration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.player.state.HubDimensionService;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KilliconScoreboardBridge {
    private static final String KILLICON_BONUS_TYPE_CLASS = "org.mods.gd656killicon.common.BonusType";
    private static final String KILLICON_KILL_TYPE_CLASS = "org.mods.gd656killicon.common.KillType";
    private static final String KILLICON_SERVER_CORE_CLASS = "org.mods.gd656killicon.server.ServerCore";
    private static final String KILLICON_SERVER_DATA_CLASS = "org.mods.gd656killicon.server.data.ServerData";
    private static final String KILLICON_NETWORK_HANDLER_CLASS = "org.mods.gd656killicon.network.NetworkHandler";
    private static final String KILLICON_KILL_ICON_PACKET_CLASS = "org.mods.gd656killicon.network.packet.KillIconPacket";
    private static final String PLAYER_DATA_MANAGER_CLASS = "org.mods.gd656killicon.server.data.PlayerDataManager";
    private static final String PLAYER_DATA_CLASS = "org.mods.gd656killicon.server.data.PlayerData";
    private static final Map<String, Integer> BONUS_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_BONUS_TYPE_WARNED = ConcurrentHashMap.newKeySet();
    private static final Set<String> BONUS_INVOKE_FAILURE_WARNED = ConcurrentHashMap.newKeySet();
    private static final Set<String> KILL_PACKET_FAILURE_WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Object bonusEngineInstance;
    private static volatile Method bonusAddMethod;
    private static volatile int bonusAddInvokeMode;
    private static volatile Method bonusTypeLookupMethod;
    private static volatile boolean bonusEngineResolved;
    private static volatile boolean bonusEngineMissingLogged;
    private static volatile boolean gdkiPlayerDataResolved;
    private static volatile Object gdkiPlayerDataManagerInstance;
    private static volatile Method gdkiPlayerDataGetMethod;
    private static volatile Method gdkiPlayerDataScoreMethod;
    private static volatile Method gdkiPlayerDataKillMethod;
    private static volatile Method gdkiPlayerDataDeathMethod;
    private static volatile Method gdkiPlayerDataAssistMethod;
    private static volatile Method gdkiPlayerDataReviveMethod;
    private static volatile boolean gdkiServerDataResolved;
    private static volatile Object gdkiServerDataInstance;
    private static volatile Method gdkiServerDataAddReviveMethod;
    private static volatile Method gdkiServerDataGetComboWindowMethod;
    private static volatile boolean gdkiKillPacketResolved;
    private static volatile boolean gdkiKillPacketMissingLogged;
    private static volatile Method gdkiNetworkSendToPlayerMethod;
    private static volatile Constructor<?> gdkiKillIconPacketConstructor;
    private static volatile int gdkiCaptureKillType = 6;

    private KilliconScoreboardBridge() {
    }

    public static Payload query(MinecraftServer server, UUID requester) {
        if (server == null || requester == null) {
            return null;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
        ServerPlayer requesterPlayer = server.getPlayerList().getPlayer(requester);
        if (requesterPlayer != null) {
            coreManager.resyncPlayerPresentation(requesterPlayer, server.getTickCount());
        }
        return coreManager.buildKilliconScoreboardPayload(requester);
    }

    public static void tryAwardConquestBonus(ServerPlayer player, String bonusTypeName, float scale, String extra) {
        tryAwardConquestBonus(player, bonusTypeName, scale, extra, -1, null);
    }

    public static void tryAwardConquestBonus(
        ServerPlayer player,
        String bonusTypeName,
        float scale,
        String extra,
        int victimId,
        String victimName
    ) {
        if (player == null || bonusTypeName == null || bonusTypeName.isBlank()) {
            return;
        }
        if (shouldBlockGlobalStatDelta(player.server, player.getUUID())) {
            return;
        }
        try {
            if (!ensureBonusEngineResolved()) {
                return;
            }
            int bonusType = resolveBonusTypeId(bonusTypeName);
            if (bonusType < 0) {
                return;
            }
            invokeBonusAdd(player, bonusType, scale, extra == null ? "" : extra, victimId, victimName);
        } catch (Exception exception) {
            if (BONUS_INVOKE_FAILURE_WARNED.add(bonusTypeName)) {
                ServerLogger.failure("GD656Killicon bonus 调用失败: %s | %s", bonusTypeName, exception.getMessage());
            }
        }
    }

    public static void trySendCaptureNotification(ServerPlayer player, int victimId, String payload) {
        if (player == null) {
            return;
        }
        if (shouldBlockGlobalStatDelta(player.server, player.getUUID())) {
            return;
        }
        try {
            if (!ensureKillPacketResolved()) {
                return;
            }
            double comboWindowSeconds = resolveComboWindowSeconds();
            Object scrollingPacket = gdkiKillIconPacketConstructor.newInstance(
                "kill_icon",
                "scrolling",
                gdkiCaptureKillType,
                0,
                -1,
                comboWindowSeconds,
                false,
                ""
            );
            Object subtitlePacket = gdkiKillIconPacketConstructor.newInstance(
                "subtitle",
                "kill_feed",
                gdkiCaptureKillType,
                0,
                victimId,
                comboWindowSeconds,
                false,
                payload == null ? "" : payload
            );
            gdkiNetworkSendToPlayerMethod.invoke(null, scrollingPacket, player);
            gdkiNetworkSendToPlayerMethod.invoke(null, subtitlePacket, player);
        } catch (Exception exception) {
            if (KILL_PACKET_FAILURE_WARNED.add("CAPTURE")) {
                ServerLogger.failure("GD656Killicon 提示发包失败: %s", exception.getMessage());
            }
        }
    }

    public static void resetPlayerMatchSnapshot(RunningRuntimeState runningState, UUID playerId) {
        if (runningState == null || playerId == null) {
            return;
        }
        runningState.getKilliconMatchStatsByPlayer().put(playerId, new RunningRuntimeState.KilliconMatchStats(0.0F, 0, 0, 0, 0));
    }

    public static void ensurePlayerMatchSnapshot(RunningRuntimeState runningState, UUID playerId) {
        if (runningState == null || playerId == null) {
            return;
        }
        runningState.getKilliconMatchStatsByPlayer().putIfAbsent(playerId, new RunningRuntimeState.KilliconMatchStats(0.0F, 0, 0, 0, 0));
    }

    public static void clearPlayerMatchSnapshot(RunningRuntimeState runningState, UUID playerId) {
        if (runningState == null || playerId == null) {
            return;
        }
        runningState.getKilliconMatchStatsByPlayer().remove(playerId);
    }

    public static boolean routeStatDelta(
        MinecraftServer server,
        UUID playerId,
        float scoreDelta,
        int killDelta,
        int deathDelta,
        int assistDelta,
        int reviveDelta
    ) {
        if (server == null || playerId == null) {
            return false;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
        String roomId = coreManager.findPlayerRoomId(playerId).orElse(null);
        if (roomId == null || roomId.isBlank()) {
            return false;
        }
        RoomDefinition room = coreManager.findRoom(roomId).orElse(null);
        if (!isRoomScopedMatchRoom(room)) {
            return false;
        }
        return coreManager.recordKilliconMatchDelta(playerId, scoreDelta, killDelta, deathDelta, assistDelta, reviveDelta);
    }

    public static void tryAddReviveStat(ServerPlayer player, int amount) {
        if (player == null || amount == 0) {
            return;
        }
        try {
            resolveGdkiServerDataMethods();
            if (gdkiServerDataInstance == null || gdkiServerDataAddReviveMethod == null) {
                return;
            }
            gdkiServerDataAddReviveMethod.invoke(gdkiServerDataInstance, player, amount);
        } catch (Exception ignored) {
        }
    }

    public static boolean shouldBlockGlobalStatDelta(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return false;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
        if (coreManager.findPlayerRoomId(playerId).isPresent()) {
            return false;
        }
        if (RangeRuntimeManager.of(server).isInRange(playerId)) {
            return true;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null && HubDimensionService.isScoreRestrictedDimension(player.level());
    }

    public static TempSnapshot getPlayerSnapshot(RunningRuntimeState runningState, UUID playerId) {
        if (playerId == null) {
            return TempSnapshot.EMPTY;
        }
        if (runningState == null) {
            return TempSnapshot.EMPTY;
        }
        RunningRuntimeState.KilliconMatchStats stats = runningState.getKilliconMatchStatsByPlayer()
            .computeIfAbsent(playerId, ignored -> new RunningRuntimeState.KilliconMatchStats(0.0F, 0, 0, 0, 0));
        return new TempSnapshot(
            Math.max(0, Math.round(stats.score())),
            Math.max(0, stats.kill()),
            Math.max(0, stats.death()),
            Math.max(0, stats.assist()),
            Math.max(0, stats.revive())
        );
    }

    public static TempSnapshot getGlobalSnapshot(UUID playerId) {
        return playerId == null ? TempSnapshot.EMPTY : readPlayerGlobalSnapshot(playerId);
    }

    private static TempSnapshot readPlayerGlobalSnapshot(UUID playerId) {
        try {
            resolveGdkiPlayerDataMethods();
            if (gdkiPlayerDataManagerInstance == null || gdkiPlayerDataGetMethod == null) {
                return TempSnapshot.EMPTY;
            }
            Object playerData = gdkiPlayerDataGetMethod.invoke(gdkiPlayerDataManagerInstance, playerId);
            if (playerData == null) {
                return TempSnapshot.EMPTY;
            }
            int score = Math.round(((Number) gdkiPlayerDataScoreMethod.invoke(playerData)).floatValue());
            int kill = ((Number) gdkiPlayerDataKillMethod.invoke(playerData)).intValue();
            int death = ((Number) gdkiPlayerDataDeathMethod.invoke(playerData)).intValue();
            int assist = ((Number) gdkiPlayerDataAssistMethod.invoke(playerData)).intValue();
            int revive = gdkiPlayerDataReviveMethod == null ? 0 : ((Number) gdkiPlayerDataReviveMethod.invoke(playerData)).intValue();
            return new TempSnapshot(score, kill, death, assist, revive);
        } catch (Exception ignored) {
            return TempSnapshot.EMPTY;
        }
    }

    private static boolean isRoomScopedMatchRoom(RoomDefinition room) {
        if (room == null) {
            return false;
        }
        RoomLifecycleState lifecycleState = room.lifecycleState();
        return lifecycleState == RoomLifecycleState.RUNNING || lifecycleState == RoomLifecycleState.ENDING;
    }

    private static void resolveGdkiPlayerDataMethods() {
        if (gdkiPlayerDataResolved) {
            return;
        }
        synchronized (KilliconScoreboardBridge.class) {
            if (gdkiPlayerDataResolved) {
                return;
            }
            gdkiPlayerDataResolved = true;
            try {
                Class<?> managerClass = Class.forName(PLAYER_DATA_MANAGER_CLASS);
                gdkiPlayerDataManagerInstance = managerClass.getMethod("get").invoke(null);
                gdkiPlayerDataGetMethod = managerClass.getMethod("getPlayerData", UUID.class);
                Class<?> playerDataClass = Class.forName(PLAYER_DATA_CLASS);
                gdkiPlayerDataScoreMethod = playerDataClass.getMethod("getScore");
                gdkiPlayerDataKillMethod = playerDataClass.getMethod("getKill");
                gdkiPlayerDataDeathMethod = playerDataClass.getMethod("getDeath");
                gdkiPlayerDataAssistMethod = playerDataClass.getMethod("getAssist");
                try {
                    gdkiPlayerDataReviveMethod = playerDataClass.getMethod("getRevive");
                } catch (NoSuchMethodException ignored) {
                    gdkiPlayerDataReviveMethod = null;
                }
            } catch (Exception ignored) {
                gdkiPlayerDataManagerInstance = null;
                gdkiPlayerDataGetMethod = null;
                gdkiPlayerDataScoreMethod = null;
                gdkiPlayerDataKillMethod = null;
                gdkiPlayerDataDeathMethod = null;
                gdkiPlayerDataAssistMethod = null;
                gdkiPlayerDataReviveMethod = null;
            }
        }
    }

    private static void resolveGdkiServerDataMethods() {
        if (gdkiServerDataResolved) {
            return;
        }
        synchronized (KilliconScoreboardBridge.class) {
            if (gdkiServerDataResolved) {
                return;
            }
            gdkiServerDataResolved = true;
            try {
                Class<?> serverDataClass = Class.forName(KILLICON_SERVER_DATA_CLASS);
                gdkiServerDataInstance = serverDataClass.getMethod("get").invoke(null);
                gdkiServerDataAddReviveMethod = serverDataClass.getMethod("addRevive", ServerPlayer.class, int.class);
                gdkiServerDataGetComboWindowMethod = serverDataClass.getMethod("getComboWindowSeconds");
            } catch (Exception ignored) {
                gdkiServerDataInstance = null;
                gdkiServerDataAddReviveMethod = null;
                gdkiServerDataGetComboWindowMethod = null;
            }
        }
    }

    private static boolean ensureKillPacketResolved() {
        if (gdkiKillPacketResolved) {
            return gdkiNetworkSendToPlayerMethod != null && gdkiKillIconPacketConstructor != null;
        }
        synchronized (KilliconScoreboardBridge.class) {
            if (gdkiKillPacketResolved) {
                return gdkiNetworkSendToPlayerMethod != null && gdkiKillIconPacketConstructor != null;
            }
            try {
                Class<?> networkHandlerClass = Class.forName(KILLICON_NETWORK_HANDLER_CLASS);
                gdkiNetworkSendToPlayerMethod = networkHandlerClass.getMethod("sendToPlayer", Object.class, ServerPlayer.class);
                Class<?> packetClass = Class.forName(KILLICON_KILL_ICON_PACKET_CLASS);
                gdkiKillIconPacketConstructor = packetClass.getConstructor(
                    String.class,
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    double.class,
                    boolean.class,
                    String.class
                );
                try {
                    Class<?> killTypeClass = Class.forName(KILLICON_KILL_TYPE_CLASS);
                    gdkiCaptureKillType = killTypeClass.getField("CAPTURE").getInt(null);
                } catch (Exception ignored) {
                    gdkiCaptureKillType = 6;
                }
                gdkiKillPacketMissingLogged = false;
            } catch (Exception ignored) {
                gdkiNetworkSendToPlayerMethod = null;
                gdkiKillIconPacketConstructor = null;
                if (!gdkiKillPacketMissingLogged) {
                    gdkiKillPacketMissingLogged = true;
                    ServerLogger.failure("未找到可用的 GD656Killicon kill packet 链路，相关提示将不会触发");
                }
            }
            gdkiKillPacketResolved = true;
        }
        return gdkiNetworkSendToPlayerMethod != null && gdkiKillIconPacketConstructor != null;
    }

    private static double resolveComboWindowSeconds() {
        resolveGdkiServerDataMethods();
        if (gdkiServerDataInstance == null || gdkiServerDataGetComboWindowMethod == null) {
            return 3.0D;
        }
        try {
            Object value = gdkiServerDataGetComboWindowMethod.invoke(gdkiServerDataInstance);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return 3.0D;
    }

    private static int resolveBonusTypeId(String bonusTypeName) {
        Integer cached = BONUS_TYPE_CACHE.get(bonusTypeName);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> bonusTypeClass = Class.forName(KILLICON_BONUS_TYPE_CLASS);
            int value = bonusTypeClass.getField(bonusTypeName).getInt(null);
            BONUS_TYPE_CACHE.put(bonusTypeName, value);
            return value;
        } catch (Exception ignored) {
        }
        try {
            Method lookup = bonusTypeLookupMethod;
            if (lookup == null) {
                Class<?> bonusTypeClass = Class.forName(KILLICON_BONUS_TYPE_CLASS);
                lookup = bonusTypeClass.getMethod("getTypeByName", String.class);
                bonusTypeLookupMethod = lookup;
            }
            Object result = lookup.invoke(null, bonusTypeName);
            if (result instanceof Number number) {
                int value = number.intValue();
                if (value >= 0) {
                    BONUS_TYPE_CACHE.put(bonusTypeName, value);
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        if (MISSING_BONUS_TYPE_WARNED.add(bonusTypeName)) {
            ServerLogger.failure("当前 GD656Killicon 不支持 bonus: %s", bonusTypeName);
        }
        return -1;
    }

    private static boolean ensureBonusEngineResolved() {
        if (bonusEngineResolved) {
            return bonusEngineInstance != null && bonusAddMethod != null;
        }
        synchronized (KilliconScoreboardBridge.class) {
            if (bonusEngineResolved) {
                return bonusEngineInstance != null && bonusAddMethod != null;
            }
            try {
                Class<?> serverCoreClass = Class.forName(KILLICON_SERVER_CORE_CLASS);
                Object engine = serverCoreClass.getField("BONUS").get(null);
                Method add = resolveBonusAddMethod(engine.getClass());
                bonusEngineInstance = engine;
                bonusAddMethod = add;
                bonusEngineMissingLogged = false;
            } catch (Exception ignored) {
                bonusEngineInstance = null;
                bonusAddMethod = null;
                if (!bonusEngineMissingLogged) {
                    bonusEngineMissingLogged = true;
                    ServerLogger.failure("未找到可用的 GD656Killicon bonus 引擎，相关 bonus 将不会触发");
                }
            }
            bonusEngineResolved = true;
        }
        return bonusEngineInstance != null && bonusAddMethod != null;
    }

    private static Method resolveBonusAddMethod(Class<?> engineClass) throws NoSuchMethodException {
        try {
            bonusAddInvokeMode = 4;
            return engineClass.getMethod("add", ServerPlayer.class, int.class, float.class, String.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            bonusAddInvokeMode = 5;
            return engineClass.getMethod("add", ServerPlayer.class, int.class, float.class, String.class, int.class);
        } catch (NoSuchMethodException ignored) {
        }
        bonusAddInvokeMode = 6;
        return engineClass.getMethod("add", ServerPlayer.class, int.class, float.class, String.class, int.class, String.class);
    }

    private static void invokeBonusAdd(
        ServerPlayer player,
        int bonusType,
        float scale,
        String extra,
        int victimId,
        String victimName
    ) throws Exception {
        if (bonusAddMethod == null || bonusEngineInstance == null) {
            return;
        }
        switch (bonusAddInvokeMode) {
            case 5 -> bonusAddMethod.invoke(bonusEngineInstance, player, bonusType, scale, extra, victimId);
            case 6 -> bonusAddMethod.invoke(bonusEngineInstance, player, bonusType, scale, extra, victimId, victimName);
            default -> bonusAddMethod.invoke(bonusEngineInstance, player, bonusType, scale, extra);
        }
    }

    public static final class TempSnapshot {
        public static final TempSnapshot EMPTY = new TempSnapshot(0, 0, 0, 0, 0);
        private final int score;
        private final int kill;
        private final int death;
        private final int assist;
        private final int revive;

        public TempSnapshot(int score, int kill, int death, int assist, int revive) {
            this.score = score;
            this.kill = kill;
            this.death = death;
            this.assist = assist;
            this.revive = revive;
        }

        public int score() {
            return score;
        }

        public int kill() {
            return kill;
        }

        public int death() {
            return death;
        }

        public int assist() {
            return assist;
        }

        public int revive() {
            return revive;
        }
    }

    public record Entry(
        UUID uuid,
        String name,
        String lastLoginName,
        String teamName,
        String squadLabel,
        int score,
        int kill,
        int death,
        int assist,
        int revive,
        int ping,
        boolean online,
        boolean spectator
    ) {
    }

    public static final class Payload {
        private final List<Entry> entries;
        private final int columns;
        private final String[] panelTeams;

        public Payload(List<Entry> entries, int columns, String[] panelTeams) {
            this.entries = entries == null ? List.of() : List.copyOf(entries);
            this.columns = columns;
            this.panelTeams = panelTeams == null ? new String[]{"", "", "", ""} : normalizePanelTeams(panelTeams);
        }

        public List<Entry> entries() {
            return entries;
        }

        public int columns() {
            return columns;
        }

        public String[] panelTeams() {
            return panelTeams.clone();
        }

        private String[] normalizePanelTeams(String[] source) {
            String[] normalized = new String[]{"", "", "", ""};
            for (int i = 0; i < 4; i++) {
                if (i < source.length && source[i] != null) {
                    normalized[i] = source[i];
                }
            }
            return normalized;
        }
    }
}
