package org.mods.gd656conquest.client.pack;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.mods.gd656conquest.common.pack.PackAuditConstants;
import org.mods.gd656conquest.common.pack.PackAuditEntry;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ServerboundPackAuditReportPacket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;

public final class ClientPackAuditService {
    private static final long PERIODIC_INTERVAL_TICKS = 20L * 60L;
    private static final long SNAPSHOT_INTERVAL_TICKS = 20L;
    private static final String OCULUS_MOD_ID = "oculus";

    private static final Deque<ReportRequest> PENDING_REQUESTS = new ArrayDeque<>();

    private static boolean sessionActive;
    private static boolean reportInFlight;
    private static long lastPeriodicReportTick = Long.MIN_VALUE;
    private static long lastSnapshotCheckTick = Long.MIN_VALUE;
    private static QuickSignatureSnapshot lastQuickSnapshot = QuickSignatureSnapshot.empty();

    private ClientPackAuditService() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        long gameTick = minecraft.level.getGameTime();
        if (!sessionActive) {
            sessionActive = true;
            lastQuickSnapshot = buildQuickSignatureSnapshot();
            lastPeriodicReportTick = gameTick;
            lastSnapshotCheckTick = gameTick;
            enqueueAutoReport(PackAuditConstants.REASON_LOGIN);
        } else {
            if (gameTick - lastPeriodicReportTick >= PERIODIC_INTERVAL_TICKS) {
                lastPeriodicReportTick = gameTick;
                enqueueAutoReport(PackAuditConstants.REASON_TIMER);
            }
            if (gameTick - lastSnapshotCheckTick >= SNAPSHOT_INTERVAL_TICKS) {
                lastSnapshotCheckTick = gameTick;
                QuickSignatureSnapshot currentSnapshot = buildQuickSignatureSnapshot();
                if (!Objects.equals(currentSnapshot.resourceInstallSignature(), lastQuickSnapshot.resourceInstallSignature())
                    || !Objects.equals(currentSnapshot.selectedResourceSignature(), lastQuickSnapshot.selectedResourceSignature())) {
                    enqueueAutoReport(PackAuditConstants.REASON_RESOURCE_RELOAD);
                }
                if (!Objects.equals(currentSnapshot.shaderInstallSignature(), lastQuickSnapshot.shaderInstallSignature())
                    || !Objects.equals(currentSnapshot.shaderRuntimeSignature(), lastQuickSnapshot.shaderRuntimeSignature())) {
                    enqueueAutoReport(PackAuditConstants.REASON_SHADER_RELOAD);
                }
                lastQuickSnapshot = currentSnapshot;
            }
        }
        startNextReportIfIdle();
    }

    public static void clear() {
        synchronized (PENDING_REQUESTS) {
            PENDING_REQUESTS.clear();
            reportInFlight = false;
        }
        sessionActive = false;
        lastPeriodicReportTick = Long.MIN_VALUE;
        lastSnapshotCheckTick = Long.MIN_VALUE;
        lastQuickSnapshot = QuickSignatureSnapshot.empty();
    }

    public static void requestUpload(String requestId, String reason) {
        String normalizedReason = reason == null || reason.isBlank() ? PackAuditConstants.REASON_MANUAL_UPDATE : reason;
        enqueueRequest(new ReportRequest(requestId, normalizedReason));
        startNextReportIfIdle();
    }

    private static void enqueueAutoReport(String reason) {
        enqueueRequest(new ReportRequest("", reason));
    }

    private static void enqueueRequest(ReportRequest request) {
        synchronized (PENDING_REQUESTS) {
            if (request == null) {
                return;
            }
            if (request.requestId().isBlank()) {
                for (ReportRequest pending : PENDING_REQUESTS) {
                    if (pending.requestId().isBlank() && pending.reason().equals(request.reason())) {
                        return;
                    }
                }
            }
            PENDING_REQUESTS.addLast(request);
        }
    }

    private static void startNextReportIfIdle() {
        ReportRequest request;
        synchronized (PENDING_REQUESTS) {
            if (reportInFlight || PENDING_REQUESTS.isEmpty()) {
                return;
            }
            reportInFlight = true;
            request = PENDING_REQUESTS.removeFirst();
        }
        CompletableFuture.runAsync(() -> generateAndSendReport(request))
            .whenComplete((ignored, throwable) -> {
                synchronized (PENDING_REQUESTS) {
                    reportInFlight = false;
                }
            });
    }

    private static void generateAndSendReport(ReportRequest request) {
        try {
            List<PackAuditEntry> entries = new ArrayList<>();
            entries.addAll(scanInstalledPackDirectory(resolveGameDirectory().resolve("resourcepacks"), PackAuditConstants.TYPE_RESOURCE_PACK));
            entries.addAll(scanInstalledPackDirectory(resolveGameDirectory().resolve("shaderpacks"), PackAuditConstants.TYPE_SHADER_PACK));
            NetworkManager.getChannel().sendToServer(new ServerboundPackAuditReportPacket(
                request.requestId(),
                request.reason(),
                true,
                "",
                entries
            ));
        } catch (Exception exception) {
            NetworkManager.getChannel().sendToServer(new ServerboundPackAuditReportPacket(
                request.requestId(),
                request.reason(),
                false,
                buildFailureMessage(exception),
                List.of()
            ));
        }
    }

    private static Path resolveGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    private static List<PackAuditEntry> scanInstalledPackDirectory(Path directory, String packType) throws Exception {
        if (directory == null || Files.notExists(directory)) {
            return List.of();
        }
        List<Path> topLevelEntries;
        try (Stream<Path> stream = Files.list(directory)) {
            topLevelEntries = stream
                .filter(ClientPackAuditService::shouldIncludePackEntry)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
        }
        List<PackAuditEntry> entries = new ArrayList<>();
        for (Path entry : topLevelEntries) {
            boolean directoryEntry = Files.isDirectory(entry);
            long size = directoryEntry ? computeDirectorySize(entry) : Files.size(entry);
            entries.add(new PackAuditEntry(
                packType,
                entry.getFileName().toString(),
                directoryEntry ? sha256Directory(entry) : sha256File(entry),
                size,
                directoryEntry ? PackAuditConstants.SOURCE_KIND_DIRECTORY : PackAuditConstants.SOURCE_KIND_FILE
            ));
        }
        return entries;
    }

    private static boolean shouldIncludePackEntry(Path entry) {
        if (entry == null || Files.notExists(entry)) {
            return false;
        }
        String fileName = entry.getFileName() == null ? "" : entry.getFileName().toString();
        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.startsWith(".")) {
            return false;
        }
        if ("desktop.ini".equals(normalized) || "thumbs.db".equals(normalized)) {
            return false;
        }
        return Files.isDirectory(entry)
            || normalized.endsWith(".zip")
            || normalized.endsWith(".jar");
    }

    private static long computeDirectorySize(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException exception) {
                        return 0L;
                    }
                })
                .sum();
        }
    }

    private static String sha256File(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256Directory(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<Path> files;
        try (Stream<Path> stream = Files.walk(path)) {
            files = stream
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(value -> path.relativize(value).toString().toLowerCase(Locale.ROOT)))
                .toList();
        }
        for (Path file : files) {
            String relativePath = path.relativize(file).toString().replace('\\', '/');
            digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static QuickSignatureSnapshot buildQuickSignatureSnapshot() {
        Path gameDir = resolveGameDirectory();
        return new QuickSignatureSnapshot(
            buildDirectoryMetadataSignature(gameDir.resolve("resourcepacks")),
            buildDirectoryMetadataSignature(gameDir.resolve("shaderpacks")),
            resolveSelectedResourcePackSignature(),
            resolveShaderRuntimeSignature()
        );
    }

    private static String buildDirectoryMetadataSignature(Path directory) {
        if (directory == null || Files.notExists(directory)) {
            return "";
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(ClientPackAuditService::shouldIncludePackEntry)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .map(ClientPackAuditService::describeTopLevelPath)
                .collect(Collectors.joining("|"));
        } catch (Exception exception) {
            return "error:" + exception.getClass().getSimpleName();
        }
    }

    private static String describeTopLevelPath(Path path) {
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            long size = Files.isDirectory(path) ? computeDirectorySize(path) : Files.size(path);
            return path.getFileName() + ":" + (Files.isDirectory(path) ? "D" : "F") + ":" + size + ":" + modified;
        } catch (Exception exception) {
            return path.getFileName() + ":error:" + exception.getClass().getSimpleName();
        }
    }

    private static String resolveSelectedResourcePackSignature() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Object repository = minecraft.getClass().getMethod("getResourcePackRepository").invoke(minecraft);
            Object selectedPacks = repository.getClass().getMethod("getSelectedPacks").invoke(repository);
            if (!(selectedPacks instanceof Collection<?> collection)) {
                return "";
            }
            List<String> ids = new ArrayList<>();
            for (Object pack : collection) {
                if (pack == null) {
                    continue;
                }
                Object id = pack.getClass().getMethod("getId").invoke(pack);
                ids.add(String.valueOf(id));
            }
            ids.sort(String::compareToIgnoreCase);
            return String.join("|", ids);
        } catch (Exception exception) {
            return "";
        }
    }

    private static String resolveShaderRuntimeSignature() {
        if (!ModList.get().isLoaded(OCULUS_MOD_ID)) {
            return "";
        }
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = irisApiClass.getMethod("getInstance").invoke(null);
            boolean inUse = (boolean) irisApiClass.getMethod("isShaderPackInUse").invoke(api);

            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Object packName = irisClass.getMethod("getCurrentPackName").invoke(null);
            return "inUse=" + inUse + ";pack=" + (packName == null ? "" : packName);
        } catch (Exception exception) {
            return "present=true;error=" + exception.getClass().getSimpleName();
        }
    }

    private static String buildFailureMessage(Exception exception) {
        String message = exception == null ? "未知错误" : exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception == null ? "未知错误" : exception.getClass().getSimpleName();
        }
        return message + " @ " + Instant.now();
    }

    private record ReportRequest(String requestId, String reason) {
        private ReportRequest {
            requestId = requestId == null ? "" : requestId;
            reason = reason == null || reason.isBlank() ? PackAuditConstants.REASON_TIMER : reason;
        }
    }

    private record QuickSignatureSnapshot(
        String resourceInstallSignature,
        String shaderInstallSignature,
        String selectedResourceSignature,
        String shaderRuntimeSignature
    ) {
        private static QuickSignatureSnapshot empty() {
            return new QuickSignatureSnapshot("", "", "", "");
        }
    }
}
