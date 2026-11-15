package c_y.meknh_core.client.render;

import c_y.meknh_core.api.multiblock.MultiblockControllerBlock;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** Formed hearth visual only: never places, stores, or lights real world lava. */
public final class PrimitiveBlastFurnaceRenderer implements BlockEntityRenderer<MultiblockControllerBlockEntity> {
    public static final float SURFACE_HEIGHT = 0.875F;

    public PrimitiveBlastFurnaceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MultiblockControllerBlockEntity furnace, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var state = furnace.getBlockState();
        if (furnace.getLevel() == null || !state.getValue(MultiblockControllerBlock.FORMED)) return;
        Direction back = state.getValue(MultiblockControllerBlock.FACING).getOpposite();
        var fluid = IClientFluidTypeExtensions.of(Fluids.LAVA);
        var texture = fluid.getStillTexture();
        if (texture == null) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        int tint = fluid.getTintColor();
        float x = back.getStepX(), z = back.getStepZ();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        // Counter-clockwise from above: a single upward-facing surface, not a lava cube.
        vertex(vertices, poses.last(), x, SURFACE_HEIGHT, z, sprite.getU0(), sprite.getV0(), tint);
        vertex(vertices, poses.last(), x, SURFACE_HEIGHT, z + 1, sprite.getU0(), sprite.getV1(), tint);
        vertex(vertices, poses.last(), x + 1, SURFACE_HEIGHT, z + 1, sprite.getU1(), sprite.getV1(), tint);
        vertex(vertices, poses.last(), x + 1, SURFACE_HEIGHT, z, sprite.getU1(), sprite.getV0(), tint);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int tint) {
        out.addVertex(pose, x, y, z).setColor(tint).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    @Override
    public AABB getRenderBoundingBox(MultiblockControllerBlockEntity furnace) {
        BlockPos pos = furnace.getBlockPos();
        Direction back = furnace.getBlockState().getValue(MultiblockControllerBlock.FACING).getOpposite();
        return new AABB(pos).minmax(new AABB(pos.relative(back)));
    }
}
