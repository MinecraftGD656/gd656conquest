package org.mods.gd656conquest.common.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.mods.gd656conquest.Gd656conquest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ConquestBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Gd656conquest.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Gd656conquest.MODID);
    private static final String OOB_BLOCK_ID = "oob_block";
    private static final String[] CAPTURE_CODES = new String[]{
            "A", "A1", "A2", "A3",
            "B", "B1", "B2", "B3",
            "C", "C1", "C2", "C3",
            "D", "D1", "D2", "D3",
            "E", "E1", "E2", "E3",
            "F", "F1", "F2", "F3"
    };
    private static final Map<String, RegistryObject<Block>> CAPTURE_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Item>> CAPTURE_BLOCK_ITEMS = new LinkedHashMap<>();
    private static final RegistryObject<Block> OOB_BLOCK = BLOCKS.register(OOB_BLOCK_ID, () ->
            new CapturePointBlock(BlockBehaviour.Properties.of().noCollission().noLootTable().noOcclusion().strength(-1.0F, 3600000.0F)));
    private static final RegistryObject<Item> OOB_BLOCK_ITEM = ITEMS.register(OOB_BLOCK_ID, () -> new BlockItem(OOB_BLOCK.get(), new Item.Properties()));
    private static final RegistryObject<Block> CAPTURE_CENTER_VISUAL = BLOCKS.register("capture_point_center_visual", () ->
            new Block(BlockBehaviour.Properties.of().noCollission().noLootTable().noOcclusion().strength(-1.0F, 3600000.0F)));

    static {
        for (String code : CAPTURE_CODES) {
            String id = "capture_point_" + code.toLowerCase(Locale.ROOT);
            RegistryObject<Block> block = BLOCKS.register(id, () ->
                    new CapturePointBlock(BlockBehaviour.Properties.of().noCollission().noLootTable().noOcclusion().strength(-1.0F, 3600000.0F)));
            RegistryObject<Item> item = ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
            CAPTURE_BLOCKS.put(code, block);
            CAPTURE_BLOCK_ITEMS.put(code, item);
        }
    }

    private ConquestBlocks() {
    }

    public static boolean isCapturePoint(BlockState state) {
        for (RegistryObject<Block> block : CAPTURE_BLOCKS.values()) {
            if (state.is(block.get())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCapturePointItem(Item item) {
        for (RegistryObject<Item> captureItem : CAPTURE_BLOCK_ITEMS.values()) {
            if (item == captureItem.get()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOutOfBoundsBlockItem(Item item) {
        return item != null && item == OOB_BLOCK_ITEM.get();
    }

    public static boolean isOutOfBoundsBlock(BlockState state) {
        return state != null && state.is(OOB_BLOCK.get());
    }

    public static Optional<String> resolveCaptureCode(BlockState state) {
        for (Map.Entry<String, RegistryObject<Block>> entry : CAPTURE_BLOCKS.entrySet()) {
            if (state.is(entry.getValue().get())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static Optional<String> resolveCaptureCode(Item item) {
        for (Map.Entry<String, RegistryObject<Item>> entry : CAPTURE_BLOCK_ITEMS.entrySet()) {
            if (item == entry.getValue().get()) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static Item getCaptureBlockItem(String code) {
        RegistryObject<Item> item = CAPTURE_BLOCK_ITEMS.get(code);
        return item != null ? item.get() : null;
    }

    public static boolean isMatchingCapturePointItem(BlockState state, CollisionContext context) {
        return isMatchingOutlineItem(state, context);
    }

    public static boolean isMatchingOutlineItem(BlockState state, CollisionContext context) {
        if (state == null || context == null) {
            return false;
        }
        if (isOutOfBoundsBlock(state)) {
            return context.isHoldingItem(OOB_BLOCK_ITEM.get());
        }
        Optional<String> code = resolveCaptureCode(state);
        if (code.isEmpty()) {
            return false;
        }
        RegistryObject<Item> item = CAPTURE_BLOCK_ITEMS.get(code.get());
        return item != null && context.isHoldingItem(item.get());
    }

    public static boolean isCapturePointForItem(BlockState state, Item item) {
        Optional<String> stateCode = resolveCaptureCode(state);
        Optional<String> itemCode = resolveCaptureCode(item);
        return stateCode.isPresent() && stateCode.equals(itemCode);
    }

    public static boolean isCaptureCenter(BlockState state) {
        if (!(state.getBlock() instanceof CapturePointBlock)) {
            return false;
        }
        return state.getValue(CapturePointBlock.CENTER);
    }

    public static BlockState resolveCaptureMarkerState(BlockState state) {
        if (isCaptureCenter(state)) {
            return CAPTURE_CENTER_VISUAL.get().defaultBlockState();
        }
        Optional<String> code = resolveCaptureCode(state);
        if (code.isPresent()) {
            RegistryObject<Block> normalBlock = CAPTURE_BLOCKS.get(code.get());
            if (normalBlock != null) {
                return normalBlock.get().defaultBlockState();
            }
        }
        return state;
    }

    public static Collection<String> captureCodes() {
        return java.util.List.of(CAPTURE_CODES);
    }

    public static Collection<RegistryObject<Item>> capturePointItems() {
        return Collections.unmodifiableCollection(CAPTURE_BLOCK_ITEMS.values());
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
