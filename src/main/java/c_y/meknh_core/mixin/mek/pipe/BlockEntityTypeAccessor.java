package c_y.meknh_core.mixin.mek.pipe;

import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 把我们的管道方块加入 Mek 机械管道的 BlockEntityType 合法方块集合。 */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {

    @Accessor("validBlocks")
    Set<Block> meknh_core$validBlocks();

    @Mutable
    @Accessor("validBlocks")
    void meknh_core$setValidBlocks(Set<Block> blocks);
}
