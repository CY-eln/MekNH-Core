package c_y.meknh_core.api.chemical;

import c_y.meknh_core.MekNHCore;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 把 {@code "fluid": true} 的化学品 JSON 注册成一个新的 NeoForge 流体。
 *
 * <p>每个定义会注册：{@code FluidType}、source fluid、flowing fluid，并把属性保存到
 * {@link FluidAttributeRegistry}。不自动创建桶/液体方块，避免为纯数据流体生成大量
 * 无意义的物品与模型；需要放置能力时可在后续扩展 block/bucket。</p>
 */
public final class FluidRegistration {

    private static final Map<ResourceLocation, Entry> FLUIDS = new LinkedHashMap<>();
    private static DeferredRegister<FluidType> types;
    private static DeferredRegister<Fluid> fluids;

    private FluidRegistration() {
    }

    public static synchronized void initialize(IEventBus bus) {
        if (types != null) {
            throw new IllegalStateException("FluidRegistration has already been initialized");
        }
        types = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MekNHCore.MODID);
        fluids = DeferredRegister.create(Registries.FLUID, MekNHCore.MODID);
        types.register(bus);
        fluids.register(bus);
    }

    public static synchronized void register(ChemicalDefinition definition) {
        if (!definition.fluid()) {
            throw new IllegalArgumentException("Definition " + definition.id() + " is not marked as fluid");
        }
        if (!definition.id().getNamespace().equals(MekNHCore.MODID)) {
            throw new IllegalArgumentException("Data-driven fluid id must use namespace " + MekNHCore.MODID
                    + ": " + definition.id());
        }
        if (types == null || fluids == null) {
            throw new IllegalStateException("FluidRegistration has not been initialized");
        }
        if (FLUIDS.containsKey(definition.id())) {
            throw new IllegalStateException("Fluid " + definition.id() + " has already been registered");
        }
        String path = definition.id().getPath();
        DeferredHolder<FluidType, FluidType> type = types.register(path,
                () -> new DataFluidType(FluidType.Properties.create()
                        .descriptionId("fluid_type." + definition.id().getNamespace() + "." + path),
                        definition.texture(), definition.texture(), definition.tint()));
        BaseFlowingFluid.Properties[] properties = new BaseFlowingFluid.Properties[1];
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source = fluids.register(path,
                () -> new BaseFlowingFluid.Source(properties[0]));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing = fluids.register(path + "_flowing",
                () -> new BaseFlowingFluid.Flowing(properties[0]));
        properties[0] = new BaseFlowingFluid.Properties(type, source, flowing);
        FLUIDS.put(definition.id(), new Entry(type, source, flowing, definition));
        FluidAttributeRegistry.register(definition);
    }

    @Nullable
    public static synchronized Entry get(ResourceLocation id) {
        return FLUIDS.get(id);
    }

    public record Entry(DeferredHolder<FluidType, FluidType> type,
                        DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
                        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing,
                        ChemicalDefinition definition) {
    }
}
