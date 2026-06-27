package org.mods.gd656conquest.mixin.client.forge;

import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraftforge.client.gui.ModMismatchDisconnectedScreen$MismatchInfoPanel")
public interface ModMismatchInfoPanelAccessor {
    @Accessor(value = "lineTable", remap = false)
    List<Pair<FormattedCharSequence, Pair<FormattedCharSequence, FormattedCharSequence>>> gd656conquest$getLineTable();

    @Mutable
    @Accessor(value = "lineTable", remap = false)
    void gd656conquest$setLineTable(List<Pair<FormattedCharSequence, Pair<FormattedCharSequence, FormattedCharSequence>>> lineTable);

    @Accessor(value = "tableWidth", remap = false)
    int gd656conquest$getTableWidth();

    @Mutable
    @Accessor(value = "contentSize", remap = false)
    void gd656conquest$setContentSize(int contentSize);
}
