package c_y.meknh_core.client.structure;

/** First playable prototype: can existing structures be understood and tried in JEI? */
public final class StructureViewState {
    private float yaw, pitch, zoom;
    private int layer;
    public StructureViewState() { reset(); }
    public void reset() { yaw = 135; pitch = 25; zoom = 1; layer = -1; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float zoom() { return zoom; }
    public int layer() { return layer; }
    public void rotate(double x, double y) {
        yaw = (yaw + (float)x * 0.6F) % 360;
        pitch = Math.clamp(pitch + (float)y * 0.6F, -85, 85);
    }
    public void zoom(double delta) { zoom = Math.clamp(zoom * (float)Math.pow(1.12, delta), 0.25F, 4F); }
    public void cycleLayer(int height) { layer = stepLayer(layer, height, 1); }
    public static int stepLayer(int current, int height, int direction) {
        return height <= 0 ? -1 : Math.floorMod(current + 1 + direction, height + 1) - 1;
    }
}
