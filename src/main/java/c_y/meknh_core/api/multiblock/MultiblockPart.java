package c_y.meknh_core.api.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块仓室的公共契约。
 *
 * <p>控制器在结构成型时调用 {@link #linkController(BlockPos)}，失效时调用
 * {@link #unlinkController(BlockPos)}。一个仓室同一时间只能属于一个控制器：
 * 结构匹配阶段会检查 {@link #controllerPos()}，已被占用的仓室会让匹配失败。</p>
 *
 * <p>自定义仓室只需要实现本接口并注册自己的方块实体；控制器通过
 * {@link MultiblockPartBlockEntity} 提供的标准容器读写资源，因此内核
 * 只认这一套实现。若需要完全自定义的资源逻辑，可以在自己的方块实体里
 * 实现 {@code MultiblockPart} 并让控制器通过 {@code MachineOutput}
 * 的扩展点接入（见 {@code MULTIBLOCK_JSON.md} 的“扩展”一节）。</p>
 */
public interface MultiblockPart {

    /**
     * 仓室功能类型。
     */
    MultiblockPartType partType();

    /**
     * 当前所属控制器坐标；未接入任何控制器时为 {@code null}。
     */
    @Nullable
    BlockPos controllerPos();

    /**
     * 接入控制器。
     */
    void linkController(BlockPos controllerPos);

    /**
     * 断开指定控制器；不是当前控制器时忽略。
     */
    void unlinkController(BlockPos controllerPos);

    /**
     * 仓室自身的方块实体。
     */
    BlockEntity self();
}
