package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.decor.description.MachineNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 多方块控制器/仓室的物品形式：显示名由「电压 + 类型/ID」拼出来。
 *
 * <p>这样每种仓室不需要写 40 条 {@code block.meknh_core.lv_item_input_hatch} 翻译键，
 * 控制器也可以直接使用数据包提供的 {@code multiblock.<命名空间>.<ID>} 翻译键。</p>
 */
public class MultiblockBlockItem extends BlockItem {

    public MultiblockBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Block block = this.getBlock();
        if (block instanceof MultiblockPartBlock part) {
            var tier = MachineNames.gtTierName(part.level());
            if (part.amp() > 2) {
                tier = Component.translatable("multiblock.meknh_core.amp", part.amp()).append(tier);
            }
            return Component.translatable("multiblock.meknh_core.hatch",
                    tier,
                    Component.translatable("multiblock.meknh_core.part." + part.partType().id()));
        }
        if (block instanceof MultiblockControllerBlock controller) {
            return Component.translatable("multiblock.meknh_core.controller",
                    MachineNames.tierName(controller.level()),
                    Component.translatable("multiblock." + controller.definition().id().getNamespace()
                            + "." + controller.definition().id().getPath()));
        }
        return super.getName(stack);
    }
}
