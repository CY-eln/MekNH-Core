package c_y.meknh_core.api.machine;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.decor.description.MachineNames;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 一个已注册的机器版本：定义 + 等级 + 对应的方块 / 物品 / 方块实体类型。
 */
public record MachineVariant(MachineDefinition definition, MachineLevel level,
                             DeferredBlock<MachineBlock> block, DeferredItem<MachineBlockItem> item,
                             DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> blockEntityType) {

    /**
     * 注册名，例如 {@code lv_alloy_smelter}。
     */
    public net.minecraft.resources.ResourceLocation id() {
        return this.block.getId();
    }

    /**
     * 内部 ID 字符串，例如 {@code lv_alloy_smelter}。
     */
    public String path() {
        return MachineNames.internalPath(this.definition.id(), this.level);
    }

    /**
     * 显示名，例如「基础合金炉」。
     */
    public net.minecraft.network.chat.MutableComponent displayName() {
        return MachineNames.displayName(this.definition.id(), this.level);
    }
}
