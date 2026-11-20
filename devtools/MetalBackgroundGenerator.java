import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** JDK-only generator for the decorative machine background; no game/layout dependencies. */
public final class MetalBackgroundGenerator {
    private static final int W = 176, H = 177;

    public static void main(String[] args) throws Exception {
        Path output = Path.of("src/main/resources/assets/meknh_core/textures/gui");
        Files.createDirectories(output);
        BufferedImage base = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double diagonal = (x / (double)(W - 1) + y / (double)(H - 1)) / 2;
                double sheen = 17 * Math.exp(-Math.pow((diagonal - .35) / .11, 2));
                // Deterministic, subtle horizontal brushed-metal grain.
                double grain = ((y * 37 % 11) - 5) * .38 + ((x * 13 + y * 7) % 5 - 2) * .22;
                int v = (int)Math.round(210 - 117 * diagonal + sheen + grain);
                int r = Math.clamp(v, 0, 255), gr = Math.clamp(v + 3, 0, 255), b = Math.clamp(v + 6, 0, 255);
                base.setRGB(x, y, 0xFF000000 | r << 16 | gr << 8 | b);
            }
        }
        Graphics2D g = base.createGraphics();
        g.setColor(new Color(0x30353B)); g.drawRect(0, 0, W - 1, H - 1);
        g.setColor(new Color(0xE0E4E7)); g.drawLine(1, 1, W - 2, 1); g.drawLine(1, 1, 1, H - 2);
        g.setColor(new Color(0x4B5157)); g.drawLine(1, H - 2, W - 2, H - 2); g.drawLine(W - 2, 1, W - 2, H - 2);
        g.dispose();
        // Never rewrite an existing legacy asset; the original deterministic rendering above is unchanged.
        Path legacy = output.resolve("metal_background.png");
        if (!Files.exists(legacy)) ImageIO.write(base, "png", legacy.toFile());
        write(output, "steam_high_background.png", brushed(0x4a4641, 0x807b74));
        write(output, "steam_bronze_background.png", brushed(0x6f5525, 0xae844d));
        Path casings = Path.of("src/main/resources/assets/meknh_core/textures/block/casings/solid");
        write(output, "primitive_brick_background.png", bricks(casings.resolve("machine_primitive_bricks.png")));
        write(output, "coke_brick_background.png", bricks(casings.resolve("machine_coke_bricks.png")));
        System.out.println("Generated four material backgrounds; preserved existing legacy metal background.");
    }

    private static void write(Path output, String name, BufferedImage image) throws Exception {
        if (!ImageIO.write(image, "png", output.resolve(name).toFile()))
            throw new IllegalStateException("PNG writer unavailable");
    }

    private static BufferedImage brushed(int start, int end) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double diagonal = (x / (double)(W - 1) + y / (double)(H - 1)) / 2;
                // Long horizontal brush marks plus broad/narrow diagonal reflections distinguish
                // metal from a flat color ramp, even at Minecraft's native GUI resolution.
                double grain = ((y * 37 % 17) - 8) * .32
                        + Math.sin(y * 2.4 + x * .025) * 1.5
                        + Math.sin(y * .57 + x * .009) * .8
                        + ((x * 13 + y * 7) % 5 - 2) * .18;
                double sheen = 38 * Math.exp(-Math.pow((diagonal - .34) / .10, 2))
                        + 12 * Math.exp(-Math.pow((diagonal - .40) / .028, 2))
                        - 10 * Math.exp(-Math.pow((diagonal - .61) / .10, 2));
                double perturbation = (grain + sheen) * Math.sin(Math.PI * diagonal);
                int rgb = 0xff000000;
                for (int shift : new int[]{16, 8, 0}) {
                    double a = start >> shift & 255, b = end >> shift & 255;
                    int channel = Math.clamp((int)Math.round(a + (b - a) * diagonal + perturbation), 0, 255);
                    rgb |= channel << shift;
                }
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static BufferedImage bricks(Path source) throws Exception {
        BufferedImage tile = ImageIO.read(source.toFile());
        if (tile == null || tile.getWidth() != 16 || tile.getHeight() != 16)
            throw new IllegalArgumentException("Expected original 16x16 brick tile: " + source);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++) {
            // Pure vertical exposure: preserve the source pattern, with no stretching or overlays.
            double exposure = 1.08 - .24 * y / (H - 1.0);
            for (int x = 0; x < W; x++) {
                int sourceRgb = tile.getRGB(x % tile.getWidth(), y % tile.getHeight());
                int rgb = 0xff000000;
                for (int shift : new int[]{16, 8, 0}) {
                    int channel = Math.clamp((int)Math.round((sourceRgb >> shift & 255) * exposure), 0, 255);
                    rgb |= channel << shift;
                }
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
}
