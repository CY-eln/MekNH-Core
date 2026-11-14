package c_y.meknh_core.common.coke;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.chemical.DataFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 焦炉产物：焦炭（物品）与杂酚油（流体 + 桶 + 液体方块）。
 *
 * <p>贴图取自 GT：焦炭用 {@code lignite} 宝石底图按 GT 的焦炭色（主色 0x575E5B、
 * 副色 0x1F1F29）着色；杂酚油用 GT 的 {@code fluid.creosote.png}（16×512 动画）。</p>
 */
public final class CokeOvenMaterials {

    private static final String MODID = MekNHCore.MODID;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MODID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MODID);

    /**
     * 杂酚油贴图（GT 原图，16×512 动画）。
     */
    public static final ResourceLocation CREOSOTE_TEXTURE = MekNHCore.rl("block/fluids/creosote");

    /**
     * 焦炭。
     */
    public static final DeferredItem<Item> COKE_GEM = ITEMS.registerItem("coke_gem", Item::new);

    public static final DeferredHolder<FluidType, FluidType> CREOSOTE_TYPE = FLUID_TYPES.register("creosote",
            () -> new DataFluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + MODID + ".creosote")
                    .density(1200)
                    .viscosity(2500),
                    CREOSOTE_TEXTURE, CREOSOTE_TEXTURE, 0xFFFFFF));

    private static BaseFlowingFluid.Properties creosoteProperties;

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CREOSOTE = FLUIDS.register("creosote",
            () -> new BaseFlowingFluid.Source(creosoteProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> CREOSOTE_FLOWING = FLUIDS.register(
            "creosote_flowing", () -> new BaseFlowingFluid.Flowing(creosoteProperties()));

    /**
     * 杂酚油桶：桶交互取流体靠它。
     */
    public static final DeferredItem<BucketItem> CREOSOTE_BUCKET = ITEMS.registerItem("creosote_bucket",
            properties -> new BucketItem(CREOSOTE.get(), properties.stacksTo(1).craftRemainder(Items.BUCKET)));

    /**
     * 杂酚油液体方块，让桶能倒出来。
     */
    public static final DeferredBlock<LiquidBlock> CREOSOTE_BLOCK = BLOCKS.register("creosote",
            () -> new LiquidBlock(CREOSOTE.get(), BlockBehaviour.Properties.of()
                    .noCollission()
                    .replaceable()
                    .strength(100.0F)
                    .noLootTable()
                    .liquid()
                    .pushReaction(PushReaction.DESTROY)
                    .sound(SoundType.EMPTY)));

    private static BaseFlowingFluid.Properties creosoteProperties() {
        if (creosoteProperties == null) {
            creosoteProperties = new BaseFlowingFluid.Properties(CREOSOTE_TYPE, CREOSOTE, CREOSOTE_FLOWING)
                    .block(CREOSOTE_BLOCK)
                    .bucket(CREOSOTE_BUCKET);
        }
        return creosoteProperties;
    }

    private CokeOvenMaterials() {
    }

    public static synchronized void initialize(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        bus.addListener(CokeOvenMaterials::addCreativeTab);
    }

    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(COKE_GEM.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CREOSOTE_BUCKET.get());
        }
    }
}
