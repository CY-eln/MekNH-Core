package c_y.meknh_core.client.structure;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Client-only synthetic world; LDLib owns rendering, picking, dragging and zoom. */
public final class StructureLdlibScene implements AutoCloseable {
    private final TrackedDummyWorld world = new TrackedDummyWorld();
    private final PreviewScene scene = new PreviewScene();
    private final List<BlockPos> all = new ArrayList<>();
    private List<BlockPos> visible = List.of();
    private final StructurePreview preview;
    private List<BlockPos> replaceable;
    private boolean showReplaceable;
    private final int minY;
    private final Vector3f center;
    private final float initialZoom;

    public StructureLdlibScene(StructurePreview preview, Consumer<BlockPos> onSelected) {
        this.preview = preview;
        minY = preview.structure().cells().stream().mapToInt(c -> c.dy()).min().orElse(0);
        for (var cell : preview.structure().cells()) {
            var state = preview.state(cell.matcher());
            if (!state.isAir()) {
                var pos = new BlockPos(cell.dx(), cell.dy(), cell.dz());
                all.add(pos);
                world.addBlock(pos, new BlockInfo(state));
            }
        }
        scene.setTickWorld(false);
        scene.setRenderFacing(true);
        scene.setRenderSelect(true);
        scene.setAllowXEILookup(false);
        scene.createScene(world);
        if (!all.isEmpty()) scene.setRenderedCore(all);
        center = new Vector3f(scene.getCenter());
        initialZoom = scene.getZoom();
        visible = List.copyOf(all);
        scene.setOnSelected((pos, facing) -> onSelected.accept(pos));
        scene.setAfterWorldRender(ignored -> renderReplaceable());
    }

    public Scene element() { return scene; }

    /** Layer index is relative to bottom of the entire structure; -1 shows all. */
    public void setLayer(int layer) {
        var cameraCenter = new Vector3f(scene.getCenter());
        visible = all.stream().filter(pos -> layer < 0 || pos.getY() == minY + layer).toList();
        scene.clearSelection();
        scene.setRenderedCore(visible, null, false);
        // setRenderedCore computes invalid bounds for an empty collection; restore a valid center.
        scene.setCenter(cameraCenter);
    }

    public void setShowReplaceable(boolean enabled) {
        if (enabled && replaceable == null) {
            replaceable = preview.structure().cells().stream()
                    .filter(cell -> preview.candidates(cell).stream()
                            .anyMatch(state -> !state.equals(preview.state(cell.matcher()))))
                    .map(cell -> new BlockPos(cell.dx(), cell.dy(), cell.dz())).toList();
        }
        showReplaceable = enabled;
    }

    /** Draw only visible-layer replacements, in LDLib's world-camera callback. */
    private void renderReplaceable() {
        if (!showReplaceable || replaceable == null || replaceable.isEmpty()) return;
        var poses = new com.mojang.blaze3d.vertex.PoseStack();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        try {
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
            var buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            for (BlockPos pos : replaceable) {
                if (!visible.contains(pos)) continue;
                com.lowdragmc.lowdraglib2.client.utils.RenderUtils.renderCubeFace(poses, buffer,
                        pos.getX() - .002f, pos.getY() - .002f, pos.getZ() - .002f,
                        pos.getX() + 1.002f, pos.getY() + 1.002f, pos.getZ() + 1.002f,
                        0.1f, 1f, 0.2f, .32f);
            }
            var mesh = buffer.build();
            if (mesh != null) com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }

    public BlockState stateAt(BlockPos pos) { return world.getBlockState(pos); }

    public void reset() {
        setLayer(-1);
        scene.setCenter(new Vector3f(center));
        scene.setCameraYawAndPitch(-135, 25);
        scene.setZoom(initialZoom);
    }

    @Override public void close() { scene.releaseRendererResource(); }

    private static final class PreviewScene extends Scene {
        @Override public void releaseRendererResource() {
            super.releaseRendererResource();
            renderer = null;
        }

        void clearSelection() {
            lastSelectedPosFace = null;
            lastHoverPosFace = null;
            lastClickPosFace = null;
            lastHoverItem = null;
        }
    }
}
