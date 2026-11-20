package c_y.meknh_core.common.pipe;

import c_y.meknh_core.api.machine.MachineBlockEntity;
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

/**
 * FE 电缆网络。
 *
 * <p>规则（按需求）：</p>
 * <ul>
 *     <li>电缆内部缓存，容量 = 电压 × 能量包个数；外部导线/创造能源可插入缓存；</li>
 *     <li>网络电压 = 网络内最低等级电缆电压，能量包数 = 最小值 Amperage；</li>
 *     <li>每 tick 传输整数个能量包，1 包 = 网络电压 FE，无距离损耗；</li>
 *     <li>相邻 FE 方块（非本模组机器）会被电缆主动抽出，灌入缓存；</li>
 *     <li>只有本模组机器作为负载，走内部 {@code acceptFePacket}；</li>
 *     <li>机器等级电压低于到达电压时爆炸（威力 2.0），有规则来源超压时低等级电缆烧毁。</li>
 * </ul>
 */
public final class FeCableNetwork {


    private static final int MAX_CABLES = 4096;
    private static final java.util.Map<ServerLevel, TickCache> CACHES = new java.util.WeakHashMap<>();

    private FeCableNetwork() {
    }

    private static final class TickCache {
        private long gameTime = Long.MIN_VALUE;
        private final Set<BlockPos> visited = new HashSet<>();
    }

    public static void tick(ServerLevel level, BlockPos start) {
        TickCache cache = CACHES.computeIfAbsent(level, ignored -> new TickCache());
        long gameTime = level.getGameTime();
        if (cache.gameTime != gameTime) {
            cache.gameTime = gameTime;
            cache.visited.clear();
        }
        if (cache.visited.contains(start)) {
            return;
        }
        List<FeCableBlockEntity> cables = collect(level, start);
        for (FeCableBlockEntity cable : cables) {
            cache.visited.add(cable.getBlockPos());
        }
        if (cables.isEmpty() || cables.size() > MAX_CABLES) {
            return;
        }

        long wireVoltage = Long.MAX_VALUE;
        int amperage = Integer.MAX_VALUE;
        for (FeCableBlockEntity cable : cables) {
            FePipeDefinition definition = cable.definition();
            if (definition == null) {
                return;
            }
            wireVoltage = Math.min(wireVoltage, definition.tier().voltage());
            amperage = Math.min(amperage, definition.amperage());
        }
        if (wireVoltage == Long.MAX_VALUE || amperage <= 0) {
            return;
        }

        List<MachineBlockEntity> consumers = new ArrayList<>();
        Set<BlockPos> seenConsumers = new HashSet<>();
        long sourceVoltage = 0L;
        for (FeCableBlockEntity cable : cables) {
            BlockPos cablePos = cable.getBlockPos();
            int pulled = 0;
            for (Direction side : Direction.values()) {
                BlockPos target = cablePos.relative(side);
                if (level.getBlockState(target).getBlock() instanceof FeCableBlock) {
                    continue;
                }
                BlockEntity blockEntity = level.getBlockEntity(target);
                if (blockEntity instanceof MachineBlockEntity machine) {
                    SideMode mode = machine.sideMode(MachineConfigType.ENERGY, side.getOpposite());
                    if (mode.allowsInsert() && seenConsumers.add(target)) {
                        consumers.add(machine);
                    }
                    if (mode.allowsExtract()) {
                        sourceVoltage = Math.max(sourceVoltage, machine.feVoltage());
                    }
                    continue;
                }
                // 主动抽出相邻 FE 方块的能量，灌进电缆缓存。
                // 每 tick 抽取上限 = 电缆自身传输速率（电压 × Amperage）。
                int space = cable.bufferCapacity() - cable.energyStorage().getEnergyStored();
                int budget = (int) Math.min(cable.definition().maxTransfer(), space - pulled);
                if (budget <= 0) {
                    continue;
                }
                IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target,
                        side.getOpposite());
                if (storage == null) {
                    storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, null);
                }
                if (storage != null) {
                    int moved = storage.extractEnergy(Math.min(budget, storage.getEnergyStored()), true);
                    if (moved > 0) {
                        moved = storage.extractEnergy(moved, false);
                        cable.energyStorage().receiveEnergy(moved, false);
                        pulled += moved;
                        continue;
                    }
                }
                // 兼容 Mekanism 的严格能量（创造能源等，单位 J）。
                mekanism.api.energy.IStrictEnergyHandler strict = level.getCapability(
                        mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(), target, side.getOpposite());
                if (strict == null) {
                    strict = level.getCapability(mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block(),
                            target, null);
                }
                if (strict != null) {
                    long want = Math.round(budget * 2.5D);
                    long got = strict.extractEnergy(0, want, mekanism.api.Action.SIMULATE);
                    if (got > 0L) {
                        got = strict.extractEnergy(0, got, mekanism.api.Action.EXECUTE);
                        int fe = (int) Math.min(Integer.MAX_VALUE, Math.floor(got / 2.5D));
                        int accepted = cable.energyStorage().receiveEnergy(fe, false);
                        pulled += accepted;
                    }
                }
            }
        }

        // 有规则来源超压：烧掉承受不住的电缆。
        if (sourceVoltage > wireVoltage) {
            for (FeCableBlockEntity cable : cables) {
                if (cable.definition().tier().voltage() < sourceVoltage) {
                    level.removeBlock(cable.getBlockPos(), false);
                }
            }
            return;
        }
        if (consumers.isEmpty()) {
            return;
        }

        int cursor = 0;
        for (int packet = 0; packet < amperage; packet++) {
            if (!hasEnergy(cables, wireVoltage)) {
                break;
            }
            // 从网络缓存里抽出正好一个能量包。
            long remaining = wireVoltage;
            for (FeCableBlockEntity cable : cables) {
                if (remaining <= 0L) {
                    break;
                }
                int want = (int) Math.min(Integer.MAX_VALUE, remaining);
                int got = cable.energyStorage().extractEnergy(want, false);
                remaining -= got;
            }
            if (remaining > 0L) {
                break;
            }

            boolean delivered = false;
            boolean exploded = false;
            for (int offset = 0; offset < consumers.size(); offset++) {
                MachineBlockEntity machine = consumers.get((cursor + offset) % consumers.size());
                if (machine.feVoltage() < wireVoltage) {
                    machine.overvoltageExplode();
                    exploded = true;
                    continue;
                }
                if (!machine.acceptsFePacket(wireVoltage)) {
                    continue;
                }
                if (machine.acceptFePacket(wireVoltage, (int) wireVoltage) > 0) {
                    cursor = (cursor + offset + 1) % consumers.size();
                    delivered = true;
                    break;
                }
            }
            if (!delivered && exploded) {
                // 超压爆炸：该能量包直接损失。
                continue;
            }
            if (!delivered) {
                // 送不出去就退回网络缓存（尽量按顺序回填）。
                long refund = wireVoltage;
                for (FeCableBlockEntity cable : cables) {
                    if (refund <= 0L) {
                        break;
                    }
                    int accepted = cable.energyStorage().receiveEnergy((int) Math.min(Integer.MAX_VALUE, refund), false);
                    refund -= accepted;
                }
                break;
            }
        }
    }

    private static boolean hasEnergy(List<FeCableBlockEntity> cables, long amount) {
        long total = 0L;
        for (FeCableBlockEntity cable : cables) {
            total += cable.energyStorage().getEnergyStored();
            if (total >= amount) {
                return true;
            }
        }
        return false;
    }

    private static List<FeCableBlockEntity> collect(ServerLevel level, BlockPos start) {
        List<FeCableBlockEntity> cables = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockState(pos).getBlock() instanceof FeCableBlock)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof FeCableBlockEntity cable) {
                cables.add(cable);
            }
            if (cables.size() > MAX_CABLES) {
                return cables;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (visited.add(next) && level.getBlockState(next).getBlock() instanceof FeCableBlock) {
                    queue.add(next);
                }
            }
        }
        return cables;
    }
}
