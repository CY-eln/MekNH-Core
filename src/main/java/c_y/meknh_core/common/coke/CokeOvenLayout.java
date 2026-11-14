package c_y.meknh_core.common.coke;

/**
 * 焦炉 GUI 的横向布局：输入槽 - 进度条 - 输出槽，整体在 176 宽面板里水平居中。
 *
 * <p>间距与 {@code SteamGuiLayout} 一致（{@code GAP = 8}），进度条用 Mek 冶金灌注机的
 * {@code gui/progress/right.png}（32×8，三行纹理：底图 / 填充 / 警告）。</p>
 */
public final class CokeOvenLayout {

    public static final int PANEL_WIDTH = 176;
    public static final int SLOT_SIZE = 18;

    /**
     * 槽位与进度条之间的固定间距，和本模组蒸汽机器布局一致。
     */
    public static final int GAP = 8;

    public static final int PROGRESS_WIDTH = 32;
    public static final int PROGRESS_HEIGHT = 8;

    /**
     * 输入槽背景 → 进度条 → 输出槽背景 的总宽度。
     */
    public static final int CONTENT_WIDTH = SLOT_SIZE + GAP + PROGRESS_WIDTH + GAP + SLOT_SIZE;

    public static final int START_X = (PANEL_WIDTH - CONTENT_WIDTH) / 2;
    public static final int SLOT_Y = 35;
    public static final int SLOT_BG_Y = SLOT_Y - 1;

    public static final int INPUT_BG_X = START_X;
    public static final int INPUT_X = INPUT_BG_X + 1;

    public static final int PROGRESS_X = START_X + SLOT_SIZE + GAP;
    public static final int PROGRESS_Y = SLOT_BG_Y + (SLOT_SIZE - PROGRESS_HEIGHT) / 2;

    public static final int OUTPUT_BG_X = PROGRESS_X + PROGRESS_WIDTH + GAP;
    public static final int OUTPUT_X = OUTPUT_BG_X + 1;

    private CokeOvenLayout() {
    }
}
