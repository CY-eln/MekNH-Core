package c_y.meknh_core.client.editor;

import c_y.meknh_core.api.machine.MachineDefinition;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.gui.screen.MachineScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 界面编辑器的覆盖层：正常情况下 {@link MachineScreen} 照常打开（右键不被拦截），
 * 编辑模式打开时这一层再画在机器界面**上面**，元素坐标就是机器界面的坐标系。
 *
 * <p>功能：自动生成元素、拖动/缩放/微调、右键选贴图、重命名、只读模式、
 * 在机器之间复制布局（含贴图）。</p>
 */
public final class MachineEditorOverlay {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;
    private static final int TOOLBAR_WIDTH = 100;
    private static final int GAP = 8;
    private static final int BUTTON_WIDTH = 46;
    private static final int COLUMN_STEP = 48;

    private static final int OUTLINE = 0xFF9E9E9E;
    private static final int SELECTED_OUTLINE = 0xFFFFD54F;
    private static final int HINT_COLOR = 0xFFB0B0B0;

    /** 机器之间复制用的剪贴板（客户端内存）。 */
    private static final List<UiElement> CLIPBOARD = new ArrayList<>();

    private final MachineScreen screen;
    private final MachineDefinition definition;
    private final List<UiElement> elements;

    private int panelX;
    private int panelY;
    private int toolbarX;
    private int selected = -1;
    private boolean readOnly;
    private DragMode dragMode = DragMode.NONE;
    private double grabX;
    private double grabY;
    @Nullable
    private EditBox nameField;
    @Nullable
    private Button readOnlyButton;
    private final List<Button> editButtons = new ArrayList<>();
    /** 选中元素时往输入框回填名字，避免把回填当成用户输入。 */
    private boolean syncingName;

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE
    }

    public MachineEditorOverlay(MachineScreen screen, MachineDefinition definition, MachineLevel level) {
        this.screen = screen;
        this.definition = definition;
        this.elements = MachineUiLayouts.layout(definition, this.screen.machineLevel());
    }

    public List<UiElement> elements() {
        return this.elements;
    }

    /**
     * 界面初始化（或窗口大小变化）时调用：算位置、加按钮。
     */
    public void init() {
        this.panelX = this.screen.guiLeft();
        this.panelY = this.screen.guiTop();
        this.toolbarX = this.panelX + PANEL_WIDTH + GAP;
        this.editButtons.clear();

        int rowA = this.toolbarX;
        int rowB = this.toolbarX + COLUMN_STEP;
        int y = this.panelY;

        this.editButtons.add(this.screen.addEditorButton(
                Component.translatable("gui.meknh_core.ui_editor.new"),
                this::addElement, rowA, y, BUTTON_WIDTH, 18));
        this.editButtons.add(this.screen.addEditorButton(
                Component.translatable("gui.meknh_core.ui_editor.delete"),
                this::removeSelected, rowA, y + 20, BUTTON_WIDTH, 18));
        this.editButtons.add(this.screen.addEditorButton(
                Component.translatable("gui.meknh_core.ui_editor.save"),
                this::saveAndExport, rowA, y + 40, BUTTON_WIDTH, 18));
        this.editButtons.add(this.screen.addEditorButton(
                Component.translatable("gui.meknh_core.ui_editor.reset"),
                this::resetDefaults, rowA, y + 60, BUTTON_WIDTH, 18));
        this.screen.addEditorButton(Component.translatable("gui.meknh_core.ui_editor.close"),
                this::close, rowA, y + 80, BUTTON_WIDTH, 18);

        this.screen.addEditorButton(Component.translatable("gui.meknh_core.ui_editor.copy"),
                this::copyLayout, rowB, y, BUTTON_WIDTH, 18);
        this.editButtons.add(this.screen.addEditorButton(
                Component.translatable("gui.meknh_core.ui_editor.paste"),
                this::pasteLayout, rowB, y + 20, BUTTON_WIDTH, 18));
        this.editButtons.add(this.screen.addEditorButton(
                Component.translatable("gui.meknh_core.ui_editor.copy_from"),
                this::openLayoutPicker, rowB, y + 40, BUTTON_WIDTH, 18));

        this.readOnlyButton = this.screen.addEditorButton(
                Component.translatable("gui.meknh_core.ui_editor.read_only"),
                this::toggleReadOnly, rowB, y + 60, BUTTON_WIDTH, 18);

        int arrowY = y + 128;
        int centerX = this.toolbarX + (TOOLBAR_WIDTH - 18) / 2;
        this.editButtons.add(this.screen.addEditorButton(Component.literal("▲"), () -> this.nudge(0, -1),
                centerX, arrowY, 18, 18));
        this.editButtons.add(this.screen.addEditorButton(Component.literal("◀"), () -> this.nudge(-1, 0),
                centerX - 20, arrowY + 20, 18, 18));
        this.editButtons.add(this.screen.addEditorButton(Component.literal("▼"), () -> this.nudge(0, 1),
                centerX, arrowY + 20, 18, 18));
        this.editButtons.add(this.screen.addEditorButton(Component.literal("▶"), () -> this.nudge(1, 0),
                centerX + 20, arrowY + 20, 18, 18));

        // 元素重命名输入框（放在底板下面）
        this.nameField = new EditBox(this.screen.editorFont(), this.panelX, this.panelY + PANEL_HEIGHT + 6,
                PANEL_WIDTH, 16, Component.translatable("gui.meknh_core.ui_editor.name"));
        this.nameField.setMaxLength(48);
        this.nameField.setResponder(text -> {
            if (!this.syncingName && this.selected >= 0 && this.selected < this.elements.size()) {
                this.elements.get(this.selected).setName(text);
            }
        });
        this.screen.addEditorWidget(this.nameField);
        this.syncNameField();
        this.applyReadOnlyState();
    }

    private void syncNameField() {
        if (this.nameField == null) {
            return;
        }
        this.syncingName = true;
        this.nameField.setValue(this.selected >= 0 && this.selected < this.elements.size()
                ? this.elements.get(this.selected).name()
                : "");
        this.syncingName = false;
    }

    /**
     * 画在机器界面上面。
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 工具栏底色
        graphics.fill(this.toolbarX - 4, this.panelY - 4,
                this.toolbarX + TOOLBAR_WIDTH + 4, this.panelY + PANEL_HEIGHT + 4, 0x90000000);

        graphics.drawString(this.screen.editorFont(), Component.translatable("gui.meknh_core.ui_editor.title",
                        this.screen.getTitle()),
                this.panelX, Math.max(2, this.panelY - 12), 0xFFFFFF, true);
        if (this.readOnly) {
            graphics.drawString(this.screen.editorFont(), Component.translatable("gui.meknh_core.ui_editor.read_only_tag"),
                    this.panelX + PANEL_WIDTH - 18, Math.max(2, this.panelY - 12), 0xFFFFD54F, true);
        }

        for (int index = 0; index < this.elements.size(); index++) {
            this.renderElement(graphics, index);
        }

        this.renderSelectionInfo(graphics);
        graphics.drawString(this.screen.editorFont(), Component.translatable("gui.meknh_core.ui_editor.hint"),
                this.panelX, this.panelY + PANEL_HEIGHT + 26, HINT_COLOR, false);

        // 按钮原本会被机器界面先画一遍，这里在覆盖层之后再画一次保证在最上面
        for (Renderable renderable : this.screen.editorRenderables()) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderElement(GuiGraphics graphics, int index) {
        UiElement element = this.elements.get(index);
        int x = this.panelX + element.x();
        int y = this.panelY + element.y();
        int width = element.width();
        int height = element.height();

        boolean drawn = UiElementRenderer.render(graphics, element, this.panelX, this.panelY,
                this.screen.storageRatio(), this.screen.progressRatio(), this.screen.steamRatio());
        if (!drawn) {
            graphics.fill(x, y, x + width, y + height, 0x33FFFFFF);
            graphics.renderOutline(x, y, width, height, OUTLINE);
        }

        if (index == this.selected) {
            graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, SELECTED_OUTLINE);
            if (!this.readOnly) {
                graphics.fill(x + width - 5, y + height - 5, x + width, y + height, SELECTED_OUTLINE);
            }
        }

        graphics.drawString(this.screen.editorFont(), element.displayName(), x + 1, y + 1, 0xFFFFFFFF, true);
        graphics.drawString(this.screen.editorFont(), width + "x" + height, x + 1, y + height - 9, 0xFFCCCCCC, true);
    }

    private void renderSelectionInfo(GuiGraphics graphics) {
        int y = this.panelY + PANEL_HEIGHT - 24;
        if (this.selected < 0 || this.selected >= this.elements.size()) {
            graphics.drawString(this.screen.editorFont(), Component.translatable("gui.meknh_core.ui_editor.none"),
                    this.toolbarX, y, HINT_COLOR, false);
            return;
        }
        UiElement element = this.elements.get(this.selected);
        graphics.drawString(this.screen.editorFont(), element.displayName(), this.toolbarX, y, 0xFFFFFF, false);
        graphics.drawString(this.screen.editorFont(), element.x() + ", " + element.y(),
                this.toolbarX, y + 10, HINT_COLOR, false);
        graphics.drawString(this.screen.editorFont(), element.width() + " x " + element.height(),
                this.toolbarX, y + 20, HINT_COLOR, false);
    }

    /**
     * @return {@code true} 表示这次点击被编辑器吃掉，不再传给机器界面
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.nameField != null && this.nameField.visible && this.nameField.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (mouseX >= this.toolbarX - 4 && mouseX < this.toolbarX + TOOLBAR_WIDTH + 4
                && mouseY >= this.panelY - 16 && mouseY < this.panelY + PANEL_HEIGHT + 4) {
            return false;
        }

        double localX = mouseX - this.panelX;
        double localY = mouseY - this.panelY;
        int hit = this.elementAt(localX, localY);
        if (hit < 0) {
            this.selected = -1;
            this.syncNameField();
            return false;
        }

        this.selected = hit;
        this.syncNameField();
        UiElement element = this.elements.get(hit);

        if (this.readOnly) {
            return true;
        }

        if (button == 1) {
            this.screen.openTexturePicker(element, this::applyTexture);
            return true;
        }
        if (button == 0) {
            boolean onHandle = localX >= element.x() + element.width() - 6
                    && localY >= element.y() + element.height() - 6;
            this.dragMode = onHandle ? DragMode.RESIZE : DragMode.MOVE;
            this.grabX = localX;
            this.grabY = localY;
            return true;
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.readOnly) {
            return false;
        }
        if (this.selected >= 0 && this.selected < this.elements.size() && this.dragMode != DragMode.NONE) {
            UiElement element = this.elements.get(this.selected);
            double localX = mouseX - this.panelX;
            double localY = mouseY - this.panelY;
            if (this.dragMode == DragMode.MOVE) {
                element.setX(clamp((int) Math.round(localX - this.grabX), -64, 320));
                element.setY(clamp((int) Math.round(localY - this.grabY), -64, 320));
            } else {
                element.setWidth(clamp((int) Math.round(localX - element.x()), 4, 400));
                element.setHeight(clamp((int) Math.round(localY - element.y()), 4, 400));
            }
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        this.dragMode = DragMode.NONE;
    }

    public boolean keyPressed(int keyCode) {
        if (this.readOnly || (this.nameField != null && this.nameField.isFocused())) {
            return false;
        }
        if (this.selected < 0) {
            return false;
        }
        int step = Screen.hasShiftDown() ? 5 : 1;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> this.nudge(-step, 0);
            case GLFW.GLFW_KEY_RIGHT -> this.nudge(step, 0);
            case GLFW.GLFW_KEY_UP -> this.nudge(0, -step);
            case GLFW.GLFW_KEY_DOWN -> this.nudge(0, step);
            case GLFW.GLFW_KEY_DELETE -> this.removeSelected();
            default -> {
                return false;
            }
        }
        return true;
    }

    public void saveNow() {
        MachineUiLayouts.save(this.definition.id(), this.elements);
    }

    // ------------------------------------------------------------------
    // 只读 / 复制
    // ------------------------------------------------------------------

    private void toggleReadOnly() {
        this.readOnly = !this.readOnly;
        this.applyReadOnlyState();
    }

    private void applyReadOnlyState() {
        for (Button button : this.editButtons) {
            button.active = !this.readOnly;
        }
        if (this.nameField != null) {
            this.nameField.setEditable(!this.readOnly);
        }
        if (this.readOnlyButton != null) {
            this.readOnlyButton.setMessage(Component.translatable(this.readOnly
                    ? "gui.meknh_core.ui_editor.edit_mode"
                    : "gui.meknh_core.ui_editor.read_only"));
        }
    }

    private void copyLayout() {
        CLIPBOARD.clear();
        for (UiElement element : this.elements) {
            CLIPBOARD.add(element.copy());
        }
        this.screen.notifyPlayer(Component.translatable("gui.meknh_core.ui_editor.copied", this.elements.size()));
    }

    private void pasteLayout() {
        if (this.readOnly || CLIPBOARD.isEmpty()) {
            return;
        }
        this.elements.clear();
        for (UiElement element : CLIPBOARD) {
            this.elements.add(element.copy());
        }
        this.selected = -1;
        this.syncNameField();
        this.screen.notifyPlayer(Component.translatable("gui.meknh_core.ui_editor.pasted", this.elements.size()));
    }

    private void openLayoutPicker() {
        if (this.readOnly) {
            return;
        }
        this.screen.openEditorScreen(new LayoutPickerScreen(this.screen, machineId -> {
            List<UiElement> layout = MachineUiLayouts.saved(machineId);
            if (layout == null) {
                return;
            }
            this.elements.clear();
            this.elements.addAll(layout);
            this.selected = -1;
            this.syncNameField();
            this.screen.notifyPlayer(Component.translatable("gui.meknh_core.ui_editor.copied_from",
                    layout.size(), machineId.toString()));
        }));
    }

    /**
     * 「保存」按钮：更新运行时布局，并把 JSON 导出到
     * {@code src/main/resources/data/gui/<机器ID>.json}（非开发环境退回
     * {@code <游戏目录>/data/gui/}）。
     */
    private void saveAndExport() {
        if (this.readOnly) {
            return;
        }
        this.saveNow();
        Path path = MachineUiLayouts.export(this.definition.id(), this.elements);
        this.screen.notifyPlayer(Component.translatable("gui.meknh_core.ui_editor.exported", path.toString()));
    }

    private void applyTexture(@Nullable ResourceLocation texture) {
        if (this.readOnly) {
            return;
        }
        if (this.selected >= 0 && this.selected < this.elements.size()) {
            this.elements.get(this.selected).setTexture(texture);
        }
    }

    private void addElement() {
        if (this.readOnly) {
            return;
        }
        this.elements.add(new UiElement(UiElementKind.CUSTOM, 60, 60, 18, 18));
        this.selected = this.elements.size() - 1;
        this.syncNameField();
    }

    private void removeSelected() {
        if (this.readOnly) {
            return;
        }
        if (this.selected >= 0 && this.selected < this.elements.size()) {
            this.elements.remove(this.selected);
            this.selected = -1;
            this.dragMode = DragMode.NONE;
            this.syncNameField();
        }
    }

    private void nudge(int dx, int dy) {
        if (this.readOnly || this.selected < 0 || this.selected >= this.elements.size()) {
            return;
        }
        UiElement element = this.elements.get(this.selected);
        element.setX(clamp(element.x() + dx, -64, 320));
        element.setY(clamp(element.y() + dy, -64, 320));
    }

    private void resetDefaults() {
        if (this.readOnly) {
            return;
        }
        this.elements.clear();
        this.elements.addAll(MachineUiLayouts.defaults(this.definition, this.screen.machineLevel()));
        this.selected = -1;
        this.dragMode = DragMode.NONE;
        this.syncNameField();
        this.saveNow();
    }

    private void close() {
        this.saveNow();
        // AbstractContainerScreen#onClose 会关掉服务端的机器容器
        this.screen.onClose();
    }

    private int elementAt(double localX, double localY) {
        for (int index = this.elements.size() - 1; index >= 0; index--) {
            if (this.elements.get(index).contains(localX, localY)) {
                return index;
            }
        }
        return -1;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
