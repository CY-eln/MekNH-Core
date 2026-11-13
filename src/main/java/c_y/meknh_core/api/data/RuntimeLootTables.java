package c_y.meknh_core.api.data;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.generator.boiler.BoilerRegistration;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.api.machine.MachineVariant;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.api.multiblock.MultiblockVariant;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * 运行时生成数据驱动方块的战利品表。
 *
 * <p>机器 / 多方块控制器 / 仓室 / 锅炉都是按数据包 JSON 动态注册的，
 * 没法手写静态战利品表；这里和 {@code MachineModelResources} 一样，
 * 在运行时按当前注册结果生成 {@code data/meknh_core/loot_table/blocks/<名字>.json}。</p>
 *
 * <p>内容与静态外壳的战利品表一致：1 次 roll，掉方块物品本身，
 * 带 {@code survives_explosion} 条件，任何工具都能掉落（与现有外壳一致）。</p>
 */
public final class RuntimeLootTables {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RuntimeLootTables() {
    }

    /**
     * 生成全部动态方块的战利品表。
     *
     * @return 资源路径 → 文件内容
     */
    public static Map<ResourceLocation, byte[]> build() {
        Set<String> paths = new LinkedHashSet<>();
        for (MachineVariant variant : MachineRegistration.variants()) {
            paths.add(variant.path());
        }
        for (MultiblockVariant variant : MultiblockRegistration.variants()) {
            paths.add(variant.path());
        }
        for (MultiblockRegistration.PartBlockInfo part : MultiblockRegistration.partBlockInfos()) {
            paths.add(part.id().getPath());
        }
        for (BoilerRegistration.Variant variant : BoilerRegistration.variants()) {
            paths.add(variant.definition().id().getPath() + "_" + variant.tier().getSerializedName());
        }

        Map<ResourceLocation, byte[]> resources = new LinkedHashMap<>();
        for (String path : paths) {
            resources.put(MekNHCore.rl("loot_table/blocks/" + path + ".json"),
                    blockLoot(path).toString().getBytes(StandardCharsets.UTF_8));
        }
        LOGGER.info("Generated {} runtime loot table(s)", resources.size());
        return resources;
    }

    private static JsonObject blockLoot(String path) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:block");
        JsonArray pools = new JsonArray();
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.addProperty("bonus_rolls", 0);
        JsonArray entries = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", MekNHCore.MODID + ":" + path);
        entries.add(entry);
        pool.add("entries", entries);
        JsonArray conditions = new JsonArray();
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");
        conditions.add(condition);
        pool.add("conditions", conditions);
        pools.add(pool);
        root.add("pools", pools);
        root.addProperty("random_sequence", MekNHCore.MODID + ":blocks/" + path);
        return root;
    }
}
