package c_y.meknh_core.register;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.mixin.mek.fission.BaseMappedRegistryAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * 原版燃料注册。
 *
 * <p>使用 NeoForge 的 {@link NeoForgeDataMaps#FURNACE_FUELS} 数据映射，而不是自定义
 * 燃料系统。钻石燃烧 6400 个物品；原版每个物品 200 tick，因此 burn_time 为
 * {@code 6400 * 200 = 1,280,000} tick。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID)
public final class FuelRegister {

    /**
     * 钻石可烧炼的物品数。
     */
    public static final int DIAMOND_SMELT_COUNT = 6400;

    /**
     * 原版熔炉烧炼一个物品的 tick 数。
     */
    public static final int TICKS_PER_ITEM = 200;

    /**
     * 钻石的 burn_time（tick）。
     */
    public static final int DIAMOND_BURN_TIME = DIAMOND_SMELT_COUNT * TICKS_PER_ITEM;

    private static final ResourceKey<Item> DIAMOND = ResourceKey.create(
            Registries.ITEM, ResourceLocation.withDefaultNamespace("diamond"));

    private FuelRegister() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        if (event.getCause() == DataMapsUpdatedEvent.UpdateCause.CLIENT_SYNC) {
            // 客户端使用服务端同步下来的数据映射，避免重复注入。
            return;
        }
        event.ifRegistry(Registries.ITEM, FuelRegister::inject);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void inject(Registry<Item> registry) {
        if (!(registry instanceof BaseMappedRegistryAccessor accessor)) {
            return;
        }
        Map<DataMapType<Item, ?>, Map<ResourceKey<Item>, ?>> dataMaps =
                (Map) accessor.meknh_core$dataMaps();
        Map<ResourceKey<Item>, ?> fuels = dataMaps.computeIfAbsent(
                NeoForgeDataMaps.FURNACE_FUELS, ignored -> new IdentityHashMap<>());
        ((Map) fuels).put(DIAMOND, new FurnaceFuel(DIAMOND_BURN_TIME));
        MekNHCore.LOGGER.debug("Registered vanilla fuel {} with burn_time={}", Items.DIAMOND, DIAMOND_BURN_TIME);
    }
}
