package org.mods.gd656conquest.client.render.item;

import org.mods.gd656conquest.common.item.ConquestGeoItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class ConquestGeoItemRenderer extends GeoItemRenderer<ConquestGeoItem> {
    public ConquestGeoItemRenderer() {
        super(new ConquestGeoItemModel());
    }
}
