package c_y.meknh_core.common.pipe;

import mekanism.common.content.network.transmitter.MechanicalPipe;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** 管道当前内容类型。 */
public final class MekPipeContents {

    public static final int EMPTY = 0;
    public static final int FLUID = 1;
    public static final int CHEMICAL = 2;

    private MekPipeContents() {
    }

    /**
     * 整张管道网络的内容类型：只要网络里任意一根管道有内容，就返回该类型。
     */
    public static int ofNetwork(MechanicalPipe pipe) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = pipe.getTransmitterTile().getBlockPos();
        queue.add(start);
        visited.add(start);
        int result = EMPTY;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            var level = pipe.getTransmitterTile().getLevel();
            if (level == null) {
                break;
            }
            if (!(level.getBlockEntity(pos) instanceof mekanism.common.tile.transmitter.TileEntityMechanicalPipe current)
                    || !(current.getBlockState().getBlock() instanceof MekPipeBlock)) {
                continue;
            }
            int content = of(current.getTransmitter());
            if (content != EMPTY) {
                if (result == EMPTY) {
                    result = content;
                } else if (result != content) {
                    return content;
                }
            }
            for (Direction side : Direction.values()) {

                BlockPos next = pos.relative(side);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return result;
    }

    public static int of(MechanicalPipe pipe) {
        if (pipe.getTransmitterTile() instanceof MekPipeChemicalAccess access) {
            var tank = access.meknh_core$chemicalTank();
            if (tank != null && !tank.isEmpty()) {
                return CHEMICAL;
            }
        }
        // 注意：Mek 机械管道的流体在 FluidNetwork 的共享罐里，
        // 单根管道的 buffer 可能为空，必须用 getBufferWithFallback 才能看到网络里的流体。
        if (!pipe.getBufferWithFallback().isEmpty()) {
            return FLUID;
        }
        return EMPTY;
    }
}
