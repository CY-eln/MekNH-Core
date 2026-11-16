package c_y.meknh_core.common.blast;

/** Shared menu, screen and JEI coordinates (slot coordinates denote item origins). */
public final class PrimitiveBlastFurnaceLayout {
    public static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    // 3x input strip, centered above the progress arrow.
    public static final int INPUT_X = 61, INPUT_Y = 16, SLOT_STEP = 18;
    // 3x output strip, centered below the progress arrow.
    public static final int OUTPUT_X = 61, OUTPUT_Y = 56;
    // Mek ProgressType.DOWN is 11x20.
    public static final int PROGRESS_WIDTH = 11, PROGRESS_HEIGHT = 20;
    public static final int PROGRESS_X = 82, PROGRESS_Y = 35;
    public static final int PLAYER_INVENTORY_Y = 84;
    private PrimitiveBlastFurnaceLayout() { }
}
