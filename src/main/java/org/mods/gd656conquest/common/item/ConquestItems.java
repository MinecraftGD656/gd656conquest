package org.mods.gd656conquest.common.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.mods.gd656conquest.Gd656conquest;

public final class ConquestItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Gd656conquest.MODID);
    private static final ResourceLocation STATIC_ITEM_ANIMATION = ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "animations/static_item.animation.json");

    public static final RegistryObject<Item> AMMO_BAG = ITEMS.register("ammo_bag", () ->
            new AmmoBagItem(new Item.Properties().stacksTo(64),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "geo/ammo_bag.geo.json"),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "textures/item/ammo_bag.png"),
                    STATIC_ITEM_ANIMATION));
    public static final RegistryObject<Item> AMMO_BOX = ITEMS.register("ammo_box", () ->
            new AmmoBoxItem(new Item.Properties().stacksTo(1),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "geo/ammo_box.geo.json"),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "textures/item/ammo_box.png"),
                    STATIC_ITEM_ANIMATION));
    public static final RegistryObject<Item> MEDIC_BAG = ITEMS.register("medic_bag", () ->
            new MedicalBagItem(new Item.Properties().stacksTo(64),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "geo/medic_bag.geo.json"),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "textures/item/medic_bag.png"),
                    STATIC_ITEM_ANIMATION));
    public static final RegistryObject<Item> MED_BOX = ITEMS.register("med_box", () ->
            new MedicalBoxItem(new Item.Properties().stacksTo(1),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "geo/med_box.geo.json"),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "textures/item/med_box.png"),
                    STATIC_ITEM_ANIMATION));
    public static final RegistryObject<Item> RADIO_BEACON = ITEMS.register("radio_beacon", () ->
            new RadioBeaconItem(new Item.Properties().stacksTo(1),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "geo/radio_beacon.geo.json"),
                    ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, "textures/item/radio_beacon.png"),
                    STATIC_ITEM_ANIMATION));
    public static final RegistryObject<Item> QUICK_JOIN_ITEM = ITEMS.register("quick_join", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.QUICK_JOIN));
    public static final RegistryObject<Item> ROOM_LIST_ITEM = ITEMS.register("room_list", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.ROOM_LIST));
    public static final RegistryObject<Item> PARTY_GUIDE_ITEM = ITEMS.register("party_guide", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.PARTY_HELP));
    public static final RegistryObject<Item> LEAVE_PARTY_ITEM = ITEMS.register("leave_party", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.LEAVE_PARTY));
    public static final RegistryObject<Item> MY_TITLE_ITEM = ITEMS.register("my_title", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.MY_TITLE));
    public static final RegistryObject<Item> LOADOUT_GUIDE_ITEM = ITEMS.register("loadout_guide", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.LOADOUT_GUIDE));
    public static final RegistryObject<Item> ENTER_RANGE_ITEM = ITEMS.register("enter_range", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.ENTER_RANGE));
    public static final RegistryObject<Item> LEAVE_ITEM = ITEMS.register("leave_session", () ->
            new UtilityActionItem(new Item.Properties().stacksTo(1), org.mods.gd656conquest.server.facility.quickaccess.QuickAccessItemService.Action.LEAVE_SESSION));

    private ConquestItems() {
    }

    private static ConquestGeoItem createGeoItem(String geoPath, String texturePath) {
        return new ConquestGeoItem(new Item.Properties(),
                ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, geoPath),
                ResourceLocation.fromNamespaceAndPath(Gd656conquest.MODID, texturePath),
                STATIC_ITEM_ANIMATION);
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        eventBus.addListener(ConquestItems::buildCreativeTab);
    }

    private static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            acceptIfPresent(event, AMMO_BAG);
            acceptIfPresent(event, AMMO_BOX);
            acceptIfPresent(event, MEDIC_BAG);
            acceptIfPresent(event, MED_BOX);
            acceptIfPresent(event, RADIO_BEACON);
            acceptIfPresent(event, QUICK_JOIN_ITEM);
            acceptIfPresent(event, ROOM_LIST_ITEM);
            acceptIfPresent(event, PARTY_GUIDE_ITEM);
            acceptIfPresent(event, LEAVE_PARTY_ITEM);
            acceptIfPresent(event, MY_TITLE_ITEM);
            acceptIfPresent(event, LOADOUT_GUIDE_ITEM);
            acceptIfPresent(event, ENTER_RANGE_ITEM);
            acceptIfPresent(event, LEAVE_ITEM);
        }
    }

    private static void acceptIfPresent(BuildCreativeModeTabContentsEvent event, RegistryObject<Item> item) {
        if (event == null || item == null || !item.isPresent()) {
            return;
        }
        event.accept(item.get());
    }
}
