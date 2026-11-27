package c_y.meknh_core.client.structure;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Screen-space GUI bounds for an affine GUI pose, before framebuffer GUI scaling. */
public record StructureViewportBounds(int left, int top, int right, int bottom) {
    public static StructureViewportBounds transform(Matrix4fc pose, int x, int y, int width, int height) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 4; i++) {
            pose.transformPosition((i & 1) == 0 ? x : x + width,
                    (i & 2) == 0 ? y : y + height, 0, corner);
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
        }
        return new StructureViewportBounds((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }
}
