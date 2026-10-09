package org.mods.gd656conquest.client.render.item;

import net.minecraft.resources.ResourceLocation;
import org.mods.gd656conquest.common.item.ConquestGeoItem;
import software.bernie.geckolib.model.GeoModel;

public class ConquestGeoItemModel extends GeoModel<ConquestGeoItem> {
    @Override
    public ResourceLocation getModelResource(ConquestGeoItem animatable) {
        return animatable.geoModelResource();
    }

    @Override
    public ResourceLocation getTextureResource(ConquestGeoItem animatable) {
        return animatable.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(ConquestGeoItem animatable) {
        return animatable.animationResource();
    }
}
