package c_y.meknh_core.api;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.chemical.ChemicalDefinition;
import c_y.meknh_core.api.chemical.ChemicalRegistration;
import java.util.List;
import java.util.Optional;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registration.impl.DeferredChemical;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * MekNH Core 对外 API 的总入口。
 *
 * <p>目前提供两类能力：</p>
 * <ul>
 *     <li>数据驱动化学品：把 JSON 放进 {@code data/addon/chemicals/}，或调用
 *     {@link #registerChemical}，即可注册 Mekanism 化学品，无需再写 Java 注册代码；</li>
 *     <li>只读查询：按注册名获取化学品、判断化学品堆叠。</li>
 * </ul>
 *
 * <p>化学品 JSON 的字段说明见 {@link ChemicalDefinition}。</p>
 */
public final class MeknhCoreAPI {

    /**
     * 本模组的 mod id。
     */
    public static final String MODID = MekNHCore.MODID;

    /**
     * 化学品定义目录（相对于 {@code data/}），每个文件同时包含注册信息与属性。
     */
    public static final String CHEMICAL_DATA_DIRECTORY = ChemicalRegistration.ADDON_DATA_DIRECTORY + "/" + ChemicalRegistration.CHEMICAL_DIRECTORY;

    private MeknhCoreAPI() {
    }

    /**
     * 构造本模组命名空间下的 {@link ResourceLocation}。
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * 在模组构造阶段注册一个化学品。必须在 {@code RegisterEvent} 触发之前调用。
     *
     * @param id      注册名
     * @param builder 化学品构造器（贴图、颜色以及遗留属性）
     * @return 对应的延迟化学品对象
     */
    public static DeferredChemical<Chemical> registerChemical(ResourceLocation id, ChemicalBuilder builder) {
        return ChemicalRegistration.register(id, builder);
    }

    /**
     * 在模组构造阶段按贴图与颜色注册一个化学品。
     *
     * @param id      注册名
     * @param texture 贴图位置，例如 {@code my_mod:liquid/my_chemical}
     * @param tint    RGB 颜色
     * @return 对应的延迟化学品对象
     */
    public static DeferredChemical<Chemical> registerChemical(ResourceLocation id, ResourceLocation texture, int tint) {
        return ChemicalRegistration.register(id, ChemicalBuilder.builder(texture).tint(tint));
    }

    /**
     * 查询挂载在流体上的化学品属性（{@code "fluid": true} 的 JSON）。
     *
     * @return 流体属性定义；没有配置则为 {@code null}
     */
    @Nullable
    public static c_y.meknh_core.api.chemical.ChemicalDefinition getFluidAttributes(ResourceLocation fluidId) {
        return c_y.meknh_core.api.chemical.FluidAttributeRegistry.get(fluidId);
    }

    /**
     * 查询流体的燃料属性；没有配置则为 {@code null}。
     */
    @Nullable
    public static c_y.meknh_core.api.chemical.ChemicalDefinition.FuelData getFluidFuel(
            net.neoforged.neoforge.fluids.FluidStack stack) {
        return c_y.meknh_core.api.chemical.FluidAttributeRegistry.fuel(stack);
    }

    /**
     * 按注册名查询 Mekanism 化学品。
     *
     * @return 化学品；若尚未注册或不存在则为 {@code null}
     */
    @Nullable
    public static Chemical getChemical(ResourceLocation id) {
        return ChemicalRegistration.getChemical(id);
    }

    /**
     * 按注册名查询 Mekanism 化学品（{@link Optional} 形式）。
     */
    public static Optional<Chemical> findChemical(ResourceLocation id) {
        return Optional.ofNullable(ChemicalRegistration.getChemical(id));
    }

    /**
     * 所有通过本 API 注册的化学品。需要在注册表冻结之后调用（例如客户端资源重载时）。
     */
    public static List<Chemical> getChemicals() {
        return ChemicalRegistration.getChemicals();
    }

    /**
     * 判断化学品堆叠是否为指定注册名的化学品。
     */
    public static boolean isChemical(ChemicalStack stack, ResourceLocation id) {
        return ChemicalRegistration.is(stack, id);
    }
}
