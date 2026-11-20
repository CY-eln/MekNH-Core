package c_y.meknh_core.common.gui.render;

/** Decorative 25x9 segmented arrow; no recipe or machine-state ownership. */
public final class ChargingArrow {
    public static final int WIDTH = 25, HEIGHT = 9;
    private static final int OUTLINE = 0xFF33424E;
    private static final int EMPTY = 0xFF25323C;
    private static final int FILL_START = 0xFF3C8DB8;
    private static final int FILL_END = 0xFFB9EBFF;

    private ChargingArrow() { }

    private static boolean contains(int x, int y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return false;
        return x < 17 ? y >= 2 && y <= 6 : Math.abs(y - 4) <= (25 - x) / 2;
    }

    public static int color(int x, int y, boolean active, float ratio) {
        if (!contains(x, y)) return 0;
        if (!contains(x - 1, y) || !contains(x + 1, y)
                || !contains(x, y - 1) || !contains(x, y + 1)) return OUTLINE;
        // The original upstream bar hides its fill while the machine is inactive.
        int filled = active ? Math.round(WIDTH * Math.max(0, Math.min(1, ratio))) : 0;
        if (x == 5 || x == 10 || x == 15) return OUTLINE;
        return x < filled ? gradient(x) : EMPTY;
    }

    private static int gradient(int x) {
        // Anchor the gradient to the full arrow, not the current fill width, so it never jumps
        // when the upstream progress advances by one pixel.
        float t = x / (float) (WIDTH - 1);
        int red = Math.round((FILL_START >> 16 & 255) * (1 - t) + (FILL_END >> 16 & 255) * t);
        int green = Math.round((FILL_START >> 8 & 255) * (1 - t) + (FILL_END >> 8 & 255) * t);
        int blue = Math.round((FILL_START & 255) * (1 - t) + (FILL_END & 255) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
