package c_y.meknh_core.client.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 贴图选择器：列出资源包里所有 GUI 贴图（{@code textures/gui/**} 与
 * Mekanism 那种 {@code gui/**}），支持搜索、翻页，点一下就把贴图交给回调。
 */
public class TexturePickerScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int HEIGHT = 210;
    private static final int COLUMNS = 8;
    private static final int ROWS = 5;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int CELL = 24;
    private static final int GRID_LEFT = 10;
    private static final int GRID_TOP = 46;

    private final Screen parent;
    private final UiElement target;
    private final Consumer<ResourceLocation> onPick;
    private final List<ResourceLocation> all;
    private List<ResourceLocation> filtered;
    private int page;
    private int left;
    private int top;
    @Nullable
    private EditBox search;

    public TexturePickerScreen(Screen parent, UiElement target, Consumer<ResourceLocation> onPick) {
        super(Component.translatable("gui.meknh_core.ui_editor.texture"));
        this.parent = parent;
        this.target = target;
        this.onPick = onPick;
        this.all = collectTextures();
        this.filtered = this.all;
    }

    @Override
    protected void init() {
        this.left = Math.max(4, (this.width - WIDTH) / 2);
        this.top = Math.max(4, (this.height - HEIGHT) / 2);

        this.search = new EditBox(this.font, this.left + GRID_LEFT, this.top + 24, WIDTH - GRID_LEFT * 2, 16,
                Component.translatable("gui.meknh_core.ui_editor.search"));
        this.search.setMaxLength(64);
        this.search.setResponder(text -> {
            this.page = 0;
            this.applyFilter(text);
        });
        this.addRenderableWidget(this.search);

        this.addRenderableWidget(Button.builder(Component.literal("◀"), button -> this.turnPage(-1))
                .bounds(this.left + GRID_LEFT, this.top + HEIGHT - 30, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), button -> this.turnPage(1))
                .bounds(this.left + WIDTH - GRID_LEFT - 20, this.top + HEIGHT - 30, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.meknh_core.ui_editor.texture_none"),
                        button -> this.pick(null))
                .bounds(this.left + WIDTH / 2 - 44, this.top + HEIGHT - 30, 88, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(this.left - 4, this.top - 4, this.left + WIDTH + 4, this.top + HEIGHT + 4, 0xF0101010);
        graphics.fill(this.left + GRID_LEFT - 2, this.top + GRID_TOP - 2,
                this.left + GRID_LEFT + COLUMNS * CELL + 2, this.top + GRID_TOP + ROWS * CELL + 2, 0x40000000);

        graphics.drawString(this.font, this.title, this.left + GRID_LEFT, this.top + 8, 0xFFFFFF, false);

        this.renderGrid(graphics, mouseX, mouseY);

        String pageText = (this.page + 1) + " / " + this.pageCount();
        graphics.drawString(this.font, pageText,
                this.left + WIDTH / 2 - this.font.width(pageText) / 2,
                this.top + HEIGHT - 25, 0xFFFFFF, false);

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int start = this.page * PAGE_SIZE;
        ResourceLocation current = this.target.texture();

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= this.filtered.size()) {
                break;
            }
            int column = slot % COLUMNS;
            int row = slot / COLUMNS;
            int x = this.left + GRID_LEFT + column * CELL;
            int y = this.top + GRID_TOP + row * CELL;

            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            if (hovered) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x50FFFFFF);
            }

            ResourceLocation texture = this.filtered.get(index);
            graphics.blit(texture, x + 4, y + 4, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);

            if (texture.equals(current)) {
                graphics.renderOutline(x, y, CELL, CELL, 0xFFFFD54F);
            }

            if (hovered) {
                graphics.renderTooltip(this.font, Component.literal(texture.toString()), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        double gridX = mouseX - (this.left + GRID_LEFT);
        double gridY = mouseY - (this.top + GRID_TOP);
        if (gridX < 0 || gridY < 0) {
            return false;
        }
        int column = (int) (gridX / CELL);
        int row = (int) (gridY / CELL);
        if (column >= COLUMNS || row >= ROWS) {
            return false;
        }

        int index = this.page * PAGE_SIZE + row * COLUMNS + column;
        if (index >= this.filtered.size()) {
            return false;
        }
        this.pick(this.filtered.get(index));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.back();
    }

    private void back() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void pick(@Nullable ResourceLocation texture) {
        this.onPick.accept(texture);
        this.back();
    }

    private void turnPage(int delta) {
        this.page = Math.max(0, Math.min(this.pageCount() - 1, this.page + delta));
    }

    private int pageCount() {
        return Math.max(1, (this.filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void applyFilter(String text) {
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            this.filtered = this.all;
            return;
        }
        List<ResourceLocation> matches = new ArrayList<>();
        for (ResourceLocation texture : this.all) {
            if (texture.toString().toLowerCase(Locale.ROOT).contains(query)) {
                matches.add(texture);
            }
        }
        this.filtered = matches;
        this.page = Math.min(this.page, this.pageCount() - 1);
    }

    /**
     * 资源包里所有 GUI 贴图：{@code assets/<ns>/textures/gui/**} 与
     * {@code assets/<ns>/gui/**}（Mekanism 的贴图不在 {@code textures/} 下）。
     */
    private static List<ResourceLocation> collectTextures() {
        Minecraft minecraft = Minecraft.getInstance();
        Set<ResourceLocation> found = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        found.addAll(minecraft.getResourceManager()
                .listResources("textures/gui", location -> location.getPath().endsWith(".png"))
                .keySet());
        found.addAll(minecraft.getResourceManager()
                .listResources("gui", location -> location.getPath().endsWith(".png"))
                .keySet());
        return new ArrayList<>(found);
    }
}
