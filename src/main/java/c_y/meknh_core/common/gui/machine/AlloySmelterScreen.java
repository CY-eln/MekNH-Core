package c_y.meknh_core.common.gui.machine;

import c_y.meknh_core.common.gui.menu.MachineMenu;
import c_y.meknh_core.common.gui.screen.MachineScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 合金炉界面。布局/渲染全部复用 {@link MachineScreen}，仅用于按机器类型选择界面类。
 */
public final class AlloySmelterScreen extends MachineScreen {

    public AlloySmelterScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
