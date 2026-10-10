package org.mods.gd656conquest.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.mixin.client.KillerCardChatAccessor;

@Mod.EventBusSubscriber(modid = "gd656conquest", value = Dist.CLIENT)
public final class ClientRoomChatState {
    private static int previousWidth = -1;
    private static boolean previousRoom;
    private ClientRoomChatState() {}
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        String id = ClientHudDebugState.getRoomId(), state = ClientHudDebugState.getRoomState();
        return mc.level != null && mc.player != null && id != null && !id.isBlank() && !"-".equals(id)
            && (state == null || !state.contains("未加入"));
    }
    public static RoomChatLayout layout() {
        Minecraft mc = Minecraft.getInstance(); ChatComponent chat = mc.gui.getChat();
        double scale = chat.getScale();
        int height = chat instanceof KillerCardChatAccessor a ? a.gd656conquest$chatLineHeight() : 9;
        boolean focused = mc.screen instanceof ChatScreen;
        int lines = 0;
        if (chat instanceof KillerCardChatAccessor a) {
            int now = mc.gui.getGuiTicks();
            for (var line : a.gd656conquest$chatLines()) {
                if (!focused && now - line.addedTime() >= 200) break;
                lines++;
                if (lines >= (focused ? 14 : 8)) break;
            }
        }
        return RoomChatLayout.calculate(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), scale, height, focused, lines);
    }
    public static int suggestionRows(int original) {
        if (!active()) return original;
        RoomChatLayout box = layout();
        return Math.max(1, Math.min(original, (box.inputY() - box.top() - 4) / 12));
    }
    /** Rewrap from original Components on room entry/exit, GUI resize and chat scale changes. */
    @SubscribeEvent public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        ChatComponent chat = mc.gui.getChat();
        boolean room = active(); int width = (int)Math.ceil(chat.getWidth() / Math.max(.1, chat.getScale()));
        if (room != previousRoom || width != previousWidth) { previousRoom = room; previousWidth = width; chat.rescaleChat(); }
    }
}
