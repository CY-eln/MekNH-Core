package c_y.meknh_core.common.blast;

/** Shared menu, screen and JEI coordinates (slot coordinates denote item origins). */
public final class PrimitiveBlastFurnaceLayout {
    public static final int PANEL_WIDTH = 176, PANEL_HEIGHT = 166;

    private static final int SLOT_SIZE = 18;
    private static final int GAP = 8;
    public static final int SLOT_STEP = SLOT_SIZE - 1;
    private static final int INPUT_HEIGHT = SLOT_SIZE + 2 * SLOT_STEP;
    private static final int OUTPUT_WIDTH = SLOT_SIZE + 2 * SLOT_STEP;
    private static final int CONTENT_WIDTH = SLOT_SIZE + GAP + 32 + GAP + OUTPUT_WIDTH;
    private static final int CONTENT_X = (PANEL_WIDTH - CONTENT_WIDTH) / 2;
    private static final int CONTENT_CENTER_Y = 45;

    public static final int INPUT_X = CONTENT_X + 1;
    public static final int INPUT_Y = CONTENT_CENTER_Y - INPUT_HEIGHT / 2 + 1;
    public static final int OUTPUT_X = CONTENT_X + SLOT_SIZE + GAP + 32 + GAP + 1;
    public static final int OUTPUT_Y = CONTENT_CENTER_Y - SLOT_SIZE / 2 + 1;
    public static final int PROGRESS_WIDTH = 32, PROGRESS_HEIGHT = 8;
    public static final int PROGRESS_X = CONTENT_X + SLOT_SIZE + GAP;
    public static final int PROGRESS_Y = CONTENT_CENTER_Y - PROGRESS_HEIGHT / 2;
    public static final int PLAYER_INVENTORY_Y = 84;

    private PrimitiveBlastFurnaceLayout() { }
}
