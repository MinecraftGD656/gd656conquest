package org.mods.gd656conquest.client.event.handler;

import org.mods.gd656conquest.client.log.ClientLogger;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.ServerboundScreenshotChunkPacket;
import org.mods.gd656conquest.network.packet.ServerboundScreenshotStatusPacket;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ScreenshotClientHandler {
    private static final int CHUNK_SIZE = 30000;
    private static final String WINDOWS_OS_PREFIX = "windows";

    public static void takeAndSendScreenshot(String sessionToken) {
        CompletableFuture.runAsync(() -> {
            try {
                if (sessionToken == null || sessionToken.isBlank()) {
                    return;
                }
                byte[] data = captureScreenshotBytes();
                if (data.length == 0) {
                    sendFailure(sessionToken, "截图数据为空");
                    return;
                }
                sendChunks(sessionToken, data);
            } catch (Exception e) {
                sendFailure(sessionToken, buildFailureMessage(e));
            }
        });
    }

    private static byte[] captureScreenshotBytes() throws Exception {
        try {
            return captureWithAwt();
        } catch (Exception awtFailure) {
            if (!isWindows()) {
                throw awtFailure;
            }
            ClientLogger.failure("AWT full-screen screenshot failed, trying PowerShell fallback: %s", buildFailureMessage(awtFailure));
            try {
                return captureWithPowerShell();
            } catch (Exception fallbackFailure) {
                throw new IllegalStateException(
                    "AWT=" + buildFailureMessage(awtFailure) + "; PowerShell=" + buildFailureMessage(fallbackFailure),
                    fallbackFailure
                );
            }
        }
    }

    private static byte[] captureWithAwt() throws Exception {
        forceEnableDesktopAwt();
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage capture = robot.createScreenCapture(screenRect);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (!ImageIO.write(capture, "jpg", baos)) {
                throw new IllegalStateException("客户端无法将截图编码为 JPG");
            }
            return baos.toByteArray();
        }
    }

    private static byte[] captureWithPowerShell() throws Exception {
        Path tempDir = Files.createTempDirectory("gd656conquest-screenshot-");
        Path scriptFile = tempDir.resolve("capture.ps1");
        Path outputFile = tempDir.resolve("capture.jpg");
        try {
            Files.writeString(scriptFile, buildPowerShellScript(outputFile), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-File",
                scriptFile.toString()
            ).redirectErrorStream(true).start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                    processOutput.isBlank() ? "PowerShell 截图进程退出码为 " + exitCode : processOutput
                );
            }
            if (!Files.exists(outputFile)) {
                throw new IllegalStateException("PowerShell 未生成截图文件");
            }
            byte[] data = Files.readAllBytes(outputFile);
            if (data.length == 0) {
                throw new IllegalStateException("PowerShell 生成的截图文件为空");
            }
            return data;
        } finally {
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(scriptFile);
            Files.deleteIfExists(tempDir);
        }
    }

    private static String buildPowerShellScript(Path outputFile) {
        String output = escapePowerShellSingleQuoted(outputFile.toString());
        return "$ErrorActionPreference = 'Stop'\n"
            + "Add-Type -AssemblyName System.Windows.Forms\n"
            + "Add-Type -AssemblyName System.Drawing\n"
            + "$bounds = [System.Windows.Forms.SystemInformation]::VirtualScreen\n"
            + "$bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height\n"
            + "$graphics = [System.Drawing.Graphics]::FromImage($bitmap)\n"
            + "try {\n"
            + "    $graphics.CopyFromScreen($bounds.Left, $bounds.Top, 0, 0, $bitmap.Size)\n"
            + "    $bitmap.Save('" + output + "', [System.Drawing.Imaging.ImageFormat]::Jpeg)\n"
            + "} finally {\n"
            + "    if ($graphics -ne $null) { $graphics.Dispose() }\n"
            + "    if ($bitmap -ne $null) { $bitmap.Dispose() }\n"
            + "}\n";
    }

    private static String escapePowerShellSingleQuoted(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static void sendChunks(String sessionToken, byte[] data) {
        int totalChunks = (int) Math.ceil((double) data.length / CHUNK_SIZE);
        for (int i = 0; i < totalChunks; i++) {
            int offset = i * CHUNK_SIZE;
            int length = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(data, offset, chunk, 0, length);
            NetworkManager.getChannel().sendToServer(
                new ServerboundScreenshotChunkPacket(sessionToken, i, totalChunks, chunk)
            );
        }
    }

    private static void forceEnableDesktopAwt() {
        String headless = System.getProperty("java.awt.headless");
        if (headless != null && Boolean.parseBoolean(headless)) {
            System.setProperty("java.awt.headless", "false");
        }
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().startsWith(WINDOWS_OS_PREFIX);
    }

    private static String buildFailureMessage(Throwable exception) {
        if (exception == null) {
            return "未知错误";
        }
        String className = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "无详细信息";
        }
        String headless = System.getProperty("java.awt.headless");
        return className + ": " + message + " (java.awt.headless=" + (headless == null ? "null" : headless) + ")";
    }

    private static void sendFailure(String sessionToken, String message) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return;
        }
        NetworkManager.getChannel().sendToServer(new ServerboundScreenshotStatusPacket(
            sessionToken,
            false,
            message == null || message.isBlank() ? "未知错误" : message
        ));
    }
}
