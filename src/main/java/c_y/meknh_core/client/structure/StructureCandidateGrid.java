package c_y.meknh_core.client.structure;

/** Integer game-pixel geometry: one border, two clear pixels, 16px icons and 2px gaps. */
public final class StructureCandidateGrid {
    private StructureCandidateGrid() {}
    public static int columns(int width) { return Math.max(1, (width - 4) / 18); }
    public static int width(int columns) { return columns * 18 + 4; }
    public static int height(int count, int columns) { return 4 + ((count + columns - 1) / columns) * 18; }
    public static int x(int index, int columns) { return 3 + index % columns * 18; }
    public static int y(int index, int columns) { return 3 + index / columns * 18; }
}
