package org.mods.gd656conquest.server.room.config;

import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomPosition;
import org.mods.gd656conquest.server.room.model.RoomSpawnGroupConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GD656Mods: 房间配置里"位置 y 的精度规则"唯一实现处。
 *
 * <p>两类规则:
 * <ul>
 *     <li><strong>边界类</strong>(战斗区域边界 / 大本营边界 / 阶段边界 …): y 一律向下取整为整数。
 *         边界是一条水平折线, 小数 y 会让同一条边界在不同渲染通路上错开
 *         (debug overlay 用 round、部署界面线框用 floor、烘焙方块用 floor(y-0.5));</li>
 *     <li><strong>出生点类</strong>(据点内/外出生组、阵营出生组、排队出生点 …): y 保留 1 位小数。
 *         出生点需要贴合地面/半砖的高度, 取整会把玩家卡进方块里。</li>
 * </ul>
 *
 * <p>{@link #normalize(RoomDefinition)} 用于把<strong>已经存在的旧小数数据</strong>就地修正:
 * 它由 {@code RoomDefinitionRepository.validateAndNormalize} 调用, 因此房间在加载/保存时
 * 都会被自动修正并回写文件, 不需要单独的迁移命令。
 */
public final class RoomPositionPrecision {
    /** GD656Mods: 边界类配置的键名特征。 */
    private static final String BOUNDARY_KEY = "boundary";
    /** GD656Mods: 出生点类配置的键名特征。 */
    private static final String SPAWN_KEY = "spawn";

    private RoomPositionPrecision() {
    }

    /** GD656Mods: 边界 y —— 向下取整为整数。 */
    public static double boundaryY(double y) {
        return Math.floor(y);
    }

    /** GD656Mods: 出生点 y —— 保留 1 位小数。 */
    public static double spawnY(double y) {
        return Math.round(y * 10.0D) / 10.0D;
    }

    public static RoomPosition boundary(RoomPosition position) {
        if (position == null) {
            return null;
        }
        double y = boundaryY(position.y());
        return y == position.y() ? position : new RoomPosition(position.x(), y, position.z());
    }

    public static RoomPosition spawn(RoomPosition position) {
        if (position == null) {
            return null;
        }
        double y = spawnY(position.y());
        return y == position.y() ? position : new RoomPosition(position.x(), y, position.z());
    }

    /** GD656Mods: 该配置项是否边界类。 */
    public static boolean isBoundaryKey(String key) {
        return key != null && key.toLowerCase(Locale.ROOT).contains(BOUNDARY_KEY);
    }

    /** GD656Mods: 该配置项是否出生点类。 */
    public static boolean isSpawnKey(String key) {
        return key != null && key.toLowerCase(Locale.ROOT).contains(SPAWN_KEY);
    }

    /**
     * GD656Mods: 按配置项键名归一化一个解析出来的配置值。
     * 只处理已知的三类形态(RoomPosition / List&lt;RoomPosition&gt; / RoomSpawnGroupConfig),
     * 其余类型(视角、字符串等)原样返回。
     */
    public static Object normalizeValue(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (isBoundaryKey(key)) {
            return mapPositions(value, RoomPositionPrecision::boundary);
        }
        if (isSpawnKey(key)) {
            return mapPositions(value, RoomPositionPrecision::spawn);
        }
        return value;
    }

    /** GD656Mods: 按配置项键名归一化单个坐标(供写入端直接调用)。 */
    public static RoomPosition normalizePosition(RoomPosition position, String key) {
        if (position == null) {
            return null;
        }
        if (isBoundaryKey(key)) {
            return boundary(position);
        }
        if (isSpawnKey(key)) {
            return spawn(position);
        }
        return position;
    }

    /**
     * GD656Mods: 归一化整间房的所有位置配置。
     * 无变化时返回原对象(便于调用方用 {@code equals} 判断是否需要回写)。
     */
    public static RoomDefinition normalize(RoomDefinition room) {
        if (room == null) {
            return null;
        }
        RoomDefinition result = room;
        for (RoomConfigDescriptor descriptor : positionDescriptors(room.gameplayId())) {
            String key = descriptor.newPath() + "|" + descriptor.configId();
            Object resolved;
            try {
                resolved = RoomConfigValueResolver.resolve(result, descriptor);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            Object normalized = normalizeValue(resolved, key);
            if (normalized == null || normalized.equals(resolved)) {
                continue;
            }
            try {
                result = RoomConfigBindingAccess.write(result, descriptor.newPath(), normalized);
            } catch (RuntimeException ignored) {
                // 单个配置项失败不应影响其它配置项的归一化
            }
        }
        return result;
    }

    private static Object mapPositions(Object value, java.util.function.UnaryOperator<RoomPosition> mapper) {
        if (value instanceof RoomPosition position) {
            return mapper.apply(position);
        }
        if (value instanceof RoomSpawnGroupConfig spawnGroup) {
            List<RoomPosition> mapped = mapList(spawnGroup.spawns(), mapper);
            return mapped.equals(spawnGroup.spawns()) ? spawnGroup : new RoomSpawnGroupConfig(spawnGroup.enabled(), mapped);
        }
        if (value instanceof Map<?, ?> map) {
            // GD656Mods: 据点"外出生组"等配置会解析成 Map(按阵营), 需要逐值递归。
            Map<Object, Object> mapped = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object replacement = mapPositions(entry.getValue(), mapper);
                changed |= replacement != entry.getValue();
                mapped.put(entry.getKey(), replacement);
            }
            return changed ? Map.copyOf(mapped) : value;
        }
        if (value instanceof List<?> list) {
            List<Object> mapped = new ArrayList<>(list.size());
            boolean changed = false;
            for (Object element : list) {
                Object replacement = element instanceof RoomPosition position ? mapper.apply(position) : element;
                changed |= replacement != element;
                mapped.add(replacement);
            }
            return changed ? List.copyOf(mapped) : value;
        }
        return value;
    }

    private static List<RoomPosition> mapList(List<RoomPosition> positions, java.util.function.UnaryOperator<RoomPosition> mapper) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        List<RoomPosition> mapped = new ArrayList<>(positions.size());
        for (RoomPosition position : positions) {
            mapped.add(mapper.apply(position));
        }
        return mapped;
    }

    /** GD656Mods: 该玩法下所有"位置类"配置项(边界 / 出生点 / 视角等), 键名决定是否归一化。 */
    private static List<RoomConfigDescriptor> positionDescriptors(RoomGameplayId gameplayId) {
        Map<String, RoomConfigDescriptor> byPath = new LinkedHashMap<>();
        collect(byPath, RoomConfigDescriptorRegistry.legacyCommonCommandIds());
        collect(byPath, gameplayDescriptorIds(gameplayId));
        List<RoomConfigDescriptor> descriptors = new ArrayList<>();
        for (RoomConfigDescriptor descriptor : byPath.values()) {
            if (descriptor == null || !supportsPositionPrecision(descriptor)) {
                continue;
            }
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    private static void collect(Map<String, RoomConfigDescriptor> byPath, List<String> descriptorIds) {
        if (descriptorIds == null || descriptorIds.isEmpty()) {
            return;
        }
        for (String descriptorId : descriptorIds) {
            RoomConfigDescriptorRegistry.find(descriptorId)
                .ifPresent(descriptor -> byPath.putIfAbsent(descriptor.newPath(), descriptor));
        }
    }

    private static List<String> gameplayDescriptorIds(RoomGameplayId gameplayId) {
        if (RoomGameplayId.BREAKTHROUGH.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyBreakthroughCommandIds();
        }
        if (RoomGameplayId.RUSH.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyRushCommandIds();
        }
        if (RoomGameplayId.DEATHMATCH.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyDeathmatchCommandIds();
        }
        if (RoomGameplayId.GUNMASTER.equals(gameplayId)) {
            return RoomConfigDescriptorRegistry.legacyGunmasterCommandIds();
        }
        return RoomConfigDescriptorRegistry.legacyConquestCommandIds();
    }

    private static boolean supportsPositionPrecision(RoomConfigDescriptor descriptor) {
        return switch (descriptor.valueType()) {
            case POSITION, POSITION_GROUP, DATA_GROUP -> true;
            default -> false;
        };
    }
}
