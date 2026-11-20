package c_y.meknh_core.common.gui.render;

/** Opaque raised-metal edge palette: top, left, bottom, right, from outside inward. */
public final class MetalBevel {
    public static final int DEPTH = 3;
    public static final int TOP = 0, LEFT = 1, BOTTOM = 2, RIGHT = 3;
    // Minecraft-style raised panel: dark outer keyline, lit bevel, then an inner shadow lip.
    private static final int[][] NEUTRAL = {
            {0xFF242320, 0xFF242320, 0xFF242320, 0xFF242320},
            {0xFFF7F4ED, 0xFFE5E2DA, 0xFF37352F, 0xFF37352F},
            {0xFFC9C5BC, 0xFFC9C5BC, 0xFF656157, 0xFF656157}
    };
    private static final int[][] BRONZE = {
            {0xFF2A2013, 0xFF2A2013, 0xFF2A2013, 0xFF2A2013},
            {0xFFF3D69D, 0xFFE3C287, 0xFF49331B, 0xFF49331B},
            {0xFFC8A56A, 0xFFC8A56A, 0xFF775631, 0xFF775631}
    };

    private MetalBevel() { }

    public static int color(MachineBackgroundMaterial material, int layer, int edge) {
        if (material.tiled()) return 0;
        return (material == MachineBackgroundMaterial.STEAM_BRONZE ? BRONZE : NEUTRAL)[layer][edge];
    }
}
