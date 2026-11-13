package c_y.meknh_core.client;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockPartBlock;
import c_y.meknh_core.api.multiblock.MultiblockPartType;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 能源仓 `overlay_tint` 的电压色染色。
 *
 * <p>对应 GT 的 {@code tintindex: 2 -> GTValues.VC[tier]}；
 * 颜色注册属于 MOD 总线事件，必须显式指定 {@code bus = MOD}。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MultiblockPartColors {

    /**
     * GTValues.VC 的 ULV～IV 段。
     */
    private static final int[] VC = {
            0xC80000, 0xDCDCDC, 0xFF6400, 0xFFFF1E, 0x808080, 0xF0F0F5
    };

    private MultiblockPartColors() {
    }

    private static int tint(MultiblockPartBlock part, int tintIndex) {
        if (tintIndex != 2) {
            return -1;
        }
        if (part.partType() != MultiblockPartType.ENERGY_INPUT
                && part.partType() != MultiblockPartType.ENERGY_OUTPUT) {
            return -1;
        }
        int tier = Math.max(0, Math.min(VC.length - 1, part.level().voltage().level()));
        return 0xFF000000 | VC[tier];
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        var blocks = MultiblockRegistration.partBlocks();
        if (!blocks.isEmpty()) {
            event.register((state, level, pos, tintIndex) -> {
                if (state.getBlock() instanceof MultiblockPartBlock part) {
                    return tint(part, tintIndex);
                }
                return -1;
            }, blocks.toArray(Block[]::new));
        }
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (Block block : MultiblockRegistration.partBlocks()) {
            event.register((stack, tintIndex) -> {
                if (block instanceof MultiblockPartBlock part) {
                    return tint(part, tintIndex);
                }
                return -1;
            }, block);
        }
    }
}
