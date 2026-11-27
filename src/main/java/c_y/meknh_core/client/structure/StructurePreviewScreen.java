package c_y.meknh_core.client.structure;

import c_y.meknh_core.api.multiblock.MultiblockMatcher;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.appliedenergistics.yoga.YogaPositionType;

/** Client-only LDLib2 inspection. The exact JEI screen remains the return target. */
public final class StructurePreviewScreen extends ModularUIScreen {
    private static final int INK = StructurePreviewPalette.BODY_TEXT;
    private final Screen parent;
    private final StructurePreview preview;
    private final UIElement root;
    private final Map<c_y.meknh_core.api.multiblock.MultiblockStructure.Cell, List<BlockState>> candidateCache = new HashMap<>();
    private final Map<BlockState, Integer> materials = new LinkedHashMap<>();
    private StructureLdlibScene scene;
    private UIElement selectionPanel;
    private int sideWidth;
    private int panelHeight;
    private boolean showReplaceable;
    private int layer = -1;
    private BlockPos selected;

    public StructurePreviewScreen(Screen parent, StructurePreview preview) {
        this(parent, preview, new UIElement());
    }

    private StructurePreviewScreen(Screen parent, StructurePreview preview, UIElement root) {
        super(ModularUI.of(UI.of(root), net.minecraft.client.Minecraft.getInstance().player), preview.title());
        this.parent = parent;
        this.preview = preview;
        this.root = root;
        Map<MultiblockMatcher, BlockState> cache = new HashMap<>();
        for (var cell : preview.structure().cells()) {
            BlockState state = cache.computeIfAbsent(cell.matcher(), preview::state);
            if (!state.isAir()) materials.merge(state, 1, Integer::sum);
        }
    }

    @Override public void init() {
        if (scene != null) scene.close();
        root.clearAllChildren();
        root.layout(s -> s.width(width).height(height));
        root.style(s -> s.background(new ColorRectTexture(0xFFC6C6C6)));
        sideWidth = Math.max(64, Math.min(160, (width - 32) / 4));
        panelHeight = Math.max(70, height - 70);
        int centerX = sideWidth + 14;
        int rightX = width - sideWidth - 8;
        int centerWidth = Math.max(1, rightX - centerX - 6);
        root.addChild(label(preview.title(), 10, 7, width - 20, 18));
        selectionPanel = panel(8, 31, sideWidth, panelHeight);
        root.addChild(selectionPanel);
        UIElement center = panel(centerX, 31, centerWidth, panelHeight);
        center.addChild(label(Component.literal("结构预览"), 6, 5, centerWidth - 12, 15));
        scene = new StructureLdlibScene(preview, this::select);
        scene.element().setShowHoverBlockTips(true);
        place(scene.element(), 3, 24, centerWidth - 6, Math.max(1, panelHeight - 44));
        center.addChild(scene.element());
        scene.setLayer(layer);
        scene.setShowReplaceable(showReplaceable);
        center.addChild(label(Component.literal("拖动旋转 · 滚轮缩放 · 点击选中"),
                5, panelHeight - 17, centerWidth - 10, 14));
        root.addChild(center);
        root.addChild(buildInformation(rightX));
        refreshSelection();
        int bw = Math.max(40, Math.min(100, (width - 28) / 2));
        root.addChild(button(Component.translatable("meknh.structure.reset"), width / 2 - bw - 3,
                height - 28, bw, () -> {
                    layer = -1;
                    selected = null;
                    scene.setLayer(-1);
                    scene.reset();
                    rebuildInformation(rightX);
                    refreshSelection();
                }));
        root.addChild(button(Component.translatable("gui.back"), width / 2 + 3,
                height - 28, bw, this::onClose));
        super.init();
    }

    private UIElement information;

    private void rebuildInformation(int x) {
        if (information != null) root.removeChild(information);
        root.addChild(buildInformation(x));
    }

    private UIElement buildInformation(int x) {
        information = panel(x, 31, sideWidth, panelHeight);
        information.addChild(label(Component.literal("结构信息"), 6, 5, sideWidth - 12, 15));
        information.addChild(label(Component.literal(preview.dimensions()), 6, 24, sideWidth - 12, 15));
        information.addChild(label(Component.literal("多方块所需材料"), 6, 43, sideWidth - 12, 15));
        ScrollerView list = scroll(5, 62, sideWidth - 16, Math.max(1, panelHeight - 139));
        int materialIndex = 0;
        for (var entry : materials.entrySet()) {
            UIElement row = itemCard(entry.getKey(), entry.getValue() + " × ", sideWidth - 24);
            final int gap = materialIndex++ == 0 ? 0 : 1;
            row.layout(s -> s.marginTop(gap));
            // Icon stays display-only; this click never performs an inventory action.
            row.addEventListener(UIEvents.CLICK, event -> {
                c_y.meknh_core.client.jei.MeknhJeiPlugin.openItemRecipes(
                        new ItemStack(entry.getKey().getBlock().asItem()));
                event.stopPropagation();
            });
            list.addScrollViewChild(row);
        }
        information.addChild(list);
        information.addChild(label(Component.literal("标签/部件可替换，非精确用量"),
                6, panelHeight - 74, sideWidth - 12, 17));
        information.addChild(button(Component.literal("可替换位置：" + (showReplaceable ? "开" : "关")),
                6, panelHeight - 51, sideWidth - 12, () -> {
                    showReplaceable = !showReplaceable;
                    scene.setShowReplaceable(showReplaceable);
                    rebuildInformation(x);
                }));
        String value = layer < 0 ? "全部" : (layer + 1) + " / " + preview.structure().sizeY();
        int arrowWidth = Math.min(20, Math.max(12, (sideWidth - 16) / 5));
        Button previous = button(Component.literal("<"), 6, panelHeight - 27,
                arrowWidth, () -> changeLayer(-1, x));
        Button layerButton = button(Component.literal("显示层：" + value), 8 + arrowWidth,
                panelHeight - 27, sideWidth - 16 - 2 * arrowWidth, () -> applyLayer(-1, x));
        layerButton.style(s -> s.tooltips(Component.literal("滚轮切换显示层 · 点击显示全部")));
        Button next = button(Component.literal(">"), sideWidth - 6 - arrowWidth,
                panelHeight - 27, arrowWidth, () -> changeLayer(1, x));
        for (Button control : List.of(previous, layerButton, next)) {
            control.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
                if (event.deltaY != 0) changeLayer(event.deltaY > 0 ? 1 : -1, x);
                event.stopPropagation();
            });
            information.addChild(control);
        }
        return information;
    }

    private void changeLayer(int direction, int informationX) {
        // Include "all" as index zero; floorMod supports backwards wrapping from all.
        applyLayer(StructureViewState.stepLayer(layer, preview.structure().sizeY(), direction), informationX);
    }

    private void applyLayer(int next, int informationX) {
        layer = next;
        selected = null;
        scene.setLayer(layer);
        refreshSelection();
        rebuildInformation(informationX);
    }

    private void select(BlockPos pos) {
        selected = pos.immutable();
        refreshSelection();
    }

    private void refreshSelection() {
        selectionPanel.clearAllChildren();
        selectionPanel.addChild(label(Component.literal("选中方块"), 6, 5, sideWidth - 12, 15));
        if (selected == null) {
            selectionPanel.addChild(label(Component.literal("点击中央模型查看方块"),
                    6, 27, sideWidth - 12, 36));
            return;
        }
        for (var cell : preview.structure().cells()) {
            if (cell.dx() != selected.getX() || cell.dy() != selected.getY() || cell.dz() != selected.getZ()) continue;
            List<BlockState> candidates = candidateCache.computeIfAbsent(cell, preview::candidates);
            UIElement current = itemCard(scene.stateAt(selected), "", sideWidth - 12);
            place(current, 6, 24, sideWidth - 12, 18);
            selectionPanel.addChild(current);
            selectionPanel.addChild(label(Component.literal("位置 " + selected.getX() + ", "
                    + selected.getY() + ", " + selected.getZ()), 6, 44, sideWidth - 12, 24));
            selectionPanel.addChild(label(Component.literal("此位置可用方块（" + candidates.size() + "）"),
                    6, 69, sideWidth - 12, 9));
            int columns = candidateColumns(sideWidth - 20);
            int groupWidth = StructureCandidateGrid.width(columns);
            List<List<BlockState>> groups = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(category -> candidates.stream()
                            .filter(state -> StructurePreview.candidateGroup(state) == category).toList())
                    .filter(group -> !group.isEmpty()).toList();
            int contentHeight = 6 + Math.max(0, groups.size() - 1) * 2;
            for (List<BlockState> group : groups) contentHeight += candidateGridHeight(group.size(), columns);
            ScrollerView list = scroll(5, 81, groupWidth + 10,
                    Math.max(1, Math.min(contentHeight, panelHeight - 87)));
            list.style(s -> s.background(new ColorRectTexture(StructurePreviewPalette.CANDIDATE_REGION)));
            list.viewPort.layout(s -> s.paddingAll(3));
            list.viewPort.style(s -> s.background(new ColorRectTexture(0)));
            for (int index = 0; index < groups.size(); index++) {
                UIElement frame = candidateGroupFrame(groupWidth, groups.get(index));
                final int gap = index == 0 ? 0 : 2;
                frame.layout(s -> s.marginTop(gap));
                list.addScrollViewChild(frame);
            }
            selectionPanel.addChild(list);
            break;
        }
    }

    private static int candidateColumns(int width) {
        // 1px border + 2px clear space on each edge, 16px icons, 2px gaps.
        return StructureCandidateGrid.columns(width);
    }

    private static int candidateGridHeight(int count, int columns) {
        return StructureCandidateGrid.height(count, columns);
    }

    private static UIElement candidateGroupFrame(int width, List<BlockState> states) {
        UIElement frame = new UIElement();
        int columns = candidateColumns(width);
        frame.layout(s -> s.width(width).height(candidateGridHeight(states.size(), columns)).flexShrink(0));
        frame.style(s -> s.background(new GuiTextureGroup(
                new ColorRectTexture(StructurePreviewPalette.LIST_ROW),
                new ColorBorderTexture(-1, StructurePreviewPalette.LIST_BORDER))));
        frame.addChild(candidateLimitHeader(width - 6, null, null));
        for (int i = 0; i < states.size(); i++) {
            BlockState state = states.get(i);
            UIElement target = new UIElement();
            place(target, StructureCandidateGrid.x(i, columns), StructureCandidateGrid.y(i, columns), 16, 16);
            target.style(s -> s.tooltips(state.getBlock().getName()));
            ItemSlot icon = new ItemSlot();
            icon.setItem(new ItemStack(state.getBlock().asItem()));
            icon.style(s -> s.backgroundTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
            icon.layout(s -> s.paddingAll(0));
            icon.slotStyle(s -> s.hoverOverlay(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)
                    .slotOverlay(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
            icon.setAllowHitTest(false);
            place(icon, 0, 0, 16, 16);
            target.addChild(icon);
            itemHoverFeedback(target);
            target.addEventListener(UIEvents.CLICK, event -> {
                c_y.meknh_core.client.jei.MeknhJeiPlugin.openItemRecipes(new ItemStack(state.getBlock().asItem()));
                event.stopPropagation();
            });
            frame.addChild(target);
        }
        return frame;
    }

    private static UIElement itemCard(BlockState state, String prefix, int width) {
        UIElement card = new UIElement();
        // Container assigns exact one-pixel gaps; cards have no intrinsic margins.
        card.layout(s -> s.width(width).height(18).flexShrink(0));
        card.style(s -> s.background(new GuiTextureGroup(
                new ColorRectTexture(StructurePreviewPalette.LIST_ROW),
                // Negative thickness draws inward, so scissoring cannot clip the border.
                new ColorBorderTexture(-1, StructurePreviewPalette.LIST_BORDER)))
                .tooltips(state.getBlock().getName()));
        UIElement contents = itemRow(state, prefix, Math.max(1, width - 2));
        place(contents, 1, 1, Math.max(1, width - 2), 16);
        card.addChild(contents);
        itemHoverFeedback(card);
        return card;
    }

    /** Draw above the icon/text without adding an input-intercepting child or sticky hover state. */
    private static void itemHoverFeedback(UIElement target) {
        var highlight = new ColorRectTexture(StructurePreviewPalette.ITEM_HOVER);
        target.style(s -> s.overlayTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.dynamic(
                () -> target.isSelfOrChildHover() ? highlight : com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY)));
    }

    /** Future structure-config adapter supplies real limits; null must never imply a made-up value. */
    private static UIElement candidateLimitHeader(int width, Integer minimum, Integer maximum) {
        if (minimum == null && maximum == null) {
            UIElement gap = new UIElement();
            gap.layout(s -> s.width(width).height(0).flexShrink(0));
            return gap;
        }
        String text = (minimum == null ? "" : "最少：" + minimum)
                + (minimum != null && maximum != null ? "  " : "")
                + (maximum == null ? "" : "最多：" + maximum);
        return label(Component.literal(text), 0, 0, width, 18);
    }

    private static UIElement itemRow(BlockState state, String prefix, int width) {
        UIElement row = new UIElement();
        row.layout(s -> s.width(width).height(16).flexShrink(0));
        row.style(s -> s.background(new ColorRectTexture(StructurePreviewPalette.LIST_ROW))
                .tooltips(state.getBlock().getName()));
        ItemSlot icon = new ItemSlot();
        icon.setItem(new ItemStack(state.getBlock().asItem()));
        icon.style(s -> s.backgroundTexture(com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture.EMPTY));
        icon.layout(s -> s.paddingAll(0));
        // A display only: never bind a player/container Slot or send inventory actions.
        icon.setAllowHitTest(false);
        place(icon, 0, 0, 16, 16);
        row.addChild(icon);
        Label name = label(Component.literal(prefix).append(state.getBlock().getName()),
                20, 0, Math.max(1, width - 20), 16);
        name.textStyle(s -> s.textColor(StructurePreviewPalette.CONTROL_TEXT).textShadow(true)
                .textAlignVertical(com.lowdragmc.lowdraglib2.gui.ui.data.Vertical.CENTER));
        row.addChild(name);
        return row;
    }

    private static ScrollerView scroll(int x, int y, int width, int height) {
        ScrollerView list = new ScrollerView();
        place(list, x, y, width, height);
        list.scrollerStyle(s -> s.mode(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode.VERTICAL)
                .horizontalScrollDisplay(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay.NEVER)
                .verticalScrollDisplay(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay.AUTO)
                .scrollerViewStyle(0).adaptiveWidth(false));
        // Reserve the gutter even without overflow so the frame-to-card gap never changes.
        list.viewPort.layout(s -> s.width(width - 4).minWidth(width - 4).maxWidth(width - 4).flexShrink(0));
        list.verticalScroller.layout(s -> s.width(4).minWidth(4).maxWidth(4)
                .flexShrink(0).marginAll(0).paddingAll(0));
        list.verticalScroller.headButton.setDisplay(false);
        list.verticalScroller.tailButton.setDisplay(false);
        list.verticalScroller.scrollContainer.style(s -> s.background(new ColorRectTexture(StructurePreviewPalette.LIST_ROW)));
        var thumb = new GuiTextureGroup(new ColorRectTexture(StructurePreviewPalette.LIST_ROW),
                new ColorBorderTexture(-1, StructurePreviewPalette.LIST_BORDER));
        list.verticalScroller.scrollBar.getButtonStyle().baseTexture(thumb).hoverTexture(thumb).pressedTexture(thumb);
        list.verticalScroller.scrollBar.layout(s -> s.width(4).minWidth(4).maxWidth(4).paddingAll(0).marginAll(0));
        // One-pixel frame + one-pixel inner clearance; unlike Sprites.BORDER, no hidden 5px chrome.
        list.viewPort.layout(s -> s.paddingAll(2));
        list.viewPort.style(s -> s.background(new GuiTextureGroup(
                new ColorRectTexture(StructurePreviewPalette.LIST_ROW),
                new ColorBorderTexture(-1, StructurePreviewPalette.LIST_BORDER))));
        return list;
    }

    private static UIElement panel(int x, int y, int width, int height) {
        UIElement panel = new UIElement();
        place(panel, x, y, width, height);
        panel.style(s -> s.background(new ColorRectTexture(StructurePreviewPalette.PANEL)));
        return panel;
    }

    private static Label label(Component text, int x, int y, int width, int height) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(s -> s.textColor(INK).textShadow(false).adaptiveWidth(false).adaptiveHeight(false));
        place(label, x, y, width, height);
        return label;
    }

    private static Button button(Component text, int x, int y, int width, Runnable action) {
        Button button = new Button();
        button.setText(text);
        button.textStyle(s -> s.textColor(StructurePreviewPalette.CONTROL_TEXT).textShadow(true));
        button.getButtonStyle()
                .baseTexture(mekanismButtonTexture(1))
                .hoverTexture(mekanismButtonTexture(2))
                .pressedTexture(mekanismButtonTexture(0));
        button.setOnClick(event -> action.run());
        place(button, x, y, width, 20);
        return button;
    }

    /** Use Mek's own nine-slice renderer: it clamps borders for narrow arrow buttons. */
    private static com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture mekanismButtonTexture(int state) {
        return new com.lowdragmc.lowdraglib2.gui.texture.TransformTexture() {
            @Override protected void drawInternal(net.minecraft.client.gui.GuiGraphics graphics,
                    float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks) {
                int w = Math.round(width), h = Math.round(height);
                if (w <= 0 || h <= 0) return;
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                mekanism.client.gui.GuiUtils.blitNineSlicedSized(graphics,
                        mekanism.client.gui.element.GuiElement.ButtonBackground.DEFAULT.getTexture(),
                        Math.round(x), Math.round(y), w, h, 20, 4, 200, 20, 0, state * 20, 200, 60);
            }
        };
    }

    private static void place(UIElement element, int x, int y, int width, int height) {
        element.layout(s -> s.positionType(YogaPositionType.ABSOLUTE)
                .left(x).top(y).width(width).height(height));
    }

    @Override public void removed() {
        if (scene != null) scene.close();
        super.removed();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
