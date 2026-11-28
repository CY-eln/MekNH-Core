package c_y.meknh_core.client.render.preview;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlock;
import c_y.meknh_core.api.multiblock.MultiblockDefinition;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多方块「世界内实心预览」。
 *
 * <p>结构样板被烘进按 {@link RenderType} 分层的静态 VBO，在世界渲染阶段一次性画出；
 * 编译在后台线程完成，主线程只做 VBO 上传与绘制。</p>
 *
 * <p>参考：格雷科技 {@code MultiblockInWorldPreviewRenderer}（1.20.1 + LDLib1），
 * 本项目按 LDLib2 / 1.21.1 API 重写，差异见 {@code docs/MULTIBLOCK_PREVIEW.md}。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID, value = Dist.CLIENT)
public final class MultiblockInWorldPreviewRenderer {

    /** 预览存活时间（tick）。 */
    private static final int DURATION_TICKS = 200;
    /** 每个方块缩小到 0.8，留缝、避免与原方块 z-fighting。 */
    private static final float BLOCK_SCALE = 0.8F;
    /** 表示「画整座」。 */
    private static final int FULL_LAYER = -1;

    private enum State {
        UNUSED,
        COMPILING,
        COMPILED
    }

    private static final RandomSource RANDOM = RandomSource.create();
    private static final AtomicReference<State> STATE = new AtomicReference<>(State.UNUSED);
    private static final AtomicInteger LEFT_TICKS = new AtomicInteger(-1);

    @Nullable
    private static VertexBuffer[] buffers;
    @Nullable
    private static boolean[] usedLayers;
    @Nullable
    private static TrackedDummyWorld level;
    @Nullable
    private static Thread compileThread;

    @Nullable
    private static BlockPos lastPos;
    private static int lastLayer = FULL_LAYER;

    private MultiblockInWorldPreviewRenderer() {
    }

    /**
     * 显示（或推进一层）某个控制器的结构预览。只在客户端调用。
     */
    public static void showPreview(MultiblockControllerBlock block, BlockState state, BlockPos pos) {
        MultiblockDefinition definition = block.definition();
        Direction facing = state.getValue(MultiblockControllerBlock.FACING);
        MultiblockPreviewShape shape = MultiblockPreviewShape.of(definition.structure(), pos, facing,
                block.level(), !definition.noVoltage());
        if (shape.entries().isEmpty()) {
            return;
        }

        if (lastPos != null && lastPos.equals(pos)) {
            lastLayer++;
            if (lastLayer >= shape.layerCount()) {
                lastLayer = FULL_LAYER;
            }
        } else {
            lastLayer = FULL_LAYER;
        }
        lastPos = pos;

        buildLevel(shape, lastLayer);
        compile(shape, lastLayer);
    }

    /**
     * 计时结束 / 切世界时清空状态（VBO 保留复用）。
     */
    public static void cleanPreview() {
        if (compileThread != null) {
            compileThread.interrupt();
            compileThread = null;
        }
        if (level != null) {
            level.clear();
            level = null;
        }
        STATE.set(State.UNUSED);
        LEFT_TICKS.set(-1);
        lastPos = null;
        lastLayer = FULL_LAYER;
    }

    // ------------------------------------------------------------------
    // 假世界
    // ------------------------------------------------------------------

    private static void buildLevel(MultiblockPreviewShape shape, int layer) {
        // 必须用无参构造：带 proxy 的构造会把 getBlockState 转发到真实世界。
        TrackedDummyWorld dummy = new TrackedDummyWorld();
        for (MultiblockPreviewShape.Entry entry : shape.entries()) {
            if (layer != FULL_LAYER && entry.layer() != layer) {
                continue;
            }
            try {
                dummy.addBlock(entry.pos(), BlockInfo.fromBlockState(entry.state()));
            } catch (RuntimeException ignored) {
                // 个别方块实体构造可能挑剔环境；单个格子失败不影响整体预览。
            }
        }
        level = dummy;
    }

    // ------------------------------------------------------------------
    // 编译
    // ------------------------------------------------------------------

    private static void compile(MultiblockPreviewShape shape, int layer) {
        if (compileThread != null) {
            compileThread.interrupt();
            compileThread = null;
        }
        TrackedDummyWorld dummy = level;
        if (dummy == null) {
            return;
        }
        ensureBuffers();
        STATE.set(State.COMPILING);

        List<MultiblockPreviewShape.Entry> entries = new ArrayList<>();
        for (MultiblockPreviewShape.Entry entry : shape.entries()) {
            if (layer == FULL_LAYER || entry.layer() == layer) {
                entries.add(entry);
            }
        }

        Thread thread = new Thread(() -> compileBuffers(dummy, entries), "meknh-multiblock-preview");
        thread.setDaemon(true);
        compileThread = thread;
        thread.start();
    }

    private static void ensureBuffers() {
        if (buffers != null) {
            return;
        }
        List<RenderType> layers = RenderType.chunkBufferLayers();
        VertexBuffer[] created = new VertexBuffer[layers.size()];
        for (int index = 0; index < created.length; index++) {
            created[index] = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        buffers = created;
        usedLayers = new boolean[created.length];
    }

    private static void compileBuffers(TrackedDummyWorld dummy, List<MultiblockPreviewShape.Entry> entries) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        ModelBlockRenderer.enableCaching();
        PoseStack pose = new PoseStack();
        try {
            List<RenderType> layers = RenderType.chunkBufferLayers();
            for (int index = 0; index < layers.size(); index++) {
                boolean[] used = usedLayers;
                if (used == null || Thread.currentThread().isInterrupted()) {
                    return;
                }
                RenderType layer = layers.get(index);
                BufferBuilder builder = new BufferBuilder(
                        new ByteBufferBuilder(layer.bufferSize()),
                        VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.BLOCK);
                WorldSceneRenderer.VertexConsumerWrapper wrapper =
                        new WorldSceneRenderer.VertexConsumerWrapper(builder);
                // 方块走「平色」消费者（抹掉明暗与光照）；流体仍走原 wrapper（要它补区块原点）。
                FlatVertexConsumer flat = new FlatVertexConsumer(wrapper);

                for (MultiblockPreviewShape.Entry entry : entries) {
                    if (Thread.currentThread().isInterrupted()) {
                        MeshData abandoned = builder.build();
                        if (abandoned != null) {
                            abandoned.close();
                        }
                        return;
                    }
                    renderEntry(dummy, dispatcher, pose, layer, wrapper, flat, entry);
                }

                MeshData mesh = builder.build();
                if (mesh == null) {
                    used[index] = false;
                    continue;
                }
                used[index] = true;
                upload(index, mesh);
            }
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            STATE.set(State.COMPILED);
            LEFT_TICKS.set(DURATION_TICKS);
        } finally {
            ModelBlockRenderer.clearCache();
            // 只清自己：连点时新线程可能已经替换了字段，无条件置空会漏掉对它的 interrupt。
            if (compileThread == Thread.currentThread()) {
                compileThread = null;
            }
        }
    }

    private static void upload(int index, MeshData mesh) {
        VertexBuffer[] current = buffers;
        if (current == null) {
            mesh.close();
            return;
        }
        VertexBuffer buffer = current[index];
        RenderSystem.recordRenderCall(() -> {
            if (buffer.isInvalid()) {
                mesh.close();
                return;
            }
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
        });
    }

    private static void renderEntry(TrackedDummyWorld dummy, BlockRenderDispatcher dispatcher, PoseStack pose,
                                    RenderType layer, WorldSceneRenderer.VertexConsumerWrapper wrapper,
                                    FlatVertexConsumer flat, MultiblockPreviewShape.Entry entry) {
        BlockPos pos = entry.pos();
        BlockState state = dummy.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        FluidState fluid = state.getFluidState();
        RANDOM.setSeed(state.getSeed(pos));

        if (state.getRenderShape() != RenderShape.INVISIBLE) {
            BakedModel model = dispatcher.getBlockModel(state);
            ModelData modelData = model.getModelData(dummy, pos, state, dummy.getModelData(pos));
            if (model.getRenderTypes(state, RANDOM, modelData).contains(layer)) {
                pose.pushPose();
                pose.translate(pos.getX(), pos.getY(), pos.getZ());
                pose.translate(0.5D, 0.5D, 0.5D);
                pose.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
                pose.translate(-0.5D, -0.5D, -0.5D);
                dispatcher.renderBatched(state, pos, dummy, pose, flat, false, RANDOM, modelData, layer);
                pose.popPose();
            }
        }

        if (!fluid.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluid) == layer) {
            // 液体顶点是区块内相对坐标，补回区块原点。
            wrapper.addOffset(pos.getX() - (pos.getX() & 15),
                    pos.getY() - (pos.getY() & 15),
                    pos.getZ() - (pos.getZ() & 15));
            dispatcher.renderLiquid(pos, dummy, wrapper, state, fluid);
            wrapper.clearOffset();
        }
    }

    /**
     * 全息观感：抹掉「面朝向明暗」和「环境光遮蔽（AO）」以及实时光照，只保留贴图本身的图案。
     *
     * <p>方块模型渲染会把面朝向系数（0.5 / 0.6 / 0.8 / 1.0）和 AO 逐顶点乘进 color，把 lightmap 坐标
     * 写进 uv2，于是同一个外壳会有明显的亮暗渐变，看起来像实体而不是投影。全息预览不需要这些：
     * color 统一成纯白、uv2 统一成全亮，位置 / UV / 法线原样透传，贴图与透明度不受影响。</p>
     */
    private static final class FlatVertexConsumer implements VertexConsumer {

        private static final int FULL_BRIGHT_U = LightTexture.FULL_BRIGHT & 0xFFFF;
        private static final int FULL_BRIGHT_V = (LightTexture.FULL_BRIGHT >> 16) & 0xFFFF;

        private final VertexConsumer delegate;

        private FlatVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            // 丢掉模型给的明暗系数，只保留透明度（半透明层需要）。
            this.delegate.setColor(255, 255, 255, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(FULL_BRIGHT_U, FULL_BRIGHT_V);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        VertexBuffer[] current = buffers;
        boolean[] used = usedLayers;
        if (STATE.get() != State.COMPILED || level == null || current == null || used == null) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        // 1.21.1 与 1.20.1 的关键差异：相机旋转不再烘焙进 stage 的 poseStack（LevelRenderer 里
        // 它是 new PoseStack()，旋转只进了 RenderSystem 的 modelView 栈），所以这里必须自己补视图矩阵。
        // 少了这一句，几何体会以「相机坐标但没转视角」的方式被画出来 —— 表现为贴着脸、跟着人走。
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(-camera.x, -camera.y, -camera.z);

        List<RenderType> layers = RenderType.chunkBufferLayers();
        for (int index = 0; index < layers.size() && index < current.length; index++) {
            if (!used[index]) {
                continue;
            }
            VertexBuffer buffer = current[index];
            if (buffer.isInvalid() || buffer.getFormat() == null) {
                continue;
            }
            RenderType layer = layers.get(index);
            layer.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                RenderSystem.setupShaderLights(shader);
                // LDLib2 的混合/深度处理与这一段完全一致，直接复用。
                WorldSceneRenderer.setDefaultRenderLayerState(layer);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                buffer.bind();
                buffer.drawWithShader(pose.last().pose(), RenderSystem.getProjectionMatrix(), shader);
                VertexBuffer.unbind();
            }
            layer.clearRenderState();
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (LEFT_TICKS.get() <= 0) {
            return;
        }
        if (LEFT_TICKS.decrementAndGet() <= 0) {
            cleanPreview();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        cleanPreview();
    }
}
