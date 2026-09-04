package org.mods.gd656conquest.common.capture;

import net.minecraft.core.BlockPos;
import org.mods.gd656conquest.common.block.ConquestBlocks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Arrays;

public record CaptureBoundaryBakeFile(
    int formatVersion,
    String roomId,
    int dataVersion,
    String payloadHash,
    int totalBlocks,
    Map<String, Integer> captureCounts,
    List<ChunkEntry> chunks
) {
    public static final int FORMAT_VERSION = 1;
    private static final int MAGIC = 0x47444342; // GDCB
    private static final List<String> CAPTURE_CODES = List.copyOf(ConquestBlocks.captureCodes());

    public CaptureBoundaryBakeFile {
        roomId = roomId == null ? "" : roomId.trim().toLowerCase(Locale.ROOT);
        payloadHash = payloadHash == null ? "" : payloadHash.trim().toLowerCase(Locale.ROOT);
        captureCounts = normalizeCounts(captureCounts);
        chunks = normalizeChunks(chunks);
        totalBlocks = Math.max(0, totalBlocks);
    }

    public static CaptureBoundaryBakeFile create(
        String roomId,
        int dataVersion,
        Map<String, Integer> captureCounts,
        List<ChunkEntry> chunks
    ) {
        Map<String, Integer> normalizedCounts = normalizeCounts(captureCounts);
        List<ChunkEntry> normalizedChunks = normalizeChunks(chunks);
        int totalBlocks = normalizedChunks.stream().mapToInt(chunk -> chunk.blocks().size()).sum();
        String hash = sha256Hex(encodePayload(totalBlocks, normalizedCounts, normalizedChunks));
        return new CaptureBoundaryBakeFile(FORMAT_VERSION, roomId, dataVersion, hash, totalBlocks, normalizedCounts, normalizedChunks);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(output);
            data.writeInt(MAGIC);
            data.writeInt(formatVersion);
            data.writeUTF(roomId);
            data.writeInt(dataVersion);
            data.writeUTF(payloadHash);
            data.write(encodePayload(totalBlocks, captureCounts, chunks));
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码烘焙边界文件", exception);
        }
    }

    public static CaptureBoundaryBakeFile read(Path path) {
        try {
            return read(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取烘焙边界文件: " + path, exception);
        }
    }

    public static CaptureBoundaryBakeFile read(byte[] bytes) {
        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
            DataInputStream input = new DataInputStream(byteStream);
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IllegalStateException("无效的烘焙文件魔数");
            }
            int formatVersion = input.readInt();
            String roomId = input.readUTF();
            int dataVersion = input.readInt();
            String payloadHash = input.readUTF();
            int payloadOffset = bytes.length - byteStream.available();
            byte[] payloadBytes = Arrays.copyOfRange(bytes, payloadOffset, bytes.length);
            int totalBlocks = input.readInt();
            int captureCountSize = input.readInt();
            Map<String, Integer> captureCounts = new LinkedHashMap<>();
            for (int i = 0; i < captureCountSize; i++) {
                captureCounts.put(input.readUTF(), input.readInt());
            }
            int chunkCount = input.readInt();
            List<ChunkEntry> chunks = new ArrayList<>(chunkCount);
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                int chunkX = input.readInt();
                int chunkZ = input.readInt();
                int blockCount = input.readInt();
                List<BlockEntry> blocks = new ArrayList<>(blockCount);
                for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
                    int localX = input.readUnsignedByte();
                    int y = input.readShort();
                    int localZ = input.readUnsignedByte();
                    int codeId = input.readUnsignedByte();
                    blocks.add(new BlockEntry(localX, y, localZ, codeId));
                }
                chunks.add(new ChunkEntry(chunkX, chunkZ, blocks));
            }
            CaptureBoundaryBakeFile file = new CaptureBoundaryBakeFile(formatVersion, roomId, dataVersion, payloadHash, totalBlocks, captureCounts, chunks);
            String computedHash = sha256Hex(payloadBytes);
            if (!computedHash.equalsIgnoreCase(file.payloadHash)) {
                throw new IllegalStateException("烘焙边界文件哈希校验失败");
            }
            return file;
        } catch (IOException exception) {
            throw new IllegalStateException("无法解析烘焙边界文件", exception);
        }
    }

    public static void writeAtomically(Path target, CaptureBoundaryBakeFile file) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(file, "file");
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = target.resolveSibling(target.getFileName().toString() + "." + UUID.randomUUID() + ".tmp");
            try {
                Files.write(temp, file.encode());
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法原子写入烘焙边界文件: " + target, exception);
        }
    }

    public Map<BlockPos, String> toBlockMap() {
        Map<BlockPos, String> result = new LinkedHashMap<>();
        for (ChunkEntry chunk : chunks) {
            int baseX = chunk.chunkX << 4;
            int baseZ = chunk.chunkZ << 4;
            for (BlockEntry block : chunk.blocks) {
                String code = captureCodeFromId(block.codeId);
                if (code == null || code.isBlank()) {
                    continue;
                }
                result.put(new BlockPos(baseX + block.localX, block.y, baseZ + block.localZ), code);
            }
        }
        return result;
    }

    public String summaryLine() {
        List<String> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : captureCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            rows.add(formatCaptureLabel(entry.getKey()) + ":" + entry.getValue());
        }
        return String.join(";", rows);
    }

    public static int captureCodeToId(String code) {
        if (code == null || code.isBlank()) {
            return -1;
        }
        for (int i = 0; i < CAPTURE_CODES.size(); i++) {
            if (CAPTURE_CODES.get(i).equalsIgnoreCase(code.trim())) {
                return i;
            }
        }
        return -1;
    }

    public static String captureCodeFromId(int id) {
        return id >= 0 && id < CAPTURE_CODES.size() ? CAPTURE_CODES.get(id) : "";
    }

    public static String formatCaptureLabel(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 1) {
            normalized = normalized + "0";
        }
        return "Capture" + normalized;
    }

    private static byte[] encodePayload(int totalBlocks, Map<String, Integer> captureCounts, List<ChunkEntry> chunks) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(output);
            data.writeInt(totalBlocks);
            data.writeInt(captureCounts.size());
            for (Map.Entry<String, Integer> entry : captureCounts.entrySet()) {
                data.writeUTF(entry.getKey());
                data.writeInt(Math.max(0, entry.getValue()));
            }
            data.writeInt(chunks.size());
            for (ChunkEntry chunk : chunks) {
                data.writeInt(chunk.chunkX);
                data.writeInt(chunk.chunkZ);
                data.writeInt(chunk.blocks.size());
                for (BlockEntry block : chunk.blocks) {
                    data.writeByte(block.localX & 0xFF);
                    data.writeShort(block.y);
                    data.writeByte(block.localZ & 0xFF);
                    data.writeByte(block.codeId & 0xFF);
                }
            }
            data.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码烘焙边界载荷", exception);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256", exception);
        }
    }

    private static Map<String, Integer> normalizeCounts(Map<String, Integer> values) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        if (values != null) {
            values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> normalized.put(entry.getKey().trim().toUpperCase(Locale.ROOT), Math.max(0, entry.getValue() == null ? 0 : entry.getValue())));
        }
        return normalized.isEmpty() ? Map.of() : Collections.unmodifiableMap(normalized);
    }

    private static List<ChunkEntry> normalizeChunks(List<ChunkEntry> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(ChunkEntry::normalized)
            .sorted(Comparator.comparingInt(ChunkEntry::chunkX).thenComparingInt(ChunkEntry::chunkZ))
            .toList();
    }

    public record ChunkEntry(int chunkX, int chunkZ, List<BlockEntry> blocks) {
        public ChunkEntry {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }

        private ChunkEntry normalized() {
            return new ChunkEntry(
                chunkX,
                chunkZ,
                blocks.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(BlockEntry::y).thenComparingInt(BlockEntry::localZ).thenComparingInt(BlockEntry::localX))
                    .toList()
            );
        }
    }

    public record BlockEntry(int localX, int y, int localZ, int codeId) {
        public BlockEntry {
            localX = Math.max(0, Math.min(15, localX));
            localZ = Math.max(0, Math.min(15, localZ));
            codeId = Math.max(0, codeId);
        }
    }
}
