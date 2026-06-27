package org.mods.gd656conquest.common.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.mods.gd656conquest.client.render.item.ConquestGeoItemRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class ConquestGeoItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ResourceLocation geoModel;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public ConquestGeoItem(Properties properties, ResourceLocation geoModel, ResourceLocation texture, ResourceLocation animation) {
        super(properties);
        this.geoModel = geoModel;
        this.texture = texture;
        this.animation = animation;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public ResourceLocation geoModelResource() {
        return geoModel;
    }

    public ResourceLocation textureResource() {
        return texture;
    }

    public ResourceLocation animationResource() {
        return animation;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new ConquestGeoItemRenderer();
                }
                return renderer;
            }
        });
    }
}
