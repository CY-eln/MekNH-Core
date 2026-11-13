package c_y.meknh_core.api.machine;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.gui.menu.MachineMenu;
import c_y.meknh_core.decor.description.MachineNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * 数据驱动机器的注册入口（{@code 批量注册机器.txt}）。
 *
 * <p>一份机器 JSON 会为 {@code level} 里的每个等级各注册一组
 * 方块 / 物品 / 方块实体类型，命名规则见
 * {@link c_y.meknh_core.decor.description.MachineNames}。</p>
 *
 * <p>能力：物品槽、FE 缓存（电力机器）或蒸汽罐（蒸汽机器）、
 * 可选的流体与化学品输入/输出罐。</p>
 */
public final class MachineRegistration {

    /**
     * 本模组数据包内容的根目录（相对于 {@code data/}）。
     */
    public static final String ADDON_DATA_DIRECTORY = "addon";

    /**
     * 机器定义目录（相对于 {@code data/addon/}）。
     */
    public static final String MACHINE_DIRECTORY = "machine";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekNHCore.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MekNHCore.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MekNHCore.MODID);

    /**
     * 所有机器共用的容器类型：机器界面走原版 {@code AbstractContainerMenu} + {@code AbstractContainerScreen}，
     * 和 Mekanism 的 {@code MekanismTileContainer} 同一套机制。方块坐标通过打开界面时的附加数据传给客户端。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MACHINE_MENU = MENU_TYPES.register(
            "machine",
            () -> new MenuType<>((IContainerFactory<MachineMenu>) MachineMenu::fromNetwork, FeatureFlags.VANILLA_SET)
    );

    private static final Map<ResourceLocation, MachineDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MachineVariant> VARIANTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MachineVariant> VARIANTS_BY_BLOCK = new HashMap<>();
    private static final Map<ResourceLocation, DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>>> BLOCK_ENTITY_TYPES =
            new LinkedHashMap<>();

    @Nullable
    private static IEventBus modBus;

    private MachineRegistration() {
    }

    /**
     * 由主模组类在构造阶段调用。
     */
    public static synchronized void initialize(IEventBus bus) {
        if (modBus != null) {
            throw new IllegalStateException("MachineRegistration has already been initialized");
        }
        modBus = bus;
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);

        bus.addListener(RegisterCapabilitiesEvent.class, MachineRegistration::registerCapabilities);
        bus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                for (MachineVariant variant : VARIANTS.values()) {
                    event.accept(variant.item());
                }
            }
        });
    }

    /**
     * 注册一份机器定义；重复 ID 会被忽略并记录日志。
     */
    public static synchronized void register(MachineDefinition definition) {
        if (DEFINITIONS.containsKey(definition.id())) {
            MekNHCore.LOGGER.warn("Machine {} is already registered, ignoring duplicate definition", definition.id());
            return;
        }
        DEFINITIONS.put(definition.id(), definition);

        List<Map.Entry<MachineLevel, DeferredBlock<MachineBlock>>> blocks = new ArrayList<>();
        for (MachineLevel level : definition.levels()) {
            String path = MachineNames.internalPath(definition.id(), level);
            DeferredBlock<MachineBlock> block = BLOCKS.registerBlock(
                    path,
                    properties -> new MachineBlock(properties, definition, level),
                    BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()
            );
            blocks.add(Map.entry(level, block));
        }

        DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> blockEntityType =
                BLOCK_ENTITIES.register(definition.id().getPath(), () -> {
                    Block[] machineBlocks = blocks.stream()
                            .map(entry -> (Block) entry.getValue().get())
                            .toArray(Block[]::new);
                    return BlockEntityType.Builder.of(
                            (pos, state) -> new MachineBlockEntity(
                                    getBlockEntityType(definition.id()),
                                    pos,
                                    state,
                                    definition,
                                    state.getBlock() instanceof MachineBlock machine ? machine.level() : definition.levels().getFirst()
                            ),
                            machineBlocks
                    ).build(null);
                });
        BLOCK_ENTITY_TYPES.put(definition.id(), blockEntityType);

        for (Map.Entry<MachineLevel, DeferredBlock<MachineBlock>> entry : blocks) {
            MachineLevel level = entry.getKey();
            String path = MachineNames.internalPath(definition.id(), level);
            DeferredItem<MachineBlockItem> item = ITEMS.registerItem(
                    path,
                    properties -> new MachineBlockItem(entry.getValue().get(), properties, definition, level)
            );
            MachineVariant variant = new MachineVariant(definition, level, entry.getValue(), item, blockEntityType);
            VARIANTS.put(variant.id(), variant);
            VARIANTS_BY_BLOCK.put(variant.id(), variant);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (MachineVariant variant : VARIANTS.values()) {
            BlockEntityType<MachineBlockEntity> type = variant.blockEntityType().get();
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, type,
                    (blockEntity, side) -> blockEntity.itemHandlerFor(side));

            if (variant.level().isSteam()) {
                event.registerBlockEntity(Capabilities.CHEMICAL.block(), type,
                        (blockEntity, side) -> blockEntity.chemicalHandlerForSide(side));
            } else {
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, type,
                        (blockEntity, side) -> blockEntity.energyStorageForSide(side));
                // Mekanism LookingAt/Jade 读取严格能量能力；线缆按面读取，NONE 面必须断开。
                event.registerBlockEntity(Capabilities.STRICT_ENERGY.block(), type,
                        (blockEntity, side) -> side == null
                                ? blockEntity.strictEnergyHandler()
                                : blockEntity.strictEnergyHandlerForSide(side));
                if (variant.definition().chemicalInputCapacity() > 0L || variant.definition().chemicalOutputCapacity() > 0L) {
                    event.registerBlockEntity(Capabilities.CHEMICAL.block(), type,
                            (blockEntity, side) -> blockEntity.chemicalHandlerForSide(side));
                }
            }

            if (variant.definition().fluidInputCapacity() > 0L || variant.definition().fluidOutputCapacity() > 0L) {
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, type,
                        (blockEntity, side) -> blockEntity.allowsConnection(
                                c_y.meknh_core.common.machine.config.MachineConfigType.FLUID, side)
                                ? blockEntity.fluidHandler() : null);
            }
        }
    }

    public static synchronized List<MachineVariant> variants() {
        return List.copyOf(VARIANTS.values());
    }

    @Nullable
    public static MachineVariant variant(ResourceLocation blockId) {
        return VARIANTS_BY_BLOCK.get(blockId);
    }

    @Nullable
    public static MachineVariant variant(Block block) {
        return VARIANTS_BY_BLOCK.get(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
    }

    @Nullable
    public static MachineDefinition definition(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static synchronized List<MachineDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    @Nullable
    public static BlockEntityType<MachineBlockEntity> getBlockEntityType(ResourceLocation machineId) {
        DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> holder = BLOCK_ENTITY_TYPES.get(machineId);
        return holder == null ? null : holder.get();
    }
}
