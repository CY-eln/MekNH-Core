package c_y.meknh_core.common.gui.render;

/** Cosmetic texture choices, independent of menu, recipe and power state. */
public enum MachineBackgroundMaterial {
    METAL("metal_background", false),
    STEAM_HIGH("steam_high_background", false),
    STEAM_BRONZE("steam_bronze_background", false),
    PRIMITIVE_BRICK("primitive_brick_background", true),
    COKE_BRICK("coke_brick_background", true);

    private final String texture;
    private final boolean tiled;

    MachineBackgroundMaterial(String texture, boolean tiled) {
        this.texture = "textures/gui/" + texture + ".png";
        this.tiled = tiled;
    }

    public String texture() { return texture; }
    public boolean tiled() { return tiled; }

    public static MachineBackgroundMaterial forSteam(boolean steam, boolean highPressure) {
        return !steam ? METAL : highPressure ? STEAM_HIGH : STEAM_BRONZE;
    }
}
