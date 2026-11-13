package c_y.meknh_core.common.network;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.machine.DropperTankHolder;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.item.ItemGaugeDropper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 滴管对非物品槽位（流体/化学品罐）的取放。
 */
public record DropperUsePayload(BlockPos pos, int action, int tankType, int tankId) implements CustomPacketPayload {

    public static final int ACTION_FILL = 0;
    public static final int ACTION_DRAIN = 1;
    public static final int ACTION_DUMP = 2;
    public static final int TANK_CHEMICAL = 0;
    public static final int TANK_FLUID = 1;

    public static final Type<DropperUsePayload> TYPE = new Type<>(MekNHCore.rl("dropper_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DropperUsePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DropperUsePayload::pos,
            ByteBufCodecs.VAR_INT, DropperUsePayload::action,
            ByteBufCodecs.VAR_INT, DropperUsePayload::tankType,
            ByteBufCodecs.VAR_INT, DropperUsePayload::tankId,
            DropperUsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DropperUsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.isEmpty() || !(carried.getItem() instanceof ItemGaugeDropper)) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(payload.pos);
            if (!(be instanceof DropperTankHolder holder)) {
                return;
            }
            if (payload.tankType == TANK_CHEMICAL) {
                IChemicalHandler tank = holder.dropperChemicalHandler(payload.tankId);
                if (tank != null) {
                    handleChemical(player, carried, tank, payload.action);
                }
            } else if (payload.tankType == TANK_FLUID) {
                IFluidHandler tank = holder.dropperFluidHandler(payload.tankId);
                if (tank != null) {
                    handleFluid(player, carried, tank, payload.action);
                }
            }
        });
    }

    private static void handleChemical(ServerPlayer player, ItemStack carried, IChemicalHandler tank, int action) {
        if (action == ACTION_DUMP) {
            if (tank.getChemicalTanks() > 0) {
                tank.setChemicalInTank(0, ChemicalStack.EMPTY);
            }
            return;
        }
        IChemicalHandler handler = Capabilities.CHEMICAL.getCapability(carried);
        if (handler == null || handler.getChemicalTanks() <= 0) {
            return;
        }
        if (action == ACTION_FILL) {
            transferChemical(tank, handler, player);
        } else if (action == ACTION_DRAIN) {
            transferChemical(handler, tank, player);
        }
    }

    private static void handleFluid(ServerPlayer player, ItemStack carried, IFluidHandler tank, int action) {
        if (action == ACTION_DUMP) {
            tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            return;
        }
        IFluidHandlerItem handler = Capabilities.FLUID.getCapability(carried);
        if (handler == null) {
            return;
        }
        if (action == ACTION_FILL) {
            transferFluid(handler, tank, player);
        } else if (action == ACTION_DRAIN) {
            transferFluid(tank, handler, player);
        }
    }

    private static void transferChemical(IChemicalHandler drain, IChemicalHandler fill, ServerPlayer player) {
        ChemicalStack stack = drain.getChemicalInTank(0);
        if (stack.isEmpty() || fill.getChemicalTanks() <= 0) {
            return;
        }
        long room = Math.max(0L, fill.getChemicalTankCapacity(0) - fill.getChemicalInTank(0).getAmount());
        if (room <= 0) {
            return;
        }
        ChemicalStack offer = stack.copyWithAmount(Math.min(stack.getAmount(), room));
        ChemicalStack remainder = fill.insertChemical(0, offer, Action.SIMULATE);
        long amount = offer.getAmount() - remainder.getAmount();
        if (amount <= 0) {
            return;
        }
        ChemicalStack extracted = drain.extractChemical(0, amount, Action.EXECUTE);
        if (!extracted.isEmpty()) {
            fill.insertChemical(0, extracted, Action.EXECUTE);
            syncCarried(player);
        }
    }

    private static void transferFluid(IFluidHandler source, IFluidHandler sink, ServerPlayer player) {
        FluidStack stack = source.getFluidInTank(0);
        if (stack.isEmpty()) {
            return;
        }
        int room = sink.getTankCapacity(0) - sink.getFluidInTank(0).getAmount();
        if (room <= 0) {
            return;
        }
        FluidStack offer = stack.copyWithAmount(Math.min(stack.getAmount(), room));
        int accepted = sink.fill(offer, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return;
        }
        FluidStack drained = source.drain(offer.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) {
            sink.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            syncCarried(player);
        }
    }

    private static void syncCarried(ServerPlayer player) {
        ItemStack carried = player.containerMenu.getCarried();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new CarriedItemPayload(carried));
    }
}
