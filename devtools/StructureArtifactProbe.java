import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;

/** Verify the actual packaged binary without loading Minecraft or extracting files. */
public class StructureArtifactProbe {
    static ZipFile zip;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static byte[] bytes(String path) throws Exception {
        var entry = zip.getEntry(path);
        check(entry != null, "Missing entry: " + path);
        try (var in = zip.getInputStream(entry)) { return in.readAllBytes(); }
    }
    static String text(String path) throws Exception { return new String(bytes(path), StandardCharsets.UTF_8); }
    static void structure(String name, int width, int height, int depth, Map<Character, Integer> expected) throws Exception {
        var json = JsonParser.parseString(text("data/addon/multiblock/" + name + ".json")).getAsJsonObject();
        var slices = json.getAsJsonArray("结构");
        check(slices.size() == depth, name + " depth");
        Map<Character, Integer> counts = new HashMap<>();
        for (var slice : slices) {
            check(slice.getAsJsonArray().size() == height, name + " height");
            for (var row : slice.getAsJsonArray()) {
                String value = row.getAsString();
                check(value.length() == width, name + " width");
                for (char c : value.toCharArray()) counts.merge(c, 1, Integer::sum);
            }
        }
        check(counts.equals(expected), name + " material/air/controller counts: " + counts);
    }
    // javap reads class files without initializing Minecraft/LDLib classes. Constants are
    // inlined at call sites, so checking only the ITEM_HOVER UTF-8 name is insufficient.
    static String javap(String jar, String name) {
        var tool = java.util.spi.ToolProvider.findFirst("javap").orElseThrow(
                () -> new IllegalStateException("Artifact probe requires a full JDK with javap"));
        var out = new java.io.StringWriter();
        var err = new java.io.StringWriter();
        int result = tool.run(new java.io.PrintWriter(out), new java.io.PrintWriter(err),
                "-p", "-c", "-constants", "-classpath", jar, name);
        check(result == 0, "javap " + name + ": " + err);
        return out.toString();
    }
    public static void main(String[] args) throws Exception {
        try (var archive = new ZipFile(args[0])) {
            zip = archive;
            for (String name : List.of("jei/StructureInfoCategory", "structure/StructurePreview", "structure/StructurePreviewRenderer", "structure/StructurePreviewScreen", "structure/StructureLdlibScene", "structure/StructureViewState", "structure/StructureViewportBounds", "structure/StructurePreviewTransform", "structure/StructurePreviewPicking", "structure/StructurePreviewPicking$Selection", "structure/StructurePickingMath")) {
                byte[] code = bytes("c_y/meknh_core/client/" + name + ".class");
                check(code[0] == (byte)0xCA && code[1] == (byte)0xFE && code[2] == (byte)0xBA && code[3] == (byte)0xBE, name + " bytecode");
                check(((code[6] & 255) << 8 | code[7] & 255) == 65, name + " Java21 class version");
            }
            for (String locale : List.of("en_us", "zh_cn")) {
                var json = JsonParser.parseString(text("assets/meknh_core/lang/" + locale + ".json")).getAsJsonObject();
                for (String key : List.of("category", "open", "reset", "layer", "hint", "representative"))
                    check(json.has("meknh.structure." + key) && !json.get("meknh.structure." + key).getAsString().isBlank(), locale + " " + key);
            }
            structure("coke_oven", 3, 3, 3, Map.of('b',25,'#',1,'@',1));
            structure("primitive_blast_furnace", 3, 4, 3, Map.of('b',32,'#',3,'@',1));
            structure("primitive_pump", 3, 3, 4, Map.of('C',10,'.',14,'A',10,'B',1,'@',1));
            check(text("META-INF/neoforge.mods.toml").contains("1.0.0-jei-structures-v1.14-hover-feedback"), "test version metadata");
            String screenCode = new String(bytes("c_y/meknh_core/client/structure/StructurePreviewScreen.class"), StandardCharsets.ISO_8859_1);
            int itemHover = 0x809EF1FF;
            String palette = javap(args[0], "c_y.meknh_core.client.structure.StructurePreviewPalette");
            check(palette.contains("public static final int ITEM_HOVER = " + itemHover + ";"),
                    "packaged ITEM_HOVER must be exactly ARGB 0x809EF1FF");
            String screenInstructions = javap(args[0], "c_y.meknh_core.client.structure.StructurePreviewScreen");
            check(screenInstructions.contains("// int " + itemHover),
                    "screen must use the exact inlined item hover color");
            check(screenCode.contains("itemHoverFeedback") && screenCode.contains("isSelfOrChildHover")
                    && screenCode.contains("overlayTexture"),
                    "shared item hover feedback uses a full-element overlay and descendant hover state");
            check(screenInstructions.lines().filter(line -> line.contains("// Method itemHoverFeedback:")).count() >= 2,
                    "material cards and candidate targets must both install item hover feedback");
            String sceneCode = new String(bytes("c_y/meknh_core/client/structure/StructureLdlibScene.class"), StandardCharsets.ISO_8859_1);
            String pluginCode = new String(bytes("c_y/meknh_core/client/jei/MeknhJeiPlugin.class"), StandardCharsets.ISO_8859_1);
            check(screenCode.contains("openItemRecipes") && !screenCode.contains("setCandidate"), "both lists open recipes, never replace blocks");
            check(pluginCode.contains("createFocus") && pluginCode.contains("OUTPUT"), "JEI material output focus");
            check(screenCode.contains("candidateLimitHeader") && screenCode.contains("setShowReplaceable"), "reserved limits and replacement toggle");
            check(screenCode.contains("candidateGroupFrame") && screenCode.contains("textAlignVertical")
                    && screenCode.contains("CENTER"), "independent candidate group frames and centered material text");
            check(screenCode.contains("mekanismButtonTexture"), "Mek native raised button textures");
            check(screenCode.contains("StructureCandidateGrid") && screenCode.contains("slotStyle"), "borderless candidate icon grid");
            check(bytes("c_y/meknh_core/client/structure/StructureCandidateGrid.class").length > 0, "integer grid geometry packaged");
            check(screenCode.contains("deltaY") && screenCode.contains("changeLayer")
                    && screenCode.contains("applyLayer"), "wheel and reversible layer controls");
            check(sceneCode.contains("renderReplaceable") && sceneCode.contains("renderCubeFace")
                    && sceneCode.contains("setAfterWorldRender"), "green replacement scene overlay");
            check(screenCode.contains("com/lowdragmc/lowdraglib2/gui/holder/ModularUIScreen"), "real LDLib2 screen linkage");
            check(!screenCode.contains("StructurePreviewRenderer") && !screenCode.contains("StructurePreviewPicking"), "full screen no longer uses handwritten renderer/picking");
            check(sceneCode.contains("com/lowdragmc/lowdraglib2/utils/virtuallevel/TrackedDummyWorld"), "real LDLib2 synthetic world linkage");
            String actualScene = new String(bytes("c_y/meknh_core/client/structure/StructureLdlibScene$PreviewScene.class"), StandardCharsets.ISO_8859_1);
            check(actualScene.contains("com/lowdragmc/lowdraglib2/gui/ui/elements/Scene"), "actual LDLib2 Scene subclass is packaged");
            String categoryCode = new String(bytes("c_y/meknh_core/client/jei/StructureInfoCategory.class"), StandardCharsets.ISO_8859_1);
            check(categoryCode.contains("addInvisibleIngredients") && categoryCode.contains("candidates"), "all allowed parts are indexed for structure usage queries");
            check(categoryCode.contains("RENDER_ONLY") && categoryCode.contains("controllers")
                    && categoryCode.contains("controllerPreview"), "separate controller and parts indexing");
            check(pluginCode.contains("structureCategories") && pluginCode.contains("controllerPreview"), "native controller categories and shared parts pagination");
            check(zip.getEntry("meknh_core.structure_jei.mixins.json") == null
                    && zip.getEntry("c_y/meknh_core/client/structure/JeiStructureCatalystsMixin.class") == null,
                    "no JEI internal hook or reserved mixin package");
            check(JsonParser.parseString(text("assets/meknh_core/lang/zh_cn.json")).getAsJsonObject()
                    .get("meknh.structure.category").getAsString().equals("多方块结构"), "final structure category title");
            check(categoryCode.contains("pickCell")
                    && categoryCode.contains("StructurePreviewPicking$Selection;"),
                    "simple JEI preview must pass hovered cell coordinates to renderer");
            check(!actualScene.contains("renderBlockOverLay"),
                    "custom hover tint must not remain on fullscreen Scene");
            check(screenCode.contains("itemCard") && screenCode.contains("marginTop")
                    && screenCode.contains("ColorBorderTexture") && screenCode.contains("GuiTextureGroup"),
                    "shared item cards retain spacing and explicit border texture linkage");
            String previewCode = new String(bytes("c_y/meknh_core/client/structure/StructurePreview.class"), StandardCharsets.ISO_8859_1);
            check(previewCode.contains("accepts") && previewCode.contains("TrackedDummyWorld")
                    && previewCode.contains("candidateGroup"), "runtime-validated hatch candidates and internal groups");
            check(screenCode.contains("MultiblockStructure$Cell") && screenCode.contains("candidateGroup"),
                    "candidate cache must preserve symbol-specific cells and group candidates");
            check(!screenCode.contains("输入输出总线 / 仓室") && !screenCode.contains("此位置无可用方块"),
                    "no category headings or empty category placeholders");
            check(screenCode.contains("openItemRecipes"), "material card must open JEI recipes");
            check(pluginCode.contains("openItemRecipes") && pluginCode.contains("createFocus")
                    && pluginCode.contains("OUTPUT"), "material recipe output focus bridge");
            check(!screenCode.contains("setCandidate"), "candidate clicks must not mutate the preview");
            check(screenCode.contains("setShowReplaceable") && screenCode.contains("candidateLimitHeader"),
                    "replacement toggle and reserved quantity header");
            check(sceneCode.contains("renderReplaceable") && sceneCode.contains("renderCubeFace")
                    && sceneCode.contains("setAfterWorldRender"), "green replacement markers in native scene callback");
            String metadata = text("META-INF/neoforge.mods.toml");
            check(metadata.contains("modId=\"ldlib2\"") && metadata.contains("[2.2.46.a,2.2.47)"), "LDLib2 client dependency and tested version range");
            archive.stream().forEach(e -> {
                for (String prefix : List.of("com/lowdragmc/lowdraglib2/", "mezz/jei/", "net/minecraft/", "devtools/", ".agents/", "logs/"))
                    check(!e.getName().startsWith(prefix), "Unwanted bundled entry: " + e.getName());
            });
            System.out.println("PASS packaged artifact: Java21 classes, 12 language entries, 3 exact structure fixtures, test version metadata, no bundled JEI/Minecraft/devtools");
        }
    }
}
