package c_y.meknh_core.api.model;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.api.machine.MachineVariant;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.api.multiblock.MultiblockVariant;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.machine.steam.SteamVariant;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * 机器模型与方块状态的生成（{@code 应用模型贴图.txt}）。
 *
 * <p>对每个机器版本生成 4 个 JSON：</p>
 * <ul>
 *     <li>{@code models/block/machine/<名字>/base.json}：外壳模型，parent 指向通用外壳模板，
 *     textures 按电压等级选择机壳贴图（蒸汽低压砖砌青铜、高压砖砌钢、电力跟随电压）；</li>
 *     <li>{@code overlay_off.json} / {@code overlay_on.json}：覆盖层模型，parent 指向通用覆盖层模板，
 *     textures 指向 {@code block/machine/<overlay>/overlay_front[_active]}；</li>
 *     <li>{@code models/item/<名字>.json}：物品模型，合并外壳与覆盖层（供物品栏显示）。</li>
 * </ul>
 *
 * <p>方块状态使用 multipart：{@code facing} 决定底座旋转，{@code active} 决定使用哪张覆盖层，
 * 未激活与激活的覆盖层叠加在底座上。</p>
 */
public final class MachineModelResources {

    /**
     * 通用外壳模板（cube，使用 #side/#top/#bottom）。
     */
    public static final ResourceLocation CASING_TEMPLATE = MekNHCore.rl("block/machine/template/casing");

    /**
     * 通用覆盖层模板（正面薄片，使用 #overlay_front）。
     */
    public static final ResourceLocation OVERLAY_TEMPLATE = MekNHCore.rl("block/machine/template/overlay");

    private static final String[] FACINGS = {"north", "east", "south", "west"};
    private static final int[] ROTATIONS = {0, 90, 180, 270};

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 焦炉砖外壳贴图。
     */
    public static final String COKE_BRICKS = "meknh_core:block/casings/solid/machine_coke_bricks";

    /**
     * 焦炉正面覆盖层目录。
     */
    public static final String COKE_OVERLAY = "meknh_core:block/multiblock/coke_oven";

    private MachineModelResources() {
    }

    /**
     * 生成全部机器版本需要的资源文件。
     *
     * @return 资源路径 → 文件内容
     */
    public static Map<ResourceLocation, byte[]> build() {
        Map<ResourceLocation, byte[]> resources = new LinkedHashMap<>();
        for (MachineVariant variant : MachineRegistration.variants()) {
            String name = variant.path();
            String casing = casingTexture(variant.level());
            String overlay = "meknh_core:block/machine/" + variant.definition().overlay();
            if (!textureExists(overlay + "/overlay_front")) {
                LOGGER.warn("Machine {}: overlay texture {} is missing", variant.id(), overlay + "/overlay_front");
            }
            boolean hasSideOverlay = textureExists(overlay + "/overlay_side");
            boolean hasTopOverlay = textureExists(overlay + "/overlay_top");

            // 资源包里的路径必须带 .json，否则 PackResources#getResource 永远不会命中
            put(resources, "blockstates/" + name + ".json", blockstate(name, hasSideOverlay, hasTopOverlay));
            put(resources, "models/block/machine/" + name + "/base.json", baseModel(casing));
            // 覆盖层必须始终生成：未激活 overlay_front / 运行中 overlay_front_active
            put(resources, "models/block/machine/" + name + "/overlay_off.json", overlayModel(overlay + "/overlay_front"));
            put(resources, "models/block/machine/" + name + "/overlay_on.json", overlayModel(overlay + "/overlay_front_active"));
            if (hasSideOverlay) {
                put(resources, "models/block/machine/" + name + "/overlay_side_off.json",
                        sideOverlayModel(overlay + "/overlay_side"));
                put(resources, "models/block/machine/" + name + "/overlay_side_on.json",
                        sideOverlayModel(overlay + "/overlay_side_active"));
            }
            if (hasTopOverlay) {
                put(resources, "models/block/machine/" + name + "/overlay_top_off.json",
                        topOverlayModel(overlay + "/overlay_top"));
                put(resources, "models/block/machine/" + name + "/overlay_top_on.json",
                        topOverlayModel(overlay + "/overlay_top_active"));
            }
            put(resources, "models/item/" + name + ".json", itemModel(casing, overlay + "/overlay_front"));
        }

        // 多方块控制器：立方体外壳 + 按 facing 的 blockstate；没有 formed/active 覆盖层。
        for (MultiblockVariant variant : MultiblockRegistration.variants()) {
            String name = variant.path();
            String model = "meknh_core:block/multiblock/" + name;
            if (variant.definition().id().getPath().equals("coke_oven")) {
                // 焦炉：焦炉砖外壳 + 正面覆盖层（空闲/工作两套），blockstate 需要 formed × active 全组合。
                put(resources, "blockstates/" + name + ".json", cokeOvenBlockstate(name));
                put(resources, "models/block/multiblock/" + name + "_idle.json",
                        partModel(COKE_BRICKS, new PartVisual(COKE_OVERLAY + "/overlay_front",
                                COKE_OVERLAY + "/overlay_front_emissive", null, false), true));
                put(resources, "models/block/multiblock/" + name + "_active.json",
                        partModel(COKE_BRICKS, new PartVisual(COKE_OVERLAY + "/overlay_front_active",
                                COKE_OVERLAY + "/overlay_front_active_emissive", null, false), true));
                put(resources, "models/item/" + name + ".json", parentItemModel(model + "_idle"));
                continue;
            }
            put(resources, "blockstates/" + name + ".json", facingCubeBlockstate(model));
            put(resources, "models/block/multiblock/" + name + ".json", baseModel(casingTexture(variant.level())));
            put(resources, "models/item/" + name + ".json", parentItemModel(model));
        }
        // 仓室：电压外壳 + 正面功能覆盖层（物品/流体/能量），按朝向旋转。
        for (MultiblockRegistration.PartBlockInfo part : MultiblockRegistration.partBlockInfos()) {
            String path = part.id().getPath();
            String model = "meknh_core:block/multiblock/part/" + path;
            put(resources, "blockstates/" + path + ".json", facingPartBlockstate(model));
            put(resources, "models/block/multiblock/part/" + path + ".json",
                    partModel(partCasingTexture(part.level(), part.type()), partVisual(part.type(), part.amp()),
                            part.type() == c_y.meknh_core.api.multiblock.MultiblockPartType.COKE_OVEN_HATCH));
            put(resources, "models/item/" + path + ".json", parentItemModel(model));
        }

        // 焦炉砖：普通 cube_all 方块。
        put(resources, "blockstates/coke_oven_bricks.json", cubeAllBlockstate());
        put(resources, "models/block/coke_oven_bricks.json", cubeAllModel());
        put(resources, "models/item/coke_oven_bricks.json", parentItemModel("meknh_core:block/coke_oven_bricks"));

        LOGGER.info("Generated {} machine model resource(s)", resources.size());
        return resources;
    }

    /**
     * 焦炉方块状态：facing 4 向 × formed 2 × active 2，全部显式列出，
     * 否则 MC 会因为缺变体而渲染成 missing model。
     */
    private static JsonObject cokeOvenBlockstate(String name) {
        JsonObject blockstate = new JsonObject();
        JsonObject variants = new JsonObject();
        for (int index = 0; index < FACINGS.length; index++) {
            String facing = FACINGS[index];
            int rotation = ROTATIONS[index];
            for (boolean formed : new boolean[]{false, true}) {
                for (boolean active : new boolean[]{false, true}) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("model", "meknh_core:block/multiblock/" + name
                            + (active ? "_active" : "_idle"));
                    if (rotation != 0) {
                        entry.addProperty("y", rotation);
                    }
                    variants.add("facing=" + facing + ",formed=" + formed + ",active=" + active, entry);
                }
            }
        }
        blockstate.add("variants", variants);
        return blockstate;
    }

    private static JsonObject cubeAllBlockstate() {
        JsonObject blockstate = new JsonObject();
        JsonObject variants = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("model", "meknh_core:block/coke_oven_bricks");
        variants.add("", entry);
        blockstate.add("variants", variants);
        return blockstate;
    }

    private static JsonObject cubeAllModel() {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", COKE_BRICKS);
        model.add("textures", textures);
        return model;
    }

    /**
     * 四方朝向共用同一个立方体模型；formed / active 属性留空作为通配。
     */
    private static JsonObject facingCubeBlockstate(String model) {
        JsonObject blockstate = new JsonObject();
        JsonObject variants = new JsonObject();
        for (String facing : FACINGS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("model", model);
            variants.add("facing=" + facing, entry);
        }
        blockstate.add("variants", variants);
        return blockstate;
    }

    /**
     * 物品模型直接继承方块模型。
     */
    private static JsonObject parentItemModel(String parent) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        return model;
    }

    /**
     * 仓室 blockstate：6 个朝向共用一个「北面带覆盖层」的模型，按朝向旋转。
     *
     * <p>旋转取值与 Mek 的 {@code basic_energy_cube.json} 一致：
     * 北 0、东 y=90、南 y=180、西 y=270、上 x=-90、下 x=90。</p>
     */
    private static JsonObject facingPartBlockstate(String model) {
        JsonObject blockstate = new JsonObject();
        JsonObject variants = new JsonObject();
        partVariant(variants, model, "north", 0, 0);
        partVariant(variants, model, "east", 0, 90);
        partVariant(variants, model, "south", 0, 180);
        partVariant(variants, model, "west", 0, 270);
        partVariant(variants, model, "up", -90, 0);
        partVariant(variants, model, "down", 90, 0);
        blockstate.add("variants", variants);
        return blockstate;
    }

    private static void partVariant(JsonObject variants, String model, String facing, int x, int y) {
        JsonObject entry = new JsonObject();
        entry.addProperty("model", model);
        if (x != 0) {
            entry.addProperty("x", x);
        }
        if (y != 0) {
            entry.addProperty("y", y);
        }
        variants.add("facing=" + facing, entry);
    }

    /**
     * 一个仓室正面的两层贴图：功能图标、动态发光环。
     */
    private record PartVisual(String overlay, String emissive, String tint, boolean pipe) {
    }

    /**
     * 仓室模型，严格按 GT {@code block/machine/template/part/hatch_machine_emissive.json}：
     *
     * <pre>
     * 外壳 cube
     * overlay_pipe      -0.01（所有仓室都有，父模型 hatch_machine 的默认纹理）
     * overlay           -0.02
     * overlay_emissive  -0.03（自发光，16×96 动态环）
     * </pre>
     */
    private static JsonObject partModel(String casing, PartVisual visual) {
        return partModel(casing, visual, false);
    }

    /**
     * @param singleTexture 外壳只有一张贴图（如焦炉砖）时用 cube_all，而不是 side/top/bottom
     */
    private static JsonObject partModel(String casing, PartVisual visual, boolean singleTexture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.addProperty("render_type", "minecraft:cutout");
        JsonObject textures = new JsonObject();
        if (singleTexture) {
            textures.addProperty("particle", casing);
            textures.addProperty("all", casing);
        } else {
            textures.addProperty("particle", casing + "/side");
            textures.addProperty("side", casing + "/side");
            textures.addProperty("top", casing + "/top");
            textures.addProperty("bottom", casing + "/bottom");
        }
        textures.addProperty("overlay_front", visual.overlay());
        if (visual.tint() != null) {
            textures.addProperty("overlay_tint", visual.tint());
        } else if (visual.pipe()) {
            textures.addProperty("overlay_pipe", "meknh_core:block/overlay/machine/overlay_pipe");
        }
        if (visual.emissive() != null) {
            textures.addProperty("overlay_emissive", visual.emissive());
        }
        model.add("textures", textures);

        JsonArray elements = new JsonArray();
        elements.add(singleTexture ? cubeAll() : cube());
        if (visual.tint() != null) {
            elements.add(overlayPlane("#overlay_tint", -0.01D, false, 2));
        } else if (visual.pipe()) {
            elements.add(overlayPlane("#overlay_pipe", -0.01D, false, -1));
        }
        elements.add(overlayPlane("#overlay_front", -0.02D, false, -1));
        if (visual.emissive() != null) {
            elements.add(overlayPlane("#overlay_emissive", -0.03D, true, -1));
        }
        model.add("elements", elements);
        return model;
    }

    /**
     * 正面薄片层。
     *
     * @param texture    纹理键
     * @param z          z 偏移（越负越靠前）
     * @param fullbright 是否自发光
     */
    private static JsonObject overlayPlane(String texture, double z, boolean fullbright, int tintIndex) {
        JsonObject element = new JsonObject();
        element.add("from", array(0.0, 0.0, z));
        element.add("to", array(16.0, 16.0, z));
        if (fullbright) {
            element.addProperty("shade", false);
            JsonObject data = new JsonObject();
            data.addProperty("block_light", 15);
            data.addProperty("sky_light", 15);
            element.add("neoforge_data", data);
        }
        JsonObject faces = new JsonObject();
        JsonObject face = new JsonObject();
        face.add("uv", array(0.0, 0.0, 16.0, 16.0));
        face.addProperty("texture", texture);
        face.addProperty("cullface", "north");
        if (tintIndex >= 0) {
            face.addProperty("tintindex", tintIndex);
        }
        faces.add("north", face);
        element.add("faces", faces);
        return element;
    }

    private static void put(Map<ResourceLocation, byte[]> resources, String path, JsonObject json) {
        resources.put(MekNHCore.rl(path), json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 外壳贴图前缀，例如 {@code meknh_core:block/casings/voltage/lv}。
     */
    /**
     * 仓室的外壳贴图：蒸汽（ULV）统一使用 {@code casings/voltage/ulv}，电力仓跟随电压等级。
     */
    private static String partCasingTexture(MachineLevel level,
                                            c_y.meknh_core.api.multiblock.MultiblockPartType type) {
        if (type == c_y.meknh_core.api.multiblock.MultiblockPartType.COKE_OVEN_HATCH) {
            // 焦炉仓使用焦炉砖外壳。
            return COKE_BRICKS;
        }
        if (type == c_y.meknh_core.api.multiblock.MultiblockPartType.STEAM_INPUT) {
            // GT steam_input_hatch 使用 casings/steam/bronze 外壳。
            return "meknh_core:block/casings/steam/bronze";
        }
        if (level.isSteam()) {
            return "meknh_core:block/casings/voltage/ulv";
        }
        return "meknh_core:block/casings/voltage/" + level.voltage().getSerializedName();
    }

    /**
     * 仓室正面的贴图层。
     *
     * <p>与 GT {@code GTMachines} 的调用完全对应：</p>
     * <ul>
     *     <li>物品总线：{@code overlay_item_hatch_input/output} + {@code overlay_pipe}
     *     + {@code overlay_pipe_in/out_emissive}；</li>
     *     <li>流体仓：{@code overlay_fluid_hatch_input/output}，没有管道环，
     *     仍然带 {@code overlay_pipe_in/out_emissive}；</li>
     *     <li>能源仓：GT 用独立的 {@code block/machine/part/energy_*_hatch} 模型，
     *     这里沿用能源图标、不加发光环。</li>
     * </ul>
     */
    private static PartVisual partVisual(c_y.meknh_core.api.multiblock.MultiblockPartType type, int amp) {
        String base = "meknh_core:block/overlay/machine/";
        String ringIn = base + "overlay_pipe_in_emissive";
        String ringOut = base + "overlay_pipe_out_emissive";
        String energy = "overlay_energy_" + amp + "a_";
        return switch (type) {
            case ITEM_INPUT -> new PartVisual(base + "overlay_item_hatch_input", ringIn, null, true);
            case ITEM_OUTPUT -> new PartVisual(base + "overlay_item_hatch_output", ringOut, null, true);
            case FLUID_INPUT -> new PartVisual(base + "overlay_fluid_hatch_input", ringIn, null, true);
            case FLUID_OUTPUT -> new PartVisual(base + "overlay_fluid_hatch_output", ringOut, null, true);
            case STEAM_INPUT -> new PartVisual("meknh_core:block/cover/pump", null, null, false);
            case COKE_OVEN_HATCH -> new PartVisual(base + "overlay_hatch", null, null, false);
            case ENERGY_INPUT -> new PartVisual(base + energy + "in", base + energy + "in_emissive", base + energy + "tinted", false);
            case ENERGY_OUTPUT -> new PartVisual(base + energy + "out", base + energy + "out_emissive", base + energy + "tinted", false);
        };
    }

    private static String casingTexture(MachineLevel level) {
        if (level.isSteam()) {
            return level.steam() == SteamVariant.HIGH
                    ? "meknh_core:block/casings/steam/bricked_steel"
                    : "meknh_core:block/casings/steam/bricked_bronze";
        }
        return "meknh_core:block/casings/voltage/" + level.voltage().getSerializedName();
    }

    private static JsonObject baseModel(String casing) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", CASING_TEMPLATE.toString());
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", casing + "/side");
        textures.addProperty("side", casing + "/side");
        textures.addProperty("top", casing + "/top");
        textures.addProperty("bottom", casing + "/bottom");
        model.add("textures", textures);
        return model;
    }

    private static JsonObject overlayModel(String overlayTexture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", OVERLAY_TEMPLATE.toString());
        JsonObject textures = new JsonObject();
        textures.addProperty("overlay_front", overlayTexture);
        model.add("textures", textures);
        return model;
    }

    private static JsonObject sideOverlayModel(String overlayTexture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", MekNHCore.rl("block/machine/template/overlay_side").toString());
        JsonObject textures = new JsonObject();
        textures.addProperty("overlay_side", overlayTexture);
        model.add("textures", textures);
        return model;
    }

    private static JsonObject topOverlayModel(String overlayTexture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", MekNHCore.rl("block/machine/template/overlay_top").toString());
        JsonObject textures = new JsonObject();
        textures.addProperty("overlay_top", overlayTexture);
        model.add("textures", textures);
        return model;
    }

    private static JsonObject itemModel(String casing, String overlayTexture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.addProperty("render_type", "minecraft:cutout");
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", casing + "/side");
        textures.addProperty("side", casing + "/side");
        textures.addProperty("top", casing + "/top");
        textures.addProperty("bottom", casing + "/bottom");
        model.add("textures", textures);

        JsonArray elements = new JsonArray();
        elements.add(cube());
        textures.addProperty("overlay_front", overlayTexture);
        elements.add(frontOverlay());
        model.add("elements", elements);
        return model;
    }

    private static JsonObject blockstate(String name, boolean hasSideOverlay, boolean hasTopOverlay) {
        JsonObject blockstate = new JsonObject();
        JsonArray multipart = new JsonArray();

        for (int index = 0; index < FACINGS.length; index++) {
            String facing = FACINGS[index];
            int rotation = ROTATIONS[index];

            JsonObject base = new JsonObject();
            JsonObject baseWhen = new JsonObject();
            baseWhen.addProperty("facing", facing);
            base.add("when", baseWhen);
            base.add("apply", applyWithRotation("meknh_core:block/machine/" + name + "/base", rotation));
            multipart.add(base);

            multipart.add(overlayPart(name, facing, rotation, false, "overlay"));
            multipart.add(overlayPart(name, facing, rotation, true, "overlay"));
            if (hasSideOverlay) {
                multipart.add(overlayPart(name, facing, rotation, false, "overlay_side"));
                multipart.add(overlayPart(name, facing, rotation, true, "overlay_side"));
            }
            if (hasTopOverlay) {
                multipart.add(overlayPart(name, facing, rotation, false, "overlay_top"));
                multipart.add(overlayPart(name, facing, rotation, true, "overlay_top"));
            }
        }

        blockstate.add("multipart", multipart);
        return blockstate;
    }

    /**
     * 尽力检查贴图是否存在，仅用于日志提醒；无论结果如何都会生成覆盖层，
     * 避免因为判断失误把覆盖层整个丢掉。
     */
    private static boolean textureExists(String textureId) {
        try {
            var modFileInfo = net.neoforged.fml.ModList.get().getModFileById(MekNHCore.MODID);
            if (modFileInfo == null) {
                return true;
            }
            String[] parts = textureId.split(":", 2);
            var path = modFileInfo.getFile().findResource("assets", parts[0], "textures", parts[1] + ".png");
            return path != null && java.nio.file.Files.exists(path);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static JsonObject overlayPart(String name, String facing, int rotation, boolean active, String layer) {
        JsonObject part = new JsonObject();
        JsonObject when = new JsonObject();
        when.addProperty("facing", facing);
        when.addProperty("active", active);
        part.add("when", when);
        part.add("apply", applyWithRotation(
                "meknh_core:block/machine/" + name + "/" + layer + (active ? "_on" : "_off"),
                rotation
        ));
        return part;
    }

    private static JsonObject applyWithRotation(String model, int rotation) {
        JsonObject apply = new JsonObject();
        apply.addProperty("model", model);
        if (rotation != 0) {
            apply.addProperty("y", rotation);
        }
        return apply;
    }

    /**
     * 单贴图立方体：所有面都用 {@code #all}（焦炉砖这种 cube_all 外壳）。
     */
    private static JsonObject cubeAll() {
        JsonObject element = new JsonObject();
        element.add("from", array(0.0, 0.0, 0.0));
        element.add("to", array(16.0, 16.0, 16.0));
        JsonObject faces = new JsonObject();
        for (String direction : new String[]{"down", "up", "north", "south", "west", "east"}) {
            faces.add(direction, face("#all", direction));
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonObject cube() {        JsonObject element = new JsonObject();
        element.add("from", array(0.0, 0.0, 0.0));
        element.add("to", array(16.0, 16.0, 16.0));
        JsonObject faces = new JsonObject();
        faces.add("down", face("#bottom", "down"));
        faces.add("up", face("#top", "up"));
        faces.add("north", face("#side", "north"));
        faces.add("south", face("#side", "south"));
        faces.add("west", face("#side", "west"));
        faces.add("east", face("#side", "east"));
        element.add("faces", faces);
        return element;
    }

    private static JsonObject frontOverlay() {
        JsonObject element = new JsonObject();
        element.add("from", array(-0.01, -0.01, -0.01));
        element.add("to", array(16.01, 16.01, 16.01));
        JsonObject faces = new JsonObject();
        JsonObject face = new JsonObject();
        face.add("uv", array(0.0, 0.0, 16.0, 16.0));
        face.addProperty("texture", "#overlay_front");
        face.addProperty("cullface", "north");
        faces.add("north", face);
        element.add("faces", faces);
        return element;
    }

    private static JsonObject face(String texture, String cullface) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", texture);
        face.addProperty("cullface", cullface);
        return face;
    }

    private static JsonArray array(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }
}
