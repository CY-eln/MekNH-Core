package c_y.meknh_core.common.gui.layout;

/**
 * 蒸汽机器 GUI 中“两输入槽 - 进度条 - 输出槽”的横向布局。
 *
 * <p>槽位本身 18×18，进度条按 Mek {@code ProgressType.BAR} 为 25×9。
 * 输入/输出分别与进度条之间留 8px，输入槽之间紧贴。整体在 176 宽的
 * 面板内水平居中。</p>
 */
public final class SteamGuiLayout {

    public static final int PANEL_WIDTH = 176;
    public static final int SLOT_SIZE = 18;
    /** 相邻输入槽重叠 1px，避免两个 1px 边框并排变成 2px。 */
    public static final int SLOT_STEP = SLOT_SIZE - 1;
    public static final int SLOT_Y = 37;
    public static final int PROGRESS_WIDTH = 25;
    public static final int PROGRESS_HEIGHT = 9;
    public static final int PROGRESS_Y = 41;
    public static final int GAP = 8;

    private SteamGuiLayout() {
    }

    private static int inputStripWidth(int inputs) {
        return inputs <= 0 ? 0 : SLOT_SIZE + (inputs - 1) * SLOT_STEP;
    }

    public static int contentWidth(int inputs, int outputs) {
        return inputStripWidth(inputs)
                + (inputs > 0 ? GAP : 0)
                + PROGRESS_WIDTH
                + (outputs > 0 ? GAP : 0) + outputs * SLOT_SIZE + Math.max(0, outputs - 1) * GAP;
    }

    public static int startX(int inputs, int outputs) {
        return (PANEL_WIDTH - contentWidth(inputs, outputs)) / 2;
    }

    /**
     * 槽位背景的起始 x；物品槽本身的位置是它 +1。输入槽之间重叠 1px。
     */
    public static int inputX(int inputs, int outputs, int index) {
        return startX(inputs, outputs) + index * SLOT_STEP;
    }

    public static int progressX(int inputs, int outputs) {
        return startX(inputs, outputs) + inputStripWidth(inputs) + (inputs > 0 ? GAP : 0);
    }

    /**
     * 槽位背景的起始 x；物品槽本身的位置是它 +1。
     */
    public static int outputX(int inputs, int outputs, int index) {
        return progressX(inputs, outputs) + PROGRESS_WIDTH + (outputs > 0 ? GAP : 0)
                + index * (SLOT_SIZE + GAP);
    }
}
