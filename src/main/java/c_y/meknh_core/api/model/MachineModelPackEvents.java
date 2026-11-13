package c_y.meknh_core.api.model;

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
 * 把运行时生成的机器模型包挂到客户端资源包列表最上层。
 */
@EventBusSubscriber(modid = MekNHCore.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MachineModelPackEvents {

    private static final String PACK_ID = MekNHCore.MODID + ":generated_models";

    private MachineModelPackEvents() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(consumer -> {
            PackLocationInfo info = new PackLocationInfo(
                    PACK_ID,
                    Component.literal("MekNH Core Generated Machine Models"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );
            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo location) {
                    return new MachineModelPackResources(location);
                }

                @Override
                public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                    return this.openPrimary(location);
                }
            };
            Pack.Metadata metadata = new Pack.Metadata(
                    Component.literal("Runtime generated machine blockstates and models"),
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
