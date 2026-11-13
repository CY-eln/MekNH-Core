package c_y.meknh_core.common.network;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 仓室 GUI 的自动搬运开关（对应 GUI 右上角的 Tab 按钮）：
 * 输出仓是自动弹出，输入仓是自动吸入。
 *
 * <p>客户端只发“目标状态”，不做本地翻转：服务端写回后由 ContainerData 同步，
 * 避免连点/丢包导致开关状态反过来。</p>
 */
public record HatchAutoIoPayload(BlockPos pos, boolean enabled) implements CustomPacketPayload {

    public static final Type<HatchAutoIoPayload> TYPE = new Type<>(MekNHCore.rl("hatch_auto_io"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HatchAutoIoPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HatchAutoIoPayload::pos,
            ByteBufCodecs.BOOL, HatchAutoIoPayload::enabled,
            HatchAutoIoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HatchAutoIoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5,
                    payload.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(payload.pos);
            if (be instanceof MultiblockPartBlockEntity part) {
                part.setAutoTransfer(payload.enabled);
            }
        });
    }
}
