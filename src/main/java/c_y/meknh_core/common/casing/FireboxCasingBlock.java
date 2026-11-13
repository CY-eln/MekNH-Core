package c_y.meknh_core.common.casing;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 格雷风格燃烧室外壳。
 *
 * <p>只有 {@link #ACTIVE} 一个状态：未点燃时显示静态外壳，点燃时在四个侧面叠加
 * 动态火焰贴图（{@code bronze_firebox_casing_bloom}）。目前没有锅炉逻辑驱动它，
 * 可以用打火石点燃、潜行空手熄灭，后续接入锅炉时直接调用 {@link #setActive} 即可。</p>
 */
public class FireboxCasingBlock extends Block {

    /** 是否处于点燃（工作）状态。 */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public FireboxCasingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    /**
     * 设置燃烧室的点燃状态，供后续锅炉/多方块逻辑调用。
     */
    public static void setActive(Level level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FireboxCasingBlock && state.getValue(ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.FLINT_AND_STEEL)) {
            if (!state.getValue(ACTIVE)) {
                if (!level.isClientSide) {
                    level.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);
                    level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.isEmpty() && player.isShiftKeyDown() && state.getValue(ACTIVE)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
