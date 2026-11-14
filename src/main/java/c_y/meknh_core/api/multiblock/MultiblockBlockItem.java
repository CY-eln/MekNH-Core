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
            // 蒸汽输入仓 / 焦炉仓不带电压前缀，直接用专有名称。
            if (part.partType() == MultiblockPartType.STEAM_INPUT
                    || part.partType() == MultiblockPartType.COKE_OVEN_HATCH) {
                return Component.translatable("multiblock.meknh_core.part." + part.partType().id());
            }
            var tier = MachineNames.gtTierName(part.level());
            if (part.amp() > 2) {
                tier = Component.translatable("multiblock.meknh_core.amp", part.amp()).append(tier);
            }
            return Component.translatable("multiblock.meknh_core.hatch",
                    tier,
                    Component.translatable("multiblock.meknh_core.part." + part.partType().id()));
        }
        if (block instanceof MultiblockControllerBlock controller) {
            Component name = Component.translatable("multiblock."
                    + controller.definition().id().getNamespace()
                    + "." + controller.definition().id().getPath());
            if (controller.definition().noVoltage()) {
                // 无电压多方块（蒸汽/原始机器）直接用本名，不加等级前缀。
                return name;
            }
            return Component.translatable("multiblock.meknh_core.controller",
                    MachineNames.tierName(controller.level()), name);
        }
        return super.getName(stack);
    }
}
