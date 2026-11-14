package c_y.meknh_core.client.jade;

import c_y.meknh_core.api.multiblock.MultiblockControllerBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 集成：给多方块控制器显示“结构已成型 / 结构不完整”。
 */
@WailaPlugin
public final class MeknhJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new MultiblockStatusProvider(), MultiblockControllerBlock.class);
    }
}
