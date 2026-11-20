package c_y.meknh_core.common.pipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.TileEntityMechanicalPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 把整张化学品管道网络的槽位聚合成一个 handler：容量相加、内容共享。
 */
public final class MekPipeChemicalNetworkHandler implements IChemicalHandler {

    private final List<IChemicalTank> tanks;

    private MekPipeChemicalNetworkHandler(List<IChemicalTank> tanks) {
        this.tanks = tanks;
    }

    public static MekPipeChemicalNetworkHandler of(TileEntityMechanicalPipe start) {
        List<IChemicalTank> tanks = new ArrayList<>();
        if (start.getLevel() == null) {
            return new MekPipeChemicalNetworkHandler(tanks);
        }
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.getBlockPos());
        queue.add(start.getBlockPos());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!(start.getLevel().getBlockEntity(pos) instanceof TileEntityMechanicalPipe pipe)
                    || !(pipe.getBlockState().getBlock() instanceof MekPipeBlock)) {
                continue;
            }
            if (pipe instanceof MekPipeChemicalAccess access) {
                IChemicalTank tank = access.meknh_core$chemicalTank();
                if (tank != null) {
                    tanks.add(tank);
                }
            }
            for (Direction side : Direction.values()) {

                BlockPos next = pos.relative(side);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return new MekPipeChemicalNetworkHandler(tanks);
    }

    public long stored() {
        long total = 0L;
        for (IChemicalTank tank : this.tanks) {
            total += tank.getStored();
        }
        return total;
    }

    public long capacity() {
        long total = 0L;
        for (IChemicalTank tank : this.tanks) {
            total += tank.getCapacity();
        }
        return total;
    }

    @Override
    public int getChemicalTanks() {
        return this.tanks.isEmpty() ? 0 : 1;
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        for (IChemicalTank candidate : this.tanks) {
            if (!candidate.isEmpty()) {
                return candidate.getStack();
            }
        }
        return ChemicalStack.EMPTY;
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        long remaining = stack.getAmount();
        for (IChemicalTank candidate : this.tanks) {
            if (remaining <= 0L) {
                break;
            }
            candidate.setStack(ChemicalStack.EMPTY);
            long accepted = Math.min(remaining, candidate.getCapacity());
            if (accepted > 0L) {
                candidate.setStack(stack.copyWithAmount(accepted));
                remaining -= accepted;
            }
        }
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return capacity();
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return !this.tanks.isEmpty();
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        long remaining = stack.getAmount();
        for (IChemicalTank candidate : this.tanks) {
            if (remaining <= 0L) {
                break;
            }
            ChemicalStack remainder = candidate.insert(stack.copyWithAmount(remaining), action,
                    AutomationType.INTERNAL);
            remaining = remainder.getAmount();
        }
        return stack.copyWithAmount(remaining);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        long remaining = amount;
        ChemicalStack first = ChemicalStack.EMPTY;
        for (IChemicalTank candidate : this.tanks) {
            if (remaining <= 0L) {
                break;
            }
            ChemicalStack extracted = candidate.extract(remaining, action, AutomationType.INTERNAL);
            if (!extracted.isEmpty()) {
                if (first.isEmpty()) {
                    first = extracted;
                }
                remaining -= extracted.getAmount();
            }
        }
        return first.isEmpty() ? ChemicalStack.EMPTY : first.copyWithAmount(amount - remaining);
    }
}
