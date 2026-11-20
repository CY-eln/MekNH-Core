package c_y.meknh_core.mixin.mek.pipe;

import c_y.meknh_core.common.pipe.MekPipeChemicalAccess;
import c_y.meknh_core.common.pipe.MekPipeDefinition;
import c_y.meknh_core.common.pipe.MekPipeDefinitionHolder;
import mekanism.api.Action;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.TileEntityMechanicalPipe;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 给 Mek 机械管道加上“mek 槽位”的化学品能力与按面传输。 */
@Mixin(TileEntityMechanicalPipe.class)
public abstract class TileEntityMechanicalPipeMixin implements MekPipeChemicalAccess {

    @Unique
    private BasicChemicalTank meknh_core$chemicalTank;

    @Unique
    private MekPipeDefinition meknh_core$definition() {
        TileEntityMechanicalPipe self = (TileEntityMechanicalPipe) (Object) this;
        Block block = self.getBlockState().getBlock();
        return block instanceof MekPipeDefinitionHolder holder ? holder.meknh_core$definition() : null;
    }

    @Unique
    private BasicChemicalTank meknh_core$tank() {
        if (this.meknh_core$chemicalTank == null) {
            MekPipeDefinition definition = meknh_core$definition();
            if (definition != null) {
                this.meknh_core$chemicalTank = (BasicChemicalTank) BasicChemicalTank.create(definition.capacity(), null);
            }
        }
        return this.meknh_core$chemicalTank;
    }

    @Override
    public IChemicalTank meknh_core$chemicalTank() {
        return meknh_core$tank();
    }

    @Override
    @Nullable
    public IChemicalHandler meknh_core$chemicalHandler(@Nullable Direction side) {
        MekPipeDefinition definition = meknh_core$definition();
        if (definition == null) {
            return null;
        }
        if (side != null) {
            TileEntityMechanicalPipe self = (TileEntityMechanicalPipe) (Object) this;
            ConnectionType type = self.getTransmitter().getConnectionTypeRaw(side);
            if (type == ConnectionType.NONE) {
                return null;
            }
        }
        // 互斥：整张网络里已经有流体时，化学品槽位关闭，避免化学品罐往流体网络里弹
        if (c_y.meknh_core.common.pipe.MekPipeContents.ofNetwork(
                ((TileEntityMechanicalPipe) (Object) this).getTransmitter())
                == c_y.meknh_core.common.pipe.MekPipeContents.FLUID) {
            return null;
        }
        // 对外暴露整张网络的聚合槽位：容量相加、内容共享
        return c_y.meknh_core.common.pipe.MekPipeChemicalNetworkHandler.of(
                (TileEntityMechanicalPipe) (Object) this);
    }

    @Unique
    private static final java.util.Map<TileEntityMechanicalPipe, Integer> MEKNH$CONTENT = new java.util.WeakHashMap<>();

    @Unique
    private static final java.util.Map<net.minecraft.server.level.ServerLevel, long[]> MEKNH$TICK_CACHE = new java.util.WeakHashMap<>();

    @Inject(method = "onUpdateServer", at = @At("TAIL"))
    private void meknh_core$tickChemical(CallbackInfo ci) {
        TileEntityMechanicalPipe self = (TileEntityMechanicalPipe) (Object) this;
        var level = self.getLevel();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        long[] cache = MEKNH$TICK_CACHE.computeIfAbsent(serverLevel, ignored -> new long[]{Long.MIN_VALUE, 0L});
        if (cache[0] != serverLevel.getGameTime()) {
            cache[0] = serverLevel.getGameTime();
            cache[1] = 0L;
        }
        // 用位图标记本 tick 已处理的网络起点，避免每根管道重复跑
        long bit = 1L << (Math.floorMod(self.getBlockPos().hashCode(), 63));
        if ((cache[1] & bit) != 0L) {
            return;
        }
        java.util.List<TileEntityMechanicalPipe> pipes = new java.util.ArrayList<>();
        java.util.Set<net.minecraft.core.BlockPos> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<net.minecraft.core.BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(self.getBlockPos());
        visited.add(self.getBlockPos());
        while (!queue.isEmpty()) {
            var pos = queue.poll();
            if (!(serverLevel.getBlockEntity(pos) instanceof TileEntityMechanicalPipe pipe)
                    || !(serverLevel.getBlockState(pos).getBlock() instanceof c_y.meknh_core.common.pipe.MekPipeBlock)) {
                continue;
            }
            pipes.add(pipe);
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                var next = pos.relative(side);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (pipes.isEmpty()) {
            return;
        }
        cache[1] |= bit;
        // 内容变化后重新校验连接：流体/化学品不一致的面会被 Mek 断开
        for (TileEntityMechanicalPipe pipe : pipes) {
            int content = c_y.meknh_core.common.pipe.MekPipeContents.ofNetwork(pipe.getTransmitter());
            MEKNH$CONTENT.put(pipe, content);
            // 从根源上关掉另一种 capability：有化学品时不提供流体，有流体时不提供化学品
            if (content == c_y.meknh_core.common.pipe.MekPipeContents.CHEMICAL) {
                pipe.invalidateCapabilityAll(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK);
                // 模型同步：内容为化学品时，把连着流体容器的面改回 NONE，避免客户端还画连接段
                for (Direction side : Direction.values()) {
                    if (serverLevel.getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                            pipe.getBlockPos().relative(side), side.getOpposite()) != null) {
                        pipe.getTransmitter().setConnectionTypeRaw(side,
                                mekanism.common.lib.transmitter.ConnectionType.NONE);
                    }
                }
            } else if (content == c_y.meknh_core.common.pipe.MekPipeContents.FLUID) {
                pipe.invalidateCapabilityAll(mekanism.common.capabilities.Capabilities.CHEMICAL.block());
                for (Direction side : Direction.values()) {
                    if (serverLevel.getCapability(
                            mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                            pipe.getBlockPos().relative(side), side.getOpposite()) != null) {
                        pipe.getTransmitter().setConnectionTypeRaw(side,
                                mekanism.common.lib.transmitter.ConnectionType.NONE);
                    }
                }
            }
            pipe.getTransmitter().refreshConnections();
            // 化学品连接不在 Mek 的 acceptor 缓存里，主动同步一次连接模型数据
            pipe.sendUpdatePacket();
        }
        long transfer = Long.MAX_VALUE;
        for (TileEntityMechanicalPipe pipe : pipes) {
            if (pipe.getBlockState().getBlock() instanceof c_y.meknh_core.common.pipe.MekPipeDefinitionHolder holder) {
                transfer = Math.min(transfer, holder.meknh_core$definition().transfer());
            }
        }
        if (transfer == Long.MAX_VALUE) {
            return;
        }
        java.util.List<mekanism.api.chemical.IChemicalTank> tanks = new java.util.ArrayList<>();
        for (TileEntityMechanicalPipe pipe : pipes) {
            if (pipe instanceof MekPipeChemicalAccess access) {
                var tank = access.meknh_core$chemicalTank();
                if (tank != null) {
                    tanks.add(tank);
                }
            }
        }
        for (TileEntityMechanicalPipe pipe : pipes) {
            if (!(pipe instanceof MekPipeChemicalAccess access)) {
                continue;
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                ConnectionType type = pipe.getTransmitter().getConnectionTypeRaw(side);
                if (type != ConnectionType.PULL && type != ConnectionType.PUSH) {
                    continue;
                }
                var handler = serverLevel.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                        pipe.getBlockPos().relative(side), side.getOpposite());
                if (handler == null) {
                    continue;
                }
                if (type == ConnectionType.PULL) {
                    long amount = Math.min(transfer, handler.getChemicalInTank(0).getAmount());
                    if (amount <= 0L) {
                        continue;
                    }
                    ChemicalStack drained = handler.extractChemical(0, amount, Action.SIMULATE);
                    if (drained.isEmpty()) {
                        continue;
                    }
                    for (var target : tanks) {
                        ChemicalStack remainder = target.insert(drained, Action.SIMULATE,
                                mekanism.api.AutomationType.INTERNAL);
                        long accepted = drained.getAmount() - remainder.getAmount();
                        if (accepted > 0L) {
                            target.insert(handler.extractChemical(0, accepted, Action.EXECUTE), Action.EXECUTE,
                                    mekanism.api.AutomationType.INTERNAL);
                            break;
                        }
                    }
                } else {
                    for (var source : tanks) {
                        ChemicalStack stored = source.getStack();
                        if (stored.isEmpty()) {
                            continue;
                        }
                        long amount = Math.min(transfer, stored.getAmount());
                        ChemicalStack simulated = handler.insertChemical(stored.copyWithAmount(amount), Action.SIMULATE);
                        long accepted = amount - simulated.getAmount();
                        if (accepted > 0L) {
                            ChemicalStack taken = source.extract(accepted, Action.EXECUTE,
                                    mekanism.api.AutomationType.INTERNAL);
                            handler.insertChemical(taken, Action.EXECUTE);
                        }
                    }
                }
            }
        }
    }
}
