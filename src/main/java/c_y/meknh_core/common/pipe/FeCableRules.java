package c_y.meknh_core.common.pipe;

import c_y.meknh_core.api.machine.MachineBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockPartType;
import c_y.meknh_core.common.machine.config.MachineConfigType;
import c_y.meknh_core.common.machine.config.SideMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Mek 通用线缆基座上的本模组 FE 能量包规则。 */
public final class FeCableRules {

    private static final int MAX_CABLES = 4096;
    private static final java.util.Map<ServerLevel, long[]> CACHE = new java.util.WeakHashMap<>();

    private FeCableRules() {
    }

    public static void tick(ServerLevel level, BlockPos start) {
        long[] cache = CACHE.computeIfAbsent(level, ignored -> new long[]{Long.MIN_VALUE, 0L});
        if (cache[0] != level.getGameTime()) {
            cache[0] = level.getGameTime();
            cache[1] = 0L;
        }
        long bit = 1L << Math.floorMod(start.hashCode(), 63);
        if ((cache[1] & bit) != 0L) {
            return;
        }
        List<FeCableAccess> cables = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        collect(level, start, cables, positions);
        cache[1] |= bit;
        if (cables.isEmpty()) {
            return;
        }
        long wireVoltage = Long.MAX_VALUE;
        int amperage = Integer.MAX_VALUE;
        for (FeCableAccess cable : cables) {
            FePipeDefinition definition = cable.meknh_core$feDefinition();
            if (definition == null) {
                return;
            }
            wireVoltage = Math.min(wireVoltage, definition.tier().voltage());
            amperage = Math.min(amperage, definition.amperage());
        }
        long sourceVoltage = 0L;
        List<c_y.meknh_core.common.pipe.FeEnergyEndpoint> consumers = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int index = 0; index < cables.size(); index++) {
            FeCableAccess cable = cables.get(index);
            IEnergyStorage storage = cable.meknh_core$feStorage();
            FePipeDefinition definition = cable.meknh_core$feDefinition();
            BlockPos cablePos = positions.get(index);
            for (Direction side : Direction.values()) {
                BlockPos target = cablePos.relative(side);
                BlockEntity blockEntity = level.getBlockEntity(target);
                if (blockEntity instanceof MachineBlockEntity machine) {
                    SideMode mode = machine.sideMode(MachineConfigType.ENERGY, side.getOpposite());
                    if (mode.allowsInsert() && seen.add(target)) {
                        consumers.add(machine);
                    }
                    if (mode.allowsExtract()) {
                        sourceVoltage = Math.max(sourceVoltage, machine.feVoltage());
                    }
                    continue;
                }
                if (blockEntity instanceof MultiblockPartBlockEntity hatch) {
                    if (side.getOpposite() != hatch.ioFace()) {
                        continue;
                    }
                    if (hatch.partType() == MultiblockPartType.ENERGY_INPUT && seen.add(target)) {
                        consumers.add(hatch);
                    } else if (hatch.partType() == MultiblockPartType.ENERGY_OUTPUT) {
                        sourceVoltage = Math.max(sourceVoltage, hatch.feVoltage());
                    }
                    continue;
                }
                int space = storage.getMaxEnergyStored() - storage.getEnergyStored();
                if (space <= 0) {
                    continue;
                }
                IEnergyStorage external = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, side.getOpposite());
                if (external == null) {
                    external = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, null);
                }
                if (external != null) {
                    int moved = external.extractEnergy(Math.min(space, (int) definition.maxTransfer()), true);
                    if (moved > 0) {
                        moved = external.extractEnergy(moved, false);
                        storage.receiveEnergy(moved, false);
                    }
                    continue;
                }
                // Mek 严格能量（J）来源：创造能源等
                mekanism.api.energy.IStrictEnergyHandler strict = level.getCapability(
                        mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(), target, side.getOpposite());
                if (strict != null) {
                    long want = Math.round(Math.min(space, (int) definition.maxTransfer()) * 2.5D);
                    long got = strict.extractEnergy(0, want, mekanism.api.Action.SIMULATE);
                    if (got > 0L) {
                        got = strict.extractEnergy(0, got, mekanism.api.Action.EXECUTE);
                        storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, Math.floor(got / 2.5D)), false);
                    }
                }
            }
        }
        if (sourceVoltage > wireVoltage) {
            for (int index = 0; index < cables.size(); index++) {
                if (cables.get(index).meknh_core$feDefinition().tier().voltage() < sourceVoltage) {
                    level.removeBlock(positions.get(index), false);
                }
            }
            return;
        }
        if (consumers.isEmpty()) {
            return;
        }
        int cursor = 0;
        for (int packet = 0; packet < amperage; packet++) {
            long total = 0L;
            for (FeCableAccess cable : cables) {
                total += cable.meknh_core$feStorage().getEnergyStored();
                if (total >= wireVoltage) {
                    break;
                }
            }
            if (total < wireVoltage) {
                break;
            }
            long remaining = wireVoltage;
            for (FeCableAccess cable : cables) {
                if (remaining <= 0L) {
                    break;
                }
                remaining -= cable.meknh_core$feStorage().extractEnergy(
                        (int) Math.min(Integer.MAX_VALUE, remaining), false);
            }
            boolean delivered = false;
            boolean exploded = false;
            for (int offset = 0; offset < consumers.size(); offset++) {
                FeEnergyEndpoint endpoint = consumers.get((cursor + offset) % consumers.size());
                if (endpoint.feVoltage() < wireVoltage) {
                    endpoint.overvoltageExplode();
                    exploded = true;
                    continue;
                }
                if (!endpoint.acceptsFePacket(wireVoltage)) {
                    continue;
                }
                if (endpoint.acceptFePacket(wireVoltage, (int) wireVoltage) > 0) {
                    cursor = (cursor + offset + 1) % consumers.size();
                    delivered = true;
                    break;
                }
            }
            if (delivered || exploded) {
                continue;
            }
            long refund = wireVoltage;
            for (FeCableAccess cable : cables) {
                refund -= cable.meknh_core$feStorage().receiveEnergy(
                        (int) Math.min(Integer.MAX_VALUE, refund), false);
            }
            break;
        }
    }

    private static void collect(ServerLevel level, BlockPos start, List<FeCableAccess> cables, List<BlockPos> positions) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty() && cables.size() <= MAX_CABLES) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof FeCableAccess cable)) {
                continue;
            }
            cables.add(cable);
            positions.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (visited.add(next) && level.getBlockEntity(next) instanceof FeCableAccess) {
                    queue.add(next);
                }
            }
        }
    }
}
