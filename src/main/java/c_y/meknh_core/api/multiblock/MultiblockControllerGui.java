package c_y.meknh_core.api.multiblock;

import net.minecraft.server.level.ServerPlayer;

/**
 * 给某一个多方块控制器挂 GUI。
 *
 * <p>默认控制器没有 GUI（只打印结构状态）；实现了本接口的控制器在右键时会打开自己的菜单，
 * 例如焦炉（输入槽 / 输出槽 / 输出罐）。</p>
 */
public interface MultiblockControllerGui {

    /**
     * 打开控制器 GUI。
     *
     * @return 是否已打开
     */
    boolean openControllerGui(ServerPlayer player);
}
