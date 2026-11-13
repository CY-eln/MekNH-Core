package c_y.meknh_core.api.generator.boiler;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.generator.boiler.BoilerBlock;
import c_y.meknh_core.common.generator.boiler.BoilerBlockEntity;
import c_y.meknh_core.common.generator.boiler.BoilerMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 锅炉动态注册。注册方式与机器一致：每份定义一个方块实体类型。
 */
public final class BoilerRegistration {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekNHCore.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MekNHCore.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MekNHCore.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BoilerMenu>> BOILER_MENU =
            MENU_TYPES.register("boiler", () -> new MenuType<>(
                    (net.neoforged.neoforge.network.IContainerFactory<BoilerMenu>)BoilerMenu::new,
                    FeatureFlags.VANILLA_SET));

    private static final Map<ResourceLocation, Variant> VARIANTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>>>
            BLOCK_ENTITY_TYPES = new LinkedHashMap<>();
    private static IEventBus modBus;

    private BoilerRegistration() {
    }

    public static synchronized void initialize(IEventBus bus) {
        if (modBus != null) {
            return;
        }
        modBus = bus;
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENU_TYPES.register(bus);
        bus.addListener(BoilerRegistration::registerCapabilities);
        bus.addListener(BoilerRegistration::addCreativeTab);
    }

    public static synchronized void register(BoilerDefinition definition) {
        List<Map.Entry<BoilerTier, DeferredBlock<BoilerBlock>>> blocks = new ArrayList<>();
        for (BoilerTier tier : BoilerTier.values()) {
            String path = definition.id().getPath() + "_" + tier.getSerializedName();
            DeferredBlock<BoilerBlock> block = BLOCKS.registerBlock(path,
                    properties -> new BoilerBlock(definition, tier, properties),
                    BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops());
            blocks.add(Map.entry(tier, block));
        }

        DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> type =
                BLOCK_ENTITIES.register(definition.id().getPath(), () -> {
                    Block[] boilerBlocks = blocks.stream()
                            .map(entry -> (Block)entry.getValue().get()).toArray(Block[]::new);
                    return BlockEntityType.Builder.of(
                            (pos, state) -> new BoilerBlockEntity(
                                    getBlockEntityType(definition.id()), pos, state),
                            boilerBlocks).build(null);
                });
        BLOCK_ENTITY_TYPES.put(definition.id(), type);

        for (Map.Entry<BoilerTier, DeferredBlock<BoilerBlock>> entry : blocks) {
            BoilerTier tier = entry.getKey();
            String path = definition.id().getPath() + "_" + tier.getSerializedName();
            DeferredItem<net.minecraft.world.item.BlockItem> item = ITEMS.registerItem(path,
                    properties -> new net.minecraft.world.item.BlockItem(entry.getValue().get(), properties));
            VARIANTS.put(MekNHCore.rl(path), new Variant(definition, tier, entry.getValue(), item, type));
        }
    }

    public static BlockEntityType<BoilerBlockEntity> typeFor(Block block) {
        if (block instanceof BoilerBlock boiler) {
            return getBlockEntityType(boiler.definition().id());
        }
        return null;
    }

    public static BlockEntityType<BoilerBlockEntity> getBlockEntityType(ResourceLocation id) {
        DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> holder = BLOCK_ENTITY_TYPES.get(id);
        return holder == null ? null : holder.get();
    }

    public static Iterable<Variant> variants() {
        return VARIANTS.values();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Variant variant : VARIANTS.values()) {
            BlockEntityType<BoilerBlockEntity> type = variant.type().get();
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, type,
                    (be, side) -> side != null && be.sideMode(
                            c_y.meknh_core.common.machine.config.MachineConfigType.ITEM, side).allowsInsert()
                            ? be.itemHandler() : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, type,
                    (be, side) -> side != null && be.sideMode(
                            c_y.meknh_core.common.machine.config.MachineConfigType.FLUID, side).allowsInsert()
                            ? be : null);
            event.registerBlockEntity(Capabilities.CHEMICAL.block(), type,
                    (be, side) -> be.chemicalHandlerForSide(side));
            event.registerBlockEntity(Capabilities.HEAT, type, (be, side) -> be);
        }
    }

    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            for (Variant variant : VARIANTS.values()) {
                event.accept(variant.item().get());
            }
        }
    }

    public record Variant(BoilerDefinition definition, BoilerTier tier,
                          DeferredBlock<BoilerBlock> block,
                          DeferredItem<net.minecraft.world.item.BlockItem> item,
                          DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> type) {
    }
}
