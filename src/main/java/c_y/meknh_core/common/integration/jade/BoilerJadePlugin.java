package c_y.meknh_core.common.integration.jade;

import c_y.meknh_core.common.generator.boiler.BoilerBlockEntity;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * 锅炉的 Jade 集成入口。
 *
 * <p>能源仓不需要自己的 provider：Mek 的 {@code JadeDataProvider} 是注册在
 * {@code BlockEntity.class} 上的，会读取任何方块的 {@code STRICT_ENERGY} 能力
 * （我们已经在 {@code MultiblockRegistration} 里注册了），再自己加一个只会重复且单位错。</p>
 */
@WailaPlugin
public final class BoilerJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BoilerJadeDataProvider.INSTANCE, BoilerBlockEntity.class);
    }
}
