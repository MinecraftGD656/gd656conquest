package org.mods.gd656conquest.server.shared.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.mods.gd656conquest.server.player.identity.PlayerIdentityService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * GD656Mods: 玩家名补全的唯一入口。
 *
 * <p>之前每个命令类各写一份 {@code SharedSuggestionProvider.suggest(<玩家名列表>, builder)}
 * (称号 give/remove、跑酷 del、组队 invite), 规则要改就得改多处。这里收敛成一处:
 * <ul>
 *     <li>{@link #sanitize(Collection)} 统一清洗: 去掉空白与 <strong>UUID 形态的条目</strong>;</li>
 *     <li>{@link #suggest(Collection, SuggestionsBuilder)} 是通用补全入口;</li>
 *     <li>{@link #suggestKnownPlayers(CommandContext, SuggestionsBuilder)} 是"已知玩家名"的便捷封装。</li>
 * </ul>
 *
 * <p>为什么要过滤 UUID: 玩家没有名字记录时, 写数据的一端会用 {@code playerId.toString()} 兜底
 * (见 {@code PlayerTitleService.giveTitleDirect} / {@code resolvePlayerNameOrUuid}),
 * 于是 {@code findLastKnownPlayerName} 会返回 UUID 字符串。UUID 是数据键而非玩家名,
 * 出现在补全列表里只会让管理员误选。
 */
public final class PlayerNameSuggestions {
    /**
     * GD656Mods: 严格匹配 8-4-4-4-12 的 UUID 文本。
     * 刻意不用 {@code UUID.fromString} 试探 —— 它对 {@code 1-1-1-1-1} 这类非法格式也会"解析成功"(自动补零),
     * 会把正常玩家名误判成 UUID。
     */
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private PlayerNameSuggestions() {
    }

    /** GD656Mods: 该字符串是否形如 UUID。 */
    public static boolean isUuidLike(String value) {
        return value != null && UUID_PATTERN.matcher(value.trim()).matches();
    }

    /** GD656Mods: 清洗候选名单 —— 剔除空白与 UUID 条目。 */
    public static List<String> sanitize(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>(names.size());
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (trimmed.isEmpty() || isUuidLike(trimmed)) {
                continue;
            }
            filtered.add(trimmed);
        }
        return filtered;
    }

    /** GD656Mods: 通用玩家名补全入口(已清洗)。 */
    public static CompletableFuture<Suggestions> suggest(Collection<String> names, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(sanitize(names), builder);
    }

    /** GD656Mods: "已知玩家名"(在线 + 历史玩家)补全, 统一走清洗。 */
    public static CompletableFuture<Suggestions> suggestKnownPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return suggest(PlayerIdentityService.of(context.getSource().getServer()).suggestKnownPlayerNames(), builder);
    }
}
