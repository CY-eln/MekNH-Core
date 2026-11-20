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
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** 数据驱动“mek 槽位”管道注册（{@code data/addon/pipe/mek/*.json}）。 */
public final class MekPipeRegistration {

    public static final String ADDON_DATA_DIRECTORY = "addon";
    public static final String PIPE_DIRECTORY = "pipe/mek";

    private static final Map<ResourceLocation, MekPipeDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DeferredBlock<MekPipeBlock>> BLOCKS = new LinkedHashMap<>();
    private static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();

    private static final DeferredRegister.Blocks BLOCK_REGISTER = DeferredRegister.createBlocks(MekNHCore.MODID);
    private static final DeferredRegister.Items ITEM_REGISTER = DeferredRegister.createItems(MekNHCore.MODID);

    private static boolean initialized;

    private MekPipeRegistration() {
    }

    public static synchronized void initialize(IEventBus bus) {
        if (initialized) {
            return;
        }
        initialized = true;
        BLOCK_REGISTER.register(bus);
        ITEM_REGISTER.register(bus);
        bus.addListener(MekPipeRegistration::addCreativeTab);
        bus.addListener(MekPipeRegistration::commonSetup);
        bus.addListener(MekPipeRegistration::registerCapabilities);
    }

    public static synchronized boolean register(MekPipeDefinition definition) {
        if (!initialized) {
            throw new IllegalStateException("MekPipeRegistration has not been initialized");
        }
        if (DEFINITIONS.containsKey(definition.id())) {
            return false;
        }
        DEFINITIONS.put(definition.id(), definition);
        String path = definition.id().getPath();
        DeferredBlock<MekPipeBlock> block = BLOCK_REGISTER.register(path, () -> new MekPipeBlock(definition));
        BLOCKS.put(definition.id(), block);
        DeferredItem<BlockItem> item = ITEM_REGISTER.registerItem(path,
                properties -> new BlockItem(block.get(), properties));
        ITEMS.add(item);
        return true;
    }

    public static List<MekPipeDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                mekanism.common.registries.MekanismTileEntityTypes.BASIC_MECHANICAL_PIPE.get(),
                (blockEntity, side) -> blockEntity instanceof MekPipeChemicalAccess access
                        ? access.meknh_core$chemicalHandler(side) : null);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            c_y.meknh_core.mixin.mek.pipe.BlockEntityTypeAccessor accessor =
                    (c_y.meknh_core.mixin.mek.pipe.BlockEntityTypeAccessor) (Object)
                            mekanism.common.registries.MekanismTileEntityTypes.BASIC_MECHANICAL_PIPE.get();
            java.util.Set<net.minecraft.world.level.block.Block> valid = new java.util.HashSet<>(accessor.meknh_core$validBlocks());
            for (DeferredBlock<MekPipeBlock> block : BLOCKS.values()) {
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
