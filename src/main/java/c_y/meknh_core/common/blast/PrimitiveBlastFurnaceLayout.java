package c_y.meknh_core.common.blast;

/** Shared menu, screen and JEI coordinates (slot coordinates denote item origins). */
public final class PrimitiveBlastFurnaceLayout {
    public static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;
    public static final int INPUT_X = 52, INPUT_Y = 20, SLOT_STEP = 18;
    public static final int OUTPUT_X = 104, OUTPUT_Y = 38, SLOT_SIZE = 18;
    public static final int PROGRESS_WIDTH = 32, PROGRESS_HEIGHT = 8;
    // Preserve GT's arrow centre (87,46.5), rather than stretching Mek's 32x8 art.
    public static final int PROGRESS_X = 71, PROGRESS_Y = 43;
    public static final int PLAYER_INVENTORY_Y = 84;
    private PrimitiveBlastFurnaceLayout() { }
}
