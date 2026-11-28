package c_y.meknh_core.client.structure;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.lwjgl.opengl.GL11;

public final class StructurePreviewRenderer {
    private StructurePreviewRenderer() {}
    public static void render(GuiGraphics graphics, StructurePreview preview, StructureViewState view,
                              int x, int y, int width, int height) {
        render(graphics, preview, view, x, y, width, height, null);
    }

    public static void render(GuiGraphics graphics, StructurePreview preview, StructureViewState view,
                              int x, int y, int width, int height,
                              @Nullable StructurePreviewPicking.Selection selection) {
        graphics.flush();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        var pose = graphics.pose();
        // MC 1.21.1 scissor coordinates do not follow the pose (JEI translates it).
        var viewport = StructureViewportBounds.transform(pose.last().pose(), x, y, width, height);
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        pose.pushPose();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(1536)) {
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
            var cells = preview.structure().cells();
            int minX = cells.stream().mapToInt(c -> c.dx()).min().orElse(0);
            int maxX = cells.stream().mapToInt(c -> c.dx()).max().orElse(0);
            int minY = cells.stream().mapToInt(c -> c.dy()).min().orElse(0);
            int maxY = cells.stream().mapToInt(c -> c.dy()).max().orElse(0);
            int minZ = cells.stream().mapToInt(c -> c.dz()).min().orElse(0);
            int maxZ = cells.stream().mapToInt(c -> c.dz()).max().orElse(0);
            float extent = Math.max(maxX-minX+1, Math.max(maxY-minY+1, maxZ-minZ+1));
            float scale = Math.min(width, height) * 0.55F / extent * view.zoom();
            pose.translate(x + width / 2F, y + height / 2F, 180);
            pose.mulPose(StructurePreviewTransform.apply(new org.joml.Matrix4f(), scale, view.pitch(), view.yaw()));
            pose.translate(-(minX+maxX+1)/2F, -(minY+maxY+1)/2F, -(minZ+maxZ+1)/2F);
            var buffer = MultiBufferSource.immediate(memory);
            var states = new java.util.HashMap<c_y.meknh_core.api.multiblock.MultiblockMatcher, net.minecraft.world.level.block.state.BlockState>();
            for (var cell : cells) {
                if (view.layer() >= 0 && cell.dy() - minY != view.layer()) continue;
                var state = states.computeIfAbsent(cell.matcher(), preview::state);
                if (state.isAir()) continue;
                pose.pushPose();
                try {
                    pose.translate(cell.dx(), cell.dy(), cell.dz());
                    // Match structure coordinates, never BlockState: repeated materials are distinct cells.
                    boolean selected = selection != null && selection.x() == cell.dx()
                            && selection.y() == cell.dy() && selection.z() == cell.dz();
                    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffer,
                            // Vanilla white overlay gently lightens only the hit block model.
                            // No extra cube: preserve actual geometry, textures and depth occlusion.
                            0xF000F0, selected ? OverlayTexture.pack(0.30F, false) : OverlayTexture.NO_OVERLAY);
                } finally { pose.popPose(); }
            }
            buffer.endBatch();
        } finally {
            pose.popPose();
            graphics.disableScissor();
            Lighting.setupFor3DItems();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }
}
