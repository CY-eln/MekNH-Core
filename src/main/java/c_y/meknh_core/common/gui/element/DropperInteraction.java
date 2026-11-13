package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.network.DropperUsePayload;
import mekanism.common.item.ItemGaugeDropper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 滴管与 GUI 非物品槽位交互的统一入口。
 */
public final class DropperInteraction {

    private DropperInteraction() {
    }

    public static boolean isDropper() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && isDropper(minecraft.player.containerMenu.getCarried());
    }

    public static boolean isDropper(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemGaugeDropper;
    }

    /**
     * @return true 表示这次点击已经被滴管消费。
     */
    public static boolean click(BlockPos pos, int tankType, int tankId, int mouseButton, boolean shift) {
        if (pos == null || !isDropper()) {
            return false;
        }
        int action;
        if (shift) {
            action = DropperUsePayload.ACTION_DUMP;
        } else if (mouseButton == 0) {
            action = DropperUsePayload.ACTION_FILL;
        } else if (mouseButton == 1) {
            action = DropperUsePayload.ACTION_DRAIN;
        } else {
            return false;
        }
        PacketDistributor.sendToServer(new DropperUsePayload(pos, action, tankType, tankId));
        return true;
    }
}
