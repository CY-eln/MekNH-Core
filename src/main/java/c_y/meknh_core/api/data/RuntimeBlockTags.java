package c_y.meknh_core.api.data;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.generator.boiler.BoilerRegistration;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.api.machine.MachineVariant;
import c_y.meknh_core.api.multiblock.MultiblockPartType;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.api.multiblock.MultiblockVariant;
import c_y.meknh_core.common.pipe.FePipeDefinition;
import c_y.meknh_core.common.pipe.FePipeRegistration;
import c_y.meknh_core.common.pipe.MekPipeDefinition;
import c_y.meknh_core.common.pipe.MekPipeRegistration;
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
 * 运行时生成挖掘标签（{@code minecraft:mineable/pickaxe} 与 {@code minecraft:needs_stone_tool}）。
 *
 * <p>机器 / 多方块控制器 / 仓室 / 锅炉 / 管道都是按数据包 JSON 动态注册的，方块 id 在编译期未知，
 * 手写静态标签永远会漏。这里和 {@link RuntimeLootTables} 一样，在运行时按当前注册结果生成。</p>
 *
 * <p>为什么要这两个标签：这些方块都带 {@code requiresCorrectToolForDrops()}，而
 * 「是否使用了正确工具」的判定就是方块有没有进 {@code mineable/*} 标签。
 * 不在标签里 → 即使用镐也不算正确工具 → 掉落物为空、挖掘速度再乘 1/3.33。
 * 这就是「方块没硬度、挖了也不掉」的根因。</p>
 */
public final class RuntimeBlockTags {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 需要石镐及以上的金属/石质方块（静态外壳 + 全部动态方块）。 */
    private static final String[] STATIC_METAL = {
            "steam_machine_casing",
            "bronze_firebox_casing",
            "bronze_brick_casing",
            "wrought_iron_brick_casing",
            "bronze_machine_casing",
            "coke_oven_bricks",
            "primitive_blast_furnace_bricks"
    };

    private RuntimeBlockTags() {
    }

    /**
     * 生成挖掘相关标签。
     *
     * @return 资源路径 → 文件内容（命名空间分别是 {@code minecraft}）
     */
    public static Map<ResourceLocation, byte[]> build() {
        Set<String> metal = new LinkedHashSet<>();
        for (String path : STATIC_METAL) {
            metal.add(path);
        }
        for (MachineVariant variant : MachineRegistration.variants()) {
            metal.add(variant.path());
        }
        for (MultiblockVariant variant : MultiblockRegistration.variants()) {
            metal.add(variant.path());
        }
        for (MultiblockRegistration.PartBlockInfo part : MultiblockRegistration.partBlockInfos()) {
            // 水泵仓是木质方块，留在 mineable/axe 里，不要求石镐
            if (part.type() == MultiblockPartType.PUMP_HATCH) {
                continue;
            }
            metal.add(part.id().getPath());
        }
        for (BoilerRegistration.Variant variant : BoilerRegistration.variants()) {
            metal.add(variant.definition().id().getPath() + "_" + variant.tier().getSerializedName());
        }

        // 管道 / 电缆是软管，进 pickaxe 能加速，但不要求石镐（木镐也能回收）
        Set<String> soft = new LinkedHashSet<>();
        for (FePipeDefinition definition : FePipeRegistration.definitions()) {
            soft.add(definition.id().getPath());
        }
        for (MekPipeDefinition definition : MekPipeRegistration.definitions()) {
            soft.add(definition.id().getPath());
        }

        Set<String> pickaxe = new LinkedHashSet<>(metal);
        pickaxe.addAll(soft);
        pickaxe.removeIf(path -> !net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .containsKey(MekNHCore.rl(path)));
        metal.removeIf(path -> !net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .containsKey(MekNHCore.rl(path)));

        Map<ResourceLocation, byte[]> resources = new LinkedHashMap<>();
        resources.put(ResourceLocation.fromNamespaceAndPath("minecraft", "tags/block/mineable/pickaxe.json"),
                tag(pickaxe).toString().getBytes(StandardCharsets.UTF_8));
        resources.put(ResourceLocation.fromNamespaceAndPath("minecraft", "tags/block/needs_stone_tool.json"),
                tag(metal).toString().getBytes(StandardCharsets.UTF_8));
        LOGGER.info("Generated mining tags: pickaxe={}, needs_stone_tool={}", pickaxe.size(), metal.size());
        return resources;
    }

    private static JsonObject tag(Set<String> paths) {
        JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        JsonArray values = new JsonArray();
        for (String path : paths) {
            values.add(MekNHCore.MODID + ":" + path);
        }
        root.add("values", values);
        return root;
    }
}
