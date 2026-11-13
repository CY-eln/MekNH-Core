package c_y.meknh_core.common.network;

import c_y.meknh_core.MekNHCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 同步玩家光标上的物品；1.21.1 的 ClientboundContainerSetSlotPacket 不接受 -1。
 */
public record CarriedItemPayload(ItemStack stack) implements CustomPacketPayload {

    public static final Type<CarriedItemPayload> TYPE = new Type<>(MekNHCore.rl("carried_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriedItemPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, CarriedItemPayload::stack,
            CarriedItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CarriedItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                context.player().containerMenu.setCarried(payload.stack.copy());
            }
        });
    }
}
