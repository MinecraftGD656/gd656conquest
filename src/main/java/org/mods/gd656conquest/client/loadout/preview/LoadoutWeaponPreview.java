package org.mods.gd656conquest.client.loadout.preview;

import java.util.*;
import java.lang.reflect.Method;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.loadout.ClientLoadoutUiState.AttachmentTypeState;

/** Combines the base weapon with server-selected attachment overrides for display. */
public final class LoadoutWeaponPreview {
    private LoadoutWeaponPreview() {}
    public record Selection(String type,String itemSnbt,boolean remove) {}
    public static List<Selection> plan(Map<String,AttachmentTypeState> states) {
        List<Selection> result=new ArrayList<>();
        for(String type:List.of("scope","muzzle","stock","grip","laser","extended_mag")) {
            var state=states.get(type);
            if(state==null || !state.available() || !state.hasStoredSelection())continue;
            String id=state.selectedAttachmentId();
            if(id==null || id.isBlank())result.add(new Selection(type,"",true));
            else {
                var choice=state.choices().get(id);
                if(choice!=null && choice.itemSnbt()!=null && !choice.itemSnbt().isBlank())
                    result.add(new Selection(type,choice.itemSnbt(),false));
            }
        }
        return List.copyOf(result);
    }

    public static ItemStack compose(ItemStack base,Map<String,AttachmentTypeState> states) {
        return apply(base,plan(states));
    }

    /** Apply only to a preview copy, preserving the catalog stack and player inventory. */
    public static ItemStack apply(ItemStack base,List<Selection> selections) {
        ItemStack preview=base.copy();
        if(preview.isEmpty() || selections.isEmpty() || Holder.ACCESS==null)return preview;
        Access api=Holder.ACCESS;
        try {
            Object gun=api.gun().invoke(null,preview);
            if(gun==null)return preview;
            for(Selection selection:selections) {
                if(selection.remove())api.unload().invoke(gun,preview,api.type(selection.type()));
                else {
                    ItemStack attachment=ItemStack.of(TagParser.parseTag(selection.itemSnbt()));
                    if(!attachment.isEmpty())api.install().invoke(gun,preview,attachment);
                }
            }
        } catch(ReflectiveOperationException | CommandSyntaxException | IllegalArgumentException e) {
            if(!Holder.reported) {
                Holder.reported=true;
                LogUtils.getLogger().warn("Could not compose the server-selected preview attachments",e);
            }
        }
        return preview;
    }
    private static final class Holder {
        static final Access ACCESS=resolve();
        static boolean reported;
    }
    private static Access resolve() {
        try {
            Class<?> gun=Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> type=Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            return new Access(gun.getMethod("getIGunOrNull",ItemStack.class),gun.getMethod("installAttachment",ItemStack.class,ItemStack.class),
                gun.getMethod("unloadAttachment",ItemStack.class,type),type);
        } catch(ClassNotFoundException e){return null;}
        catch(ReflectiveOperationException | LinkageError e) {
            LogUtils.getLogger().warn("TACZ preview attachment API unavailable",e);return null;
        }
    }
    private record Access(Method gun,Method install,Method unload,Class<?> attachmentType) {
        @SuppressWarnings({"rawtypes","unchecked"})
        Object type(String name){return Enum.valueOf((Class)attachmentType,name.toUpperCase(Locale.ROOT));}
    }
}
