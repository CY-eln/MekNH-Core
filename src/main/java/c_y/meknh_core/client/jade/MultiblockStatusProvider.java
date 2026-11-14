package c_y.meknh_core.client.jade;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 控制器方块顶部的结构状态：成型绿色、不完整红色。
 */
public final class MultiblockStatusProvider implements IBlockComponentProvider {

    public static final ResourceLocation UID = MekNHCore.rl("multiblock_status");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        if (!state.hasProperty(MultiblockControllerBlock.FORMED)) {
            return;
        }
        boolean formed = state.getValue(MultiblockControllerBlock.FORMED);
        tooltip.add(Component.translatable(formed
                        ? "jade.meknh_core.multiblock.formed"
                        : "jade.meknh_core.multiblock.unformed")
                .withStyle(formed ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
