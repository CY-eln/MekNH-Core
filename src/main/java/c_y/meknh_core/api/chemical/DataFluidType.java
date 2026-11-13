package c_y.meknh_core.api.chemical;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 数据驱动流体的 FluidType：贴图与颜色来自化学品 JSON。
 */
public final class DataFluidType extends FluidType {

    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;
    private final int tint;

    public DataFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture, int tint) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tint = tint & 0xFFFFFF;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
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
        });
    }
}
