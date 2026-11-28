package c_y.meknh_core.api.multiblock;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.decor.description.MachineDescriptions;
import c_y.meknh_core.decor.description.MachineNames;
import java.util.List;
import java.util.Locale;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 多方块控制器/仓室的物品形式：显示名由「电压 + 类型/ID」拼出来。
 *
 * <p>这样每种仓室不需要写 40 条 {@code block.meknh_core.lv_item_input_hatch} 翻译键，
 * 控制器也可以直接使用数据包提供的 {@code multiblock.<命名空间>.<ID>} 翻译键。</p>
 *
 * <p>提示文本与机器一致：不按 Shift 显示两条操作提示；左 Shift 显示详细
 * （安全信息 / 容量 / 等级 / 结构）；Shift + N 显示词条描述。</p>
 */
public class MultiblockBlockItem extends BlockItem {

    public MultiblockBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Block block = this.getBlock();
        if (block instanceof MultiblockPartBlock part) {
            return part.displayName();
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

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Block block = this.getBlock();
        boolean shift = Screen.hasShiftDown();
        if (shift && isKeyDownN()) {
            tooltip.add(description(block));
            return;
        }
        if (shift) {
            addDetails(block, tooltip);
            return;
        }
        tooltip.add(MekanismLang.HOLD_FOR_DETAILS.translateColored(EnumColor.GRAY, EnumColor.INDIGO,
                Component.literal("左Shift")));
        tooltip.add(MekanismLang.HOLD_FOR_DESCRIPTION.translateColored(EnumColor.GRAY, EnumColor.AQUA,
                Component.literal("Shift + N")));
    }

    private static Component description(Block block) {
        if (block instanceof MultiblockPartBlock part) {
            return Component.translatable("multiblock.meknh_core.part." + part.partType().id() + ".description");
        }
        if (block instanceof MultiblockControllerBlock controller) {
            return Component.translatable("multiblock."
                    + controller.definition().id().getNamespace()
                    + "." + controller.definition().id().getPath() + ".description");
        }
        return Component.empty();
    }

    private static void addDetails(Block block, List<Component> tooltip) {
        tooltip.add(MekanismLang.NO_OWNER.translateColored(EnumColor.RED));
        tooltip.add(MekanismLang.SECURITY.translateColored(EnumColor.DARK_GREEN,
                Component.translatable("security.mekanism.public")));
        if (block instanceof MultiblockPartBlock part) {
            addPartDetails(part, tooltip);
        } else if (block instanceof MultiblockControllerBlock controller) {
            addControllerDetails(controller, tooltip);
        }
    }

    private static void addPartDetails(MultiblockPartBlock part, List<Component> tooltip) {
        MultiblockPartType type = part.partType();
        MachineLevel level = part.level();
        switch (type) {
            case ITEM_INPUT, ITEM_OUTPUT -> tooltip.add(label("multiblock.meknh_core.tooltip.slots",
                    number(MultiblockPartBlockEntity.itemSlots(level))));
            case FLUID_INPUT, FLUID_OUTPUT -> tooltip.add(label("multiblock.meknh_core.tooltip.fluid",
                    number(MultiblockPartBlockEntity.fluidCapacity(level))));
            case STEAM_INPUT -> tooltip.add(label("multiblock.meknh_core.tooltip.steam",
                    number(level.steamCapacity())));
            case ENERGY_INPUT -> {
                tooltip.add(label("multiblock.meknh_core.tooltip.energy",
                        number(MultiblockPartBlockEntity.energyCapacity(level, part.amp()))));
                tooltip.add(label("multiblock.meknh_core.tooltip.amp_in", number(part.amp())));
            }
            case ENERGY_OUTPUT -> {
                tooltip.add(label("multiblock.meknh_core.tooltip.energy",
                        number(MultiblockPartBlockEntity.energyCapacity(level, part.amp()))));
                tooltip.add(label("multiblock.meknh_core.tooltip.amp_out", number(part.amp())));
            }
            case PUMP_HATCH -> tooltip.add(label("multiblock.meknh_core.tooltip.fluid", number(1_000L)));
            case COKE_OVEN_HATCH -> {
                tooltip.add(Component.translatable("multiblock.meknh_core.tooltip.coke_slots")
                        .withStyle(ChatFormatting.GRAY));
                tooltip.add(label("multiblock.meknh_core.tooltip.coke_fluid",
                        number(c_y.meknh_core.common.coke.CokeOvenControllerBlockEntity.TANK_CAPACITY)));
            }
        }
        tooltip.add(Component.translatable("machine.meknh_core.tooltip.tier",
                Component.literal(MachineDescriptions.tierText(level))).withStyle(ChatFormatting.GRAY));
    }

    private static void addControllerDetails(MultiblockControllerBlock controller, List<Component> tooltip) {
        MultiblockDefinition definition = controller.definition();
        MultiblockStructure structure = definition.structure();
        if (definition.noVoltage()) {
            tooltip.add(Component.translatable("multiblock.meknh_core.tooltip.novoltage")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            MachineLevel level = controller.level();
            if (level.isSteam()) {
                tooltip.add(label("multiblock.meknh_core.tooltip.steam", number(level.steamCapacity())));
            } else {
                tooltip.add(label("multiblock.meknh_core.tooltip.energy", number(level.energyCapacity())));
            }
            tooltip.add(Component.translatable("machine.meknh_core.tooltip.tier",
                    Component.literal(MachineDescriptions.tierText(level))).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("multiblock.meknh_core.tooltip.structure",
                structure.sizeX(), structure.sizeY(), structure.sizeZ()).withStyle(ChatFormatting.GRAY));
        if (definition.noVoltage()) {
            tooltip.add(Component.translatable("multiblock.meknh_core.tooltip.recipe_time",
                    definition.time()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("multiblock.meknh_core.tooltip.recipe",
                    definition.time(), shortDouble(definition.energy())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("machine.meknh_core.tooltip.security_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        // 预览手势只在「控制器物品」上出现：仓室物品走 addPartDetails，不会执行到这里。
        tooltip.add(Component.translatable("multiblock.meknh_core.preview_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component label(String key, Object value) {
        return Component.translatable(key, value).withStyle(ChatFormatting.GRAY);
    }


    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String shortDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return Long.toString((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static boolean isKeyDownN() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_N);
    }
}
