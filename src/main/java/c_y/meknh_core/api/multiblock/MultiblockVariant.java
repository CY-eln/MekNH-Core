package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.decor.description.MachineNames;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 一个已注册的多方块控制器版本：定义 + 等级 + 对应的方块 / 物品 / 方块实体类型。
 *
 * @param definition      多方块定义
 * @param level           控制器等级
 * @param block           控制器方块
 * @param item            控制器物品
 * @param blockEntityType 控制器方块实体类型
 */
public record MultiblockVariant(MultiblockDefinition definition, MachineLevel level,
                                DeferredBlock<MultiblockControllerBlock> block,
                                DeferredItem<?> item,
                                DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockControllerBlockEntity>> blockEntityType) {

    /**
     * 注册名，例如 {@code lv_my_multiblock}。
     */
    public ResourceLocation id() {
        return this.block.getId();
    }

    /**
     * 内部路径，例如 {@code lv_my_multiblock}。
     */
    public String path() {
        return MachineNames.internalPath(this.definition.id(), this.level);
    }
}
