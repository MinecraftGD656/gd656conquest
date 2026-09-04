package org.mods.gd656conquest.client.event.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import org.mods.gd656conquest.client.log.ClientLogger;
import org.mods.gd656conquest.common.capture.CaptureBoundaryBakeFile;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.CaptureBoundaryBakeRequestPacket;
import org.mods.gd656conquest.server.capture.CaptureBoundaryBakeService;
import org.mods.gd656conquest.server.shared.text.ServerText;

import net.minecraft.ChatFormatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ClientCaptureBakeState {
    private static String activeRoomId = "";
    private static String failedRequestKey = "";
    private static PendingDownload pendingDownload;

    private ClientCaptureBakeState() {
    }

    public static void handleMeta(String roomId, int version, String payloadHash, boolean available) {
        activeRoomId = CaptureBoundaryBakeService.canonicalRoomId(roomId);
        if (!available) {
            pendingDownload = null;
            failedRequestKey = "";
            ClientEventHandler.getInstance().applyCaptureBake(activeRoomId, Map.of());
            return;
        }
        try {
            Path file = resolveLocalBakeFile(activeRoomId);
            if (Files.exists(file)) {
                CaptureBoundaryBakeFile local = CaptureBoundaryBakeFile.read(file);
                if (local.dataVersion() == version && local.payloadHash().equalsIgnoreCase(payloadHash)) {
                    ClientEventHandler.getInstance().applyCaptureBake(activeRoomId, local.toBlockMap());
                    return;
                }
            }
        } catch (Exception exception) {
            ClientLogger.warning("capture bake local cache invalid: room=%s reason=%s", activeRoomId, exception.getMessage());
        }
        String requestKey = activeRoomId + "#" + version + "#" + payloadHash;
        if (requestKey.equalsIgnoreCase(failedRequestKey)) {
            return;
        }
        pendingDownload = new PendingDownload(activeRoomId, version, payloadHash);
        NetworkManager.getChannel().sendToServer(new CaptureBoundaryBakeRequestPacket(activeRoomId));
    }

    public static void handleChunk(String roomId, int version, String payloadHash, int batchIndex, int batchCount, byte[] payload) {
        String canonicalRoomId = CaptureBoundaryBakeService.canonicalRoomId(roomId);
        if (pendingDownload == null
            || !pendingDownload.roomId.equalsIgnoreCase(canonicalRoomId)
            || pendingDownload.version != version
            || !pendingDownload.payloadHash.equalsIgnoreCase(payloadHash)) {
            pendingDownload = new PendingDownload(canonicalRoomId, version, payloadHash);
        }
        pendingDownload.batchCount = Math.max(1, batchCount);
        pendingDownload.batches.put(batchIndex, payload == null ? new byte[0] : payload);
        if (pendingDownload.batches.size() < pendingDownload.batchCount) {
            return;
        }
        try {
            int totalSize = pendingDownload.batches.values().stream().mapToInt(bytes -> bytes == null ? 0 : bytes.length).sum();
            byte[] merged = new byte[totalSize];
            int cursor = 0;
            for (int index = 0; index < pendingDownload.batchCount; index++) {
                byte[] bytes = pendingDownload.batches.get(index);
                if (bytes == null) {
                    throw new IllegalStateException("下载分包缺失");
                }
                System.arraycopy(bytes, 0, merged, cursor, bytes.length);
                cursor += bytes.length;
            }
            CaptureBoundaryBakeFile bakeFile = CaptureBoundaryBakeFile.read(merged);
            Path target = resolveLocalBakeFile(canonicalRoomId);
            CaptureBoundaryBakeFile.writeAtomically(target, bakeFile);
            failedRequestKey = "";
            pendingDownload = null;
            if (canonicalRoomId.equalsIgnoreCase(activeRoomId)) {
                ClientEventHandler.getInstance().applyCaptureBake(canonicalRoomId, bakeFile.toBlockMap());
            }
        } catch (Exception exception) {
            ClientLogger.failure("capture bake download/apply failed: room=%s version=%d hash=%s reason=%s(%s)",
                canonicalRoomId,
                version,
                payloadHash,
                exception.getClass().getSimpleName(),
                exception.getMessage()
            );
            pendingDownload = null;
            handleFailure(canonicalRoomId, "下载烘焙数据失败，请检查网络连接稳定性或尝试重新进入房间");
        }
    }

    public static void handleFailure(String roomId, String message) {
        failedRequestKey = CaptureBoundaryBakeService.canonicalRoomId(roomId)
            + "#"
            + (pendingDownload == null ? 0 : pendingDownload.version)
            + "#"
            + (pendingDownload == null ? "" : pendingDownload.payloadHash);
        pendingDownload = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null && message != null && !message.isBlank()) {
            minecraft.player.sendSystemMessage(ServerText.prefixed(null, message, ChatFormatting.RED));
        }
    }

    public static void setDebugScanEnabled(boolean enabled) {
        ClientEventHandler.getInstance().setCaptureDebugScanEnabled(enabled);
    }

    public static void reset() {
        activeRoomId = "";
        failedRequestKey = "";
        pendingDownload = null;
    }

    private static Path resolveLocalBakeFile(String roomId) {
        Path root = FMLPaths.GAMEDIR.get().resolve("gd656conquest").resolve("bake");
        return root.resolve(org.mods.gd656conquest.server.data.ServerDataPaths.toBakeFileName(roomId));
    }

    private static final class PendingDownload {
        private final String roomId;
        private final int version;
        private final String payloadHash;
        private int batchCount;
        private final Map<Integer, byte[]> batches = new HashMap<>();

        private PendingDownload(String roomId, int version, String payloadHash) {
            this.roomId = roomId;
            this.version = version;
            this.payloadHash = payloadHash == null ? "" : payloadHash;
        }
    }
}
