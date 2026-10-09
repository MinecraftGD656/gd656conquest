package org.mods.gd656conquest.server.social.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerActionBarService {
    private static final int DEFAULT_DURATION_TICKS = 110;
    private static final int ACTIONBAR_RESEND_INTERVAL_TICKS = 40;

    private static final Map<UUID, PlayerActionBarState> PLAYER_STATES = new LinkedHashMap<>();

    private static MinecraftServer currentServer;
    private static boolean initialized;

    private ServerActionBarService() {
    }

    public static void initialize(MinecraftServer server) {
        if (initialized) {
            return;
        }
        currentServer = server;
        PLAYER_STATES.clear();
        initialized = true;
    }

    public static void showText(ServerPlayer player, String sourceId, String text) {
        showText(player, sourceId, text, DEFAULT_DURATION_TICKS);
    }

    public static void showText(ServerPlayer player, String sourceId, String text, int durationTicks) {
        showComponent(player, sourceId, parseLegacyText(text == null ? "" : text.trim()), durationTicks);
    }

    public static void showComponent(ServerPlayer player, String sourceId, Component component) {
        showComponent(player, sourceId, component, DEFAULT_DURATION_TICKS);
    }

    public static void showComponent(ServerPlayer player, String sourceId, Component component, int durationTicks) {
        if (!initialized || player == null) {
            return;
        }
        if (component == null || component.getString().trim().isEmpty()) {
            clearSource(player, sourceId);
            return;
        }
        storeEntry(player, sourceId, component, durationTicks);
    }

    public static void showBuilder(ServerPlayer player, String sourceId, Builder builder) {
        showBuilder(player, sourceId, builder, DEFAULT_DURATION_TICKS);
    }

    public static void showBuilder(ServerPlayer player, String sourceId, Builder builder, int durationTicks) {
        showComponent(player, sourceId, builder == null ? null : builder.build(), durationTicks);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void clearSource(ServerPlayer player, String sourceId) {
        if (!initialized || player == null) {
            return;
        }
        PlayerActionBarState state = PLAYER_STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        state.entries.remove(normalizeSourceId(sourceId));
        refreshPlayer(player, state, true);
    }

    public static void clearPlayer(ServerPlayer player) {
        if (!initialized || player == null) {
            return;
        }
        PLAYER_STATES.remove(player.getUUID());
    }

    public static void onServerTick() {
        if (!initialized || currentServer == null || PLAYER_STATES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PlayerActionBarState>> iterator = PLAYER_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerActionBarState> entry = iterator.next();
            ServerPlayer player = currentServer.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            PlayerActionBarState state = entry.getValue();
            Iterator<Map.Entry<String, ActionBarEntry>> sourceIterator = state.entries.entrySet().iterator();
            while (sourceIterator.hasNext()) {
                Map.Entry<String, ActionBarEntry> sourceEntry = sourceIterator.next();
                ActionBarEntry actionBarEntry = sourceEntry.getValue();
                int nextTicks = actionBarEntry.remainingTicks - 1;
                if (nextTicks <= 0) {
                    sourceIterator.remove();
                } else {
                    sourceEntry.setValue(new ActionBarEntry(actionBarEntry.component, nextTicks));
                }
            }
            if (state.entries.isEmpty()) {
                iterator.remove();
                continue;
            }
            refreshPlayer(player, state, false, false);
        }
    }

    public static void shutdown() {
        if (!initialized) {
            return;
        }
        PLAYER_STATES.clear();
        currentServer = null;
        initialized = false;
    }

    private static void refreshPlayer(ServerPlayer player, PlayerActionBarState state, boolean removeWhenEmpty) {
        refreshPlayer(player, state, removeWhenEmpty, true);
    }

    private static void refreshPlayer(ServerPlayer player, PlayerActionBarState state, boolean removeWhenEmpty, boolean forceSend) {
        if (player == null || state == null) {
            return;
        }
        if (state.entries.isEmpty()) {
            state.hadVisibleContent = false;
            state.lastSentComponent = null;
            state.lastSentTick = Long.MIN_VALUE;
            if (removeWhenEmpty) {
                PLAYER_STATES.remove(player.getUUID());
            }
            return;
        }
        MutableComponent displayText = buildDisplayText(state);
        long currentTick = currentServer == null ? 0L : currentServer.getTickCount();
        boolean shouldResend = forceSend
            || !state.hadVisibleContent
            || state.lastSentComponent == null
            || !state.lastSentComponent.equals(displayText)
            || currentTick - state.lastSentTick >= ACTIONBAR_RESEND_INTERVAL_TICKS;
        if (shouldResend) {
            sendActionBar(player, displayText);
            state.lastSentComponent = displayText.copy();
            state.lastSentTick = currentTick;
        }
        state.hadVisibleContent = true;
    }

    private static MutableComponent buildDisplayText(PlayerActionBarState state) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (ActionBarEntry entry : state.entries.values()) {
            if (!first) {
                result.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            }
            result.append(entry.component.copy());
            first = false;
        }
        return result;
    }

    private static void storeEntry(ServerPlayer player, String sourceId, Component component, int durationTicks) {
        String normalizedSourceId = normalizeSourceId(sourceId);
        int resolvedDuration = Math.max(1, durationTicks);
        PlayerActionBarState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), ignored -> new PlayerActionBarState());
        state.entries.put(normalizedSourceId, new ActionBarEntry(component.copy(), resolvedDuration));
        refreshPlayer(player, state, false);
    }

    private static MutableComponent parseLegacyText(String text) {
        String input = text == null ? "" : text;
        MutableComponent root = Component.empty();
        StringBuilder buffer = new StringBuilder();
        ChatFormatting currentColor = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '\u00A7' && i + 1 < input.length()) {
                appendSegment(root, buffer, currentColor, bold, italic, underline, strikethrough, obfuscated);
                char code = Character.toLowerCase(input.charAt(++i));
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting == null) {
                    continue;
                }
                if (formatting == ChatFormatting.RESET) {
                    currentColor = null;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    obfuscated = false;
                    continue;
                }
                if (formatting.isColor()) {
                    currentColor = formatting;
                    bold = false;
                    italic = false;
                    underline = false;
                    strikethrough = false;
                    obfuscated = false;
                    continue;
                }
                switch (formatting) {
                    case BOLD -> bold = true;
                    case ITALIC -> italic = true;
                    case UNDERLINE -> underline = true;
                    case STRIKETHROUGH -> strikethrough = true;
                    case OBFUSCATED -> obfuscated = true;
                    default -> {
                    }
                }
                continue;
            }
            buffer.append(current);
        }

        appendSegment(root, buffer, currentColor, bold, italic, underline, strikethrough, obfuscated);
        return root;
    }

    private static void appendSegment(
        MutableComponent root,
        StringBuilder buffer,
        ChatFormatting color,
        boolean bold,
        boolean italic,
        boolean underline,
        boolean strikethrough,
        boolean obfuscated
    ) {
        if (buffer.isEmpty()) {
            return;
        }
        MutableComponent segment = Component.literal(buffer.toString()).withStyle(style -> {
            if (color != null) {
                style = style.withColor(color);
            }
            return style
                .withBold(bold)
                .withItalic(italic)
                .withUnderlined(underline)
                .withStrikethrough(strikethrough)
                .withObfuscated(obfuscated);
        });
        root.append(segment);
        buffer.setLength(0);
    }

    private static String normalizeSourceId(String sourceId) {
        String normalized = sourceId == null ? "" : sourceId.trim();
        return normalized.isEmpty() ? "default" : normalized;
    }

    private static void sendActionBar(ServerPlayer player, Component component) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(component == null ? Component.empty() : component));
    }

    private static MutableComponent literalWithStyle(String text, ChatFormatting... formats) {
        MutableComponent component = Component.literal(text == null ? "" : text);
        if (formats == null || formats.length == 0) {
            return component;
        }
        return component.withStyle(formats);
    }

    private static final class PlayerActionBarState {
        private final Map<String, ActionBarEntry> entries = new LinkedHashMap<>();
        private boolean hadVisibleContent;
        private Component lastSentComponent;
        private long lastSentTick = Long.MIN_VALUE;
    }

    private record ActionBarEntry(Component component, int remainingTicks) {
    }

    public static final class Builder {
        private final MutableComponent root = Component.empty();

        private Builder() {
        }

        public Builder text(String text, ChatFormatting... formats) {
            if (text == null || text.isEmpty()) {
                return this;
            }
            root.append(literalWithStyle(text, formats));
            return this;
        }

        public Builder key(String keyName) {
            return text("[" + (keyName == null ? "" : keyName) + "]", ChatFormatting.GOLD);
        }

        public Builder separator() {
            return text(" | ", ChatFormatting.DARK_GRAY);
        }

        public Builder labelValue(String label, String value, ChatFormatting labelColor, ChatFormatting valueColor) {
            text(label, labelColor);
            text(value, valueColor);
            return this;
        }

        public Builder append(Component component) {
            if (component == null || component.getString().isEmpty()) {
                return this;
            }
            root.append(component.copy());
            return this;
        }

        public MutableComponent build() {
            return root.copy();
        }
    }

    public static final class Templates {
        private Templates() {
        }

        public static MutableComponent keyPrompt(String keyName, String actionText) {
            return builder()
                .text("按下", ChatFormatting.WHITE)
                .key(keyName)
                .text(actionText, ChatFormatting.WHITE)
                .build();
        }

        public static MutableComponent cooldownSeconds(String label, int remainSeconds) {
            return cooldownSeconds(label, remainSeconds, ChatFormatting.RED, ChatFormatting.GOLD, "秒", ChatFormatting.RED);
        }

        public static MutableComponent cooldownSeconds(
            String label,
            int remainSeconds,
            ChatFormatting labelColor,
            ChatFormatting secondsColor,
            String suffix,
            ChatFormatting suffixColor
        ) {
            return builder()
                .text(label, labelColor)
                .text(String.valueOf(Math.max(1, remainSeconds)), secondsColor)
                .text(suffix, suffixColor)
                .build();
        }

        public static MutableComponent subjectAction(
            String prefix,
            ChatFormatting prefixColor,
            String subject,
            ChatFormatting subjectColor,
            String suffix,
            ChatFormatting suffixColor
        ) {
            return builder()
                .text(prefix, prefixColor)
                .text(subject, subjectColor)
                .text(suffix, suffixColor)
                .build();
        }

        public static MutableComponent labeledPair(
            String leftLabel,
            String leftValue,
            ChatFormatting leftLabelColor,
            ChatFormatting leftValueColor,
            String rightLabel,
            String rightValue,
            ChatFormatting rightLabelColor,
            ChatFormatting rightValueColor
        ) {
            return builder()
                .labelValue(leftLabel, leftValue, leftLabelColor, leftValueColor)
                .separator()
                .labelValue(rightLabel, rightValue, rightLabelColor, rightValueColor)
                .build();
        }
    }
}
