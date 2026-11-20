package c_y.meknh_core.common.gui.render;

/** Opaque raised-metal edge palette: top, left, bottom, right, from outside inward. */
public final class MetalBevel {
    public static final int DEPTH = 2;
    public static final int TOP = 0, LEFT = 1, BOTTOM = 2, RIGHT = 3;
    private static final int[][] NEUTRAL = {
            {0xFFF3F0E9, 0xFFDCD9D2, 0xFFAAA59C, 0xFFB9B4AA},
            {0xFFC1BDB4, 0xFFD0CCC2, 0xFF59574F, 0xFF69665D}
    };
    private static final int[][] BRONZE = {
            {0xFFE4C58D, 0xFFD1AF70, 0xFFB9985E, 0xFFB9985E},
            {0xFFA88A55, 0xFFB3935E, 0xFF654A28, 0xFF70522E}
    };

    private MetalBevel() { }

    public static int color(MachineBackgroundMaterial material, int layer, int edge) {
        if (material.tiled()) return 0;
        return (material == MachineBackgroundMaterial.STEAM_BRONZE ? BRONZE : NEUTRAL)[layer][edge];
    }
}
