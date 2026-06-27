package org.mods.gd656conquest.server.loadout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TaczDisplayNameResolver {
    private static final String DEFAULT_NO_NAME_KEY = "custom.tacz.error.no_name";
    private static volatile Map<String, String> zhCnCache = Map.of();
    private static volatile Map<String, String> enUsCache = Map.of();
    private static volatile Path cacheRoot = null;

    private TaczDisplayNameResolver() {
    }

    public static String resolve(MinecraftServer server, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String hoverName = safeText(stack.getHoverName().getString());
        String descriptionId = safeText(stack.getDescriptionId());
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) {
            return hoverName;
        }
        if (!"tacz".equalsIgnoreCase(itemKey.getNamespace())) {
            return hoverName;
        }
        for (String translationKey : collectTranslationKeys(stack, hoverName, descriptionId)) {
            String localized = resolveLocalizedName(server, translationKey);
            if (!localized.isBlank()) {
                return localized;
            }
        }
        if (isReadableDisplayName(hoverName)) {
            return hoverName;
        }
        String fallback = prettifyTaczIdentifier(resolveDisplayOrGunId(stack));
        if (!fallback.isBlank()) {
            return fallback;
        }
        return hoverName;
    }

    public static boolean isTaczGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null || !"tacz".equalsIgnoreCase(itemKey.getNamespace())) {
            return false;
        }
        return stack.getTag() != null && stack.getTag().contains("GunId");
    }

    private static String resolveLocalizedName(MinecraftServer server, String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String normalized = key.trim();
        String value = getOrLoadZhCn(server).get(normalized);
        if (!isUsableLocalizedValue(value, normalized)) {
            value = getOrLoadEnUs(server).get(normalized);
        }
        if (!isUsableLocalizedValue(value, normalized)) {
            return "";
        }
        if (looksLikeTranslationKey(value)) {
            if (value.equals(normalized)) {
                return "";
            }
            return resolveLocalizedName(server, value);
        }
        return value.trim();
    }

    private static Map<String, String> getOrLoadZhCn(MinecraftServer server) {
        ensureLangCaches(server);
        return zhCnCache;
    }

    private static Map<String, String> getOrLoadEnUs(MinecraftServer server) {
        ensureLangCaches(server);
        return enUsCache;
    }

    private static void ensureLangCaches(MinecraftServer server) {
        Path root = resolveTaczRoot(server);
        if (root == null) {
            zhCnCache = Map.of();
            enUsCache = Map.of();
            cacheRoot = null;
            return;
        }
        if (root.equals(cacheRoot) && (!zhCnCache.isEmpty() || !enUsCache.isEmpty())) {
            return;
        }
        synchronized (TaczDisplayNameResolver.class) {
            if (root.equals(cacheRoot) && (!zhCnCache.isEmpty() || !enUsCache.isEmpty())) {
                return;
            }
            zhCnCache = loadLangMap(root, "zh_cn.json");
            enUsCache = loadLangMap(root, "en_us.json");
            cacheRoot = root;
        }
    }

    private static Path resolveTaczRoot(MinecraftServer server) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path fromGameDir = gameDir.resolve("tacz");
        if (Files.isDirectory(fromGameDir)) {
            return fromGameDir;
        }
        if (server != null) {
            Path fromServerDir = server.getServerDirectory().toPath().resolve("tacz");
            if (Files.isDirectory(fromServerDir)) {
                return fromServerDir;
            }
        }
        return null;
    }

    private static Map<String, String> loadLangMap(Path taczRoot, String fileName) {
        Map<String, String> map = new HashMap<>();
        try (var stream = Files.walk(taczRoot)) {
            stream.filter(path -> path.getFileName() != null && path.getFileName().toString().equalsIgnoreCase(fileName))
                .forEach(path -> mergeLangFile(path, map));
        } catch (IOException ignored) {
        }
        return map;
    }

    private static void mergeLangFile(Path file, Map<String, String> target) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                    continue;
                }
                target.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception ignored) {
        }
    }

    private static Set<String> collectTranslationKeys(ItemStack stack, String hoverName, String descriptionId) {
        Set<String> keys = new LinkedHashSet<>();
        addTranslationKey(keys, hoverName);
        addTranslationKey(keys, descriptionId);
        addResourceTranslationKeys(keys, readResourceId(stack, "GunDisplayId"));
        addResourceTranslationKeys(keys, readResourceId(stack, "GunId"));
        ResourceLocation registryKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        addResourceTranslationKeys(keys, registryKey);
        if (registryKey != null) {
            addTranslationKey(keys, "item." + registryKey.getNamespace() + "." + registryKey.getPath());
        }
        return keys;
    }

    private static void addResourceTranslationKeys(Set<String> keys, ResourceLocation id) {
        if (keys == null || id == null) {
            return;
        }
        String namespace = safeText(id.getNamespace());
        String path = safeText(id.getPath());
        if (namespace.isBlank() || path.isBlank()) {
            return;
        }
        addTranslationKey(keys, String.format(Locale.ROOT, "%s.gun.%s.name", namespace, path));
        addTranslationKey(keys, String.format(Locale.ROOT, "%s.gun.%s", namespace, path));
        addTranslationKey(keys, String.format(Locale.ROOT, "item.%s.%s", namespace, path));
    }

    private static void addTranslationKey(Set<String> keys, String key) {
        if (keys == null) {
            return;
        }
        String normalized = safeText(key);
        if (normalized.isBlank() || !looksLikeTranslationKey(normalized)) {
            return;
        }
        keys.add(normalized);
    }

    private static ResourceLocation readResourceId(ItemStack stack, String tagKey) {
        if (stack == null || stack.getTag() == null || tagKey == null || tagKey.isBlank()) {
            return null;
        }
        String raw = safeText(stack.getTag().getString(tagKey));
        return raw.isBlank() ? null : ResourceLocation.tryParse(raw);
    }

    private static ResourceLocation resolveDisplayOrGunId(ItemStack stack) {
        ResourceLocation displayId = readResourceId(stack, "GunDisplayId");
        if (displayId != null && !"default".equalsIgnoreCase(displayId.getPath())) {
            return displayId;
        }
        ResourceLocation gunId = readResourceId(stack, "GunId");
        if (gunId != null) {
            return gunId;
        }
        ResourceLocation registryKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return registryKey != null && "tacz".equalsIgnoreCase(registryKey.getNamespace()) ? registryKey : null;
    }

    private static boolean isUsableLocalizedValue(String value, String requestedKey) {
        String normalized = safeText(value);
        if (normalized.isBlank()) {
            return false;
        }
        if (DEFAULT_NO_NAME_KEY.equalsIgnoreCase(normalized)) {
            return false;
        }
        if ("error: no name".equalsIgnoreCase(normalized) || "错误：没有名字".equalsIgnoreCase(normalized)) {
            return false;
        }
        return !normalized.equals(requestedKey);
    }

    private static boolean isReadableDisplayName(String text) {
        String normalized = safeText(text);
        if (normalized.isBlank()) {
            return false;
        }
        if (DEFAULT_NO_NAME_KEY.equalsIgnoreCase(normalized)) {
            return false;
        }
        if (looksLikeTranslationKey(normalized)) {
            return false;
        }
        return !"error: no name".equalsIgnoreCase(normalized) && !"错误：没有名字".equalsIgnoreCase(normalized);
    }

    private static String prettifyTaczIdentifier(ResourceLocation id) {
        if (id == null || id.getPath() == null || id.getPath().isBlank()) {
            return "";
        }
        String[] parts = id.getPath().split("[_\\-]+");
        ArrayList<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String token = safeText(part);
            if (token.isBlank() || "display".equalsIgnoreCase(token)) {
                continue;
            }
            if (token.length() <= 2 || containsDigit(token)) {
                tokens.add(token.toUpperCase(Locale.ROOT));
                continue;
            }
            tokens.add(Character.toUpperCase(token.charAt(0)) + token.substring(1));
        }
        return String.join(" ", tokens).trim();
    }

    private static boolean containsDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeTranslationKey(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains(" ")) {
            return false;
        }
        return text.indexOf('.') > 0;
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
