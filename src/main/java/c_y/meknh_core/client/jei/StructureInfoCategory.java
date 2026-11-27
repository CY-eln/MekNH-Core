package c_y.meknh_core.client.jei;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.client.structure.*;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

/** Structure information, separate from machine processing recipes. */
public final class StructureInfoCategory implements IRecipeCategory<StructurePreview> {
    public static final RecipeType<StructurePreview> TYPE = RecipeType.create(MekNHCore.MODID, "structures", StructurePreview.class);
    private final RecipeType<StructurePreview> type;
    private final StructurePreview controllerPreview;
    private final java.util.Set<net.minecraft.world.item.Item> controllers;
    private final Map<StructurePreview, java.util.List<ItemStack>> ingredients = new WeakHashMap<>();
    private final IDrawable background, icon;
    private final Map<StructurePreview, ThumbnailState> thumbnails = new WeakHashMap<>();

    private static final class ThumbnailState {
        final StructureViewState view = new StructureViewState();
        BlockState selected;
        boolean dragging, wasDown;
        double lastX, lastY;
    }
    public StructureInfoCategory(IGuiHelper helper, StructurePreview controllerPreview, java.util.Set<? extends net.minecraft.world.item.Item> controllers) {
        this.controllerPreview = controllerPreview;
        this.controllers = java.util.Set.copyOf(controllers);
        this.type = controllerPreview == null ? TYPE : RecipeType.create(MekNHCore.MODID, "structures/controller/" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(controllerPreview.variant().item().get()).getPath(), StructurePreview.class);
        background = helper.createBlankDrawable(176, 152);
        icon = helper.createDrawableItemStack(new ItemStack(controllerPreview == null ? Items.BRICKS : controllerPreview.variant().item().get()));
    }
    @Override public RecipeType<StructurePreview> getRecipeType() { return type; }
    public StructurePreview controllerPreview() { return controllerPreview; }
    @Override public Component getTitle() { return Component.translatable("meknh.structure.category"); }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 176; }
    @Override public int getHeight() { return 152; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, StructurePreview preview, IFocusGroup focuses) {
        ItemStack controller = new ItemStack(preview.variant().item().get());
        builder.addSlot(controllerPreview == null ? RecipeIngredientRole.RENDER_ONLY : RecipeIngredientRole.INPUT, 3, 3).addItemStack(controller);
        builder.addSlot(controllerPreview == null ? RecipeIngredientRole.RENDER_ONLY : RecipeIngredientRole.OUTPUT, 157, 3).addItemStack(controller.copy());
        // Shared parts link only the structures that actually accept them, never every controller.
        if (controllerPreview == null) builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(ingredients.computeIfAbsent(preview,
                p -> p.structure().cells().stream().flatMap(cell -> p.candidates(cell).stream())
                        .map(state -> state.getBlock().asItem()).filter(item -> !controllers.contains(item)).distinct().map(ItemStack::new)
                        .filter(stack -> !stack.isEmpty()).toList()));
    }
    @Override public void draw(StructurePreview preview, IRecipeSlotsView slots, GuiGraphics g, double mx, double my) {
        var font = Minecraft.getInstance().font;
        // Opaque vanilla-like header: readable independently of JEI background themes.
        g.fill(0, 0, 176, 29, 0xFFC6C6C6);
        String name = font.plainSubstrByWidth(preview.title().getString(), 130);
        g.drawString(font, name, 88 - font.width(name) / 2, 4, 0xFF202020, false);
        String dimensions = preview.dimensions();
        g.drawString(font, dimensions, 88 - font.width(dimensions) / 2, 18, 0xFF404040, false);
        // A quiet dark well separates the model from the surrounding recipe page.
        g.fill(0, 30, 176, 128, 0xFF555555);
        g.fill(1, 31, 175, 127, 0xFF171717);
        // JEI does not forward drag events to recipe categories; poll only this viewport.
        ThumbnailState thumbnail = thumbnails.computeIfAbsent(preview, p -> new ThumbnailState());
        boolean down = GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!down) thumbnail.dragging = false;
        if (down && !thumbnail.wasDown && inPreview(mx, my)) {
            thumbnail.dragging = true;
            thumbnail.lastX = mx;
            thumbnail.lastY = my;
        }
        if (down && thumbnail.dragging) {
            thumbnail.view.rotate(mx - thumbnail.lastX, my - thumbnail.lastY);
            thumbnail.lastX = mx;
            thumbnail.lastY = my;
        }
        thumbnail.wasDown = down;
        // One frame-local hit drives both tint and tooltip; equal-material cells remain distinct.
        var pointedCell = inPreview(mx, my) && !thumbnail.dragging
                ? StructurePreviewPicking.pickCell(preview, thumbnail.view, 0, 31, 176, 96, mx, my)
                : null;
        StructurePreviewRenderer.render(g, preview, thumbnail.view, 0, 31, 176, 96, pointedCell);
        if (thumbnail.selected != null) {
            String selectedName = font.plainSubstrByWidth(thumbnail.selected.getBlock().getName().getString(), 164);
            g.fill(2, 114, 174, 126, 0xDD171717);
            g.drawString(font, selectedName, 88 - font.width(selectedName) / 2, 116, 0xFFFFFF, false);
        }
        boolean hover = hovered(mx, my);
        g.fill(8, 130, 168, 150, hover ? 0xFFFFFFFF : 0xFF373737);
        g.fill(9, 131, 167, 149, hover ? 0xFF777777 : 0xFF5B5B5B);
        g.fill(9, 131, 167, 132, hover ? 0xFFAAAAAA : 0xFF888888);
        g.fill(9, 148, 167, 149, 0xFF303030);
        g.drawCenteredString(font, Component.translatable("meknh.structure.open"), 88, 136, 0xFFFFFF);
        if (pointedCell != null) {
            BlockState pointed = pointedCell.state();
            var screenMouse = g.pose().last().pose().transformPosition((float) mx, (float) my, 0, new org.joml.Vector3f());
            g.pose().pushPose();
            try {
                g.pose().last().pose().identity();
                g.pose().last().normal().identity();
                g.renderTooltip(font, pointed.getBlock().getName(), (int) screenMouse.x, (int) screenMouse.y);
            } finally { g.pose().popPose(); }
        }
    }
    private static boolean inPreview(double x, double y) { return x >= 0 && x < 176 && y >= 31 && y < 127; }
    private static boolean hovered(double x, double y) { return x >= 8 && x < 168 && y >= 130 && y < 150; }
    @Override public boolean handleInput(StructurePreview preview, double mx, double my, InputConstants.Key key) {
        if (hovered(mx,my) && key.getType() == InputConstants.Type.MOUSE && key.getValue() == 0) {
            var mc = Minecraft.getInstance();
            var parent = mc.screen;
            mc.setScreen(new StructurePreviewScreen(parent, preview));
            return true;
        }
        if (inPreview(mx, my) && key.getType() == InputConstants.Type.MOUSE && key.getValue() == 0) {
            ThumbnailState thumbnail = thumbnails.computeIfAbsent(preview, p -> new ThumbnailState());
            thumbnail.selected = StructurePreviewPicking.pick(preview, thumbnail.view, 0, 31, 176, 96, mx, my);
            thumbnail.dragging = true;
            thumbnail.lastX = mx;
            thumbnail.lastY = my;
            return true;
        }
        return false;
    }
}
