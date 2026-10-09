package org.mods.gd656conquest.server.facility.sign;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.Event;
import org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService;
import org.mods.gd656conquest.server.facility.range.RangeRuntimeManager;
import org.mods.gd656conquest.server.room.core.RoomJoinResult;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.entry.RoomJoinAccessPolicy;
import org.mods.gd656conquest.server.room.entry.RoomSessionTransitionService;
import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomLifecycleState;
import org.mods.gd656conquest.server.shared.text.ServerMessageSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SignPlaceholderService {
    private static final Pattern ROOM_STATE_PATTERN = Pattern.compile("<gdc:sr(\\d+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_PLAYER_PATTERN = Pattern.compile("<gdc:pr(\\d+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_JOIN_PATTERN = Pattern.compile("<gdc:jr(\\d+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_JOIN_AUTO_PATTERN = Pattern.compile("<gdc:jra>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_LEAVE_AUTO_PATTERN = Pattern.compile("<gdc:lra>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_PLACEHOLDER_PATTERN = Pattern.compile("<gdc:(sr\\d+|pr\\d+|jr\\d+|jra|lra)>", Pattern.CASE_INSENSITIVE);
    private static final String TEMPLATE_KEY_PREFIX = "gd656conquest_sign_template_";
    private static final int UPDATE_INTERVAL_TICKS = 100;
    private static final Map<MinecraftServer, SignPlaceholderService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<ResourceKey<Level>, Set<BlockPos>> trackedSigns = new HashMap<>();

    private SignPlaceholderService(MinecraftServer server) {
        this.server = server;
    }

    public static SignPlaceholderService of(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, SignPlaceholderService::new);
        }
    }

    public void onServerTick(long gameTick) {
        if (gameTick % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Set<BlockPos> positions = trackedSigns.get(level.dimension());
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            Collection<BlockPos> snapshot = new ArrayList<>(positions);
            for (BlockPos pos : snapshot) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!(blockEntity instanceof SignBlockEntity sign)) {
                    positions.remove(pos);
                    continue;
                }
                boolean hasPlaceholder = updateSignText(level, sign);
                if (!hasPlaceholder) {
                    positions.remove(pos);
                }
            }
        }
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        if (event == null || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) {
            return;
        }
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                continue;
            }
            if (containsPlaceholder(sign)) {
                trackSign(serverLevel, sign.getBlockPos());
                updateSignText(serverLevel, sign);
            }
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(event.getPos());
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return;
        }
        if (player.isCreative()) {
            event.setCanceled(false);
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.ALLOW);
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }
        trackSign(serverLevel, sign.getBlockPos());
        updateSignText(serverLevel, sign);
        Optional<JoinRequest> joinRequest = extractJoinRequest(sign);
        if (joinRequest.isEmpty() || player.isShiftKeyDown()) {
            return;
        }
        executeJoin(player, joinRequest.get());
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.server == null) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(event.getPos());
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return;
        }
        trackSign(serverLevel, sign.getBlockPos());
        updateSignText(serverLevel, sign);
        Optional<JoinRequest> joinRequest = extractJoinRequest(sign);
        if (joinRequest.isEmpty() || player.isCreative()) {
            return;
        }
        executeJoin(player, joinRequest.get());
        event.setCanceled(true);
    }

    private void executeJoin(ServerPlayer player, JoinRequest request) {
        if (request.type() == JoinType.RANGE) {
            String result = RangeRuntimeManager.of(server).joinRange(player);
            QuickAccessItemService.refreshHotbar(player);
            ServerMessageSender.system(player, "告示牌", result, ChatFormatting.AQUA);
            return;
        }
        if (request.type() == JoinType.RANGE_LEAVE) {
            String result = RangeRuntimeManager.of(server).leaveRange(player);
            QuickAccessItemService.refreshHotbar(player);
            ServerMessageSender.system(player, "告示牌", result, ChatFormatting.AQUA);
            return;
        }
        RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
        String roomId = resolveRoomIdByNumericSuffix(coreManager, request.roomNumber());
        if (roomId == null) {
            ServerMessageSender.system(player, "告示牌", "未找到房间编号: " + request.roomNumber(), ChatFormatting.YELLOW);
            return;
        }
        RoomDefinition room = coreManager.findRoom(roomId).orElse(null);
        RoomJoinAccessPolicy.JoinCheckResult access = RoomJoinAccessPolicy.evaluate(player, room, coreManager);
        if (!access.allowed()) {
            ServerMessageSender.system(player, "告示牌", access.deniedMessage(), ChatFormatting.RED);
            return;
        }
        RoomSessionTransitionService.JoinExecutionResult result = RoomSessionTransitionService.of(server)
            .joinRoom(player, roomId, access.maintenance());
        if (result.denied()) {
            ServerMessageSender.system(player, "告示牌", result.deniedMessage(), ChatFormatting.RED);
            return;
        }
        boolean joined = result.success();
        String message = joined
            ? (room != null && room.lifecycleState() == RoomLifecycleState.MAINTENANCE
                ? "已进入维护房间: " + roomId
                : (result.groupAction() && result.operatorMessage() != null && !result.operatorMessage().isBlank()
                    ? result.operatorMessage()
                    : describeJoinMessage(roomId, result.operatorJoinResult())))
            : "加入房间失败: " + roomId;
        ServerMessageSender.system(player, "告示牌", message, joined ? ChatFormatting.AQUA : ChatFormatting.RED);
    }

    private String describeJoinMessage(String roomId, RoomJoinResult result) {
        if (result == null) {
            return "已加入房间: " + roomId;
        }
        return switch (result.kind()) {
            case MIDJOIN_RUNNING -> "已加入进行中的对局: " + roomId;
            case MIDJOIN_ENDING -> "已加入当前对局结算: " + roomId;
            case REJOIN_RUNNING -> "已重连进行中的对局: " + roomId;
            case REJOIN_ENDING -> "已重连当前对局结算: " + roomId;
            case MATCHING -> "已加入房间匹配队列: " + roomId;
        };
    }

    private boolean updateSignText(ServerLevel level, SignBlockEntity sign) {
        boolean hasPlaceholder = false;
        hasPlaceholder |= updateOneSide(level, sign, true);
        hasPlaceholder |= updateOneSide(level, sign, false);
        return hasPlaceholder;
    }

    private boolean updateOneSide(ServerLevel level, SignBlockEntity sign, boolean front) {
        SignText text = front ? sign.getFrontText() : sign.getBackText();
        SignText updated = text;
        boolean changed = false;
        boolean hasPlaceholder = false;
        for (int line = 0; line < 4; line++) {
            String current = text.getMessage(line, false).getString();
            String templateKey = templateKey(front, line);
            String template = readTemplate(sign, templateKey);
            if (containsPlaceholder(current)) {
                template = current;
                writeTemplate(sign, templateKey, template);
            }
            if (template == null || template.isBlank() || !containsPlaceholder(template)) {
                continue;
            }
            hasPlaceholder = true;
            Component rendered = renderTemplateComponent(template);
            if (!rendered.equals(text.getMessage(line, false))) {
                updated = updated.setMessage(line, rendered);
                changed = true;
            }
        }
        if (changed) {
            sign.setText(updated, front);
            sign.setChanged();
            level.sendBlockUpdated(sign.getBlockPos(), level.getBlockState(sign.getBlockPos()), level.getBlockState(sign.getBlockPos()), 3);
        }
        return hasPlaceholder;
    }

    private Component renderTemplateComponent(String template) {
        if (template == null || template.isBlank()) {
            return Component.empty();
        }
        Matcher matcher = ANY_PLACEHOLDER_PATTERN.matcher(template);
        int cursor = 0;
        MutableComponent output = Component.empty();
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                output.append(Component.literal(template.substring(cursor, matcher.start())));
            }
            String token = matcher.group(1).toLowerCase(Locale.ROOT);
            output.append(resolvePlaceholderComponent(token));
            cursor = matcher.end();
        }
        if (cursor < template.length()) {
            output.append(Component.literal(template.substring(cursor)));
        }
        return output;
    }

    private Component resolvePlaceholderComponent(String token) {
        if (token == null || token.isBlank()) {
            return Component.literal("-");
        }
        if ("jra".equals(token)) {
            return Component.literal("[加入靶场]").withStyle(ChatFormatting.AQUA);
        }
        if ("lra".equals(token)) {
            return Component.literal("[离开靶场]").withStyle(ChatFormatting.RED);
        }
        if (token.startsWith("jr")) {
            return Component.literal("[点击加入]").withStyle(ChatFormatting.GREEN);
        }
        if (token.startsWith("sr")) {
            int roomNo = parseIntSafe(token.substring(2));
            RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
            String roomId = resolveRoomIdByNumericSuffix(coreManager, roomNo);
            if (roomId == null) {
                return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
            }
            RoomLifecycleState state = coreManager.findRoom(roomId)
                .map(RoomDefinition::lifecycleState)
                .orElse(null);
            if (state == null) {
                return Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
            }
            return Component.literal(resolveStateDisplayName(state)).withStyle(resolveStateColor(state));
        }
        if (token.startsWith("pr")) {
            int roomNo = parseIntSafe(token.substring(2));
            RoomCoreRuntimeManager coreManager = RoomCoreRuntimeManager.of(server);
            String roomId = resolveRoomIdByNumericSuffix(coreManager, roomNo);
            if (roomId == null) {
                return Component.literal("-/-").withStyle(ChatFormatting.DARK_GRAY);
            }
            RoomDefinition room = coreManager.findRoom(roomId).orElse(null);
            if (room == null) {
                return Component.literal("-/-").withStyle(ChatFormatting.DARK_GRAY);
            }
            int current = resolveCoreRoomPlayerCount(coreManager, room);
            int max = room.commonConfig().matchingConfig().campAMaxPlayers()
                + room.commonConfig().matchingConfig().campBMaxPlayers();
            return Component.literal(current + "/" + max).withStyle(resolvePlayerCountColor(current, max));
        }
        return Component.literal("-");
    }

    private ChatFormatting resolveStateColor(RoomLifecycleState state) {
        if (state == null) {
            return ChatFormatting.DARK_GRAY;
        }
        return switch (state) {
            case RUNNING -> ChatFormatting.GREEN;
            case MATCHING -> ChatFormatting.YELLOW;
            case ENDING -> ChatFormatting.GOLD;
            case DISABLED, CLEANING, SAVING -> ChatFormatting.RED;
            case IDLE, MAINTENANCE -> ChatFormatting.GRAY;
        };
    }

    private String resolveStateDisplayName(RoomLifecycleState state) {
        if (state == null) {
            return "-";
        }
        return switch (state) {
            case RUNNING -> "进行中";
            case MATCHING -> "匹配中";
            case ENDING -> "结算中";
            case DISABLED -> "禁用";
            case MAINTENANCE -> "维护中";
            case IDLE -> "空闲";
            case SAVING -> "保存中";
            case CLEANING -> "清理中";
        };
    }

    private ChatFormatting resolvePlayerCountColor(int current, int max) {
        if (max <= 0) {
            return ChatFormatting.DARK_GRAY;
        }
        if (current >= max) {
            return ChatFormatting.RED;
        }
        double ratio = current / (double) max;
        if (ratio >= 0.7D) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.GREEN;
    }

    private Optional<JoinRequest> extractJoinRequest(SignBlockEntity sign) {
        for (boolean front : new boolean[]{true, false}) {
            for (int line = 0; line < 4; line++) {
                String source = resolveTemplateSource(sign, front, line);
                if (source == null || source.isBlank()) {
                    continue;
                }
                Matcher roomJoinMatcher = ROOM_JOIN_PATTERN.matcher(source);
                if (roomJoinMatcher.find()) {
                    return Optional.of(new JoinRequest(JoinType.ROOM, parseIntSafe(roomJoinMatcher.group(1))));
                }
                if (ROOM_JOIN_AUTO_PATTERN.matcher(source).find()) {
                    return Optional.of(new JoinRequest(JoinType.RANGE, -1));
                }
                if (RANGE_LEAVE_AUTO_PATTERN.matcher(source).find()) {
                    return Optional.of(new JoinRequest(JoinType.RANGE_LEAVE, -1));
                }
            }
        }
        return Optional.empty();
    }

    private String resolveTemplateSource(SignBlockEntity sign, boolean front, int line) {
        String template = readTemplate(sign, templateKey(front, line));
        if (template != null && !template.isBlank()) {
            return template;
        }
        SignText text = front ? sign.getFrontText() : sign.getBackText();
        return text.getMessage(line, false).getString();
    }

    private boolean containsPlaceholder(SignBlockEntity sign) {
        for (boolean front : new boolean[]{true, false}) {
            for (int line = 0; line < 4; line++) {
                String text = resolveTemplateSource(sign, front, line);
                if (containsPlaceholder(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsPlaceholder(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("<gdc:sr")
            || normalized.contains("<gdc:pr")
            || normalized.contains("<gdc:jr")
            || normalized.contains("<gdc:jra>")
            || normalized.contains("<gdc:lra>");
    }

    private int resolveCoreRoomPlayerCount(RoomCoreRuntimeManager coreManager, RoomDefinition room) {
        if (coreManager == null || room == null) {
            return 0;
        }
        if (room.lifecycleState() == RoomLifecycleState.MATCHING) {
            return coreManager.findMatchingState(room.roomId())
                .map(state -> state.getQueuePlayers().size())
                .orElse(0);
        }
        return coreManager.countRoomPlayers(room.roomId());
    }

    private String resolveRoomIdByNumericSuffix(RoomCoreRuntimeManager coreManager, int roomNumber) {
        if (roomNumber <= 0) {
            return null;
        }
        return coreManager.listRooms().stream()
            .map(RoomDefinition::roomId)
            .filter(id -> extractRoomNumericSuffix(id) == roomNumber)
            .sorted(String::compareToIgnoreCase)
            .findFirst()
            .orElse(null);
    }

    private int extractRoomNumericSuffix(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return -1;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < roomId.length(); i++) {
            char c = roomId.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        if (digits.isEmpty()) {
            return -1;
        }
        return parseIntSafe(digits.toString());
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void trackSign(ServerLevel level, BlockPos pos) {
        trackedSigns.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    private String templateKey(boolean front, int line) {
        return TEMPLATE_KEY_PREFIX + (front ? "front_" : "back_") + line;
    }

    private String readTemplate(SignBlockEntity sign, String key) {
        if (!sign.getPersistentData().contains(key)) {
            return null;
        }
        return sign.getPersistentData().getString(key);
    }

    private void writeTemplate(SignBlockEntity sign, String key, String value) {
        sign.getPersistentData().putString(key, value == null ? "" : value);
        sign.setChanged();
    }

    private enum JoinType {
        ROOM,
        RANGE,
        RANGE_LEAVE
    }

    private record JoinRequest(JoinType type, int roomNumber) {
    }
}
