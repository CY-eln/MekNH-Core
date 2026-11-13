package c_y.meknh_core.common.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MeknhNetwork {

    private MeknhNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(DropperUsePayload.TYPE, DropperUsePayload.STREAM_CODEC, DropperUsePayload::handle)
                .playToServer(HatchAutoIoPayload.TYPE, HatchAutoIoPayload.STREAM_CODEC,
                        HatchAutoIoPayload::handle)
                .playToClient(CarriedItemPayload.TYPE, CarriedItemPayload.STREAM_CODEC, CarriedItemPayload::handle);
    }
}
