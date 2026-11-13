package c_y.meknh_core.mixin.mek.fission;

import java.util.Map;
import net.neoforged.neoforge.registries.BaseMappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 NeoForge 注册表的 {@code dataMaps} 字段，供 {@code ChemicalDataMaps} 注入本模组的化学品属性。
 */
@Mixin(BaseMappedRegistry.class)
public interface BaseMappedRegistryAccessor {

    @Accessor("dataMaps")
    Map<?, ?> meknh_core$dataMaps();
}
