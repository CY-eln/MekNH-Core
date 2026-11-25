package c_y.meknh_core.common.pipe;

import c_y.meknh_core.MekNHCore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import c_y.meknh_core.mixin.mek.pipe.BlockEntityTypeAccessor;

/** 数据驱动 FE 电缆注册（data/addon/pipe/fe/*.json）。 */
public final class FePipeRegistration {

    public static final String ADDON_DATA_DIRECTORY = "addon";
    public static final String PIPE_DIRECTORY = "pipe/fe";

    private static final Map<ResourceLocation, FePipeDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DeferredBlock<FeCableBlock>> BLOCKS = new LinkedHashMap<>();
    private static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();

    private static final DeferredRegister.Blocks BLOCK_REGISTER = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEM_REGISTER = DeferredRegister.createItems(MekNHCore.MODID);

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
        bus.addListener(FePipeRegistration::addCreativeTab);
        bus.addListener(FePipeRegistration::commonSetup);
        bus.addListener(FePipeRegistration::registerCapabilities);
    }

    public static synchronized boolean register(FePipeDefinition definition) {
        if (!initialized) {
            throw new IllegalStateException("FePipeRegistration has not been initialized");
        }
        if (DEFINITIONS.containsKey(definition.id())) {
            return false;
        }
        DEFINITIONS.put(definition.id(), definition);
        String path = definition.id().getPath();
        DeferredBlock<FeCableBlock> block = BLOCK_REGISTER.register(path, () -> new FeCableBlock(definition));
        BLOCKS.put(definition.id(), block);
        DeferredItem<BlockItem> item = ITEM_REGISTER.registerItem(path,
                properties -> new BlockItem(block.get(), properties));
        ITEMS.add(item);
        return true;
    }

    public static List<FePipeDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                mekanism.common.registries.MekanismTileEntityTypes.BASIC_UNIVERSAL_CABLE.get(),
                (blockEntity, side) -> blockEntity instanceof FeCableAccess access
                        ? access.meknh_core$feStorage() : null);
        event.registerBlockEntity(mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(),
                mekanism.common.registries.MekanismTileEntityTypes.BASIC_UNIVERSAL_CABLE.get(),
                (blockEntity, side) -> blockEntity instanceof FeCableAccess access
                        ? new mekanism.api.energy.IStrictEnergyHandler() {
                            @Override
                            public int getEnergyContainerCount() {
                                return 1;
                            }

                            @Override
                            public long getEnergy(int container) {
                                return Math.round(access.meknh_core$feStorage().getEnergyStored() * 2.5D);
                            }

                            @Override
                            public void setEnergy(int container, long energy) {
                                if (access.meknh_core$feStorage() instanceof FeCableStorage storage) {
                                    storage.forceSet((int) Math.round(energy / 2.5D));
                                }
                            }

                            @Override
                            public long getMaxEnergy(int container) {
                                return Math.round(access.meknh_core$feStorage().getMaxEnergyStored() * 2.5D);
                            }

                            @Override
                            public long getNeededEnergy(int container) {
                                return Math.max(0L, this.getMaxEnergy(container) - this.getEnergy(container));
                            }

                            @Override
                            public long insertEnergy(int container, long amount, mekanism.api.Action action) {
                                int accepted = access.meknh_core$feStorage().receiveEnergy(
                                        (int) Math.min(Integer.MAX_VALUE, Math.floor(amount / 2.5D)),
                                        action.simulate());
                                return amount - Math.round(accepted * 2.5D);
                            }

                            @Override
                            public long extractEnergy(int container, long amount, mekanism.api.Action action) {
                                int extracted = access.meknh_core$feStorage().extractEnergy(
                                        (int) Math.min(Integer.MAX_VALUE, Math.ceil(amount / 2.5D)),
                                        action.simulate());
                                return Math.round(extracted * 2.5D);
                            }
                        } : null);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BlockEntityTypeAccessor accessor = (BlockEntityTypeAccessor) (Object)
                    mekanism.common.registries.MekanismTileEntityTypes.BASIC_UNIVERSAL_CABLE.get();
            java.util.Set<net.minecraft.world.level.block.Block> valid =
                    new java.util.HashSet<>(accessor.meknh_core$validBlocks());
            for (DeferredBlock<FeCableBlock> block : BLOCKS.values()) {
                valid.add(block.get());
            }
            accessor.meknh_core$setValidBlocks(java.util.Set.copyOf(valid));
        });
    }

    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            for (DeferredItem<BlockItem> item : ITEMS) {
                event.accept(item.get());
            }
        }
    }
}
