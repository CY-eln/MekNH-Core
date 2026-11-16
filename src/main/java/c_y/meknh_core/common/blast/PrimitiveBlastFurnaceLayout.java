package c_y.meknh_core.common.blast;

/** Shared menu, screen and JEI coordinates (slot coordinates denote item origins). */
public final class PrimitiveBlastFurnaceLayout {
    public static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    // 3x input column on the left. 17px step overlaps the 18px slot borders by 1px.
    public static final int INPUT_X = 45, INPUT_Y = 16, SLOT_STEP = 17;
    // 3x output strip along the bottom, also overlapping borders by 1px.
    public static final int OUTPUT_X = 63, OUTPUT_Y = 58;
    // Mek ProgressType.DOWN is 11x20.
    public static final int PROGRESS_WIDTH = 11, PROGRESS_HEIGHT = 20;
    public static final int PROGRESS_X = 82, PROGRESS_Y = 32;
    public static final int PLAYER_INVENTORY_Y = 84;
    private PrimitiveBlastFurnaceLayout() { }
}
