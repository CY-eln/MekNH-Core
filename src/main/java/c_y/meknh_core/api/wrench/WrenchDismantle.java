package c_y.meknh_core.api.wrench;

import java.util.List;
import mekanism.api.MekanismItemAbilities;
import mekanism.common.tags.MekanismTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扳手潜行右键拆卸，对齐 Mekanism 的 {@code TileEntityMekanism#tryWrenchDismantle}：
 * 方块直接进玩家背包（塞不下才掉在地上），并保留方块实体数据。
 *
 * <p>判定与 Mek 一致：</p>
 * <ul>
 *     <li>工具必须提供 {@link MekanismItemAbilities#WRENCH_DISMANTLE}（Mek 配置器 = 扳手模式）；
 *     或者工具没有任何扳手动作、但在 {@code mekanism:configurators} 标签里；</li>
 *     <li>玩家必须潜行。</li>
 * </ul>
 */
public final class WrenchDismantle {

    private WrenchDismantle() {
    }

    /**
     * 当前手持物 + 潜行是否构成「拆卸」。
     */
    public static boolean canDismantle(ItemStack stack, Player player) {
        if (stack.isEmpty() || player == null || !player.isShiftKeyDown()) {
            return false;
        }
        if (stack.canPerformAction(MekanismItemAbilities.WRENCH_DISMANTLE)) {
            return true;
        }
        // 明确声明了别的扳手动作（旋转/清空/配置）时，说明作者不希望用它拆方块。
        if (stack.canPerformAction(MekanismItemAbilities.WRENCH_ROTATE)
                || stack.canPerformAction(MekanismItemAbilities.WRENCH_EMPTY)
                || stack.canPerformAction(MekanismItemAbilities.WRENCH_CONFIGURE)) {
            return false;
        }
        return stack.is(MekanismTags.Items.CONFIGURATORS);
    }

    /**
     * 这个方块是不是可以被扳手拆走的机器（机器 / 多方块控制器 / 仓室 / 锅炉）。
     */
    public static boolean isDismantlable(Block block) {
        return block instanceof c_y.meknh_core.api.machine.MachineBlock
                || block instanceof c_y.meknh_core.api.multiblock.MultiblockControllerBlock
                || block instanceof c_y.meknh_core.api.multiblock.MultiblockPartBlock
                || block instanceof c_y.meknh_core.common.generator.boiler.BoilerBlock;
    }

    /**
     * 权限检查：私有机器 / 上锁锅炉不允许别人拆。
     */
    public static boolean canDismantleAt(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof c_y.meknh_core.api.machine.MachineBlockEntity machine
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            return machine.canUse(serverPlayer);
        }
        if (blockEntity instanceof c_y.meknh_core.common.generator.boiler.BoilerBlockEntity boiler) {
            return boiler.canAccess(player.getUUID());
        }
        return true;
    }

    /**
     * 拆下方块并放进玩家背包。
     *
     * @return 是否处理了这次点击（客户端也返回 {@code true}，用来吞掉交互）
     */
    public static boolean tryDismantle(Level level, BlockPos pos, Player player, ItemStack tool) {
        if (!canDismantle(tool, player)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, tool);
        if (drops.isEmpty()) {
            // 数据驱动的机器没有静态战利品表，直接回退成方块物品。
            ItemStack self = new ItemStack(state.getBlock().asItem());
            if (self.isEmpty()) {
                return false;
            }
            drops = List.of(self);
        }
        attachBlockEntityData(serverLevel, blockEntity, drops, state.getBlock());
        // 先取掉落再移除方块，保证 BlockEntity 还在。
        level.removeBlock(pos, false);
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            if (!player.addItem(drop)) {
                player.drop(drop, false);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.2F, (level.random.nextFloat() - level.random.nextFloat()) * 1.4F + 2.0F);
        return true;
    }

    /**
     * 把方块实体的内容写进掉落物，放下后能恢复库存/能量/罐体。
     */
    private static void attachBlockEntityData(ServerLevel level, BlockEntity blockEntity, List<ItemStack> drops,
                                              Block block) {
        if (blockEntity == null || drops.size() != 1) {
            return;
        }
        ItemStack drop = drops.getFirst();
        if (drop.isEmpty() || !drop.is(block.asItem())) {
            return;
        }
        CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
        // id 必须保留：block_entity_data 的 codec 要求有 id，放置时也靠它找回方块实体类型。
        if (!tag.contains("id", Tag.TAG_STRING)) {
            ResourceLocation typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            if (typeId == null) {
                return;
            }
            tag.putString("id", typeId.toString());
        }
        // 位置由放置时重新写入，留着反而是旧坐标。
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        // 仓室重新放下时应该由控制器重新接线，不能带着旧控制器的坐标。
        if (blockEntity instanceof c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity) {
            tag.remove("Controller");
        }
        CustomData data = CustomData.of(tag);
        drop.set(DataComponents.BLOCK_ENTITY_DATA, data);
    }
}
