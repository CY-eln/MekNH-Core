package c_y.meknh_core.api.casing;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.casing.FireboxCasingBlock;
import c_y.meknh_core.common.casing.PumpFacingBlock;
import c_y.meknh_core.common.casing.PrimitivePumpControllerBlock;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 格雷风格外壳方块注册。
 *
 * <p>方块 ID 与 GTCEu 保持一致（去掉 {@code gtceu:} 命名空间），方便配方/多方块按名称对应：
 * 蒸汽外壳、燃烧室外壳，以及原始水泵、平台、防腐木框架和水泵仓。</p>
 */
public final class CasingRegistration {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekNHCore.MODID);

    private static boolean initialized;

    /** 蒸汽机器外壳（青铜板材砖）。 */
    public static final DeferredBlock<Block> STEAM_MACHINE_CASING = BLOCKS.register("steam_machine_casing",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /** 青铜燃烧室外壳，带点燃状态。 */
    public static final DeferredBlock<FireboxCasingBlock> BRONZE_FIREBOX_CASING =
            BLOCKS.register("bronze_firebox_casing",
                    () -> new FireboxCasingBlock(BlockBehaviour.Properties.of()
                            .strength(5.0F, 6.0F)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.METAL)
                            .lightLevel(state -> state.getValue(FireboxCasingBlock.ACTIVE) ? 15 : 0)));

    public static final DeferredItem<BlockItem> STEAM_MACHINE_CASING_ITEM =
            ITEMS.registerItem("steam_machine_casing",
                    properties -> new BlockItem(STEAM_MACHINE_CASING.get(), properties));

    public static final DeferredItem<BlockItem> BRONZE_FIREBOX_CASING_ITEM =
            ITEMS.registerItem("bronze_firebox_casing",
                    properties -> new BlockItem(BRONZE_FIREBOX_CASING.get(), properties));

    /** 与 GTCEu 对应的泵结构方块，仅注册方块与外观。 */
    public static final ResourceLocation PRIMITIVE_PUMP_ID = MekNHCore.rl("primitive_pump");

    public static final DeferredBlock<Block> PRIMITIVE_PUMP = BLOCKS.register("primitive_pump",
            () -> MultiblockRegistration.controllerDefinition(PRIMITIVE_PUMP_ID) != null
                    ? new PrimitivePumpControllerBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 3.0F).sound(SoundType.WOOD))
                    : new PumpFacingBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> PUMP_DECK = BLOCKS.register("pump_deck",
            () -> new Block(BlockBehaviour.Properties.of().strength(2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> TREATED_WOOD_FRAME = BLOCKS.register("treated_wood_frame",
            () -> new Block(BlockBehaviour.Properties.of().strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<PumpFacingBlock> PUMP_HATCH = BLOCKS.register("pump_hatch",
            () -> new PumpFacingBlock(BlockBehaviour.Properties.of().strength(2.0F, 3.0F).sound(SoundType.WOOD)));

    public static final DeferredItem<BlockItem> PRIMITIVE_PUMP_ITEM =
            ITEMS.registerItem("primitive_pump", properties -> new BlockItem(PRIMITIVE_PUMP.get(), properties));
    public static final DeferredItem<BlockItem> PUMP_DECK_ITEM =
            ITEMS.registerItem("pump_deck", properties -> new BlockItem(PUMP_DECK.get(), properties));
    public static final DeferredItem<BlockItem> TREATED_WOOD_FRAME_ITEM =
            ITEMS.registerItem("treated_wood_frame", properties -> new BlockItem(TREATED_WOOD_FRAME.get(), properties));
    public static final DeferredItem<BlockItem> PUMP_HATCH_ITEM =
            ITEMS.registerItem("pump_hatch", properties -> new BlockItem(PUMP_HATCH.get(), properties));

    private CasingRegistration() {
    }

    public static synchronized void initialize(IEventBus bus) {
        if (initialized) {
            return;
        }
        initialized = true;
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(CasingRegistration::addCreativeTab);
    }

    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(STEAM_MACHINE_CASING_ITEM.get());
            event.accept(BRONZE_FIREBOX_CASING_ITEM.get());
            event.accept(PRIMITIVE_PUMP_ITEM.get());
            event.accept(PUMP_DECK_ITEM.get());
            event.accept(TREATED_WOOD_FRAME_ITEM.get());
            event.accept(PUMP_HATCH_ITEM.get());
        }
    }
}
