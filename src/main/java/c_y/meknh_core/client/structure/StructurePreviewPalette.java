package c_y.meknh_core.client.structure;

/** Explicit foreground/background pairs; independent of LDLib theme defaults. */
public final class StructurePreviewPalette {
    private StructurePreviewPalette() {}
    public static final int BODY_TEXT = 0xFF303030;
    public static final int PANEL = 0xFFD8D8D8;
    public static final int CONTROL_TEXT = 0xFFFFFFFF;
    public static final int BUTTON = 0xFF34313D;
    public static final int BUTTON_HOVER = 0xFF514B60;
    public static final int BUTTON_PRESSED = 0xFF24212B;
    /** 50% cyan hover tint (ARGB); shared by material cards and candidate icons. */
    public static final int ITEM_HOVER = 0x809EF1FF;
    public static final int LIST_ROW = 0xFF1A2231;
    public static final int LIST_BORDER = 0xFF029B94;
    public static final int CANDIDATE_REGION = 0xFFBAD6CD;
}
