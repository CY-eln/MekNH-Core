import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Read-only source parity audit. Pass the local GTCEu source checkout as the sole argument. */
public final class PumpTextureAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: PumpTextureAudit <GTCEu checkout>");
        Path source = Path.of(args[0], "src/main/resources/assets/gtceu/textures");
        Path target = Path.of("src/main/resources/assets/meknh_core/textures");
        String[] copies = {"block/casings/pump_deck/top.png", "block/casings/pump_deck/side.png",
            "block/casings/pump_deck/bottom.png", "block/multiblock/primitive_pump/overlay_front.png",
            "block/multiblock/primitive_pump/overlay_front.png.mcmeta", "block/overlay/machine/overlay_pipe.png",
            "block/overlay/machine/overlay_fluid_hatch_output.png", "block/overlay/machine/overlay_pipe_out_emissive.png",
            "block/overlay/machine/overlay_pipe_out_emissive.png.mcmeta"};
        for (String file : copies) {
            if (Files.mismatch(source.resolve(file), target.resolve(file)) != -1) throw new AssertionError("Source mismatch: " + file);
            System.out.println("PASS byte-identical source asset: " + file);
        }
        BufferedImage base = ImageIO.read(source.resolve("block/material_sets/dull/frame_gt.png").toFile());
        BufferedImage secondary = ImageIO.read(source.resolve("block/material_sets/dull/frame_gt_secondary.png").toFile());
        BufferedImage actual = ImageIO.read(target.resolve("block/material_sets/treated_wood/frame_gt.png").toFile());
        if (actual.getWidth() != base.getWidth() || actual.getHeight() != base.getHeight()) throw new AssertionError("Frame dimensions");
        int transparent = 0, translucent = 0, opaque = 0;
        int[] primaryTint = {100, 66, 24}, secondaryTint = {78, 11, 0};
        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++) {
            int p = base.getRGB(x, y), q = secondary.getRGB(x, y), got = actual.getRGB(x, y);
            double ap = (p >>> 24) / 255.0, aq = (q >>> 24) / 255.0, a = aq + ap * (1 - aq);
            int expectedAlpha = (int) Math.round(a * 255), gotAlpha = got >>> 24;
            if (gotAlpha != expectedAlpha) throw new AssertionError("Frame alpha at " + x + "," + y);
            if (gotAlpha == 0) { transparent++; continue; }
            if (gotAlpha == 255) opaque++; else translucent++;
            for (int c = 0; c < 3; c++) {
                int shift = 16 - c * 8;
                double expected = (((q >>> shift) & 255) * secondaryTint[c] / 255.0 * aq
                    + ((p >>> shift) & 255) * primaryTint[c] / 255.0 * ap * (1 - aq)) / a;
                if (Math.abs(((got >>> shift) & 255) - Math.floor(expected)) > 1)
                    throw new AssertionError("Frame color at " + x + "," + y + " channel " + c);
            }
        }
        if (transparent == 0 || opaque == 0) throw new AssertionError("Frame lost transparent holes or solid edges");
        if (translucent != 0) throw new AssertionError("Frame now has partial alpha; re-evaluate the cutout render layer");
        System.out.println("PASS treated wood base+secondary tint composite; alpha counts transparent=" + transparent
            + " translucent=" + translucent + " opaque=" + opaque);
    }
}
