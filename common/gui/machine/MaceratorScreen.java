package c_y.meknh_core.common.gui.machine;

import c_y.meknh_core.common.gui.menu.MachineMenu;
import c_y.meknh_core.common.gui.screen.MachineScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 研磨机界面。与合金炉唯一的布局差异是输入槽数量（1 个），
 * 通用绘制仍由 {@link MachineScreen} 负责。
 */
public final class MaceratorScreen extends MachineScreen {

    public MaceratorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
