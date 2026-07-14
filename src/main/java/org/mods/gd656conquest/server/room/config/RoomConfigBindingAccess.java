package org.mods.gd656conquest.server.room.config;

import org.mods.gd656conquest.server.room.model.RoomDefinition;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;
import org.mods.gd656conquest.server.room.model.RoomTeamId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RoomConfigBindingAccess {
    private RoomConfigBindingAccess() {
    }

    static Object resolve(RoomDefinition definition, String path) {
        if (definition == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = definition;
        for (PathSegment segment : parse(path)) {
            if (current == null) {
                return null;
            }
            current = readSegment(current, segment);
        }
        return current;
    }

    static RoomDefinition write(RoomDefinition definition, String path, Object value) {
        List<PathSegment> segments = parse(path);
        if (definition == null || segments.isEmpty()) {
            return definition;
        }
        return (RoomDefinition) writeRecursive(definition, segments, 0, value);
    }

    private static Object writeRecursive(Object current, List<PathSegment> segments, int index, Object value) {
        PathSegment segment = segments.get(index);
        if (index == segments.size() - 1) {
            return writeSegment(current, segment, value);
        }
        Object child = readSegment(current, segment);
        Object updatedChild = writeRecursive(child, segments, index + 1, value);
        return writeSegment(current, segment, updatedChild);
    }

    private static Object readSegment(Object current, PathSegment segment) {
        Object value = invokeGetter(current, segment.name());
        if (segment.index() == null || value == null) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return map.get(normalizeMapKey(segment.index()));
        }
        throw new IllegalArgumentException("不支持带索引读取: " + segment.name());
    }

    private static Object writeSegment(Object current, PathSegment segment, Object value) {
        if (current instanceof RoomDefinition definition && isGameplayConfigAlias(segment.name())) {
            return rebuildRoomDefinitionGameplayConfig(definition, segment.name(), value);
        }
        if (segment.index() == null) {
            return rebuildRecord(current, segment.name(), value);
        }
        Object existing = invokeGetter(current, segment.name());
        Map<Object, Object> copy = new LinkedHashMap<>();
        if (existing instanceof Map<?, ?> map) {
            copy.putAll(map);
        }
        Object key = normalizeMapKey(segment.index());
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return rebuildRecord(current, segment.name(), Map.copyOf(copy));
    }

    private static Object rebuildRecord(Object record, String componentName, Object replacement) {
        try {
            RecordComponent[] components = record.getClass().getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int index = 0; index < components.length; index++) {
                RecordComponent component = components[index];
                parameterTypes[index] = component.getType();
                arguments[index] = component.getName().equals(componentName)
                    ? replacement
                    : component.getAccessor().invoke(record);
            }
            Constructor<?> constructor = record.getClass().getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("无法更新配置路径: " + componentName, exception);
        }
    }

    private static Object invokeGetter(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("无法读取配置路径: " + target.getClass().getSimpleName() + "." + name, exception);
        }
    }

    private static boolean isGameplayConfigAlias(String name) {
        return "conquestConfig".equals(name) || "breakthroughConfig".equals(name) || "deathmatchConfig".equals(name);
    }

    private static Object rebuildRoomDefinitionGameplayConfig(RoomDefinition definition, String alias, Object replacement) {
        if ("conquestConfig".equals(alias) && !RoomGameplayId.CONQUEST.equals(definition.gameplayId())) {
            throw new IllegalArgumentException("当前房间玩法不是 conquest，无法写入征服配置");
        }
        if ("breakthroughConfig".equals(alias) && !RoomGameplayId.BREAKTHROUGH.equals(definition.gameplayId())) {
            throw new IllegalArgumentException("当前房间玩法不是 breakthrough，无法写入突破配置");
        }
        if ("deathmatchConfig".equals(alias) && !RoomGameplayId.DEATHMATCH.equals(definition.gameplayId())) {
            throw new IllegalArgumentException("当前房间玩法不是 deathmatch，无法写入团队死斗配置");
        }
        return rebuildRecord(definition, "gameplayConfig", replacement);
    }

    private static Object normalizeMapKey(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if ("CAMP_A".equals(normalized)) {
            return RoomTeamId.CAMP_A;
        }
        if ("CAMP_B".equals(normalized)) {
            return RoomTeamId.CAMP_B;
        }
        return normalized;
    }

    private static List<PathSegment> parse(String path) {
        List<PathSegment> segments = new ArrayList<>();
        for (String raw : path.split("\\.")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int bracket = raw.indexOf('[');
            if (bracket >= 0 && raw.endsWith("]")) {
                segments.add(new PathSegment(raw.substring(0, bracket), raw.substring(bracket + 1, raw.length() - 1)));
            } else {
                segments.add(new PathSegment(raw.trim(), null));
            }
        }
        return segments;
    }

    private record PathSegment(String name, String index) {
    }
}
