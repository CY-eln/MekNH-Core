import c_y.meknh_core.client.structure.StructureViewportBounds;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Random;

/** Headless JOML regression; no Minecraft, OpenGL context, or Gradle needed. */
public final class StructureViewportBoundsProbe {
    private static int checks;
    private static void expect(Matrix4f pose, int x, int y, int w, int h, int l, int t, int r, int b) {
        var actual = StructureViewportBounds.transform(pose, x, y, w, h);
        var expected = new StructureViewportBounds(l, t, r, b);
        if (!actual.equals(expected)) throw new AssertionError(actual + " != " + expected);
        checks++;
    }
    public static void main(String[] args) {
        expect(new Matrix4f(), 8, 40, 304, 130, 8, 40, 312, 170);
        expect(new Matrix4f().translation(120, 70, 400), 0, 31, 176, 96, 120, 101, 296, 197);
        expect(new Matrix4f().translation(10.25f, -2.25f, 0).scale(1.5f, 0.5f, 1),
                0, 31, 176, 96, 10, 13, 275, 62);
        expect(new Matrix4f().translation(120, 70, 0).scale(-2, -0.5f, 1),
                0, 31, 176, 96, -232, 6, 120, 55);
        // Exact quarter turn: opposite-corner-only code would fail for general rotations below.
        expect(new Matrix4f().m00(0).m01(1).m10(-1).m11(0),
                0, 31, 176, 96, -127, 0, -31, 176);
        Random random = new Random(0x5C1550);
        for (int n = 0; n < 10000; n++) {
            Matrix4f pose = new Matrix4f().translation(random.nextFloat()*800-400, random.nextFloat()*600-300, 0)
                    .rotateZ(random.nextFloat()*6.28f).scale(random.nextFloat()*6-3, random.nextFloat()*6-3, 1);
            int x = random.nextInt(100)-50, y = random.nextInt(100)-50;
            int w = random.nextInt(300)+1, h = random.nextInt(200)+1;
            Matrix4f original = new Matrix4f(pose);
            var bounds = StructureViewportBounds.transform(pose, x, y, w, h);
            if (!pose.equals(original)) throw new AssertionError("Mutated caller pose");
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (int cx : new int[]{x, x+w}) for (int cy : new int[]{y, y+h}) {
                Vector3f point = new Vector3f(cx, cy, 0).mulPosition(pose);
                minX = Math.min(minX, point.x); minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y);
            }
            if (bounds.left() != (int)Math.floor(minX) || bounds.top() != (int)Math.floor(minY)
                    || bounds.right() != (int)Math.ceil(maxX) || bounds.bottom() != (int)Math.ceil(maxY))
                throw new AssertionError("Incorrect four-corner bounds: " + bounds);
            checks++;
        }
        System.out.println("StructureViewportBoundsProbe: " + checks + " cases passed");
    }
}
