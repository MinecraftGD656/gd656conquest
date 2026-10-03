package org.mods.gd656conquest.common.overlay;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GD656Mods: debug overlay 行表协议(服务端与客户端共用的纯逻辑, 不依赖任何 MC 类型)。
 *
 * <p>设计背景: 旧的 AdminDebugOverlayPacket 把整张点表拼成一个字符串并在单包内发送,
 * payload 超过 writeUtf 上限(32767)时被静默截断, 点一多就"传不到客户端"。
 * 现在把它拆成两层:
 * <ul>
 *     <li>行表({@code 键 -> 行文本}): 键用于增删改定位, 行文本与旧 payload 的单行格式完全一致;</li>
 *     <li>传输: 首次按行表分片发基线, 之后只发 diff(新增/变更/删除)。</li>
 * </ul>
 * 客户端拿到行表后仍然拼接成与旧格式完全相同的字符串再交给下游解析,
 * 因此渲染/交互侧无需改动。
 *
 * <p>编码约定(键与行文本内都不会出现这两个控制字符: 行文本是
 * {@code kind,code,int,int,int,队别,队别,float,base64}, base64 为 URL-safe 字母数字集):
 * <ul>
 *     <li>{@link #ENTRY_SEPARATOR} 分隔条目;</li>
 *     <li>{@link #KEY_VALUE_SEPARATOR} 分隔键与值;</li>
 *     <li>值内部的多个行文本用 {@link #ROW_SEPARATOR}(与 payload 一致)连接。</li>
 * </ul>
 */
public final class AdminDebugOverlayRowTable {
    /** GD656Mods: 条目分隔符, 不会出现在键或行文本内。 */
    public static final char ENTRY_SEPARATOR = '\u0001';
    /** GD656Mods: 键与值分隔符, 不会出现在键或行文本内。 */
    public static final char KEY_VALUE_SEPARATOR = '\u0002';
    /** GD656Mods: 行分隔符, 与旧 payload 保持一致。 */
    public static final char ROW_SEPARATOR = ';';
    /** GD656Mods: 空表在客户端侧表现为"无 overlay", 沿用旧协议的空值标记。 */
    public static final String EMPTY_PAYLOAD = "-";

    private AdminDebugOverlayRowTable() {
    }

    /**
     * GD656Mods: 写入一行。同一键再次写入时追加 {@code #n} 后缀而不是覆盖,
     * 保证"每个点在客户端都有独立条目"(键冲突会让 Map 合并掉重叠点)。
     */
    public static void putRow(Map<String, String> table, String key, String row) {
        if (table == null || key == null || key.isEmpty() || row == null) {
            return;
        }
        if (!table.containsKey(key)) {
            table.put(key, row);
            return;
        }
        int suffix = 2;
        while (true) {
            String candidate = key + "#" + suffix;
            if (!table.containsKey(candidate)) {
                table.put(candidate, row);
                return;
            }
            suffix++;
        }
    }

    /** GD656Mods: 把 {@code 键 -> 行文本} 拼成旧协议格式的 payload。 */
    public static String toPayload(Map<String, String> table) {
        if (table == null || table.isEmpty()) {
            return EMPTY_PAYLOAD;
        }
        boolean first = true;
        StringBuilder builder = new StringBuilder();
        for (String value : table.values()) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!first) {
                builder.append(ROW_SEPARATOR);
            }
            builder.append(value);
            first = false;
        }
        return builder.length() == 0 ? EMPTY_PAYLOAD : builder.toString();
    }

    /** GD656Mods: 把整张行表编码成可传输的条目串。 */
    public static String encodeEntries(Map<String, String> table) {
        if (table == null || table.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(ENTRY_SEPARATOR);
            }
            builder.append(entry.getKey()).append(KEY_VALUE_SEPARATOR).append(entry.getValue());
        }
        return builder.toString();
    }

    /** GD656Mods: 解码条目串为保序行表; 残缺条目直接丢弃(宁缺勿错, 由下一次基线自愈)。 */
    public static Map<String, String> decodeEntries(String blob) {
        Map<String, String> table = new LinkedHashMap<>();
        if (blob == null || blob.isEmpty()) {
            return table;
        }
        for (String entry : splitEntries(blob)) {
            int separatorIndex = entry.indexOf(KEY_VALUE_SEPARATOR);
            if (separatorIndex <= 0) {
                continue;
            }
            String key = entry.substring(0, separatorIndex);
            String value = entry.substring(separatorIndex + 1);
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            table.put(key, value);
        }
        return table;
    }

    /**
     * GD656Mods: 按 UTF-8 字节把条目串切成若干片, 片内绝不切断条目,
     * 因此客户端可以逐片解码后直接合并。
     */
    public static List<String> chunk(String entriesBlob, int maxChunkBytes) {
        List<String> chunks = new ArrayList<>();
        if (entriesBlob == null || entriesBlob.isEmpty()) {
            // GD656Mods: 空表也要产出一片 —— 客户端据此原子替换成"空表",
            // 而不是因为"一片都没收到"而保留旧点集。
            chunks.add("");
            return chunks;
        }
        int limit = maxChunkBytes > 0 ? maxChunkBytes : AdminDebugOverlayProtocol.DEFAULT_CHUNK_BYTES;
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        for (String entry : splitEntries(entriesBlob)) {
            int entryBytes = utf8Length(entry);
            int extraBytes = current.length() == 0 ? entryBytes : entryBytes + 1;
            if (current.length() > 0 && currentBytes + extraBytes > limit) {
                chunks.add(current.toString());
                current.setLength(0);
                currentBytes = 0;
                extraBytes = entryBytes;
            }
            if (current.length() > 0) {
                current.append(ENTRY_SEPARATOR);
            }
            current.append(entry);
            currentBytes += extraBytes;
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        // GD656Mods: 单个条目本身就超上限时无法分片(理论上不可能: 一条行文本 < 200 字节),
        // 此时保留该片并由 encode 侧作废处理, 不允许静默丢点。
        return chunks;
    }

    /** GD656Mods: 计算行表相对上一版的差异。 */
    public static Diff diff(Map<String, String> previous, Map<String, String> next) {
        Map<String, String> safePrevious = previous == null ? Map.of() : previous;
        Map<String, String> safeNext = next == null ? Map.of() : next;
        Map<String, String> upserts = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : safeNext.entrySet()) {
            String oldValue = safePrevious.get(entry.getKey());
            if (oldValue == null || !oldValue.equals(entry.getValue())) {
                upserts.put(entry.getKey(), entry.getValue());
            }
        }
        Set<String> removed = new LinkedHashSet<>();
        for (String key : safePrevious.keySet()) {
            if (!safeNext.containsKey(key)) {
                removed.add(key);
            }
        }
        return new Diff(upserts, removed);
    }

    /** GD656Mods: UTF-8 字节长度(writeUtf 的实际上限按字节计更保守)。 */
    public static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static List<String> splitEntries(String blob) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < blob.length(); index++) {
            if (blob.charAt(index) != ENTRY_SEPARATOR) {
                continue;
            }
            if (index > start) {
                entries.add(blob.substring(start, index));
            }
            start = index + 1;
        }
        if (start < blob.length()) {
            entries.add(blob.substring(start));
        }
        return entries;
    }

    /** GD656Mods: 行表差异。{@code upserts} 为新增或内容变化的条目, {@code removed} 为需要删除的键。 */
    public record Diff(Map<String, String> upserts, Set<String> removed) {
        public Diff {
            // GD656Mods: 保持插入顺序 —— 顺序稳定才能让客户端拼出的 payload 具备确定性,
            // 否则下游"字符串相等即跳过重解析"的判断会失效。
            upserts = upserts == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(upserts));
            removed = removed == null ? Set.of() : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(removed));
        }

        public boolean isEmpty() {
            return upserts.isEmpty() && removed.isEmpty();
        }

        /** GD656Mods: 估算本次 diff 的传输字节数, 用于判断是否需要降级为重新发基线。 */
        public int byteSize() {
            int size = utf8Length(encodeEntries(upserts));
            for (String key : removed) {
                size += utf8Length(key) + 1;
            }
            return size;
        }
    }
}
