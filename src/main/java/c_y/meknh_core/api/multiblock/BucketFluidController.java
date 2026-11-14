package c_y.meknh_core.api.multiblock;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * 支持用桶右键取放流体的多方块控制器（例如焦炉取杂酚油）。
 *
 * <p>返回的 handler 只用来做桶交互，不会注册成方块能力。</p>
 */
public interface BucketFluidController {

    /**
     * 桶交互用的流体视图（一般只允许抽取）。
     */
    IFluidHandler bucketFluidHandler();
}
