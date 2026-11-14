package c_y.meknh_core.common.coke;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 焦炉相关注册：焦炉砖方块、焦炉 GUI、焦炉控制器工厂。
 *
 * <p>焦炉控制器本身由数据包 {@code data/addon/multiblock/coke_oven.json} 注册，
 * 这里只把它的方块实体换成带容器/GUI 的 {@link CokeOvenControllerBlockEntity}。
 * 焦炉仓在 {@link MultiblockRegistration} 里注册（属于仓室体系）。</p>
 */
public final class CokeOvenRegistration {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekNHCore.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MekNHCore.MODID);

    /**
     * 焦炉砖：焦炉结构的成型方块。
     */
    public static final DeferredBlock<Block> COKE_OVEN_BRICKS = BLOCKS.register("coke_oven_bricks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    public static final DeferredItem<BlockItem> COKE_OVEN_BRICKS_ITEM =
            ITEMS.registerItem("coke_oven_bricks",
                    properties -> new BlockItem(COKE_OVEN_BRICKS.get(), properties));

    public static final DeferredHolder<MenuType<?>, MenuType<CokeOvenMenu>> COKE_OVEN_MENU =
            MENU_TYPES.register("coke_oven", () -> new MenuType<>(
                    (net.neoforged.neoforge.network.IContainerFactory<CokeOvenMenu>) CokeOvenMenu::new,
                    FeatureFlags.VANILLA_SET));

    private static boolean initialized;

    private CokeOvenRegistration() {
    }

    public static synchronized void initialize(IEventBus bus) {
        if (initialized) {
            return;
        }
        initialized = true;
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENU_TYPES.register(bus);
        bus.addListener(CokeOvenRegistration::addCreativeTab);
        MultiblockRegistration.registerControllerFactory(MekNHCore.rl("coke_oven"),
                CokeOvenControllerBlockEntity::new);
    }

    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(COKE_OVEN_BRICKS_ITEM.get());
        }
    }
}
