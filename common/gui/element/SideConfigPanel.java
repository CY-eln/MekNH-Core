package c_y.meknh_core.common.gui.element;

import c_y.meknh_core.common.gui.layout.MekGuiTextures;
import c_y.meknh_core.common.gui.render.GuiDraw;
import c_y.meknh_core.common.machine.config.SideMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 机器/锅炉共用的侧面配置窗口。
 *
 * <p>布局与 Mek {@code GuiSideConfiguration} 一致：窗口 156×135，阴影 3px，
 * 类型 Tab 在窗口左侧 -26，关闭/Pin/自动输出/清空按钮、内屏、六面按钮与
 * “格子”文字都使用源码坐标。所有数据通过 {@link Context} 由调用方提供，
 * 避免机器和锅炉各写一套。</p>
 */
public final class SideConfigPanel {

    public static final int WIDTH = 156;
    public static final int HEIGHT = 135;
    private static final int TYPE_TAB = 26;
    private static final int BUTTON_INSET = 4;

    /** 每个机器/锅炉自己的 Pin 状态；只影响客户端 GUI，不进 NBT。 */
    private static final Map<BlockPos, Boolean> PINNED = new ConcurrentHashMap<>();

    public enum RelSide {
        TOP(67, 46, "top", Direction.UP),
        FRONT(67, 69, "front", null),
        BOTTOM(67, 92, "bottom", Direction.DOWN),
        LEFT(44, 69, "left", null),
        RIGHT(90, 69, "right", null),
        BACK(44, 92, "back", null);

        private final int x;
        private final int y;
        private final String key;
        private final Direction fixed;

        RelSide(int x, int y, String key, Direction fixed) {
            this.x = x;
            this.y = y;
            this.key = key;
            this.fixed = fixed;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }

        public String key() {
            return this.key;
        }

        public Direction direction(Direction facing) {
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

    /** 调用方提供的数据与操作；GUI 不直接依赖机器/锅炉类型。 */
    public interface Context {
        int sideMode(int type, Direction side);

        boolean supports(int type);

        boolean autoEject(int type);

        boolean canEject(int type);

        boolean allowsMode(int type, SideMode mode);

        Direction facing();

        BlockPos pos();

        Level level();

        void sendButton(int id);
    }

    private boolean open;
    private boolean pinned;
    private int configType;
    private BlockPos pos;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private double dragStartX;
    private double dragStartY;
    private int dragStartPanelX;
    private int dragStartPanelY;
    private int lastLeft;
    private int lastTop;

    private int originX(int left) {
        return left + (176 - WIDTH) / 2 + this.dragOffsetX;
    }

    private int originY(int top) {
        return top + 15 + this.dragOffsetY;
    }

    public void init(BlockPos pos) {
        this.pos = pos;
        this.pinned = pos != null && PINNED.getOrDefault(pos, false);
        this.open = this.pinned;
        this.configType = 0;
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isPinned() {
        return this.pinned;
    }

    public void toggle() {
        this.open = !this.open;
    }

    public void close() {
        this.open = false;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
        if (this.pos != null) {
            if (pinned) {
                PINNED.put(this.pos, true);
            } else {
                PINNED.remove(this.pos);
            }
        }
    }

    public void render(GuiGraphics graphics, Font font, int left, int top, Context context) {
        if (!this.open) {
            return;
        }
        this.ensureType(context);
        this.lastLeft = left;
        this.lastTop = top;
        int originX = this.originX(left);
        int originY = this.originY(top);

        // 先把 super.render 留下的物品批次冲掉，再不透明覆盖窗口区域。
        graphics.flush();
        graphics.fill(originX, originY, originX + WIDTH, originY + HEIGHT, 0xFFC6C6C6);
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.75F);
        GuiDraw.nineSlice(graphics, MekGuiTextures.WINDOW_SHADOW, originX - 3, originY - 3,
                WIDTH + 6, HEIGHT + 6, 4, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        GuiDraw.nineSlice(graphics, MekGuiTextures.WINDOW_BASE, originX, originY, WIDTH, HEIGHT, 4, 256, 256);

        Component title = Component.translatable("gui.meknh_core.config.title." + typeKey(this.configType));
        int titleLeft = originX + 30;
        int titleRight = originX + WIDTH - 18;
        graphics.drawString(font, title, titleLeft + (titleRight - titleLeft - font.width(title)) / 2,
                originY + 5, 4210752, false);

        for (int type = 0; type < 4; type++) {
            if (type != this.configType && context.supports(type)) {
                drawConfigTypeTab(graphics, originX - TYPE_TAB, originY + 2 + type * 28, type);
            }
        }

        drawButtonBase(graphics, originX + 6, originY + 6, 8, 8);
        graphics.blit(MekGuiTextures.BUTTON_CLOSE, originX + 6, originY + 6, 8, 8,
                0, 0, 12, 12, 12, 12);
        drawButtonBase(graphics, originX + 16, originY + 6, 16, 8);
        graphics.blit(this.pinned ? MekGuiTextures.PINNED : MekGuiTextures.UNPINNED,
                originX + 16, originY + 6, 16, 8, 0, 0, 12, 24, 12, 24);
        boolean canEject = context.canEject(this.configType);
        if (!canEject) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 0.35F);
        }
        drawButtonBase(graphics, originX + 136, originY + 6, 14, 14);
        graphics.blit(MekGuiTextures.BUTTON_AUTO_EJECT, originX + 136, originY + 6,
                14, 14, 0, 0, 14, 14, 14, 14);
        if (!canEject) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        drawButtonBase(graphics, originX + 136, originY + 95, 14, 14);
        graphics.blit(MekGuiTextures.BUTTON_CLEAR_SIDES, originX + 136, originY + 95,
                14, 14, 0, 0, 14, 14, 14, 14);

        GuiDraw.nineSliceXY(graphics, MekGuiTextures.INNER_SCREEN,
                originX + 38, originY + 25, 80, 12, 4, 4, 32, 32, 0, 0, 32, 32);
        Component autoText = canEject
                ? Component.translatable("gui.meknh_core.config.eject",
                Component.translatable(context.autoEject(this.configType)
                        ? "gui.meknh_core.config.on" : "gui.meknh_core.config.off"))
                : MekanismLang.NO_EJECT.translate();
        graphics.pose().pushPose();
        graphics.pose().translate(originX + 43.0F, originY + 28.0F, 0.0F);
        graphics.pose().scale(0.8F, 0.8F, 1.0F);
        graphics.drawString(font, autoText, 0, 0, 0x3BE03B, false);
        graphics.pose().popPose();

        for (RelSide side : RelSide.values()) {
            Direction direction = side.direction(context.facing());
            SideMode mode = SideMode.byOrdinal(context.sideMode(this.configType, direction));
            int color = color(mode);
            int x = originX + side.x();
            int y = originY + side.y();
            graphics.setColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F, 1.0F);
            GuiDraw.nineSliceXY(graphics, MekGuiTextures.TAB_BUTTON, x, y, 22, 22,
                    20, 4, 200, 60, 0, 20, 200, 20);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        Component slots = Component.translatable("gui.meknh_core.config.slots");
        graphics.drawString(font, slots, originX + (WIDTH - font.width(slots)) / 2,
                originY + 120, 4210752, false);

        // 相邻方块预览必须第二遍绘制，避免被按钮底板/边框覆盖。
        Level level = context.level();
        BlockPos machinePos = context.pos();
        if (level != null && machinePos != null && Minecraft.getInstance().player != null) {
            for (RelSide side : RelSide.values()) {
                Direction direction = side.direction(context.facing());
                BlockPos neighborPos = machinePos.relative(direction);
                BlockState state = level.getBlockState(neighborPos);
                if (state.isAir()) {
                    continue;
                }
                ItemStack stack = state.getCloneItemStack(
                        new BlockHitResult(neighborPos.getCenter(), direction.getOpposite(), neighborPos, false),
                        level, neighborPos, Minecraft.getInstance().player);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, originX + side.x() + 3, originY + side.y() + 3);
                }
            }
        }
        graphics.flush();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int left, int top, Context context) {
        if (!this.open) {
            return false;
        }
        this.ensureType(context);
        this.lastLeft = left;
        this.lastTop = top;
        int originX = this.originX(left);
        int originY = this.originY(top);

        for (int type = 0; type < 4; type++) {
            int tx = originX - TYPE_TAB;
            int ty = originY + 2 + type * 28;
            if (type != this.configType && context.supports(type)
                    && mouseX >= tx && mouseX < tx + TYPE_TAB && mouseY >= ty && mouseY < ty + TYPE_TAB) {
                this.configType = type;
                return true;
            }
        }

        if (mouseX < originX || mouseX >= originX + WIDTH
                || mouseY < originY || mouseY >= originY + HEIGHT) {
            if (!this.pinned) {
                this.open = false;
            }
            return true;
        }

        for (RelSide side : RelSide.values()) {
            int x = originX + side.x();
            int y = originY + side.y();
            if (mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22) {
                Direction direction = side.direction(context.facing());
                context.sendButton((button == 1 ? 150 : 100) + this.configType * 6 + direction.ordinal());
                return true;
            }
        }

        if (mouseX >= originX + 136 && mouseX < originX + 150
                && mouseY >= originY + 6 && mouseY < originY + 20) {
            if (button == 0 && context.canEject(this.configType)) {
                context.sendButton(140 + this.configType);
            }
            return true;
        }
        if (mouseX >= originX + 136 && mouseX < originX + 150
                && mouseY >= originY + 95 && mouseY < originY + 109) {
            if (Screen.hasShiftDown()) {
                context.sendButton(188);
            } else if (button == 0) {
                context.sendButton(180 + this.configType);
            } else if (button == 1) {
                context.sendButton(184 + this.configType);
            }
            return true;
        }
        if (mouseX >= originX + 6 && mouseX < originX + 14
                && mouseY >= originY + 6 && mouseY < originY + 14) {
            this.open = false;
            return true;
        }
        if (mouseX >= originX + 16 && mouseX < originX + 32
                && mouseY >= originY + 6 && mouseY < originY + 14) {
            this.setPinned(!this.pinned);
            return true;
        }
        // GuiWindow：标题区域可拖拽。
        if (button == 0 && mouseY >= originY && mouseY < originY + 18) {
            this.dragging = true;
            this.dragStartX = mouseX;
            this.dragStartY = mouseY;
            this.dragStartPanelX = originX;
            this.dragStartPanelY = originY;
            return true;
        }
        // 模态：窗口内未命中的点击也必须消费。
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!this.open || !this.dragging) {
            return false;
        }
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int baseX = this.lastLeft + (176 - WIDTH) / 2;
        int baseY = this.lastTop + 15;
        int targetX = Math.max(0, Math.min(screenWidth - WIDTH,
                this.dragStartPanelX + (int) Math.round(mouseX - this.dragStartX)));
        int targetY = Math.max(0, Math.min(screenHeight - HEIGHT,
                this.dragStartPanelY + (int) Math.round(mouseY - this.dragStartY)));
        this.dragOffsetX = targetX - baseX;
        this.dragOffsetY = targetY - baseY;
        return true;
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && this.open) {
            if (!this.pinned) {
                this.open = false;
            }
            return true;
        }
        return false;
    }

    public boolean renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                                 int left, int top, Context context) {
        if (!this.open) {
            return false;
        }
        this.ensureType(context);
        this.lastLeft = left;
        this.lastTop = top;
        int originX = this.originX(left);
        int originY = this.originY(top);

        for (int type = 0; type < 4; type++) {
            int tx = originX - TYPE_TAB;
            int ty = originY + 2 + type * 28;
            if (type != this.configType && context.supports(type)
                    && mouseX >= tx && mouseX < tx + TYPE_TAB && mouseY >= ty && mouseY < ty + TYPE_TAB) {
                graphics.renderTooltip(font,
                        Component.translatable("gui.meknh_core.config.type." + typeKey(type)), mouseX, mouseY);
                return true;
            }
        }

        if (mouseX >= originX + 38 && mouseX < originX + 118
                && mouseY >= originY + 25 && mouseY < originY + 37
                && !context.canEject(this.configType)) {
            graphics.renderTooltip(font, MekanismLang.CANT_EJECT_TOOLTIP.translate(), mouseX, mouseY);
            return true;
        }

        if (mouseX >= originX + 136 && mouseX < originX + 150
                && mouseY >= originY + 95 && mouseY < originY + 109) {
            SideMode common = commonMode(context);
            SideMode target = common == null || !context.allowsMode(this.configType, common)
                    ? SideMode.NONE
                    : nextAllowedMode(context, this.configType, common, false);
            java.util.List<Component> tooltip = new java.util.ArrayList<>(2);
            if (target == SideMode.NONE) {
                tooltip.add(MekanismLang.SIDE_CONFIG_CLEAR.translate());
                tooltip.add(MekanismLang.SIDE_CONFIG_CLEAR_ALL.translate());
            } else {
                tooltip.add(MekanismLang.SIDE_CONFIG_INCREMENT.translate());
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return true;
        }
        if (mouseX >= originX + 136 && mouseX < originX + 150
                && mouseY >= originY + 6 && mouseY < originY + 20) {
            if (!context.canEject(this.configType)) {
                graphics.renderTooltip(font, MekanismLang.CANT_EJECT_TOOLTIP.translate(), mouseX, mouseY);
                return true;
            }
            graphics.renderTooltip(font, Component.translatable("gui.meknh_core.config.eject",
                    Component.translatable(context.autoEject(this.configType)
                            ? "gui.meknh_core.config.on" : "gui.meknh_core.config.off")), mouseX, mouseY);
            return true;
        }
        if (mouseX >= originX + 6 && mouseX < originX + 14
                && mouseY >= originY + 6 && mouseY < originY + 14) {
            graphics.renderTooltip(font, Component.translatable("gui.meknh_core.config.close"), mouseX, mouseY);
            return true;
        }
        if (mouseX >= originX + 16 && mouseX < originX + 32
                && mouseY >= originY + 6 && mouseY < originY + 14) {
            graphics.renderTooltip(font, Component.translatable(this.pinned
                    ? "gui.meknh_core.config.unpinned" : "gui.meknh_core.config.pinned"), mouseX, mouseY);
            return true;
        }

        for (RelSide side : RelSide.values()) {
            int x = originX + side.x();
            int y = originY + side.y();
            if (mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22) {
                Direction direction = side.direction(context.facing());
                SideMode mode = SideMode.byOrdinal(context.sideMode(this.configType, direction));
                graphics.renderTooltip(font, Component.translatable("gui.meknh_core.side.tooltip",
                        Component.translatable("gui.meknh_core.side.face." + side.key()),
                        mode.displayName()), mouseX, mouseY);
                return true;
            }
        }
        return true;
    }

    private void ensureType(Context context) {
        if (!context.supports(this.configType)) {
            for (int type = 0; type < 4; type++) {
                if (context.supports(type)) {
                    this.configType = type;
                    return;
                }
            }
        }
    }

    private SideMode commonMode(Context context) {
        SideMode common = null;
        for (Direction direction : Direction.values()) {
            SideMode mode = SideMode.byOrdinal(context.sideMode(this.configType, direction));
            if (common == null) {
                common = mode;
            } else if (common != mode) {
                return null;
            }
        }
        return common;
    }

    private static SideMode nextAllowedMode(Context context, int type, SideMode current, boolean reverse) {
        SideMode candidate = current;
        for (int i = 0; i < 4; i++) {
            candidate = reverse ? candidate.previous() : candidate.next();
            if (context.allowsMode(type, candidate)) {
                return candidate;
            }
        }
        return SideMode.NONE;
    }

    private static String typeKey(int type) {
        return switch (type) {
            case 1 -> "fluid";
            case 2 -> "chemical";
            case 3 -> "energy";
            default -> "item";
        };
    }

    private static int color(SideMode mode) {
        EnumColor color = switch (mode) {
            case INPUT -> EnumColor.DARK_RED;
            case OUTPUT -> EnumColor.DARK_BLUE;
            case BOTH -> EnumColor.PURPLE;
            default -> EnumColor.GRAY;
        };
        return color.getPackedColor() & 0xFFFFFF;
    }

    private static void drawConfigTypeTab(GuiGraphics graphics, int x, int y, int type) {
        net.minecraft.resources.ResourceLocation icon = switch (type) {
            case 1 -> MekGuiTextures.CONFIG_FLUID;
            case 2 -> MekGuiTextures.CONFIG_CHEMICAL;
            case 3 -> MekGuiTextures.CONFIG_ENERGY;
            default -> MekGuiTextures.CONFIG_ITEM;
        };
        int tint = switch (type) {
            case 1 -> 0x366BD0;
            case 2 -> 0xFFDD4F;
            case 3 -> 0x59C15F;
            default -> 0xCFCFCF;
        };
        graphics.setColor((tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F, 1.0F);
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.TAB_HOLDER_LEFT, x, y, TYPE_TAB, TYPE_TAB,
                4, 4, 26, 9, 0, 0, 26, 9);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.TAB_BUTTON, x + BUTTON_INSET, y + BUTTON_INSET,
                18, 18, 20, 4, 200, 60, 0, 20, 200, 20);
        graphics.blit(icon, x + BUTTON_INSET, y + BUTTON_INSET, 18, 18, 0, 0, 18, 18, 18, 18);
    }

    private static void drawButtonBase(GuiGraphics graphics, int x, int y, int width, int height) {
        GuiDraw.nineSliceXY(graphics, MekGuiTextures.TAB_BUTTON, x, y, width, height,
                20, 4, 200, 60, 0, 20, 200, 20);
    }
}
