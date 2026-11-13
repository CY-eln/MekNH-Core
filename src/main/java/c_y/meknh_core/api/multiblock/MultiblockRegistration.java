package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.machine.steam.SteamVariant;
import c_y.meknh_core.decor.description.MachineNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块内核的注册入口。
 *
 * <p>负责三件事：</p>
 * <ol>
 *     <li>把 {@code data/addon/multiblock/*.json} 解析出的定义注册成控制器方块
 *     （每个电压等级一个，命名规则与单方块机器一致）；</li>
 *     <li>注册内核自带的 8 种电力仓室 × 5 个电压等级，外加 1 种蒸汽仓；</li>
 *     <li>把仓室的物品 / 流体 / 化学品 / 能量能力注册到 NeoForge 与 Mekanism 的能力系统。</li>
 * </ol>
 *
 * <p>本类不定义任何具体多方块；具体结构全部来自数据包。</p>
 */
public final class MultiblockRegistration {

    /**
     * 数据包内容根目录（相对于 {@code data/}）。
     */
    public static final String ADDON_DATA_DIRECTORY = "addon";

    /**
     * 多方块定义目录（相对于 {@code data/addon/}）。
     */
    public static final String MULTIBLOCK_DIRECTORY = "multiblock";

    /**
     * 仓室可用的电压等级：LV..IV，与单方块机器一致。
     */
    public static final List<MachineLevel> ELECTRIC_LEVELS = VoltageTier.ELECTRIC.stream()
            .map(MachineLevel::electric)
            .toList();

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekNHCore.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MekNHCore.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MekNHCore.MODID);

    /**
     * 仓室 GUI。槽位/数据由 {@link c_y.meknh_core.common.gui.menu.HatchMenu} 提供。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<c_y.meknh_core.common.gui.menu.HatchMenu>> HATCH_MENU =
            MENU_TYPES.register("hatch", () -> new MenuType<>(
                    (net.neoforged.neoforge.network.IContainerFactory<c_y.meknh_core.common.gui.menu.HatchMenu>)
                            c_y.meknh_core.common.gui.menu.HatchMenu::new,
                    net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

    private static final Map<ResourceLocation, MultiblockDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MultiblockVariant> VARIANTS = new LinkedHashMap<>();
    private static final Map<Block, MultiblockVariant> VARIANTS_BY_BLOCK = new HashMap<>();
    private static final Map<ResourceLocation, DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>>> CONTROLLER_TYPES =
            new LinkedHashMap<>();
    private static final Map<Block, MultiblockPartType> PART_BLOCKS = new HashMap<>();
    private static final Map<MultiblockPartType, List<DeferredBlock<MultiblockPartBlock>>> PART_BLOCK_HOLDERS =
            new LinkedHashMap<>();
    private static final List<PartTypeEntry> PART_TYPES = new ArrayList<>();
    private static final List<PartBlockInfo> PART_BLOCK_INFOS = new ArrayList<>();
    private static final List<DeferredItem<?>> ALL_ITEMS = new ArrayList<>();

    /**
     * 一个仓室 BE 类型与它的功能类型。
     */
    private record PartTypeEntry(MultiblockPartType type,
                                 DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartBlockEntity>> holder) {
    }

    /**
     * 仓室方块的模型生成信息。
     *
     * @param id    方块注册名
     * @param level 电压等级
     * @param type  功能类型
     */
    public record PartBlockInfo(ResourceLocation id, MachineLevel level, MultiblockPartType type, int amp) {
    }

    private static boolean initialized;
    private static boolean lookupsBuilt;

    private MultiblockRegistration() {
    }

    /**
     * 由主模组类在构造阶段调用。
     */
    public static synchronized void initialize(IEventBus bus) {
        if (initialized) {
            throw new IllegalStateException("MultiblockRegistration has already been initialized");
        }
        initialized = true;

        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);

        // 反查表在第一次查询时懒建，不依赖 RegisterEvent 的监听器顺序。
        bus.addListener(RegisterCapabilitiesEvent.class, MultiblockRegistration::registerCapabilities);
        bus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                for (MultiblockVariant variant : VARIANTS.values()) {
                    event.accept(variant.item());
                }
                for (DeferredItem<?> item : ALL_ITEMS) {
                    event.accept(item);
                }
            }
        });

        registerHatches();
    }

    /**
     * 注册一份多方块定义；重复 ID 会被忽略。
     */
    public static synchronized void register(MultiblockDefinition definition) {
        if (DEFINITIONS.containsKey(definition.id())) {
            MekNHCore.LOGGER.warn("Multiblock {} is already registered, ignoring duplicate definition", definition.id());
            return;
        }
        DEFINITIONS.put(definition.id(), definition);

        List<Map.Entry<MachineLevel, DeferredBlock<MultiblockControllerBlock>>> blocks = new ArrayList<>();
        for (MachineLevel level : definition.levels()) {
            String path = MachineNames.internalPath(definition.id(), level);
            DeferredBlock<MultiblockControllerBlock> block = BLOCKS.registerBlock(
                    path,
                    properties -> new MultiblockControllerBlock(properties, definition, level),
                    BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()
            );
            blocks.add(Map.entry(level, block));
        }

        DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>> blockEntityType =
                BLOCK_ENTITIES.register(definition.id().getPath(), () -> {
                    Block[] machineBlocks = blocks.stream()
                            .map(entry -> (Block) entry.getValue().get())
                            .toArray(Block[]::new);
                    return BlockEntityType.Builder.of(
                            (pos, state) -> new MultiblockControllerBlockEntity(
                                    getBlockEntityType(definition.id()),
                                    pos,
                                    state,
                                    definition,
                                    state.getBlock() instanceof MultiblockControllerBlock controller
                                            ? controller.level()
                                            : definition.levels().getFirst()
                            ),
                            machineBlocks
                    ).build(null);
                });
        CONTROLLER_TYPES.put(definition.id(), blockEntityType);

        for (Map.Entry<MachineLevel, DeferredBlock<MultiblockControllerBlock>> entry : blocks) {
            String path = MachineNames.internalPath(definition.id(), entry.getKey());
            DeferredItem<MultiblockBlockItem> item = ITEMS.registerItem(
                    path,
                    properties -> new MultiblockBlockItem(entry.getValue().get(), properties)
            );
            MultiblockVariant variant = new MultiblockVariant(definition, entry.getKey(), entry.getValue(), item,
                    blockEntityType);
            VARIANTS.put(variant.id(), variant);
        }
        MekNHCore.LOGGER.debug("Registered multiblock {} with {} controller variant(s)",
                definition.id(), blocks.size());
    }

    /**
     * 注册内核自带的电力仓室：8 种功能 × 5 个电压等级；蒸汽仓单独注册。
     *
     * <p>仓室方块命名：{@code <电压>_<功能>_hatch}，例如 {@code lv_energy_input_hatch}。
     * 每个功能共用一个方块实体类型，具体类型与等级从方块读取。</p>
     */
    private static void registerHatches() {
        for (MultiblockPartType type : MultiblockPartType.values()) {
            if (type == MultiblockPartType.STEAM_INPUT) {
                continue;
            }
            // 能量仓只有电力版本；物品/流体仓额外提供 ULV（蒸汽）版本。
            boolean energy = type == MultiblockPartType.ENERGY_INPUT || type == MultiblockPartType.ENERGY_OUTPUT;
            List<MachineLevel> levels = new ArrayList<>();
            levels.add(MachineLevel.steam(SteamVariant.LOW));
            levels.addAll(ELECTRIC_LEVELS);

            List<DeferredBlock<MultiblockPartBlock>> blocks = new ArrayList<>();
            for (MachineLevel level : levels) {
                // ULV 仓使用 GT 的 ulv_ 前缀，而不是蒸汽机器的 lp_steam_。
                String tierPrefix = level.isSteam() ? "ulv" : level.idPrefix();
                for (int amp : ampsFor(type, level)) {
                    String path = tierPrefix + "_" + type.id() + "_hatch" + (amp == 2 ? "" : "_" + amp + "a");
                    DeferredBlock<MultiblockPartBlock> block = BLOCKS.registerBlock(
                            path,
                            properties -> new MultiblockPartBlock(properties, type, level, amp),
                            BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()
                    );
                    DeferredItem<MultiblockBlockItem> item = ITEMS.registerItem(
                            path,
                            properties -> new MultiblockBlockItem(block.get(), properties)
                    );
                    blocks.add(block);
                    PART_BLOCK_INFOS.add(new PartBlockInfo(block.getId(), level, type, amp));
                    ALL_ITEMS.add(item);
                }
            }
            PART_BLOCK_HOLDERS.put(type, List.copyOf(blocks));
            DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartBlockEntity>> holder =
                    BLOCK_ENTITIES.register(type.id() + "_hatch", () -> {
                        Block[] partBlocks = blocks.stream()
                                .map(block -> (Block) block.get())
                                .toArray(Block[]::new);
                        return BlockEntityType.Builder.of(
                                (pos, state) -> {
                                    if (state.getBlock() instanceof MultiblockPartBlock part) {
                                        return new MultiblockPartBlockEntity(getPartBlockEntityType(type), pos, state,
                                                part.partType(), part.level(), part.amp());
                                    }
                                    throw new IllegalStateException("Block " + state.getBlock()
                                            + " is not a multiblock part, but is registered to " + type.id() + "_hatch");
                                },
                                partBlocks
                        ).build(null);
                    });
            PART_TYPES.add(new PartTypeEntry(type, holder));
        }
        registerSteamHatch();
    }

    /**
     * 能源仓的安培变体：ULV～HV 只有 2A，EV/IV 额外提供 4A 与 16A。
     */
    private static List<Integer> ampsFor(MultiblockPartType type, MachineLevel level) {
        if (type != MultiblockPartType.ENERGY_INPUT && type != MultiblockPartType.ENERGY_OUTPUT) {
            return List.of(2);
        }
        return switch (level.voltage()) {
            case EV, IV -> List.of(2, 4, 16);
            default -> List.of(2);
        };
    }

    /**
     * 蒸汽输入仓：蒸汽多方块唯一的能源仓，只接受 {@code mekanism:steam}。
     *
     * <p>蒸汽只有 ULV 一个等级；低压/高压由控制器决定，不由仓室决定。</p>
     */
    private static void registerSteamHatch() {
        MachineLevel level = MachineLevel.steam(SteamVariant.LOW);
        String path = "steam_input_hatch";
        DeferredBlock<MultiblockPartBlock> block = BLOCKS.registerBlock(
                path,
                properties -> new MultiblockPartBlock(properties, MultiblockPartType.STEAM_INPUT, level),
                BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()
        );
        DeferredItem<MultiblockBlockItem> item = ITEMS.registerItem(
                path,
                properties -> new MultiblockBlockItem(block.get(), properties)
        );
        ALL_ITEMS.add(item);
        PART_BLOCK_INFOS.add(new PartBlockInfo(block.getId(), level, MultiblockPartType.STEAM_INPUT, 2));
        PART_BLOCK_HOLDERS.put(MultiblockPartType.STEAM_INPUT, List.of(block));

        DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartBlockEntity>> holder =
                BLOCK_ENTITIES.register("steam_input_hatch", () -> BlockEntityType.Builder.of(
                        (pos, state) -> {
                            if (state.getBlock() instanceof MultiblockPartBlock part) {
                                return new MultiblockPartBlockEntity(
                                        getPartBlockEntityType(MultiblockPartType.STEAM_INPUT), pos, state,
                                        part.partType(), part.level());
                            }
                            throw new IllegalStateException("Block " + state.getBlock()
                                    + " is not a steam input hatch");
                        },
                        block.get()
                ).build(null));
        PART_TYPES.add(new PartTypeEntry(MultiblockPartType.STEAM_INPUT, holder));
    }

    /**
     * 兜底：方块注册完成后填充「方块 → 功能类型 / 控制器版本」反查表。
     *
     * <p>不依赖 {@code RegisterEvent} 的监听器顺序：第一次查询时再建表，
     * 如果方块还没注册完就返回，下一次查询重试。</p>
     */
    private static synchronized void ensureLookups() {
        if (lookupsBuilt) {
            return;
        }
        Map<Block, MultiblockPartType> parts = new HashMap<>();
        Map<Block, MultiblockVariant> variants = new HashMap<>();
        try {
            for (Map.Entry<MultiblockPartType, List<DeferredBlock<MultiblockPartBlock>>> entry
                    : PART_BLOCK_HOLDERS.entrySet()) {
                for (DeferredBlock<MultiblockPartBlock> holder : entry.getValue()) {
                    parts.put(holder.get(), entry.getKey());
                }
            }
            for (MultiblockVariant variant : VARIANTS.values()) {
                variants.put(variant.block().get(), variant);
            }
        } catch (RuntimeException notReady) {
            // 方块注册尚未完成；保持未建表状态，下次查询再试。
            return;
        }
        PART_BLOCKS.clear();
        PART_BLOCKS.putAll(parts);
        VARIANTS_BY_BLOCK.clear();
        VARIANTS_BY_BLOCK.putAll(variants);
        lookupsBuilt = true;
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (PartTypeEntry entry : PART_TYPES) {
            BlockEntityType<MultiblockPartBlockEntity> type = entry.holder().get();
            switch (entry.type()) {
                case ITEM_INPUT, ITEM_OUTPUT -> event.registerBlockEntity(
                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        type,
                        (blockEntity, side) -> blockEntity.externalItemHandler());
                case FLUID_INPUT, FLUID_OUTPUT -> {
                    event.registerBlockEntity(
                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            type,
                            (blockEntity, side) -> blockEntity.externalFluidHandler());
                    event.registerBlockEntity(
                            Capabilities.CHEMICAL.block(),
                            type,
                            (blockEntity, side) -> blockEntity.externalChemicalHandler());
                }
                case STEAM_INPUT -> event.registerBlockEntity(
                        Capabilities.CHEMICAL.block(),
                        type,
                        (blockEntity, side) -> blockEntity.externalChemicalHandler());
                case ENERGY_INPUT, ENERGY_OUTPUT -> {
                    event.registerBlockEntity(
                            net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                            type,
                            (blockEntity, side) -> blockEntity.energyStorage());
                    event.registerBlockEntity(
                            Capabilities.STRICT_ENERGY.block(),
                            type,
                            (blockEntity, side) -> blockEntity.strictEnergyHandler());
                }
            }
        }
    }

    /**
     * 全部已注册的多方块定义。
     */
    public static synchronized List<MultiblockDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    /**
     * 全部已注册的控制器版本。
     */
    public static synchronized List<MultiblockVariant> variants() {
        return List.copyOf(VARIANTS.values());
    }

    /**
     * 按方块查控制器版本；不是控制器时返回 {@code null}。
     */
    @Nullable
    public static MultiblockVariant variant(Block block) {
        ensureLookups();
        return VARIANTS_BY_BLOCK.get(block);
    }

    /**
     * 按 ID 查定义。
     */
    @Nullable
    public static MultiblockDefinition definition(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    /**
     * 按方块查仓室功能类型；不是仓室时返回 {@code null}。
     */
    @Nullable
    public static MultiblockPartType partType(Block block) {
        ensureLookups();
        return PART_BLOCKS.get(block);
    }

    /**
     * 按多方块 ID 取控制器方块实体类型。
     */
    @Nullable
    public static BlockEntityType<MultiblockControllerBlockEntity> getBlockEntityType(ResourceLocation multiblockId) {
        DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>> holder =
                CONTROLLER_TYPES.get(multiblockId);
        return holder == null ? null : holder.get();
    }

    /**
     * 按仓室功能类型取方块实体类型。
     */
    @Nullable
    public static BlockEntityType<MultiblockPartBlockEntity> getPartBlockEntityType(MultiblockPartType type) {
        for (PartTypeEntry entry : PART_TYPES) {
            if (entry.type() == type) {
                return entry.holder().get();
            }
        }
        return null;
    }

    /**
     * 仓室物品列表，供 JEI/创造栏等外部代码遍历。
     */
    public static List<DeferredItem<?>> partItems() {
        return List.copyOf(ALL_ITEMS);
    }

    /**
     * 仓室方块的注册信息，供运行时模型生成使用。
     */
    public static List<PartBlockInfo> partBlockInfos() {
        return List.copyOf(PART_BLOCK_INFOS);
    }

    /**
     * 全部已注册的仓室方块（客户端颜色处理器使用）。
     */
    public static List<Block> partBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (List<DeferredBlock<MultiblockPartBlock>> holders : PART_BLOCK_HOLDERS.values()) {
            for (DeferredBlock<MultiblockPartBlock> holder : holders) {
                blocks.add(holder.get());
            }
        }
        return blocks;
    }
}
