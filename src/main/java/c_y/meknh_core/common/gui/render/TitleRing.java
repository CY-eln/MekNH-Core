package c_y.meknh_core.common.gui.render;

import java.util.ArrayList;
import java.util.List;

/** Pixel-aligned rounded title outline, with clockwise perimeter motion and no side leads. */
public final class TitleRing {
    private final int[] points;

    public TitleRing(int panelWidth, int titleWidth) {
        int width = Math.min(panelWidth - 12, Math.max(24, titleWidth + 14));
        int left = (panelWidth - width) / 2;
        int right = left + width - 2;
        // Title glyph area y=6..13: a 2px stroke leaves a 2px inner gap on both sides.
        int top = 2, bottom = 16;
        // All corner steps are exact horizontal/vertical or 45-degree pixels, mirrored on both axes.
        // Avoid 2:1 diagonals: their rounded half-pixels differ when the path direction reverses.
        int[][] corners = {{left + 2, top}, {right - 2, top}, {right - 1, top + 1},
                {right, top + 2}, {right, bottom - 2}, {right - 1, bottom - 1},
                {right - 2, bottom}, {left + 2, bottom}, {left + 1, bottom - 1},
                {left, bottom - 2}, {left, top + 2}, {left + 1, top + 1}};
        List<Integer> path = new ArrayList<>();
        for (int segment = 0; segment < corners.length; segment++) {
            int[] a = corners[segment], b = corners[(segment + 1) % corners.length];
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int step = 0; step < steps; step++) {
                int x = a[0] + Math.round((b[0] - a[0]) * step / (float) steps);
                int y = a[1] + Math.round((b[1] - a[1]) * step / (float) steps);
                path.add(x << 16 | y);
            }
        }
        points = path.stream().mapToInt(Integer::intValue).toArray();
    }

    public int size() { return points.length; }
    public int x(int index) { return points[index] >>> 16; }
    public int y(int index) { return points[index] & 0xFFFF; }

    public int color(int index, MachineBackgroundState state, double phase) {
        if (state == MachineBackgroundState.IDLE) return 0xFFFFA160;
        if (state == MachineBackgroundState.UNPOWERED) return 0xFF343434;
        double distance = (phase * size() - index + size()) % size();
        double strength = Math.max(0, 1 - distance / Math.min(32, size() / 3.0));
        int red = (int) Math.round(0x34 + (0x39 - 0x34) * strength);
        int green = (int) Math.round(0x34 + (0xFB - 0x34) * strength);
        int blue = (int) Math.round(0x34 + (0x98 - 0x34) * strength);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
