package c_y.meknh_core.api.chemical;

import c_y.meknh_core.MekNHCore;
import mekanism.api.MekanismAPI;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

/**
 * 在 Mekanism 读取数据映射之前，把 {@link ChemicalDataMaps} 中的属性注入化学品注册表。
 */
@EventBusSubscriber(modid = MekNHCore.MODID)
public final class ChemicalDataMapHandler {

    private ChemicalDataMapHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        if (event.getCause() == DataMapsUpdatedEvent.UpdateCause.CLIENT_SYNC) {
            // 客户端同步下来的数据已经包含服务端注入的条目
            return;
        }
        event.ifRegistry(MekanismAPI.CHEMICAL_REGISTRY_NAME, ChemicalDataMaps::inject);
    }
}
