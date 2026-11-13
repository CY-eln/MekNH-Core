package c_y.meknh_core.client;

import c_y.meknh_core.api.MeknhCoreAPI;
import c_y.meknh_core.api.chemical.ChemicalRegistration;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import mekanism.api.chemical.Chemical;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

/**
 * 自定义图集来源：把所有通过 {@link ChemicalRegistration}
 * 注册的化学品的贴图自动加入 {@code minecraft:blocks} 图集。
 *
 * <p>这样数据包新增化学品时不需要再手工编辑 {@code assets/minecraft/atlases/blocks.json}。</p>
 */
public final class ChemicalAtlasSource implements SpriteSource {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ChemicalAtlasSource INSTANCE = new ChemicalAtlasSource();

    public static final MapCodec<ChemicalAtlasSource> CODEC = MapCodec.unit(INSTANCE);
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    private ChemicalAtlasSource() {
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        Set<ResourceLocation> added = new HashSet<>();
        for (Chemical chemical : MeknhCoreAPI.getChemicals()) {
            ResourceLocation icon = chemical.getIcon();
            if (!added.add(icon)) {
                continue;
            }
            ResourceLocation file = TEXTURE_ID_CONVERTER.idToFile(icon);
            Optional<Resource> resource = resourceManager.getResource(file);
            if (resource.isPresent()) {
                output.add(icon, resource.get());
            } else {
                LOGGER.warn("Missing texture {} for data-driven chemical {}", file, chemical.getTranslationKey());
            }
        }
        LOGGER.debug("Added {} data-driven chemical texture(s) to the blocks atlas", added.size());
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
