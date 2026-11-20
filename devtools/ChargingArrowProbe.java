import c_y.meknh_core.common.gui.render.ChargingArrow;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class ChargingArrowProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        for (float ratio : new float[]{-1, 0, .5f, 1, 2}) {
            for (int y = -1; y <= 9; y++) for (int x = -1; x <= 25; x++) {
                int color = ChargingArrow.color(x, y, true, ratio);
                check(color == ChargingArrow.color(x, 8-y, true, ratio), "vertical symmetry");
                if (x < 0 || x >= 25 || y < 0 || y >= 9) check(color == 0, "bounds");
                check(ChargingArrow.color(x,y,false,ratio) == ChargingArrow.color(x,y,true,0), "inactive fill hidden");
                if (color != 0) check((color >>> 24) == 255, "fully opaque pixels");
            }
        }
        check(ChargingArrow.color(2,4,true,.5f) != ChargingArrow.color(2,4,true,0), "half filled left");
        check(ChargingArrow.color(2,4,true,.5f) == ChargingArrow.color(2,4,true,1), "gradient stays anchored as progress advances");
        check(ChargingArrow.color(18,4,true,.5f) == ChargingArrow.color(18,4,true,0), "half empty right");
        check(ChargingArrow.color(22,4,true,1) != ChargingArrow.color(22,4,true,0), "full arrowhead");
        int previous = -1, unique = 0;
        for (int x = 1; x < 24; x++) {
            int color = ChargingArrow.color(x,4,true,1);
            if (color == ChargingArrow.color(x,4,true,0)) continue;
            check((color & 255) > previous, "smoothly increasing blue channel");
            previous = color & 255; unique++;
        }
        check(unique >= 15, "continuous gradient rather than one flat color per segment");
        check(ChargingArrow.color(2,4,true,1) != ChargingArrow.color(22,4,true,1), "filled arrow must have a blue color gradient");
        BufferedImage image = new BufferedImage(600,110,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics(); g.setColor(new Color(0xA9AFB2)); g.fillRect(0,0,600,110);
        for (int i=0; i<3; i++) {
            g.setColor(new Color(0x26343B)); g.drawString((i*50)+"%",i*200+18,24);
            for(int y=0;y<9;y++) for(int x=0;x<25;x++) {
                int color=ChargingArrow.color(x,y,true,i*.5f);
                if(color!=0) { g.setColor(new Color(color,true)); g.fillRect(i*200+18+x*6,38+y*6,6,6); }
            }
        }
        g.dispose(); ImageIO.write(image,"png",new File("build/charging-arrow-states.png"));
        System.out.println("PASS: arrow bounds, symmetric shape, inactive behavior, clamped and 0/50/100% fill.");
    }
}
