package c_y.meknh_core.api.chemical;

import c_y.meknh_core.api.MeknhCoreAPI;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组使用到的化学品注册名。
 *
 * <p>化学品本身不再硬编码在 Java 中，而是由 {@code data/addon/chemicals/*.json}
 * 在注册阶段生成，这里只保留注册名常量与判断辅助方法。</p>
 */
public final class Chemical {

    /**
     * 魔源活化裂变燃料：{@code data/addon/chemicals/magic_activated_fissile_fuel.json}
     */
    public static final ResourceLocation MAGIC_ACTIVATED_FISSILE_FUEL = MeknhCoreAPI.id("magic_activated_fissile_fuel");

    /**
     * 超临界凛冰溶液：{@code data/addon/chemicals/supercritical_cryo_ice_solution.json}
     */
    public static final ResourceLocation SUPERCRITICAL_CRYO_ICE_SOLUTION = MeknhCoreAPI.id("supercritical_cryo_ice_solution");

    private Chemical() {
    }

    public static boolean is(ChemicalStack stack, ResourceLocation id) {
        return ChemicalRegistration.is(stack, id);
    }
}
