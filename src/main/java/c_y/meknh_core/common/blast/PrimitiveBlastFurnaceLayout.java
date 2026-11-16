package c_y.meknh_core.common.blast;

/** Shared menu, screen and JEI coordinates (slot coordinates denote item origins). */
public final class PrimitiveBlastFurnaceLayout {
    public static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    // Left: 3x vertical input column. 17px step overlaps the 18px slot borders by 1px.
    public static final int INPUT_X = 41, INPUT_Y = 21, SLOT_STEP = 17;
    // Right: 3x horizontal output strip, vertically centered against the input column.
    public static final int OUTPUT_X = 86, OUTPUT_Y = 38;
    // Center: Mek ProgressType.DOWN, 11x20.
    public static final int PROGRESS_WIDTH = 11, PROGRESS_HEIGHT = 20;
    public static final int PROGRESS_X = 66, PROGRESS_Y = 36;
    public static final int PLAYER_INVENTORY_Y = 84;
    private PrimitiveBlastFurnaceLayout() { }
}
