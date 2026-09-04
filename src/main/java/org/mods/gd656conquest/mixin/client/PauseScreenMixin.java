package org.mods.gd656conquest.mixin.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import org.mods.gd656conquest.client.room.ClientRoomLockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gd656conquest$injectRoomButtons(CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        Button disconnectButton = findDisconnectButton();
        int x = disconnectButton != null ? disconnectButton.getX() : this.width / 2 + 4;
        int y = disconnectButton != null ? disconnectButton.getY() : this.height / 4 + 104;
        int width = disconnectButton != null ? disconnectButton.getWidth() : 98;
        int height = disconnectButton != null ? disconnectButton.getHeight() : 20;
        if (disconnectButton != null) {
            disconnectButton.setMessage(disconnectButton.getMessage().copy().withStyle(ChatFormatting.DARK_RED));
        }
        if (!isJoinedRoom()) {
            return;
        }

        Button leaveRoomButton = Button.builder(
                Component.translatable("gd656conquest.client.gui.pause.leave_room").withStyle(ChatFormatting.RED),
                button -> executeRoomCommand("gdc room leave")
            )
            .bounds(x, y + height + 4, width, height)
            .build();
        leaveRoomButton.active = true;
        this.addRenderableWidget(leaveRoomButton);

        Button redeployButton = Button.builder(
                Component.translatable("gd656conquest.client.gui.pause.redeploy"),
                button -> executeRoomCommand("gdc redeploy")
            )
            .bounds(x, y + (height + 4) * 2, width, height)
            .build();
        redeployButton.active = canUseRedeploy(minecraft);
        this.addRenderableWidget(redeployButton);
    }

    private boolean isJoinedRoom() {
        String roomId = normalize(ClientHudDebugState.getRoomId());
        String roomState = normalize(ClientHudDebugState.getRoomState());
        return !roomId.isEmpty() && !"-".equals(roomId) && !roomState.contains("未加入");
    }

    private boolean canUseRedeploy(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        String roomState = normalize(ClientHudDebugState.getRoomState());
        if (!roomState.contains("进行中")) {
            return false;
        }
        if (minecraft.player.isSpectator()) {
            return false;
        }
        if (ClientDownedState.isActive()) {
            return true;
        }
        return !ClientDeathFlowState.hasActiveCameraOverride() && !ClientRoomLockState.shouldReleaseMouseForUi();
    }

    private void executeRoomCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        // #region debug-point A:pause-redeploy-click
        reportRedeployDebug(
            "A",
            "[DEBUG] pause executeRoomCommand before sendUnsignedCommand",
            "{\"command\":\"" + escapeJson(command) + "\",\"screen\":\"" + escapeJson(this.getClass().getSimpleName()) + "\",\"player\":\"" + escapeJson(minecraft.player.getScoreboardName()) + "\",\"hasCameraOverride\":" + ClientDeathFlowState.hasActiveCameraOverride() + ",\"downedActive\":" + ClientDownedState.isActive() + ",\"releaseMouseForUi\":" + ClientRoomLockState.shouldReleaseMouseForUi() + "}"
        );
        // #endregion
        minecraft.player.connection.sendUnsignedCommand(command);
        // #region debug-point A:pause-redeploy-click
        reportRedeployDebug(
            "A",
            "[DEBUG] pause executeRoomCommand after sendUnsignedCommand",
            "{\"command\":\"" + escapeJson(command) + "\",\"screen\":\"" + escapeJson(this.getClass().getSimpleName()) + "\"}"
        );
        // #endregion
        minecraft.setScreen(null);
        // #region debug-point A:pause-redeploy-click
        reportRedeployDebug(
            "A",
            "[DEBUG] pause executeRoomCommand after setScreen null",
            "{\"command\":\"" + escapeJson(command) + "\",\"screenCleared\":" + (minecraft.screen == null) + "}"
        );
        // #endregion
    }

    private static void reportRedeployDebug(String hypothesisId, String msg, String dataJson) {
        try {
            String body = "{\"sessionId\":\"redeploy-click-freeze\",\"runId\":\"pre-fix\",\"hypothesisId\":\""
                + escapeJson(hypothesisId)
                + "\",\"location\":\"PauseScreenMixin.executeRoomCommand\",\"msg\":\""
                + escapeJson(msg)
                + "\",\"data\":"
                + (dataJson == null || dataJson.isBlank() ? "{}" : dataJson)
                + ",\"ts\":"
                + System.currentTimeMillis()
                + "}";
            java.net.http.HttpClient.newHttpClient().sendAsync(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:7777/event"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()
            ).exceptionally(ignored -> null);
        } catch (Exception ignored) {
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Button findDisconnectButton() {
        String disconnectText = I18n.get("menu.disconnect");
        String returnToMenuText = I18n.get("menu.returnToMenu");
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button button)) {
                continue;
            }
            String text = normalize(button.getMessage().getString());
            if (text.equals(normalize(disconnectText)) || text.equals(normalize(returnToMenuText))) {
                return button;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
