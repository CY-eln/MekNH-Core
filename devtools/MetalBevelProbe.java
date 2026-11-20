import c_y.meknh_core.common.gui.render.MachineBackgroundMaterial;
import c_y.meknh_core.common.gui.render.MetalBevel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class MetalBevelProbe {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static int brightness(int color) {
        return (color >> 16 & 255) + (color >> 8 & 255) + (color & 255);
    }
    public static void main(String[] args) throws Exception {
        var preview = new BufferedImage(1056,354,BufferedImage.TYPE_INT_RGB);
        var out = preview.createGraphics();
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int column = 0;
        for (var material : MachineBackgroundMaterial.values()) {
            if (material.tiled()) {
                check(MetalBevel.color(material,0,MetalBevel.TOP) == 0, "no bevel on bricks");
                continue;
            }
            var image = ImageIO.read(Path.of("src/main/resources/assets/meknh_core", material.texture()).toFile());
            int center = image.getRGB(88,88);
            var graphics = image.createGraphics();
            for (int layer=0; layer<MetalBevel.DEPTH; layer++) {
                int l=layer, t=layer, r=176-layer, b=177-layer;
                int[] colors = new int[4];
                for (int edge=0;edge<4;edge++) {
                    colors[edge]=MetalBevel.color(material,layer,edge);
                    check(colors[edge]>>>24==255,"fully opaque bevel");
                }
                if (layer == 0) {
                    check(colors[0] == colors[1] && colors[1] == colors[2] && colors[2] == colors[3], "continuous dark outer outline");
                } else {
                    check(brightness(colors[0])-brightness(colors[2])>200, "strong top highlight / bottom shadow contrast");
                    check(brightness(colors[1])-brightness(colors[3])>200, "strong left highlight / right shadow contrast");
                }
                graphics.setColor(new Color(colors[0],true)); graphics.fillRect(l,t,r-l,1);
                graphics.setColor(new Color(colors[1],true)); graphics.fillRect(l,t,1,b-t);
                graphics.setColor(new Color(colors[2],true)); graphics.fillRect(l,b-1,r-l,1);
                graphics.setColor(new Color(colors[3],true)); graphics.fillRect(r-1,t,1,b-t);
            }
            graphics.dispose();
            check(image.getRGB(88,88)==center,"interior unchanged");
            check(MetalBevel.DEPTH == 3, "three-pixel vanilla-style bevel");
            check(brightness(image.getRGB(88,0)) < brightness(image.getRGB(88,174)), "outline darker than inner shadow");
            for (int layer=0; layer<MetalBevel.DEPTH; layer++) {
                check(image.getRGB(88,layer)==MetalBevel.color(material,layer,MetalBevel.TOP),"top edge");
                check(image.getRGB(layer,88)==MetalBevel.color(material,layer,MetalBevel.LEFT),"left edge");
                check(image.getRGB(88,176-layer)==MetalBevel.color(material,layer,MetalBevel.BOTTOM),"bottom edge");
                check(image.getRGB(175-layer,88)==MetalBevel.color(material,layer,MetalBevel.RIGHT),"right edge");
            }
            out.drawImage(image,column++*352,0,352,354,null);
        }
        out.dispose();
        Files.createDirectories(Path.of("build"));
        ImageIO.write(preview,"png",Path.of("build/metal-bevel-preview.png").toFile());
        if (args.length > 0) ImageIO.write(preview,"png",Path.of(args[0]).toFile());
        System.out.println("PASS: three opaque raised-metal bevels, highlights/shadows, unchanged interiors and unframed bricks.");
    }
}
