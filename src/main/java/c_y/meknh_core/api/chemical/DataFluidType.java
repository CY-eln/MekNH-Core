package c_y.meknh_core.api.chemical;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 数据驱动流体的 FluidType：贴图与颜色来自化学品 JSON。
 */
public final class DataFluidType extends FluidType {

    /**
     * 已创建的数据流体类型；客户端在 {@code RegisterClientExtensionsEvent} 里统一注册贴图。
     */
    private static final List<DataFluidType> INSTANCES = new CopyOnWriteArrayList<>();

    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;
    private final int tint;

    public DataFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture, int tint) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tint = tint & 0xFFFFFF;
        INSTANCES.add(this);
    }

    public static List<DataFluidType> instances() {
        return INSTANCES;
    }

    /**
     * 客户端贴图扩展；由 {@code ClientEvents} 注册到 NeoForge。
     */
    public IClientFluidTypeExtensions clientExtensions() {
        return new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return DataFluidType.this.stillTexture;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return DataFluidType.this.flowingTexture;
            }

            @Override
            public int getTintColor() {
                return 0xFF000000 | DataFluidType.this.tint;
            }
        };
    }
}
