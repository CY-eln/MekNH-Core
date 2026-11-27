package c_y.meknh_core.client.structure;

import c_y.meknh_core.api.multiblock.MultiblockMatcher;
import java.util.HashMap;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Unit-cell AABB picking, not pixel-accurate picking of a block's baked model. */
public final class StructurePreviewPicking {
    private StructurePreviewPicking() {}

    public record Selection(int x, int y, int z, BlockState state) {}

    /** Mouse and viewport coordinates must be in the same local GUI coordinate space. */
    @Nullable
    public static BlockState pick(StructurePreview preview, StructureViewState view,
                                  int x, int y, int width, int height, double mouseX, double mouseY) {
        var selection = pickCell(preview, view, x, y, width, height, mouseX, mouseY);
        return selection == null ? null : selection.state();
    }

    /** Returns the hit position as well as its state, so equal-material cells stay distinct. */
    @Nullable
    public static Selection pickCell(StructurePreview preview, StructureViewState view,
                                     int x, int y, int width, int height, double mouseX, double mouseY) {
        if (width <= 0 || height <= 0 || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)
                || mouseX < x || mouseX >= (double) x + width
                || mouseY < y || mouseY >= (double) y + height) return null;
        var cells = preview.structure().cells();
        if (cells.isEmpty()) return null;
        int minX = cells.stream().mapToInt(c -> c.dx()).min().orElse(0);
        int maxX = cells.stream().mapToInt(c -> c.dx()).max().orElse(0);
        int minY = cells.stream().mapToInt(c -> c.dy()).min().orElse(0);
        int maxY = cells.stream().mapToInt(c -> c.dy()).max().orElse(0);
        int minZ = cells.stream().mapToInt(c -> c.dz()).min().orElse(0);
        int maxZ = cells.stream().mapToInt(c -> c.dz()).max().orElse(0);
        float extent = Math.max(maxX-minX+1, Math.max(maxY-minY+1, maxZ-minZ+1));
        float scale = Math.min(width, height) * 0.55F / extent * view.zoom();
        Matrix4f model = new Matrix4f().translation(x + width / 2F, y + height / 2F, 180);
        StructurePreviewTransform.apply(model, scale, view.pitch(), view.yaw());
        model.translate(-(minX+maxX+1)/2F, -(minY+maxY+1)/2F, -(minZ+maxZ+1)/2F);
        var ray = StructurePickingMath.ray(model, mouseX, mouseY,
                StructurePickingMath.front(model, minX, minY, minZ, maxX+1, maxY+1, maxZ+1) + 1);
        double nearest = Double.POSITIVE_INFINITY;
        Selection picked = null;
        var states = new HashMap<MultiblockMatcher, BlockState>();
        for (var cell : cells) {
            if (view.layer() >= 0 && cell.dy() - minY != view.layer()) continue;
            var state = states.computeIfAbsent(cell.matcher(), preview::state);
            if (state.isAir()) continue;
            double distance = rayBox(ray[0], ray[1], cell.dx(), cell.dy(), cell.dz(),
                    cell.dx()+1, cell.dy()+1, cell.dz()+1);
            if (distance < nearest) {
                nearest = distance;
                picked = new Selection(cell.dx(), cell.dy(), cell.dz(), state);
            }
        }
        return picked;
    }

    /** Pure JOML seam: nonnegative ray parameter, or positive infinity for a miss. */
    public static double rayBox(Vector3dc origin, Vector3dc direction,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        return StructurePickingMath.rayBox(origin, direction, minX, minY, minZ, maxX, maxY, maxZ);
    }
}

// BEGIN PURE JOML PICKING MATH (compiled independently by StructurePickingProbe)
final class StructurePickingMath {
    private StructurePickingMath() {}

    static Vector3d[] ray(Matrix4fc model, double mouseX, double mouseY, double frontZ) {
        var inverse = new org.joml.Matrix4d(model).invert();
        // GUI higher Z is nearer: unproject a +Z origin and a -Z direction.
        var origin = inverse.transformPosition(new Vector3d(mouseX, mouseY, frontZ));
        var direction = inverse.transformDirection(new Vector3d(0, 0, -1));
        return new Vector3d[] {origin, direction};
    }

    static double front(Matrix4fc model, double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ) {
        double front = Double.NEGATIVE_INFINITY;
        var matrix = new org.joml.Matrix4d(model);
        for (int i = 0; i < 8; i++) {
            var p = matrix.transformPosition(new Vector3d((i & 1) == 0 ? minX : maxX,
                    (i & 2) == 0 ? minY : maxY, (i & 4) == 0 ? minZ : maxZ));
            front = Math.max(front, p.z);
        }
        return front;
    }

    static double rayBox(Vector3dc origin, Vector3dc direction,
                         double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        double near = 0, far = Double.POSITIVE_INFINITY;
        boolean moving = false;
        for (int axis = 0; axis < 3; axis++) {
            double o = origin.get(axis), d = direction.get(axis);
            double min = axis == 0 ? minX : axis == 1 ? minY : minZ;
            double max = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            if (!Double.isFinite(o) || !Double.isFinite(d)
                    || !Double.isFinite(min) || !Double.isFinite(max) || min > max)
                return Double.POSITIVE_INFINITY;
            if (d == 0) {
                if (o < min || o > max) return Double.POSITIVE_INFINITY;
                continue;
            }
            moving = true;
            double a = (min - o) / d, b = (max - o) / d;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return Double.POSITIVE_INFINITY;
        }
        return moving ? near : Double.POSITIVE_INFINITY;
    }
}
