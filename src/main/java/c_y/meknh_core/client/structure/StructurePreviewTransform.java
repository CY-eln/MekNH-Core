package c_y.meknh_core.client.structure;

import org.joml.Matrix4f;

/** Shared GUI model transform, exercised by the isolated OpenGL regression probe. */
public final class StructurePreviewTransform {
    private StructurePreviewTransform() {}

    public static Matrix4f apply(Matrix4f matrix, float scale, float pitch, float yaw) {
        // GUI projection already flips Y; reflecting Z again turns outward faces inside-out.
        return matrix.scale(scale, -scale, scale)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw));
    }
}
