package c_y.meknh_core.client.editor;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 「从其他机器复制」的选择器：列出已经保存过布局的机器 ID，
 * 点一个就把那台机器的元素（位置、大小、贴图、名称）整套复制过来。
 *
 * <p>打开这个界面不会关掉机器容器：{@code MachineScreen#removed()} 已经跳过了关闭容器。</p>
 */
public class LayoutPickerScreen extends Screen {

    private static final int WIDTH = 210;
    private static final int HEIGHT = 190;
    private static final int ROW_HEIGHT = 20;
    private static final int MAX_ROWS = 6;

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    private final List<ResourceLocation> layouts;
    private int page;
    private int left;
    private int top;

    public LayoutPickerScreen(Screen parent, Consumer<ResourceLocation> onPick) {
        super(Component.translatable("gui.meknh_core.ui_editor.copy_from"));
        this.parent = parent;
        this.onPick = onPick;
        this.layouts = MachineUiLayouts.availableLayouts();
    }

    @Override
    protected void init() {
        this.left = Math.max(4, (this.width - WIDTH) / 2);
        this.top = Math.max(4, (this.height - HEIGHT) / 2);

        int start = this.page * MAX_ROWS;
        for (int row = 0; row < MAX_ROWS; row++) {
            int index = start + row;
            if (index >= this.layouts.size()) {
                break;
            }
            ResourceLocation id = this.layouts.get(index);
            this.addRenderableWidget(Button.builder(Component.literal(id.toString()),
                            button -> this.pick(id))
                    .bounds(this.left + 8, this.top + 26 + row * ROW_HEIGHT, WIDTH - 16, 18).build());
        }

        if (this.page > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("◀"), button -> this.turnPage(-1))
                    .bounds(this.left + 8, this.top + HEIGHT - 26, 20, 18).build());
        }
        if ((this.page + 1) * MAX_ROWS < this.layouts.size()) {
            this.addRenderableWidget(Button.builder(Component.literal("▶"), button -> this.turnPage(1))
                    .bounds(this.left + WIDTH - 28, this.top + HEIGHT - 26, 20, 18).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.left - 4, this.top - 4,
                this.left + WIDTH + 4, this.top + HEIGHT + 4, 0xF0101010);
        graphics.drawString(this.font, this.title, this.left + 8, this.top + 8, 0xFFFFFF, false);

        if (this.layouts.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.meknh_core.ui_editor.no_layouts"),
                    this.left + 8, this.top + 30, 0xFFB0B0B0, false);
        }

        String pageText = (this.page + 1) + " / " + Math.max(1, (this.layouts.size() + MAX_ROWS - 1) / MAX_ROWS);
        graphics.drawString(this.font, pageText,
                this.left + WIDTH / 2 - this.font.width(pageText) / 2,
                this.top + HEIGHT - 21, 0xFFFFFF, false);

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void pick(ResourceLocation machineId) {
        this.onPick.accept(machineId);
        this.onClose();
    }

    private void turnPage(int delta) {
        this.page = Math.max(0, this.page + delta);
        this.init(this.minecraft, this.width, this.height);
    }
}
