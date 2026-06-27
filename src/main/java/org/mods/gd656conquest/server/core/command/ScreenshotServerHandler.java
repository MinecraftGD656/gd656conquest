package org.mods.gd656conquest.server.core.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.mods.gd656conquest.common.log.ClientMessageType;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ClientboundScreenshotRequestPacket;
import org.mods.gd656conquest.server.shared.log.ServerLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ScreenshotServerHandler {
    private static final long REQUEST_TIMEOUT_MS = 20_000L;
    private static final Map<String, PendingRequest> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<String, ScreenshotSession> SESSIONS = new ConcurrentHashMap<>();

    private ScreenshotServerHandler() {
    }

    public static void requestScreenshot(CommandSourceStack source, ServerPlayer target) {
        if (source == null || target == null) {
            return;
        }
        String sessionToken = UUID.randomUUID().toString();
        PendingRequest request = new PendingRequest(
            sessionToken,
            source.getServer(),
            source.getEntity() instanceof ServerPlayer requester ? requester.getUUID() : null,
            source.getTextName(),
            target.getUUID(),
            target.getScoreboardName()
        );
        REQUESTS.put(sessionToken, request);
        NetworkManager.sendToPlayer(target, new ClientboundScreenshotRequestPacket(sessionToken));
        notifyRequester(request, ClientMessageType.INFO, "已向 %s 发送截图请求，等待回传...", request.targetName());
        CompletableFuture.delayedExecutor(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).execute(() -> {
            MinecraftServer server = request.server();
            if (server != null) {
                server.execute(() -> timeoutRequest(sessionToken));
            }
        });
    }

    public static void handleChunk(ServerPlayer sender, String sessionToken, int chunkIndex, int totalChunks, byte[] data) {
        PendingRequest request = REQUESTS.get(sessionToken);
        if (request == null || !request.targetId().equals(sender.getUUID()) || totalChunks <= 0) {
            return;
        }
        ScreenshotSession session = SESSIONS.computeIfAbsent(sessionToken, k -> new ScreenshotSession(totalChunks));
        session.addChunk(chunkIndex, data);
        if (session.isComplete()) {
            SESSIONS.remove(sessionToken);
            REQUESTS.remove(sessionToken);
            saveScreenshot(request, session.assemble());
        }
    }

    public static void handleStatus(ServerPlayer sender, String sessionToken, boolean success, String message) {
        PendingRequest request = REQUESTS.get(sessionToken);
        if (request == null || !request.targetId().equals(sender.getUUID())) {
            return;
        }
        if (success) {
            return;
        }
        REQUESTS.remove(sessionToken);
        SESSIONS.remove(sessionToken);
        notifyRequester(request, ClientMessageType.FAILURE, "未能获取 %s 的截图：%s", request.targetName(), normalizeMessage(message));
    }

    private static void timeoutRequest(String sessionToken) {
        PendingRequest request = REQUESTS.remove(sessionToken);
        if (request == null) {
            return;
        }
        SESSIONS.remove(sessionToken);
        notifyRequester(request, ClientMessageType.FAILURE, "截图请求超时，未收到 %s 的截图回传", request.targetName());
    }

    private static void saveScreenshot(PendingRequest request, byte[] imageData) {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("gd656conquest").resolve("screenshot");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String dateStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String fileName = dateStr + "_" + sanitizeFileToken(request.targetName()) + ".jpg";
            Path file = dir.resolve(fileName);
            Files.write(file, imageData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            ServerLogger.info("Saved screenshot for player %s to %s", request.targetName(), file.toAbsolutePath());
            notifyRequester(request, ClientMessageType.SUCCESS, "已保存 %s 的截图：%s", request.targetName(), file.toAbsolutePath());
        } catch (Exception e) {
            notifyRequester(request, ClientMessageType.FAILURE, "保存 %s 的截图失败：%s", request.targetName(), normalizeMessage(e.getMessage()));
            ServerLogger.failure("Failed to save screenshot for player %s: %s", request.targetName(), normalizeMessage(e.getMessage()));
        }
    }

    private static void notifyRequester(PendingRequest request, ClientMessageType type, String message, Object... args) {
        if (request == null) {
            return;
        }
        ServerPlayer requester = request.requesterId() == null ? null : request.server().getPlayerList().getPlayer(request.requesterId());
        if (requester != null) {
            ServerLogger.sendToClient(requester, type, message, args);
            return;
        }
        String formatted = args == null || args.length == 0 ? message : String.format(Locale.ROOT, message, args);
        if (type == ClientMessageType.FAILURE) {
            ServerLogger.failure("[%s] %s", request.requesterName(), formatted);
        } else {
            ServerLogger.info("[%s] %s", request.requesterName(), formatted);
        }
    }

    private static String sanitizeFileToken(String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        }
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String normalizeMessage(String message) {
        return message == null || message.isBlank() ? "未知错误" : message;
    }

    private static class ScreenshotSession {
        private final int totalChunks;
        private final byte[][] chunks;
        private int receivedChunks = 0;

        public ScreenshotSession(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }

        public synchronized void addChunk(int index, byte[] data) {
            if (index >= 0 && index < totalChunks && chunks[index] == null) {
                chunks[index] = data;
                receivedChunks++;
            }
        }

        public synchronized boolean isComplete() {
            return receivedChunks == totalChunks;
        }

        public synchronized byte[] assemble() {
            int totalLength = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    totalLength += chunk.length;
                }
            }
            byte[] fullData = new byte[totalLength];
            int offset = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, fullData, offset, chunk.length);
                    offset += chunk.length;
                }
            }
            return fullData;
        }
    }

    private record PendingRequest(
        String sessionToken,
        MinecraftServer server,
        UUID requesterId,
        String requesterName,
        UUID targetId,
        String targetName
    ) {
    }
}
