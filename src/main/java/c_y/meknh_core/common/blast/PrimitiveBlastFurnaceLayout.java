package c_y.meknh_core.common.blast;

/** Shared menu, screen and JEI coordinates (slot coordinates denote item origins). */
public final class PrimitiveBlastFurnaceLayout {
    public static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    // Left: 3x vertical input column. 17px step overlaps the 18px slot borders by 1px.
    public static final int INPUT_X = 30, INPUT_Y = 21, SLOT_STEP = 17;
    // Right: 3x horizontal output strip, vertically centered against the input column.
    public static final int OUTPUT_X = 96, OUTPUT_Y = 38;
    // Center: Mek ProgressType.RIGHT, 32x8.
    public static final int PROGRESS_WIDTH = 32, PROGRESS_HEIGHT = 8;
    public static final int PROGRESS_X = 55, PROGRESS_Y = 42;
    public static final int PLAYER_INVENTORY_Y = 84;
    private PrimitiveBlastFurnaceLayout() { }
}
