package c_y.meknh_core.common.integration.jade;

import c_y.meknh_core.common.generator.boiler.BoilerBlockEntity;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * 锅炉的 Jade 集成入口。
 */
@WailaPlugin
public final class BoilerJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BoilerJadeDataProvider.INSTANCE, BoilerBlockEntity.class);
    }
}
