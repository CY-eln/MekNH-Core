import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Standalone JDK21 regression checks; writes the build preview and regenerates material assets. */
public final class MachineBackgroundMaterialsValidation {
    static final Path GUI = Path.of("src/main/resources/assets/meknh_core/textures/gui");
    static final String[] NAMES = {"steam_high_background.png", "steam_bronze_background.png",
            "primitive_brick_background.png", "coke_brick_background.png"};
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static int c(int rgb, int shift) { return rgb >> shift & 255; }
    static double mean(BufferedImage image, int x0, int y0, int w, int h) {
        double sum = 0;
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) {
            int p = image.getRGB(x, y);
            sum += (c(p,16) + c(p,8) + c(p,0)) / 3.0;
        }
        return sum / (w * h);
    }
    public static void main(String[] args) throws Exception {
        byte[] legacy = Files.readAllBytes(GUI.resolve("metal_background.png"));
        // Reproduce the original pre-extension algorithm in memory, without touching the asset.
        BufferedImage original = new BufferedImage(176,177,BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 177; y++) for (int x = 0; x < 176; x++) {
            double diagonal = (x / 175.0 + y / 176.0) / 2;
            double sheen = 17 * Math.exp(-Math.pow((diagonal - .35) / .11, 2));
            double grain = ((y * 37 % 11) - 5) * .38 + ((x * 13 + y * 7) % 5 - 2) * .22;
            int v = (int)Math.round(210 - 117 * diagonal + sheen + grain);
            original.setRGB(x,y,0xff000000 | Math.clamp(v,0,255) << 16
                    | Math.clamp(v+3,0,255) << 8 | Math.clamp(v+6,0,255));
        }
        Graphics2D border = original.createGraphics();
        border.setColor(new Color(0x30353B)); border.drawRect(0,0,175,176);
        border.setColor(new Color(0xE0E4E7)); border.drawLine(1,1,174,1); border.drawLine(1,1,1,175);
        border.setColor(new Color(0x4B5157)); border.drawLine(1,175,174,175); border.drawLine(174,1,174,175);
        border.dispose();
        var encoded = new java.io.ByteArrayOutputStream();
        ImageIO.write(original,"png",encoded);
        check(Arrays.equals(legacy,encoded.toByteArray()), "original legacy algorithm no longer reproduces asset bytes");
        System.out.println("PASS original legacy algorithm reproduces existing PNG byte-for-byte");
        byte[][] first = new byte[4][];
        BufferedImage preview = new BufferedImage(176 * 2 * 4, 177 * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preview.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int i = 0; i < NAMES.length; i++) {
            first[i] = Files.readAllBytes(GUI.resolve(NAMES[i]));
            BufferedImage image = ImageIO.read(GUI.resolve(NAMES[i]).toFile());
            check(image.getWidth() == 176 && image.getHeight() == 177, "dimensions " + NAMES[i]);
            for (int y = 0; y < 177; y++) for (int x = 0; x < 176; x++)
                check(image.getRGB(x,y) >>> 24 == 255, "opacity " + NAMES[i]);
            if (i < 2) {
                int start = i == 0 ? 0x4a4641 : 0x6f5525, end = i == 0 ? 0x807b74 : 0xae844d;
                check((image.getRGB(0,0) & 0xffffff) == start, "start endpoint");
                check((image.getRGB(175,176) & 0xffffff) == end, "end endpoint");
                double maxError = 0;
                for (int y = 0; y < 177; y++) for (int x = 0; x < 176; x++) {
                    double t = (x / 175.0 + y / 176.0) / 2;
                    for (int shift : new int[]{16,8,0}) {
                        double expected = c(start,shift) + (c(end,shift) - c(start,shift)) * t;
                        maxError = Math.max(maxError, Math.abs(c(image.getRGB(x,y),shift) - expected));
                    }
                }
                check(maxError > 25 && maxError < 55, "visible but controlled metal reflection");
                double grainContrast = 0;
                for (int y = 20; y < 155; y++) {
                    grainContrast += Math.abs(c(image.getRGB(88,y),8)
                            - 2*c(image.getRGB(88,y+1),8) + c(image.getRGB(88,y+2),8));
                }
                check(grainContrast / 135 > 3, "visible native-pixel brushed grain");
                if (i == 0) check(mean(image,0,0,176,177) > 85, "high-pressure metal must no longer be too dark");
                check(mean(image,160,0,16,16) > mean(image,0,0,16,16), "horizontal trend");
                check(mean(image,0,161,16,16) > mean(image,0,0,16,16), "vertical trend");
                System.out.printf("PASS %s: exact #%06x -> #%06x; maximum channel deviation %.3f%n", NAMES[i], start,end,maxError);
            } else {
                String tileName = i == 2 ? "machine_primitive_bricks.png" : "machine_coke_bricks.png";
                BufferedImage tile = ImageIO.read(Path.of("src/main/resources/assets/meknh_core/textures/block/casings/solid",tileName).toFile());
                check(tile.getWidth() == 16 && tile.getHeight() == 16, "native tile dimensions");
                for (int y = 0; y < 177; y++) for (int x = 0; x < 176; x++) {
                    for (int shift : new int[]{16,8,0}) {
                        int expected = Math.clamp((int)Math.round(c(tile.getRGB(x%16,y%16),shift) * (1.08 - .24*y/176)),0,255);
                        check(c(image.getRGB(x,y),shift) == expected, "native tiled pattern / pure vertical shading");
                    }
                    if (x >= 16) check(image.getRGB(x,y) == image.getRGB(x-16,y), "horizontal tile period");
                    if (y >= 16) for (int shift : new int[]{16,8,0})
                        check(c(image.getRGB(x,y),shift) <= c(image.getRGB(x,y-16),shift), "same-phase vertical darkening");
                }
                double top = mean(image,0,0,176,16), bottom = mean(image,0,160,176,16);
                check(top > bottom, "top brighter than bottom");
                System.out.printf("PASS %s: native 16x16 exact tiling, vertical-only exposure; mean top %.2f > bottom %.2f%n",NAMES[i],top,bottom);
            }
            g.drawImage(image,i*352,0,352,354,null);
            System.out.println("  176x177, every pixel opaque");
        }
        g.dispose();
        Files.createDirectories(Path.of("build"));
        ImageIO.write(preview,"png",Path.of("build/machine-background-materials.png").toFile());
        MetalBackgroundGenerator.main(new String[0]);
        check(Arrays.equals(legacy,Files.readAllBytes(GUI.resolve("metal_background.png"))), "legacy bytes changed");
        for (int i = 0; i < 4; i++) check(Arrays.equals(first[i],Files.readAllBytes(GUI.resolve(NAMES[i]))), "non-deterministic regeneration");
        System.out.println("PASS deterministic regeneration; existing legacy byte preservation");
        System.out.println("Legacy SHA-256: " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(legacy)));
        System.out.println("Preview: build/machine-background-materials.png (1408x354, 2x nearest neighbor, no slots)");
    }
}
