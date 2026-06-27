package org.mods.gd656conquest.server.data.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public abstract class JsonDataStoreSupport {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    protected final void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建数据目录: " + directory, exception);
        }
    }

    protected final void ensurePath(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                ensureDirectory(parent);
            }
            if (Files.notExists(path)) {
                Files.createFile(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建数据文件: " + path, exception);
        }
    }

    protected final <T> T read(Path path, Class<T> type, Supplier<T> defaultSupplier) {
        ensurePath(path);
        try (Reader reader = Files.newBufferedReader(path)) {
            T data = GSON.fromJson(reader, type);
            return data == null ? defaultSupplier.get() : data;
        } catch (Exception exception) {
            return defaultSupplier.get();
        }
    }

    protected final void write(Path path, Object data) {
        ensurePath(path);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(data, writer);
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入数据文件: " + path, exception);
        }
    }
}
