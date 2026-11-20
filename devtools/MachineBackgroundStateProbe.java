import c_y.meknh_core.common.gui.render.MachineBackgroundState;
import c_y.meknh_core.common.gui.render.TitleRing;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class MachineBackgroundStateProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        for (long power : new long[]{0, 1, Integer.MAX_VALUE, Long.MAX_VALUE}) {
            check(MachineBackgroundState.fromSynced(false, power) ==
                    (power > 0 ? MachineBackgroundState.IDLE : MachineBackgroundState.UNPOWERED), "idle/power");
            check(MachineBackgroundState.fromSynced(true, power) == MachineBackgroundState.WORKING, "running");
        }
        for (int titleWidth : new int[]{0, 45, 72, 120, 300}) {
            TitleRing ring = new TitleRing(176, titleWidth);
            boolean[][] mask = new boolean[176][24];
            int minX = 176, maxX = 0, minY = 24, maxY = 0;
            for (int p = 0; p < ring.size(); p++) {
                for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) {
                    int x = ring.x(p) + dx, y = ring.y(p) + dy;
                    mask[x][y] = true;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
            // Nominal 8px title glyph area y=6..13; both inner gaps must be 2px.
            check(6 - (minY + 2) == 2 && (maxY - 1) - 14 == 2, "equal title top/bottom padding");
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
                check(mask[x][y] == mask[minX + maxX - x][y], "left/right corner mirror symmetry");
                check(mask[x][y] == mask[x][minY + maxY - y], "top/bottom corner mirror symmetry");
            }
            for (int p = 0; p < ring.size(); p++) {
                int next = (p + 1) % ring.size();
                check(Math.abs(ring.x(p) - ring.x(next)) <= 1 && Math.abs(ring.y(p) - ring.y(next)) <= 1,
                        "continuous closed perimeter");
                check(ring.x(p) >= 6 && ring.x(p) < 170 && ring.y(p) >= 2 && ring.y(p) <= 18,
                        "no side leads, bounded ring");
                check(ring.color(p, MachineBackgroundState.IDLE, 0) == 0xFFFFA160, "orange standby");
                check(ring.color(p, MachineBackgroundState.UNPOWERED, 0) == 0xFF343434, "dark no power");
                check(ring.color(p, MachineBackgroundState.WORKING, p / (double)ring.size()) == 0xFF39FB98,
                        "green flow head follows perimeter");
            }
        }
        BufferedImage base = ImageIO.read(new File("src/main/resources/assets/meknh_core/textures/gui/metal_background.png"));
        check(base.getRGB(16, 11) != 0xFF343434, "old side leads must not remain baked into background");
        BufferedImage preview = new BufferedImage(1056, 384, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preview.createGraphics();
        g.setColor(new Color(0x24282D)); g.fillRect(0, 0, 1056, 384);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] names = {"NO POWER", "IDLE", "WORKING"};
        MachineBackgroundState[] states = {MachineBackgroundState.UNPOWERED, MachineBackgroundState.IDLE, MachineBackgroundState.WORKING};
        for (int state = 0; state < 3; state++) {
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.drawString(names[state], state * 352 + 12, 21);
            Graphics2D panel = (Graphics2D)g.create(state * 352, 30, 352, 354);
            panel.scale(2, 2); panel.drawImage(base, 0, 0, null);
            panel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 9));
            String title = "精英合金炉";
            int titleWidth = panel.getFontMetrics().stringWidth(title);
            TitleRing ring = new TitleRing(176, titleWidth);
            for (int p = 0; p < ring.size(); p++) {
                panel.setColor(new Color(ring.color(p, states[state], .35), true));
                panel.fillRect(ring.x(p), ring.y(p), 2, 2);
            }
            panel.setColor(new Color(0x404040));
            panel.drawString(title, (176 - titleWidth) / 2, 14);
            panel.dispose();
        }
        g.dispose();
        int greenPixels = 0;
        for (int y = 30; y < 70; y++) for (int x = 704; x < 1056; x++) {
            int color = preview.getRGB(x, y);
            if ((color >> 16 & 255) < 100 && (color >> 8 & 255) > 150) greenPixels++;
        }
        check(greenPixels > 0, "working highlight must remain visible after compositing");
        ImageIO.write(preview, "png", new File("build/metal-background-states.png"));
        System.out.println("PASS: state selection, 5 title widths, closed geometry, 3 colors, clockwise flow and no side leads.");
    }
}
