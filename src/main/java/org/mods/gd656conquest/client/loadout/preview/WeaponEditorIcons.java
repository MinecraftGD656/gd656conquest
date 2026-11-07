package org.mods.gd656conquest.client.loadout.preview;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.*;

/** Recolors the actual TACZ slot texture's alpha for the game's monochrome menu icons. */
final class WeaponEditorIcons implements AutoCloseable {
    record Icon(ResourceLocation texture,int width,int height) {}
    private record Key(ResourceLocation source,int color,boolean silhouette) {}
    private final Map<Key,Icon> cache=new HashMap<>();
    private final Set<Key> missing=new HashSet<>();
    private boolean reported;
    Icon get(ItemStack stack,int color,boolean silhouette) {
        if(Holder.API==null)return null;
        try {
            Api a=Holder.API;
            Object display=((Optional<?>)a.gunDisplay.invoke(null,stack)).orElse(null);
            ResourceLocation source;
            if(display!=null)source=(ResourceLocation)a.gunSlot.invoke(display);
            else {
                Object attachment=a.attachment.invoke(null,stack);if(attachment==null)return null;
                Object id=a.attachmentId.invoke(attachment,stack);
                Object index=((Optional<?>)a.attachmentIndex.invoke(null,id)).orElse(null);
                if(index==null)return null;
                source=(ResourceLocation)a.attachmentSlot.invoke(index);
            }
            if(source==null)return null;
            Key key=new Key(source,color,silhouette);if(missing.contains(key))return null;if(cache.containsKey(key))return cache.get(key);
            try(var stream=Minecraft.getInstance().getResourceManager().open(source);NativeImage original=NativeImage.read(stream)) {
                int minX=original.getWidth(),minY=original.getHeight(),maxX=-1,maxY=-1;
                for(int y=0;y<original.getHeight();y++)for(int x=0;x<original.getWidth();x++)if((original.getPixelRGBA(x,y)>>>24)>0){
                    minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
                }
                if(maxX<minX){missing.add(key);return null;}
                NativeImage image=new NativeImage(maxX-minX+1,maxY-minY+1,false);
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                    int pixel=original.getPixelRGBA(minX+x,minY+y),alpha=(pixel>>>24)*(color>>>24)/255;
                    double shade=silhouette?1:0.35+0.65*((pixel&255)*0.2126+(pixel>>8&255)*0.7152+(pixel>>16&255)*0.0722)/255;
                    int rgb=(int)((color>>16&255)*shade)|((int)((color>>8&255)*shade)<<8)|((int)((color&255)*shade)<<16);
                    image.setPixelRGBA(x,y,(alpha<<24)|rgb);
                }
                DynamicTexture texture=new DynamicTexture(image);
                Icon result=new Icon(Minecraft.getInstance().getTextureManager().register("bf656-weapon-editor",texture),image.getWidth(),image.getHeight());
                cache.put(key,result);return result;
            } catch(IOException error) {missing.add(key);report(error);return null;}
        } catch(ReflectiveOperationException | LinkageError error){report(error);return null;}
    }
    public void close(){for(Icon icon:cache.values())Minecraft.getInstance().getTextureManager().release(icon.texture);cache.clear();missing.clear();}
    private void report(Throwable error){if(!reported){reported=true;LogUtils.getLogger().warn("Weapon editor uses the native item icon because its slot texture is unavailable",error);}}
    private static final class Holder {static final Api API=resolve();}
    private static Api resolve(){
        try {
            Class<?> api=Class.forName("com.tacz.guns.api.TimelessAPI"),gun=Class.forName("com.tacz.guns.client.resource.GunDisplayInstance"),
                attachment=Class.forName("com.tacz.guns.api.item.IAttachment"),index=Class.forName("com.tacz.guns.client.resource.index.ClientAttachmentIndex");
            return new Api(api.getMethod("getGunDisplay",ItemStack.class),gun.getMethod("getSlotTexture"),
                attachment.getMethod("getIAttachmentOrNull",ItemStack.class),attachment.getMethod("getAttachmentId",ItemStack.class),
                api.getMethod("getClientAttachmentIndex",ResourceLocation.class),index.getMethod("getSlotTexture"));
        } catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException | LinkageError error){LogUtils.getLogger().warn("Weapon editor slot icon API unavailable",error);return null;}
    }
    private record Api(Method gunDisplay,Method gunSlot,Method attachment,Method attachmentId,Method attachmentIndex,Method attachmentSlot) {}
}
