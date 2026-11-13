package c_y.meknh_core.api.casing;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.casing.FireboxCasingBlock;
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
 * {@code steam_machine_casing} 与 {@code bronze_firebox_casing}。</p>
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
        }
    }
}
