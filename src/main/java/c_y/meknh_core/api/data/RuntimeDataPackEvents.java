package c_y.meknh_core.api.data;

import c_y.meknh_core.MekNHCore;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * 把运行时生成的数据包（战利品表）挂到服务端数据包列表最上层。
 *
 * <p>攻击/破坏方块走的是服务端战利品表，所以这份包必须是 {@link PackType#SERVER_DATA}，
 * 客户端单独启动时也要注册（单人游戏内置服务端靠它）。</p>
 */
@EventBusSubscriber(modid = MekNHCore.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = {Dist.CLIENT, Dist.DEDICATED_SERVER})
public final class RuntimeDataPackEvents {

    private static final String PACK_ID = MekNHCore.MODID + ":generated_loot";

    private RuntimeDataPackEvents() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(consumer -> {
            PackLocationInfo info = new PackLocationInfo(
                    PACK_ID,
                    Component.literal("MekNH Core Generated Loot Tables"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );
            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo location) {
                    return new RuntimeDataPackResources(location);
                }

                @Override
                public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                    return this.openPrimary(location);
                }
            };
            Pack.Metadata metadata = new Pack.Metadata(
                    Component.literal("Runtime generated loot tables for data-driven machines"),
                    PackCompatibility.COMPATIBLE,
                    FeatureFlagSet.of(),
                    List.of(),
                    false
            );
            Pack pack = new Pack(info, supplier, metadata, new PackSelectionConfig(true, Pack.Position.TOP, false));
            consumer.accept(pack);
        });
    }
}
