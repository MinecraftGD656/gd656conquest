package org.mods.gd656conquest.mixin.client.forge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModMismatchDisconnectedScreen.class)
public abstract class ModMismatchDisconnectedScreenMixin extends Screen {
    private static final String MISMATCH_INFO_PANEL_CLASS = "net.minecraftforge.client.gui.ModMismatchDisconnectedScreen$MismatchInfoPanel";

    protected ModMismatchDisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void gd656conquest$injectGuidanceIntoPanel(CallbackInfo callbackInfo) {
        for (GuiEventListener child : this.children()) {
            if (!MISMATCH_INFO_PANEL_CLASS.equals(child.getClass().getName())) {
                continue;
            }
            if (!(child instanceof ModMismatchInfoPanelAccessor accessor)) {
                continue;
            }
            List<Pair<FormattedCharSequence, Pair<FormattedCharSequence, FormattedCharSequence>>> mergedLines = new ArrayList<>();
            appendNoticeLines(mergedLines, accessor.gd656conquest$getTableWidth() - 4);
            mergedLines.addAll(accessor.gd656conquest$getLineTable());
            accessor.gd656conquest$setLineTable(mergedLines);
            accessor.gd656conquest$setContentSize(mergedLines.size());
            break;
        }
    }

    private void appendNoticeLines(List<Pair<FormattedCharSequence, Pair<FormattedCharSequence, FormattedCharSequence>>> rows, int wrapWidth) {
        if (this.font == null) {
            return;
        }
        for (MutableComponent line : buildNoticeLines()) {
            for (FormattedCharSequence sequence : this.font.split(line, Math.max(40, wrapWidth))) {
                rows.add(Pair.of(sequence, null));
            }
        }
        rows.add(Pair.of(FormattedCharSequence.EMPTY, null));
    }

    private static List<MutableComponent> buildNoticeLines() {
        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.empty()
            .append(Component.literal("您的某个模组与服务端存在差异，请前往").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("群文件").withStyle(ChatFormatting.GOLD)));
        lines.add(Component.empty()
            .append(Component.literal("“").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("大战场服务器所需整合包与模组文件").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("”").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("文件夹下载").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("新版本的模组").withStyle(ChatFormatting.GOLD)));
        lines.add(Component.empty()
            .append(Component.literal("进行").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("模组替换").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("后，").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("重启游戏").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("即可正常进行游玩。").withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal("服务器测试版给您带来不便，敬请谅解！").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
