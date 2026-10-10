package org.mods.gd656conquest.mixin.client;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only native chat lines and geometry for the shared room layout. */
@Mixin(value = ChatComponent.class)
public interface KillerCardChatAccessor {
    @Accessor("trimmedMessages") List<GuiMessage.Line> gd656conquest$chatLines();
    @Invoker("getLineHeight") int gd656conquest$chatLineHeight();
    @Invoker("isChatHidden") boolean gd656conquest$chatHidden();
}
