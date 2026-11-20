package c_y.meknh_core.common.event;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity;
import c_y.meknh_core.common.generator.boiler.BoilerBlockEntity;
import mekanism.common.item.ItemGaugeDropper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 滴管右键方块时统一先打开 GUI，禁止原版 Mek 滴管直接操作方块能力。
 *
 * <p>这是机器/仓室的通用行为，不在各个方块里单独写例外。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID)
public final class DropperBlockInteractions {

    private DropperBlockInteractions() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ItemGaugeDropper)) {
            return;
        }
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (!(blockEntity instanceof MachineBlockEntity
                || blockEntity instanceof BoilerBlockEntity
                || blockEntity instanceof MultiblockPartBlockEntity
                || blockEntity instanceof MultiblockControllerBlockEntity)) {
            return;
        }
        if (!event.getLevel().isClientSide) {
            BlockState state = event.getLevel().getBlockState(event.getPos());
            state.useWithoutItem(event.getLevel(), event.getEntity(), event.getHitVec());
        }
        // 客户端只负责阻止原版滴管；实际打开 GUI 由服务端执行。
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
