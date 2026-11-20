import java.nio.file.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Headless regression: real TabStrip code, recording graphics in place of a live Minecraft client. */
public final class TabHoverProbe {
    private static final Path ROOT = Path.of("build/tab-hover-probe");
    private static final String GUI = "c_y.meknh_core.common.gui.";
    public static void main(String[] args) throws Exception {
        Map<String, String> stubs = new LinkedHashMap<>();
        stubs.put("org.jetbrains.annotations.Nullable", "public @interface Nullable {}");
        stubs.put("com.mojang.blaze3d.platform.GlStateManager", "public class GlStateManager { public enum SourceFactor { DST_COLOR } public enum DestFactor { ZERO } }");
        stubs.put("com.mojang.blaze3d.systems.RenderSystem", "public class RenderSystem { public static void enableBlend() {} public static void disableBlend() {} public static void blendFunc(Object a,Object b) {} }");
        stubs.put("net.minecraft.resources.ResourceLocation", "public record ResourceLocation(String id) { public static ResourceLocation fromNamespaceAndPath(String n,String p) { return new ResourceLocation(n+\":\"+p); } }");
        stubs.put("net.minecraft.network.chat.Component", "public class Component { public static Component translatable(String key,Object... args) { return new Component(); } }");
        stubs.put("net.minecraft.client.gui.GuiGraphics", "public class GuiGraphics { public final StringBuilder log = new StringBuilder(); public void blit(Object... a) { log.append(java.util.Arrays.deepToString(a)); } public void fill(int... a) { log.append(java.util.Arrays.toString(a)); } public void setColor(float... a) { log.append(java.util.Arrays.toString(a)); } }");
        stubs.put(GUI+"render.GuiDraw", "public class GuiDraw { public static void nineSliceXY(net.minecraft.client.gui.GuiGraphics g,Object... a) { g.log.append(java.util.Arrays.deepToString(a)); } }");
        stubs.put(GUI+"render.MetalMachineBackground", "public class MetalMachineBackground { public static void renderBase(net.minecraft.client.gui.GuiGraphics g,int x,int y,int w,int h,MachineBackgroundMaterial m) { g.fill(x,y,w,h,m.ordinal()); } }");
        stubs.put("c_y.meknh_core.common.machine.config.RedstoneMode", "public class RedstoneMode { public static RedstoneMode byOrdinal(int i) { return new RedstoneMode(); } public net.minecraft.network.chat.Component displayName() { return new net.minecraft.network.chat.Component(); } }");
        Files.createDirectories(ROOT);
        List<String> compiler = new ArrayList<>(List.of("-d", ROOT.toString()));
        for (var entry : stubs.entrySet()) {
            Path path = ROOT.resolve("src/"+entry.getKey().replace('.', '/')+".java");
            Files.createDirectories(path.getParent());
            int dot = entry.getKey().lastIndexOf('.');
            Files.writeString(path,"package "+entry.getKey().substring(0,dot)+";\n"+entry.getValue());
            compiler.add(path.toString());
        }
        for (String path : List.of("element/TabStrip", "layout/MekGuiTextures", "render/MachineBackgroundMaterial")) {
            compiler.add("src/main/java/c_y/meknh_core/common/gui/"+path+".java");
        }
        if (ToolProvider.getSystemJavaCompiler().run(null,null,null,compiler.toArray(String[]::new)) != 0) throw new AssertionError("fixture compilation");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{ROOT.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> tabs=loader.loadClass(GUI+"element.TabStrip");
            Class<?> graphics=loader.loadClass("net.minecraft.client.gui.GuiGraphics");
            Class<?> material=loader.loadClass(GUI+"render.MachineBackgroundMaterial");
            Method render=Arrays.stream(tabs.getMethods()).filter(m->m.getName().equals("render") && m.getParameterCount()==10).findFirst().orElse(null);
            Method legacy=Arrays.stream(tabs.getMethods()).filter(m->m.getName().equals("render") && m.getParameterCount()==7).findFirst().orElseThrow();
            for (String name : List.of("METAL","STEAM_BRONZE","STEAM_HIGH")) {
                Object mat=material.getField(name).get(null);
                String normal=draw(render,legacy,graphics,mat,0,0,true);
                for (int[] point : new int[][]{{80,76},{80,104},{80,179},{80,207},{286,76},{286,104}}) {
                    if (normal.equals(draw(render,legacy,graphics,mat,point[0],point[1],true))) throw new AssertionError(name+" missing hover feedback at "+Arrays.toString(point));
                    if (!normal.equals(draw(render,legacy,graphics,mat,point[0],point[1],false))) throw new AssertionError("hover leaked through modal window");
                }
                // GUI scaling/resizing changes the GUI-space parent origin, not the mouse coordinate system.
                for (int[] origin : new int[][]{{0,0},{37,19},{319,171}}) {
                    Object g=graphics.getConstructor().newInstance();
                    render.invoke(null,g,origin[0],origin[1],0,false,8,mat,origin[0]-20,origin[1]+16,true);
                    String shifted=graphics.getField("log").get(g).toString();
                    Object outside=graphics.getConstructor().newInstance();
                    render.invoke(null,outside,origin[0],origin[1],0,false,8,mat,origin[0]-27,origin[1]+16,true);
                    if(shifted.equals(graphics.getField("log").get(outside).toString())) throw new AssertionError("hover lost at shifted origin");
                }
                for (int[] point : new int[][]{{73,76},{80,65},{80,92},{302,76}}) {
                    if (!normal.equals(draw(render,legacy,graphics,mat,point[0],point[1],true))) throw new AssertionError("hover outside half-open tab bounds");
                }
            }
            System.out.println("PASS: real TabStrip hover output changes for six tabs and three materials; modal and boundary exclusions hold.");
        }
    }
    private static String draw(Method render,Method legacy,Class<?> graphics,Object material,int x,int y,boolean enabled) throws Exception {
        Object g=graphics.getConstructor().newInstance();
        if(render==null) legacy.invoke(null,g,100,60,0,false,8,material);
        else render.invoke(null,g,100,60,0,false,8,material,x,y,enabled);
        return graphics.getField("log").get(g).toString();
    }
}
