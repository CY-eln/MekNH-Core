package c_y.meknh_core.api.chemical;

import c_y.meknh_core.mixin.mek.fission.BaseMappedRegistryAccessor;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import mekanism.api.chemical.Chemical;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.ChemicalSolidTag;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.api.datamaps.chemical.attribute.CooledCoolant;
import mekanism.api.datamaps.chemical.attribute.HeatedCoolant;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.slf4j.Logger;

/**
 * 把 {@code data/addon/chemicals/*.json} 中声明的属性写入 Mekanism 的化学品数据映射。
 *
 * <p>这些数据映射会随 NeoForge 的注册表数据同步发送到客户端，因此客户端与服务端都能
 * 读取到燃料、冷却剂、放射性、固体标签等属性，行为与 Mekanism 原生数据包一致。</p>
 */
public final class ChemicalDataMaps {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ChemicalDataMaps() {
    }

    /**
     * 在数据映射加载完成后注入本模组的化学品属性。
     *
     * <p>需要在 {@code DataMapsUpdatedEvent} 中调用，并且要早于 Mekanism 自己的监听器，
     * 这样 {@code Chemical#updateFromDataMap} 才能读取到注入的值。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void inject(Registry<Chemical> registry) {
        Collection<ChemicalDefinition> definitions = ChemicalRegistration.getDefinitions();
        if (definitions.isEmpty() || !(registry instanceof BaseMappedRegistryAccessor accessor)) {
            return;
        }
        Map<DataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> dataMaps = (Map) accessor.meknh_core$dataMaps();
        IMekanismDataMapTypes types = IMekanismDataMapTypes.INSTANCE;
        for (ChemicalDefinition definition : definitions) {
            if (definition.fluid()) {
                continue;
            }
            ResourceKey<Chemical> key = ChemicalRegistration.key(definition.id());
            if (registry.get(key) == null) {
                continue;
            }
            try {
                if (definition.fuel() != null) {
                    put(dataMaps, types.chemicalFuel(), key,
                            new ChemicalFuel(definition.fuel().burnTime(), definition.fuel().energyPerTick()));
                }
                if (definition.radioactivity() != null) {
                    put(dataMaps, types.chemicalRadioactivity(), key,
                            new ChemicalRadioactivity(definition.radioactivity()));
                }
                if (definition.solid() != null) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, definition.solid());
                    put(dataMaps, types.chemicalSolidTag(), key, new ChemicalSolidTag(tag));
                }
                if (definition.cooledCoolant() != null) {
                    Optional<Holder.Reference<Chemical>> variant = registry.getHolder(
                            ChemicalRegistration.key(definition.cooledCoolant().variant()));
                    if (variant.isPresent()) {
                        put(dataMaps, types.cooledChemicalCoolant(), key, new CooledCoolant(
                                variant.get(), definition.cooledCoolant().thermalEnthalpy(), definition.cooledCoolant().conductivity()));
                    } else {
                        LOGGER.error("Chemical {}: cooled_coolant.hot_variant {} is not registered", definition.id(),
                                definition.cooledCoolant().variant());
                    }
                }
                if (definition.heatedCoolant() != null) {
                    Optional<Holder.Reference<Chemical>> variant = registry.getHolder(
                            ChemicalRegistration.key(definition.heatedCoolant().variant()));
                    if (variant.isPresent()) {
                        put(dataMaps, types.heatedChemicalCoolant(), key, new HeatedCoolant(
                                variant.get(), definition.heatedCoolant().thermalEnthalpy(),
                                definition.heatedCoolant().conductivity(), definition.heatedCoolant().temperature()));
                    } else {
                        LOGGER.error("Chemical {}: heated_coolant.cool_variant {} is not registered", definition.id(),
                                definition.heatedCoolant().variant());
                    }
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Chemical {}: invalid attribute data", definition.id(), e);
            }
        }
        LOGGER.debug("Injected data map attributes for {} data-driven chemical(s)", definitions.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <A> void put(Map<DataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> dataMaps,
                                DataMapType<Chemical, A> type, ResourceKey<Chemical> key, A value) {
        ((Map) dataMaps.computeIfAbsent(type, ignored -> new IdentityHashMap<>())).put(key, value);
    }
}
