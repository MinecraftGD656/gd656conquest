package org.mods.gd656conquest.client.downed;

import java.lang.reflect.Field;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.client.hud.ClientHudDebugState;
import org.mods.gd656conquest.client.room.ClientDeathFlowState;
import org.mods.gd656conquest.client.room.ClientDownedState;
import org.mods.gd656conquest.client.room.ClientRoomViewScope;
import org.mods.gd656killicon.client.downed.DownedClient;
import org.mods.gd656killicon.client.downed.DownedScreenRequest;

/** The original server state owns the death screen handoff, before the HUD's next tick. */
@Mod.EventBusSubscriber(modid="gd656conquest",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ConquestDeathScreen {
    private static final Pattern OLD_DOWNED_COUNTER=Pattern.compile("^你将在\\s*\\d+\\s*秒后彻底死亡$");
    private static final Pattern OLD_KILLER_INFO=Pattern.compile("^(?:击倒者|击杀者)[:：]\\s*[^|]+\\|\\s*武器[:：]\\s*[^|]+(?:\\|\\s*你将在\\s*\\d+\\s*秒后彻底死亡)?$");
    static {DownedScreenRequest.bind(ConquestDeathScreen::ownsNativeFlow);}
    private ConquestDeathScreen() {}
    public static boolean ownsNativeFlow() {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getConnection()==null)return false;
        String room=ClientHudDebugState.getRoomId(),state=ClientHudDebugState.getRoomState();
        if(room==null||room.isBlank()||room.equals("-")||state!=null&&state.contains("未加入")||!ClientRoomViewScope.current())return false;
        var downed=ClientDownedState.visualSnapshot();
        if(downed.active()&&!downed.debug()&&downed.level()==mc.level)return true;
        return ClientDeathFlowState.hasActiveCameraOverride()&&ClientDeathFlowState.visualSnapshot().level()==mc.level;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void opening(ScreenEvent.Opening event) {
        if(DownedScreenRequest.isClose(event.getNewScreen())){event.setNewScreen(null);return;}
        if(!(event.getNewScreen() instanceof DeathScreen)||!ownsNativeFlow())return;
        if(event.getCurrentScreen() instanceof DeathScreen)event.setNewScreen(null);
        else event.setCanceled(true); // Keep an existing chat/menu, or the unobstructed world.
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof DeathScreen&&ownsNativeFlow()) {
            // An explicit screen bypasses Minecraft's null/dead fallback, which can request a respawn.
            // The Opening event above hands this already-open screen to null through vanilla cleanup.
            mc.setScreen(mc.screen);
        }
    }
    @SubscribeEvent public static void legacyCounter(RenderGuiOverlayEvent.Pre event) {
        if(org.mods.gd656killicon.client.gui.theme.BattlefieldAccessibility.classic())return;
        if(!event.getOverlay().id().toString().equals("minecraft:record_overlay")||!ownsNativeFlow()||!DownedClient.active())return;
        try {
            Component message=(Component)GuiFields.MESSAGE.get(Minecraft.getInstance().gui);
            Component filtered=withoutLegacyNotice(message);
            if(filtered!=message){
                GuiFields.MESSAGE.set(Minecraft.getInstance().gui,filtered);
                if(filtered.getString().isBlank())event.setCanceled(true);
            }
        } catch(IllegalAccessException error) {throw new IllegalStateException("Cannot read the native downed actionbar",error);}
    }
    private static boolean legacyNotice(Component message){
        String text=ChatFormatting.stripFormatting(message.getString());
        return text!=null&&(OLD_DOWNED_COUNTER.matcher(text.strip()).matches()||OLD_KILLER_INFO.matcher(text.strip()).matches());
    }
    private static Component withoutLegacyNotice(Component message){
        if(message==null)return null;
        if(legacyNotice(message))return Component.empty();
        // ServerActionBarService composes source entries as sibling components separated by " | ".
        if(!message.getContents().equals(Component.empty().getContents()))return message;
        var kept=new java.util.ArrayList<Component>();boolean changed=false;
        for(Component part:message.getSiblings()){
            if(part.getString().equals(" | "))continue;
            if(legacyNotice(part))changed=true;else kept.add(part);
        }
        if(!changed)return message;
        var result=Component.empty().setStyle(message.getStyle());
        for(Component part:kept){
            if(!result.getSiblings().isEmpty())result.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            result.append(part.copy());
        }
        return result;
    }
    private static final class GuiFields {
        private static final Field MESSAGE=message();
        private static Field message() {
            Field field;
            try {field=Gui.class.getDeclaredField("overlayMessageString");}
            catch(NoSuchFieldException named) {
                try {field=Gui.class.getDeclaredField("f_92990_");}
                catch(NoSuchFieldException runtime){throw new ExceptionInInitializerError(runtime);}
            }
            field.setAccessible(true);return field;
        }
    }
}
