package c_y.meknh_core.api.machine;

import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.decor.description.MachineDescriptions;
import c_y.meknh_core.decor.description.MachineNames;
import java.util.List;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.util.text.BooleanStateDisplay.YesNo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * 机器方块对应的物品。
 *
 * <p>显示名按 {@code 批量注册机器.txt} 的规则拼装（「基础合金炉」「低压蒸汽合金炉」…）。</p>
 *
 * <p>说明文本已暂时移除，需要恢复时重新接上 {@code MachineDescriptions#tooltip}。</p>
 */
public class MachineBlockItem extends BlockItem {

    private final MachineDefinition definition;
    private final MachineLevel level;

    public MachineBlockItem(Block block, Properties properties, MachineDefinition definition, MachineLevel level) {
        super(block, properties);
        this.definition = definition;
        this.level = level;
    }

    public MachineDefinition definition() {
        return this.definition;
    }

    public MachineLevel level() {
        return this.level;
    }

    @Override
    public Component getName(ItemStack stack) {
        return MachineNames.displayName(this.definition.id(), this.level);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean shift = Screen.hasShiftDown();
        boolean description = shift && isKeyDownN();
        if (description) {
            tooltip.add(MachineDescriptions.description(this.definition.id()));
            return;
        }
        if (shift) {
            tooltip.add(MekanismLang.NO_OWNER.translateColored(EnumColor.RED));
            tooltip.add(MekanismLang.SECURITY.translateColored(EnumColor.DARK_GREEN,
                    Component.translatable("security.mekanism.public")));
            if (this.level.isSteam()) {
                tooltip.add(Component.translatable("machine.meknh_core.tooltip.steam",
                        String.format(java.util.Locale.ROOT, "%,d", this.level.steamCapacity()))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(MekanismLang.STORED_ENERGY.translateColored(EnumColor.BRIGHT_GREEN, EnumColor.GRAY,
                        Component.literal(formatEnergy(0L) + "/" + formatEnergy(this.level.energyCapacity()))));
            }
            tooltip.add(MekanismLang.HAS_INVENTORY.translateColored(EnumColor.AQUA, EnumColor.GRAY, YesNo.NO));
            tooltip.add(Component.translatable("machine.meknh_core.tooltip.tier",
                    Component.literal(MachineDescriptions.tierText(this.level))).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(MekanismLang.HOLD_FOR_DETAILS.translateColored(EnumColor.GRAY, EnumColor.INDIGO,
                Component.literal("左Shift")));
        tooltip.add(MekanismLang.HOLD_FOR_DESCRIPTION.translateColored(EnumColor.GRAY, EnumColor.AQUA,
                Component.literal("Shift + N")));
    }

    private static boolean isKeyDownN() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_N);
    }

    private static String formatEnergy(long value) {
        if (value >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1f MFE", value / 1_000_000.0D);
        }
        if (value >= 1_000L) {
            return String.format(java.util.Locale.ROOT, "%.1f kFE", value / 1_000.0D);
        }
        return String.format(java.util.Locale.ROOT, "%.1f FE", (double) value);
    }
}
