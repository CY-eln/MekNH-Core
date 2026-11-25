package c_y.meknh_core.common.blast;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

/** The controller block and BE type belong to the JSON multiblock registry. */
public final class PrimitiveBlastFurnaceRegistration {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekNHCore.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MekNHCore.MODID);
    public static final DeferredBlock<Block> PRIMITIVE_BLAST_FURNACE_BRICKS = BLOCKS.register("primitive_blast_furnace_bricks",
            () -> new Block(BlockBehaviour.Properties.of().strength(3.5F, 6F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final DeferredItem<BlockItem> PRIMITIVE_BLAST_FURNACE_BRICKS_ITEM = ITEMS.registerItem("primitive_blast_furnace_bricks",
            properties -> new BlockItem(PRIMITIVE_BLAST_FURNACE_BRICKS.get(), properties));
    public static final DeferredItem<Item> DUST_TINY_DARK_ASH = ITEMS.registerSimpleItem("dust_tiny_dark_ash");
    public static final DeferredItem<Item> DUST_ASH = ITEMS.registerSimpleItem("dust_ash");
    public static final DeferredItem<Item> DUST_BRICK = ITEMS.registerSimpleItem("dust_brick");
    public static final DeferredItem<Item> DUST_CLAY = ITEMS.registerSimpleItem("dust_clay");
    public static final DeferredItem<Item> DUST_FIRECLAY = ITEMS.registerSimpleItem("dust_fireclay");
    public static final DeferredItem<Item> FIRECLAY_BRICK = ITEMS.registerSimpleItem("firebrick");
    public static final DeferredItem<Item> COMPRESSED_FIRECLAY = ITEMS.registerSimpleItem("compressed_fireclay");
    public static final DeferredHolder<MenuType<?>, MenuType<PrimitiveBlastFurnaceMenu>> PRIMITIVE_BLAST_FURNACE_MENU =
            MENUS.register("primitive_blast_furnace", () -> new MenuType<>(
                    (net.neoforged.neoforge.network.IContainerFactory<PrimitiveBlastFurnaceMenu>) PrimitiveBlastFurnaceMenu::new,
                    FeatureFlags.VANILLA_SET));
    private static boolean initialized;
    private PrimitiveBlastFurnaceRegistration() { }
    public static synchronized void initialize(IEventBus bus) {
        if (initialized) return;
        initialized = true;
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        MultiblockRegistration.registerControllerFactory(MekNHCore.rl("primitive_blast_furnace"), PrimitiveBlastFurnaceControllerBlockEntity::new);
        MultiblockRegistration.registerStaticControllerAssets(MekNHCore.rl("primitive_blast_furnace"));
        bus.addListener(PrimitiveBlastFurnaceRegistration::registerCapabilities);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS)
                event.accept(PRIMITIVE_BLAST_FURNACE_BRICKS_ITEM.get());
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.INGREDIENTS) {
                event.accept(DUST_TINY_DARK_ASH.get());
                event.accept(DUST_ASH.get());
                event.accept(DUST_BRICK.get());
                event.accept(DUST_CLAY.get());
                event.accept(DUST_FIRECLAY.get());
                event.accept(FIRECLAY_BRICK.get());
                event.accept(COMPRESSED_FIRECLAY.get());
            }
        });
    }
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var type = MultiblockRegistration.getBlockEntityType(MekNHCore.rl("primitive_blast_furnace"));
        if (type != null) event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (be, side) -> be instanceof PrimitiveBlastFurnaceControllerBlockEntity furnace ? furnace.items().externalView() : null);
        else MekNHCore.LOGGER.error("Missing JSON primitive_blast_furnace BE type during capability registration");
    }
}
