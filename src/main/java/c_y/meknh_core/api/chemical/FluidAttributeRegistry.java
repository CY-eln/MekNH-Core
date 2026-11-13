package c_y.meknh_core.api.chemical;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 保存通过 {@code "fluid": true} 注册的新流体及其化学品属性。
 *
 * <p>{@code data/addon/chemicals/*.json} 中的 fluid 条目由 {@link FluidRegistration}
 * 注册为 NeoForge 流体，本类负责按流体 id/堆叠查询燃料、放射性、冷却剂等属性。</p>
 */
public final class FluidAttributeRegistry {

    private static final Map<ResourceLocation, ChemicalDefinition> DEFINITIONS = new LinkedHashMap<>();

    private FluidAttributeRegistry() {
    }

    public static synchronized void register(ChemicalDefinition definition) {
        if (!definition.fluid()) {
            throw new IllegalArgumentException("Definition " + definition.id() + " is not marked as fluid");
        }
        ChemicalDefinition previous = DEFINITIONS.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Fluid attributes for " + definition.id() + " have already been registered");
        }
    }

    @Nullable
    public static synchronized ChemicalDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    @Nullable
    public static ChemicalDefinition get(Fluid fluid) {
        return get(BuiltInRegistries.FLUID.getKey(fluid));
    }

    @Nullable
    public static ChemicalDefinition get(FluidStack stack) {
        return stack.isEmpty() ? null : get(stack.getFluid());
    }

    public static synchronized Collection<ChemicalDefinition> getDefinitions() {
        return java.util.List.copyOf(DEFINITIONS.values());
    }

    /**
     * 查询流体的燃料属性；没有配置时返回 {@code null}。
     */
    @Nullable
    public static ChemicalDefinition.FuelData fuel(FluidStack stack) {
        ChemicalDefinition definition = get(stack);
        return definition == null ? null : definition.fuel();
    }

    /**
     * 这个流体是否挂载了任何化学品属性。
     */
    public static boolean hasAttributes(FluidStack stack) {
        ChemicalDefinition definition = get(stack);
        return definition != null && (definition.fuel() != null || definition.radioactivity() != null
                || definition.cooledCoolant() != null || definition.heatedCoolant() != null
                || definition.solid() != null);
    }

    /**
     * 只用于文档/调试：返回某个流体对应的化学品键。
     */
    public static ResourceLocation key(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }
}
