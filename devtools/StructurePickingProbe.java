import c_y.meknh_core.client.structure.StructurePreviewTransform;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.tools.ToolProvider;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** JDK + JOML only: compiles the exact production math section, without Minecraft stubs. */
public final class StructurePickingProbe {
    private static Method box, ray, front;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args.length == 0
                ? "src/main/java/c_y/meknh_core/client/structure/StructurePreviewPicking.java" : args[0]);
        String text = Files.readString(source);
        String marker = "// BEGIN PURE JOML PICKING MATH";
        int start = text.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing production math marker");
        Path temp = Files.createTempDirectory("structure-picking-probe-");
        try {
            Path math = temp.resolve("StructurePickingMath.java");
            Files.writeString(math, "import org.joml.Matrix4fc; import org.joml.Vector3d; import org.joml.Vector3dc;\n" + text.substring(start));
            int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-classpath", System.getProperty("java.class.path"), "-d", temp.toString(), math.toString());
            if (result != 0) throw new AssertionError("Production math did not compile: " + result);
            try (var loader = new URLClassLoader(new java.net.URL[] {temp.toUri().toURL()},
                    StructurePickingProbe.class.getClassLoader())) {
                Class<?> type = loader.loadClass("StructurePickingMath");
                box = type.getDeclaredMethod("rayBox", Vector3dc.class, Vector3dc.class,
                        double.class, double.class, double.class, double.class, double.class, double.class);
                ray = type.getDeclaredMethod("ray", Matrix4fc.class, double.class, double.class, double.class);
                front = type.getDeclaredMethod("front", Matrix4fc.class, double.class, double.class,
                        double.class, double.class, double.class, double.class);
                box.setAccessible(true); ray.setAccessible(true); front.setAccessible(true);
                basic();
                transformed();
            }
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        System.out.println("StructurePickingProbe PASS: " + checks + " checks (production JOML math; no game/GPU)");
    }

    private static void basic() throws Exception {
        equal(hit(new Vector3d(.5,.5,5), new Vector3d(0,0,-1), 0,0,0), 4, "front face");
        equal(hit(new Vector3d(.5,.5,-5), new Vector3d(0,0,1), 0,0,0), 5, "reverse direction");
        equal(hit(new Vector3d(.5,.5,.5), new Vector3d(1,0,0), 0,0,0), 0, "origin inside");
        equal(hit(new Vector3d(1,.5,5), new Vector3d(0,0,-1), 0,0,0), 4, "parallel boundary");
        miss(hit(new Vector3d(1.01,.5,5), new Vector3d(0,0,-1), 0,0,0), "parallel outside");
        miss(hit(new Vector3d(.5,.5,5), new Vector3d(0,0,1), 0,0,0), "box behind");
        miss(hit(new Vector3d(.5,.5,.5), new Vector3d(), 0,0,0), "zero direction");
        miss(hit(new Vector3d(Double.NaN,0,0), new Vector3d(1,0,0), 0,0,0), "NaN rejected");
        double a = hit(new Vector3d(.5,.5,5), new Vector3d(0,0,-1), 0,0,0);
        double b = hit(new Vector3d(.5,.5,5), new Vector3d(0,0,-1), 0,0,2);
        check(b < a, "GUI higher Z is nearer");
    }

    private static void transformed() throws Exception {
        Random random = new Random(0x5049434bL);
        for (int i = 0; i < 10000; i++) {
            float scale = .25F + random.nextFloat()*250;
            Matrix4f model = new Matrix4f().translation(random.nextFloat()*500, random.nextFloat()*500, 180);
            StructurePreviewTransform.apply(model, scale, -85 + random.nextFloat()*170, random.nextFloat()*720-360);
            model.translate(-3.5F, 1.5F, -2.5F);
            Matrix4f before = new Matrix4f(model);
            // Aim at an interior point: the inverse ray must cross this unit cell at every camera angle.
            Vector3d target = new org.joml.Matrix4d(model).transformPosition(new Vector3d(2.5,-1.5,1.5));
            double frontZ = (double) front.invoke(null, model, 2d,-2d,1d,3d,-1d,2d) + 1;
            Vector3d[] r = (Vector3d[]) ray.invoke(null, model, target.x, target.y, frontZ);
            double t = hit(r[0], r[1], 2,-2,1);
            check(Double.isFinite(t) && t >= 0, "rotated/scaled cell hit " + i);
            Vector3d entry = new Vector3d(r[1]).mul(t).add(r[0]);
            Vector3d projected = new org.joml.Matrix4d(model).transformPosition(entry);
            check(Math.abs(projected.x-target.x) < 1e-5 && Math.abs(projected.y-target.y) < 1e-5,
                    "roundtrip mouse coordinates " + i);
            check(projected.z >= target.z-1e-5, "entry is GUI-nearest surface " + i);
            check(model.equals(before), "matrix not mutated " + i);
        }
    }

    private static double hit(Vector3d o, Vector3d d, double x, double y, double z) throws Exception {
        return (double) box.invoke(null, o,d,x,y,z,x+1,y+1,z+1);
    }
    private static void equal(double actual, double expected, String message) {
        check(Math.abs(actual-expected) < 1e-9, message + ": " + actual);
    }
    private static void miss(double actual, String message) { check(actual == Double.POSITIVE_INFINITY, message); }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
