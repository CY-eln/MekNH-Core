package c_y.meknh_core.client.editor;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.client.MeknhUiTool;
import c_y.meknh_core.common.gui.screen.MachineScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 编辑模式下，在**别的模组的机器界面**（例如 Mekanism 的原版机器）上叠加的参考编辑器。
 *
 * <p>Mekanism 机器的界面布局写死在它自己的代码里，没有可读取的数据；所以这里提供的是
 * 「照着它的界面摆元素」的描图模式：可以新建/拖动/缩放/删除元素、保存导出，
 * 之后在自己的机器里用「从机器」把整套布局复制过去。</p>
 *
 * <p>它是只读参考 + 描图：不修改原机器的任何东西，也不提供贴图选择（切换界面会关掉
 * 对方的容器）。贴图等复制到自己机器后再挑。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID, value = Dist.CLIENT)
public final class EditorReferenceOverlay {

    private static final int GRID = 10;
    private static final int SPAN = 256;

    @Nullable
    private static Screen cachedScreen;
    @Nullable
    private static TraceEditor cachedEditor;

    private EditorReferenceOverlay() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!MeknhUiTool.isEditorMode()) {
            cachedScreen = null;
            cachedEditor = null;
            return;
        }
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof MachineScreen) {
            cachedScreen = null;
            cachedEditor = null;
            return;
        }
        if (cachedScreen != screen) {
            cachedScreen = screen;
            cachedEditor = TraceEditor.create(container);
        }
        if (cachedEditor == null) {
            return;
        }
        cachedEditor.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (cachedEditor != null && event.getScreen() == cachedScreen
                && cachedEditor.mouseClicked((int) event.getMouseX(), (int) event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (cachedEditor != null && event.getScreen() == cachedScreen
                && cachedEditor.mouseDragged((int) event.getMouseX(), (int) event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (cachedEditor != null && event.getScreen() == cachedScreen) {
            cachedEditor.mouseReleased();
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (cachedEditor != null && event.getScreen() == cachedScreen && cachedEditor.keyPressed(event.getKeyCode())) {
            event.setCanceled(true);
        }
    }

    /**
     * 单个外来界面的描图状态。
     */
    private static final class TraceEditor {

        private final AbstractContainerScreen<?> screen;
        private final ResourceLocation machineId;
        private final List<UiElement> elements;
        private final List<TraceButton> buttons = new ArrayList<>();

        private int guiLeft;
        private int guiTop;
        private int selected = -1;
        private boolean dragging;
        private boolean resizing;
        private double grabX;
        private double grabY;

        private TraceEditor(AbstractContainerScreen<?> screen, ResourceLocation machineId, List<UiElement> elements) {
            this.screen = screen;
            this.machineId = machineId;
            this.elements = elements;
            this.guiLeft = screen.getGuiLeft();
            this.guiTop = screen.getGuiTop();

            int x = this.guiLeft + 176 + 8;
            int y = this.guiTop;
            this.buttons.add(new TraceButton(Component.translatable("gui.meknh_core.ui_editor.new"),
                    x, y, 70, 18, this::addElement));
            this.buttons.add(new TraceButton(Component.translatable("gui.meknh_core.ui_editor.delete"),
                    x, y + 20, 70, 18, this::removeSelected));
            this.buttons.add(new TraceButton(Component.translatable("gui.meknh_core.ui_editor.save"),
                    x, y + 40, 70, 18, this::save));
            this.buttons.add(new TraceButton(Component.translatable("gui.meknh_core.ui_editor.close"),
                    x, y + 60, 70, 18, () -> this.screen.onClose()));
        }

        @Nullable
        static TraceEditor create(AbstractContainerScreen<?> screen) {
            ResourceLocation machineId = machineId(screen);
            if (machineId == null) {
                return null;
            }
            List<UiElement> layout = MachineUiLayouts.saved(machineId);
            return new TraceEditor(screen, machineId, layout == null ? new ArrayList<>() : layout);
        }

        @Nullable
        private static ResourceLocation machineId(AbstractContainerScreen<?> screen) {
            if (screen instanceof mekanism.client.gui.GuiMekanismTile<?, ?> gui) {
                return BuiltInRegistries.BLOCK.getKey(gui.getTileEntity().getBlockState().getBlock());
            }
            return null;
        }

        void render(GuiGraphics graphics, int mouseX, int mouseY) {
            // 参考网格 + 坐标
            for (int x = 0; x <= SPAN; x += GRID) {
                graphics.fill(this.guiLeft + x, this.guiTop, this.guiLeft + x + 1, this.guiTop + SPAN, 0x22FFFFFF);
            }
            for (int y = 0; y <= SPAN; y += GRID) {
                graphics.fill(this.guiLeft, this.guiTop + y, this.guiLeft + SPAN, this.guiTop + y + 1, 0x22FFFFFF);
            }
            graphics.drawString(Minecraft.getInstance().font,
                    "x=" + (mouseX - this.guiLeft) + "  y=" + (mouseY - this.guiTop),
                    mouseX + 10, mouseY - 10, 0xFFFFD54F, true);

            // 元素
            for (int index = 0; index < this.elements.size(); index++) {
                UiElement element = this.elements.get(index);
                int x = this.guiLeft + element.x();
                int y = this.guiTop + element.y();
                int width = element.width();
                int height = element.height();

                boolean drawn = UiElementRenderer.render(graphics, element, this.guiLeft, this.guiTop, 0.0F, 0.0F, 0.0F);
                if (!drawn) {
                    graphics.fill(x, y, x + width, y + height, 0x33FFFFFF);
                    graphics.renderOutline(x, y, width, height, 0xFF9E9E9E);
                }
                if (index == this.selected) {
                    graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFFFFD54F);
                    graphics.fill(x + width - 5, y + height - 5, x + width, y + height, 0xFFFFD54F);
                }
                graphics.drawString(Minecraft.getInstance().font, element.displayName(), x + 1, y + 1, 0xFFFFFFFF, true);
            }

            // 工具栏
            if (!this.buttons.isEmpty()) {
                TraceButton first = this.buttons.get(0);
                TraceButton last = this.buttons.get(this.buttons.size() - 1);
                graphics.fill(first.x - 4, first.y - 4, first.x + first.width + 4, last.y + last.height + 4, 0x90000000);
            }
            for (TraceButton button : this.buttons) {
                button.render(graphics, mouseX, mouseY);
            }
        }

        boolean mouseClicked(int mouseX, int mouseY, int button) {
            for (TraceButton entry : this.buttons) {
                if (entry.contains(mouseX, mouseY)) {
                    if (button == 0) {
                        entry.action().run();
                    }
                    return true;
                }
            }

            double localX = mouseX - this.guiLeft;
            double localY = mouseY - this.guiTop;
            int hit = -1;
            for (int index = this.elements.size() - 1; index >= 0; index--) {
                if (this.elements.get(index).contains(localX, localY)) {
                    hit = index;
                    break;
                }
            }
            if (hit < 0) {
                this.selected = -1;
                return false;
            }
            this.selected = hit;
            if (button == 0) {
                UiElement element = this.elements.get(hit);
                boolean onHandle = localX >= element.x() + element.width() - 6
                        && localY >= element.y() + element.height() - 6;
                this.resizing = onHandle;
                this.dragging = !onHandle;
                this.grabX = localX;
                this.grabY = localY;
            }
            return true;
        }

        boolean mouseDragged(int mouseX, int mouseY) {
            if (this.selected < 0 || (!this.dragging && !this.resizing)) {
                return false;
            }
            UiElement element = this.elements.get(this.selected);
            double localX = mouseX - this.guiLeft;
            double localY = mouseY - this.guiTop;
            if (this.dragging) {
                element.setX(clamp((int) Math.round(localX - this.grabX), -64, 320));
                element.setY(clamp((int) Math.round(localY - this.grabY), -64, 320));
            } else {
                element.setWidth(clamp((int) Math.round(localX - element.x()), 4, 400));
                element.setHeight(clamp((int) Math.round(localY - element.y()), 4, 400));
            }
            return true;
        }

        void mouseReleased() {
            this.dragging = false;
            this.resizing = false;
        }

        boolean keyPressed(int keyCode) {
            if (this.selected < 0) {
                return false;
            }
            switch (keyCode) {
                case GLFW.GLFW_KEY_DELETE -> this.removeSelected();
                case GLFW.GLFW_KEY_LEFT -> this.nudge(-1, 0);
                case GLFW.GLFW_KEY_RIGHT -> this.nudge(1, 0);
                case GLFW.GLFW_KEY_UP -> this.nudge(0, -1);
                case GLFW.GLFW_KEY_DOWN -> this.nudge(0, 1);
                default -> {
                    return false;
                }
            }
            return true;
        }

        private void addElement() {
            this.elements.add(new UiElement(UiElementKind.CUSTOM, 40, 20, 18, 18));
            this.selected = this.elements.size() - 1;
        }

        private void removeSelected() {
            if (this.selected >= 0 && this.selected < this.elements.size()) {
                this.elements.remove(this.selected);
                this.selected = -1;
            }
        }

        private void nudge(int dx, int dy) {
            if (this.selected < 0 || this.selected >= this.elements.size()) {
                return;
            }
            UiElement element = this.elements.get(this.selected);
            element.setX(clamp(element.x() + dx, -64, 320));
            element.setY(clamp(element.y() + dy, -64, 320));
        }

        private void save() {
            MachineUiLayouts.save(this.machineId, this.elements);
            var path = MachineUiLayouts.export(this.machineId, this.elements);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("gui.meknh_core.ui_editor.exported", path.toString()), false);
            }
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private record TraceButton(Component label, int x, int y, int width, int height, Runnable action) {

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;
        }

        void render(GuiGraphics graphics, int mouseX, int mouseY) {
            boolean hover = this.contains(mouseX, mouseY);
            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, hover ? 0xCC5A5A5A : 0xCC333333);
            graphics.renderOutline(this.x, this.y, this.width, this.height, 0xFF9E9E9E);
            int textWidth = Minecraft.getInstance().font.width(this.label);
            graphics.drawString(Minecraft.getInstance().font, this.label,
                    this.x + (this.width - textWidth) / 2, this.y + 5, 0xFFFFFF, false);
        }
    }
}
