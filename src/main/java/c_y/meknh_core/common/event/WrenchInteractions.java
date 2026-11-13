package c_y.meknh_core.common.event;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockPartBlock;
import c_y.meknh_core.api.wrench.WrenchDismantle;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 配置器 / 扳手的右键交互统一入口。
 *
 * <p>为什么不写在方块 {@code useItemOn} 里：NeoForge 的
 * {@code ServerPlayerGameMode#useItemOn} 有这段判断——</p>
 *
 * <pre>
 * flag1 = 潜行 &amp;&amp; 手上有物品 &amp;&amp; !(主手 bypass &amp;&amp; 副手 bypass)
 * if (useBlock.isTrue() || (useBlock.isDefault() &amp;&amp; !flag1)) { 调用 BlockState.useItemOn }
 * </pre>
 *
 * <p>只有「扳手模式」的配置器 {@code doesSneakBypassUse} 返回 true；配置模式返回 false，
 * 于是潜行时 {@code flag1} 为真，<b>方块交互被整个跳过</b>，请求直接落到物品上——
 * 所以换覆盖面永远轮不到方块。放到 {@link PlayerInteractEvent.RightClickBlock} 里
 * 就在物品/方块分流之前，两个功能都能稳定触发。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WrenchInteractions {

    private WrenchInteractions() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        BlockState state = level.getBlockState(event.getPos());

        // 1) 配置模式 + 潜行：仓室换覆盖面（覆盖层所在面即吸入/弹出口）。
        if (state.getBlock() instanceof MultiblockPartBlock && player.isShiftKeyDown()
                && MultiblockPartBlock.isFaceConfigMode(stack)) {
            Direction face = event.getHitVec().getDirection();
            if (state.getValue(MultiblockPartBlock.FACING) != face) {
                level.setBlockAndUpdate(event.getPos(), state.setValue(MultiblockPartBlock.FACING, face));
                level.playSound(null, event.getPos(), SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            consumeOnServer(event);
            return;
        }

        // 2) 扳手 + 潜行：拆进背包。
        if (WrenchDismantle.canDismantle(stack, player) && WrenchDismantle.isDismantlable(state.getBlock())) {
            if (!level.isClientSide && !WrenchDismantle.canDismantleAt(level, event.getPos(), player)) {
                event.setCanceled(true);
                return;
            }
            WrenchDismantle.tryDismantle(level, event.getPos(), player, stack);
            consumeOnServer(event);
        }
    }

    /**
     * 只在服务端吞掉这次右键（客户端必须放行，否则 {@code ServerboundUseItemOnPacket} 根本不会发出去）。
     */
    private static void consumeOnServer(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
