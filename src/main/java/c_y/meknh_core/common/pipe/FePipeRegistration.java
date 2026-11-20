package c_y.meknh_core.common.pipe;

import c_y.meknh_core.MekNHCore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 数据驱动 FE 电缆注册：{@code data/addon/pipe/fe/*.json} → 方块 + 物品 + 方块实体。
 */
public final class FePipeRegistration {

    public static final String ADDON_DATA_DIRECTORY = "addon";
    public static final String PIPE_DIRECTORY = "pipe/fe";

    private static final Map<ResourceLocation, FePipeDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DeferredBlock<FeCableBlock>> BLOCKS = new LinkedHashMap<>();
    private static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();

    private static final DeferredRegister.Blocks BLOCK_REGISTER = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEM_REGISTER = DeferredRegister.createItems(MekNHCore.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MekNHCore.MODID);

    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> cableType;
    private static boolean initialized;

    private FePipeRegistration() {
    }

    public static synchronized void initialize(IEventBus bus) {
        if (initialized) {
            return;
        }
        initialized = true;
        BLOCK_REGISTER.register(bus);
        ITEM_REGISTER.register(bus);
        BLOCK_ENTITY_REGISTER.register(bus);
        cableType = BLOCK_ENTITY_REGISTER.register("fe_cable",
                () -> BlockEntityType.Builder.of(FeCableBlockEntity::new, cableBlocks()).build(null));
        bus.addListener(FePipeRegistration::addCreativeTab);
        bus.addListener(FePipeRegistration::registerCapabilities);
    }

    /**
     * 由数据加载器调用；重复 ID 返回 {@code false}。
     */
    public static synchronized boolean register(FePipeDefinition definition) {
        if (!initialized) {
            throw new IllegalStateException("FePipeRegistration has not been initialized");
        }
        if (DEFINITIONS.containsKey(definition.id())) {
            return false;
        }
        DEFINITIONS.put(definition.id(), definition);
        String path = definition.id().getPath();
        DeferredBlock<FeCableBlock> block = BLOCK_REGISTER.register(path,
                () -> new FeCableBlock(BlockBehaviour.Properties.of()
                        .strength(0.5F)
                        .sound(SoundType.METAL)
                        .noOcclusion(), definition));
        BLOCKS.put(definition.id(), block);
        DeferredItem<BlockItem> item = ITEM_REGISTER.registerItem(path,
                properties -> new BlockItem(block.get(), properties));
        ITEMS.add(item);
        return true;
    }

    private static Block[] cableBlocks() {
        return BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new);
    }

    public static List<FePipeDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static BlockEntityType<?> cableType() {
        return cableType == null ? null : cableType.get();
    }

    public static FeCableBlock cableBlock(ResourceLocation id) {
        DeferredBlock<FeCableBlock> block = BLOCKS.get(id);
        return block == null ? null : block.get();
    }

    @SuppressWarnings("unchecked")
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        BlockEntityType<FeCableBlockEntity> type = (BlockEntityType<FeCableBlockEntity>) cableType.get();
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type,
                (blockEntity, side) -> blockEntity.energyStorage());
        event.registerBlockEntity(mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(), type,
                (blockEntity, side) -> blockEntity.strictEnergyStorage());
    }

    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            for (DeferredItem<BlockItem> item : ITEMS) {
                event.accept(item.get());
            }
        }
    }
}
