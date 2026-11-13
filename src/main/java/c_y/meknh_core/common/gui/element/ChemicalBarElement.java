package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.util.text.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.util.Mth;

/**
 * 蒸汽机器底部横向条的复刻（显示机器蒸汽能量罐）。
 *
 * <p>几何与 Mekanism {@code GuiFactory} 中的
 * {@code new GuiChemicalBar(..., 7, 76, 138, 4, true)} 一致：
 * 控件外框 140x6，内部内容区 138x4。贴图直接引用 Mekanism 的
 * {@code mekanism:gui/bar/base.png} 与 {@code mekanism:gui/dump.png}，
 * 归属声明见项目根目录 {@code LICENSE-MEKANISM}。</p>
 */
public final class ChemicalBarElement {

    /** 与 Mekanism {@code GuiBar.BAR} 相同的资源路径。 */
    public static final ResourceLocation BAR = MekGuiTextures.CHEMICAL_BAR;
    public static final ResourceLocation DUMP = MekGuiTextures.DUMP;

    public static final int X = 7;
    public static final int Y = 76;
    /** 传入 GuiChemicalBar 的内容宽（控件会自动 +2）。 */
    public static final int WIDTH = 138;
    /** 传入 GuiChemicalBar 的内容高（控件会自动 +2）。 */
    public static final int HEIGHT = 4;
    public static final int FRAME_WIDTH = WIDTH + 2;
    public static final int FRAME_HEIGHT = HEIGHT + 2;

    public static final int DUMP_X = 148;
    public static final int DUMP_Y = 76;
    public static final int DUMP_WIDTH = 21;
    public static final int DUMP_HEIGHT = 10;

    private ChemicalBarElement() {
    }

    /**
     * @param capacity Tank 容量，用于计算填充比例；与 Mekanism 的
     *                 {@code tank.getStored() / tank.getCapacity()} 一致。
     */
    public static void render(GuiGraphics graphics, ChemicalStack stack, long capacity, int guiLeft, int guiTop) {
        int x = guiLeft + X;
        int y = guiTop + Y;
        // GuiBar#renderExtendedTexture(BAR, 2, 2)：5x5 贴图按 2px 九宫格拉伸到 140x6。
        GuiDraw.nineSliceXY(graphics, BAR, x, y, FRAME_WIDTH, FRAME_HEIGHT, 2, 2, 5, 5, 0, 0, 5, 5);
        if (stack.isEmpty() || capacity <= 0L) {
            return;
        }

        int innerWidth = WIDTH;
        int innerHeight = HEIGHT;
        double level = Mth.clamp(stack.getAmount() / (double) capacity, 0.0D, 1.0D);
        int display = (int) (level * innerWidth);
        if (display <= 0) {
            return;
        }

        int tint = stack.getChemicalTint();
        float alpha = ((tint >>> 24) & 0xFF) / 255.0F;
        if (alpha <= 0.0F) {
            alpha = 1.0F;
        }
        float red = ((tint >>> 16) & 0xFF) / 255.0F;
        float green = ((tint >>> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;

        // GuiTankBar#renderBarOverlay(horizontal=true) 绘制内部 4px 高的一条，
        // 且使用化学品方块贴图平铺。这里用 scissor 裁出该条的底部 4px，
        // 避免把 16x16 的化学品贴图压扁。
        TextureAtlasSprite sprite = MekanismRenderer.getChemicalTexture(stack);
        graphics.enableScissor(x + 1, y + 1, x + 1 + display, y + 1 + innerHeight);
        for (int offset = 0; offset < display; offset += 16) {
            int drawWidth = Math.min(16, display - offset);
            graphics.blit(
                    x + 1 + offset,
                    y + 1 + innerHeight - 16,
                    0,
                    16,
                    16,
                    sprite,
                    red,
                    green,
                    blue,
                    alpha
            );
            // 最后一片被 scissor 裁掉右侧多余部分，与 Mekanism 的 maskRight 等价。
            if (drawWidth < 16) {
                break;
            }
        }
        graphics.disableScissor();
    }

    public static void renderDumpButton(GuiGraphics graphics, int guiLeft, int guiTop) {
        graphics.blit(
                DUMP,
                guiLeft + DUMP_X,
                guiTop + DUMP_Y,
                0,
                0,
                DUMP_WIDTH,
                DUMP_HEIGHT,
                DUMP_WIDTH,
                DUMP_HEIGHT
        );
    }

    public static boolean isOverBar(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return mouseX >= guiLeft + X && mouseX < guiLeft + X + FRAME_WIDTH
                && mouseY >= guiTop + Y && mouseY < guiTop + Y + FRAME_HEIGHT;
    }

    public static boolean isOverDump(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return mouseX >= guiLeft + DUMP_X && mouseX < guiLeft + DUMP_X + DUMP_WIDTH
                && mouseY >= guiTop + DUMP_Y && mouseY < guiTop + DUMP_Y + DUMP_HEIGHT;
    }

    /**
     * 文案与 {@code GuiChemicalBar#getTooltip} 对齐：
     * 空 → {@code MekanismLang.EMPTY}；非空 → {@code GENERIC_STORED_MB} + 化学品自身 tooltip。
     */
    public static void renderTooltip(GuiGraphics graphics, Font font, ChemicalStack stack, int mouseX, int mouseY) {
        List<Component> tooltip = new ArrayList<>();
        if (stack.isEmpty()) {
            tooltip.add(MekanismLang.EMPTY.translate());
        } else {
            tooltip.add(MekanismLang.GENERIC_STORED_MB.translate(stack, TextUtils.format(stack.getAmount())));
            stack.appendHoverText(
                    TooltipContext.of(Minecraft.getInstance().level),
                    tooltip,
                    Minecraft.getInstance().options.advancedItemTooltips
                            ? TooltipFlag.Default.ADVANCED
                            : TooltipFlag.Default.NORMAL
            );
        }
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }
}
