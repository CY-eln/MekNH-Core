package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * 机器/锅炉共用的传输配置窗口（严格输入、输出颜色、六面输入颜色）。
 */
public final class TransporterPanel {

    public static final int WIDTH = 156;
    public static final int HEIGHT = 119;

    public interface Context {
        int strictInput();

        int outputColor();

        int inputColor(Direction direction);

        Direction facing();

        void sendButton(int id);
    }

    private enum Face {
        TOP(41, 34, Direction.UP),
        FRONT(41, 57, null),
        BOTTOM(41, 80, Direction.DOWN),
        LEFT(18, 57, null),
        RIGHT(64, 57, null),
        BACK(18, 80, null);

        private final int x;
        private final int y;
        private final Direction fixed;

        Face(int x, int y, Direction fixed) {
            this.x = x;
            this.y = y;
            this.fixed = fixed;
        }

        Direction direction(Direction facing) {
            return switch (this) {
                case TOP -> Direction.UP;
                case BOTTOM -> Direction.DOWN;
                case FRONT -> facing;
                case BACK -> facing.getOpposite();
                case LEFT -> facing.getClockWise();
                case RIGHT -> facing.getCounterClockWise();
            };
        }
    }

    private boolean open;
    private static final int[] COLORS = {
            0xFFFFFF, 0xD87F33, 0xB468D9, 0x6699D8, 0xE5E533, 0x7FCC19, 0xF27FA5, 0x4C4C4C,
            0x999999, 0x4C7F99, 0x7F3FB2, 0x334CB2, 0x664C33, 0x667F33, 0x993333, 0x191919
    };

    public boolean isOpen() {
        return this.open;
    }

    public void toggle() {
        this.open = !this.open;
    }

    public void close() {
        this.open = false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && this.open) {
            this.open = false;
            return true;
        }
        return false;
    }

    public void render(GuiGraphics graphics, Font font, int left, int top, Context context) {
        if (!this.open) {
            return;
        }
        int originX = left + (176 - WIDTH) / 2;
        int originY = top + 15;

        graphics.flush();
        graphics.fill(originX, originY, originX + WIDTH, originY + HEIGHT, 0xFFC6C6C6);
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.75F);
        GuiDraw.nineSlice(graphics, MekGuiTextures.WINDOW_SHADOW, originX - 3, originY - 3,
                WIDTH + 6, HEIGHT + 6, 4, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        GuiDraw.nineSlice(graphics, MekGuiTextures.WINDOW_BASE, originX, originY, WIDTH, HEIGHT, 4, 256, 256);

        Component title = Component.translatable("gui.meknh_core.transporter.title");
        graphics.drawString(font, title, originX + 12 + (126 - font.width(title)) / 2,
                originY + 5, 4210752, false);
        drawButtonBase(graphics, originX + 6, originY + 6, 8, 8);
        graphics.blit(MekGuiTextures.BUTTON_CLOSE, originX + 6, originY + 6, 8, 8,
                0, 0, 12, 12, 12, 12);

        GuiDraw.nineSliceXY(graphics, MekGuiTextures.INNER_SCREEN, originX + 38, originY + 15,
                80, 12, 4, 4, 32, 32, 0, 0, 32, 32);
        Component strict = Component.translatable("gui.meknh_core.transporter.strict",
                Component.translatable(context.strictInput() != 0
                        ? "gui.meknh_core.config.on" : "gui.meknh_core.config.off"));
        graphics.pose().pushPose();
        graphics.pose().translate(originX + 43.0F, originY + 18.0F, 0.0F);
        graphics.pose().scale(0.8F, 0.8F, 1.0F);
        graphics.drawString(font, strict, 0, 0, 0x3BE03B, false);
        graphics.pose().popPose();

        drawButtonBase(graphics, originX + 136, originY + 6, 14, 14);
        graphics.blit(MekGuiTextures.BUTTON_EXCLAMATION, originX + 136, originY + 6,
                14, 14, 0, 0, 14, 14, 14, 14);
        graphics.blit(MekGuiTextures.SLOT_NORMAL, originX + 111, originY + 48,
                18, 18, 0, 0, 18, 18, 18, 18);
        graphics.fill(originX + 112, originY + 49, originX + 128, originY + 65,
                0xFF000000 | COLORS[Math.floorMod(context.outputColor(), COLORS.length)]);

        for (Face face : Face.values()) {
            int x = originX + face.x;
            int y = originY + face.y;
            int color = COLORS[Math.floorMod(context.inputColor(face.direction(context.facing())), COLORS.length)];
            graphics.setColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F, 1.0F);
            GuiDraw.nineSliceXY(graphics, MekGuiTextures.TAB_BUTTON, x, y, 22, 22,
                    20, 4, 200, 60, 0, 20, 200, 20);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        graphics.drawString(font, Component.translatable("gui.meknh_core.transporter.input"),
                originX + 18, originY + 105, 4210752, false);
        graphics.drawString(font, Component.translatable("gui.meknh_core.transporter.output"),
                originX + 86, originY + 68, 4210752, false);
        graphics.flush();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int left, int top, Context context) {
        if (!this.open) {
            return false;
        }
        int originX = left + (176 - WIDTH) / 2;
        int originY = top + 15;
        if (mouseX >= originX + 6 && mouseX < originX + 14 && mouseY >= originY + 6 && mouseY < originY + 14) {
            this.open = false;
            return true;
        }
        if (mouseX >= originX + 136 && mouseX < originX + 150 && mouseY >= originY + 6 && mouseY < originY + 20) {
            if (button == 0) {
                context.sendButton(210);
            }
            return true;
        }
        if (mouseX >= originX + 112 && mouseX < originX + 128 && mouseY >= originY + 49 && mouseY < originY + 65) {
            if (button == 0) {
                context.sendButton(211);
            }
            return true;
        }
        for (Face face : Face.values()) {
            int x = originX + face.x;
            int y = originY + face.y;
            if (mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22) {
                if (button == 0) {
                    context.sendButton(212 + face.direction(context.facing()).ordinal());
                }
                return true;
            }
        }
        if (mouseX < originX || mouseX >= originX + WIDTH || mouseY < originY || mouseY >= originY + HEIGHT) {
            this.open = false;
        }
        return true;
    }

    public boolean renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                                 int left, int top, Context context) {
        if (!this.open) {
            return false;
        }
        int originX = left + (176 - WIDTH) / 2;
        int originY = top + 15;
        if (mouseX >= originX + 6 && mouseX < originX + 14 && mouseY >= originY + 6 && mouseY < originY + 14) {
            graphics.renderTooltip(font, Component.translatable("gui.meknh_core.config.close"), mouseX, mouseY);
            return true;
        }
        if (mouseX >= originX + 136 && mouseX < originX + 150 && mouseY >= originY + 6 && mouseY < originY + 20) {
            graphics.renderTooltip(font, Component.translatable("gui.meknh_core.transporter.strict",
                    Component.translatable(context.strictInput() != 0
                            ? "gui.meknh_core.config.on" : "gui.meknh_core.config.off")), mouseX, mouseY);
            return true;
        }
        if (mouseX >= originX + 112 && mouseX < originX + 128 && mouseY >= originY + 49 && mouseY < originY + 65) {
            graphics.renderTooltip(font, Component.translatable("gui.meknh_core.transporter.output"),
                    mouseX, mouseY);
            return true;
        }
        for (Face face : Face.values()) {
            int x = originX + face.x;
            int y = originY + face.y;
            if (mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22) {
                graphics.renderTooltip(font, Component.translatable("gui.meknh_core.transporter.input"),
                        mouseX, mouseY);
                return true;
            }
        }
        return true;
    }

    private static void drawButtonBase(GuiGraphics graphics, int x, int y, int width, int height) {
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.TAB_BUTTON, x, y, width, height,
                20, 4, 200, 60, 0, 20, 200, 20);
    }
}
