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
        stubs.put("c_y.meknh_core.MekNHCore", "public class MekNHCore { public static final String MODID = \"meknh_core\"; }");
        stubs.put("org.jetbrains.annotations.Nullable", "public @interface Nullable {}");
        stubs.put("com.mojang.blaze3d.platform.GlStateManager", "public class GlStateManager { public enum SourceFactor { DST_COLOR } public enum DestFactor { ZERO } }");
        stubs.put("com.mojang.blaze3d.systems.RenderSystem", "public class RenderSystem { public static void enableBlend() {} public static void disableBlend() {} public static void blendFunc(Object a,Object b) {} }");
        stubs.put("net.minecraft.resources.ResourceLocation", "public record ResourceLocation(String id) { public static ResourceLocation fromNamespaceAndPath(String n,String p) { return new ResourceLocation(n+\":\"+p); } }");
        stubs.put("net.minecraft.network.chat.Component", "public class Component { public static Component translatable(String key,Object... args) { return new Component(); } }");
        stubs.put("net.minecraft.client.gui.GuiGraphics", "public class GuiGraphics { public final StringBuilder log = new StringBuilder(); public final java.util.List<Object[]> events = new java.util.ArrayList<>(); public void blit(Object... a) { events.add(a); log.append(java.util.Arrays.deepToString(a)); } public void fill(int... a) { events.add(new Object[]{a}); log.append(java.util.Arrays.toString(a)); } public void setColor(float... a) { log.append(java.util.Arrays.toString(a)); } }");
        stubs.put(GUI+"render.GuiDraw", "public class GuiDraw { public static void nineSliceXY(net.minecraft.client.gui.GuiGraphics g,Object... a) { g.events.add(new Object[]{\"slice\",a}); g.log.append(java.util.Arrays.deepToString(a)); } }");
        stubs.put(GUI+"render.MetalMachineBackground", "public class MetalMachineBackground { public static void renderBase(net.minecraft.client.gui.GuiGraphics g,int x,int y,int w,int h,MachineBackgroundMaterial m) { g.events.add(new Object[]{m,x,y,w,h}); g.log.append(m).append(x).append(\",\").append(y).append(\",\").append(w).append(\",\").append(h); } }");
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
        for (String path : List.of("element/TabStrip", "layout/MekGuiTextures", "render/MachineBackgroundMaterial", "render/MekButtonHover")) {
            compiler.add("src/main/java/c_y/meknh_core/common/gui/"+path+".java");
        }
        if (ToolProvider.getSystemJavaCompiler().run(null,null,null,compiler.toArray(String[]::new)) != 0) throw new AssertionError("fixture compilation");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{ROOT.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> tabs=loader.loadClass(GUI+"element.TabStrip");
            Class<?> graphics=loader.loadClass("net.minecraft.client.gui.GuiGraphics");
            Class<?> material=loader.loadClass(GUI+"render.MachineBackgroundMaterial");
            checkPublicHelpers(loader,tabs,graphics);
            // Mekanism jar evidence supplied by parent: four inset buttons use button.png v40
            // behind the icon, on the inner 18x18 only. Energy/warning are display tabs.
            List<String> failures = new ArrayList<>();
            int cases=0;
            int[][] offsets={{-26,6},{-26,34},{-26,109},{-26,137},{176,6},{176,34}};
            for (Method renderer : tabs.getMethods()) {
                if (!renderer.getName().equals("render") || (renderer.getParameterCount()!=10 && renderer.getParameterCount()!=11)) continue;
                for (Object mat : material.getEnumConstants()) for(int[] origin:new int[][]{{0,0},{37,19},{319,171}}) {
                    Object normalGraphics=render(renderer,graphics,mat,origin,origin[0]+50,origin[1]+50,true);
                    String normal=log(graphics,normalGraphics), normalFills=fills(graphics,normalGraphics);
                    for(int t=0;t<6;t++) for(int dx:new int[]{-1,0,3,4,5,12,20,21,22,25,26}) for(int dy:new int[]{-1,0,3,4,5,12,20,21,22,25,26}) {
                        int x=origin[0]+offsets[t][0], y=origin[1]+offsets[t][1];
                        Object g=render(renderer,graphics,mat,origin,x+dx,y+dy,true);
                        boolean hover=t!=2&&t!=3&&dx>=4&&dx<22&&dy>=4&&dy<22;
                        String label=renderer.getParameterCount()+" args "+mat+" origin="+Arrays.toString(origin)+" tab="+t+" local="+dx+","+dy;
                        if(normal.equals(log(graphics,g))==hover) failures.add(label+" incorrect hover feedback (inner18 actions only)");
                        if(!normalFills.equals(fills(graphics,g))) failures.add(label+" hover introduced custom fill instead of native atlas");
                        assertHoverSlice(graphics,g,x+4,y+4,hover,label,failures);
                        assertNoLateFill(graphics,g,label,failures);
                        cases++;
                    }
                    for(int[] offset:offsets) {
                        Object modal=render(renderer,graphics,mat,origin,origin[0]+offset[0]+10,origin[1]+offset[1]+10,false);
                        if(!normal.equals(log(graphics,modal))) failures.add("hover leaked through modal");
                    }
                }
            }
            if(!failures.isEmpty()) throw new AssertionError("Tab hover contract failed ("+failures.size()+" assertions):\n"+String.join("\n",failures.subList(0,Math.min(20,failures.size()))));
            System.out.println("PASS: "+cases+" real renderer cases: both overloads/all five materials/three origins; inner18 v40 before icon, display/outer-ring exclusion and modal gating.");
        }
    }
    private static void checkPublicHelpers(ClassLoader loader,Class<?> tabs,Class<?> graphics) throws Exception {
        Method hit=tabs.getMethod("tabAt",int.class,int.class,int.class,int.class,boolean.class);
        Class<?> tab=loader.loadClass(GUI+"element.TabStrip$Tab");
        Method tooltip=tabs.getMethod("tooltip",tab,int.class,boolean.class);
        String[] names={"CONFIG","TRANSPORTER","WARNING","ENERGY","REDSTONE","SECURITY"};
        int[][] offsets={{-26,6},{-26,34},{-26,109},{-26,137},{176,6},{176,34}};
        int cases=0;
        for(int[] origin:new int[][]{{0,0},{37,19},{319,171}}) for(int t=0;t<6;t++) {
            int x=origin[0]+offsets[t][0],y=origin[1]+offsets[t][1];
            for(int dx=-1;dx<=26;dx++) for(int dy=-1;dy<=26;dy++) {
                Object actual=hit.invoke(null,x+dx,y+dy,origin[0],origin[1],true);
                boolean inside=dx>=0&&dx<26&&dy>=0&&dy<26;
                if(!Objects.equals(actual==null?null:actual.toString(),inside?names[t]:null)) throw new AssertionError("tabAt half-open "+names[t]+" "+dx+","+dy);
                if(inside && tooltip.invoke(null,actual,0,false)==null) throw new AssertionError("missing tooltip "+names[t]);
                cases++;
            }
        }
        if(hit.invoke(null,80,179,100,60,false)!=null) throw new AssertionError("hidden warning has hit target");
        Class<?> helper=loader.loadClass(GUI+"render.MekButtonHover");
        Method contains=helper.getMethod("contains",int.class,int.class,int.class,int.class,int.class,int.class);
        Method base=helper.getMethod("drawBase",graphics,int.class,int.class,int.class,int.class,int.class,int.class,boolean.class);
        for(int px=9;px<=28;px++) for(int py=19;py<=38;py++) for(boolean enabled:new boolean[]{false,true}) {
            boolean inside=px>=10&&px<28&&py>=20&&py<38;
            if(!contains.invoke(null,10,20,18,18,px,py).equals(inside)) throw new AssertionError("helper half-open rectangle");
            Object g=graphics.getConstructor().newInstance();
            base.invoke(null,g,10,20,18,18,px,py,enabled);
            List<?> events=(List<?>)graphics.getField("events").get(g);
            Object[] slice=(Object[])((Object[])events.getFirst())[1];
            if(!slice[0].toString().contains("gui/button.png") || !slice[10].equals(enabled?(inside?40:20):0)) throw new AssertionError("actual public helper atlas state");
        }
        System.out.println("PASS: "+cases+" public tabAt/tooltip boundary cases and 800 real MekButtonHover atlas/contains cases.");
    }
    // Observe actual raster command ordering, not source text or a reimplementation of render.
    private static void assertNoLateFill(Class<?> graphics,Object g,String label,List<String> failures) throws Exception {
        List<?> events=(List<?>)graphics.getField("events").get(g);
        for(int i=0;i<events.size();i++) {
            Object[] icon=(Object[])events.get(i);
            if(icon.length<9 || !(icon[0].toString().contains("configuration.png") || icon[0].toString().contains("transporter_config.png") || icon[0].toString().contains("redstone_control_") || icon[0].toString().contains("public.png") || icon[0].toString().contains("energy_info_fe.png") || icon[0].toString().contains("warning_info_left.png"))) continue;
            int x=((Number)icon[1]).intValue(),y=((Number)icon[2]).intValue();
            for(int j=i+1;j<events.size();j++) {
                Object[] event=(Object[])events.get(j);
                if(event.length==2 && event[0].equals("slice")) {
                    Object[] a=(Object[])event[1];
                    int sx=((Number)a[1]).intValue(),sy=((Number)a[2]).intValue(),w=((Number)a[3]).intValue(),h=((Number)a[4]).intValue();
                    if(sx<x+18 && sx+w>x && sy<y+18 && sy+h>y) failures.add(label+" late texture slice covers icon");
                }
                if(event.length!=1 || !(event[0] instanceof int[] fill) || fill.length!=5) continue;
                if(fill[0]<x+18 && fill[2]>x && fill[1]<y+18 && fill[3]>y && (fill[4]>>>24)!=0) {
                    failures.add(label+" icon="+icon[0]+" command="+i+" covered by fill command="+j+" ARGB="+Integer.toHexString(fill[4])); break;
                }
            }
        }
    }
    private static Object render(Method renderer,Class<?> graphics,Object mat,int[] origin,int x,int y,boolean enabled) throws Exception {
        Object g=graphics.getConstructor().newInstance();
        if(renderer.getParameterCount()==11) renderer.invoke(null,g,origin[0],origin[1],0,false,8,false,mat,x,y,enabled);
        else renderer.invoke(null,g,origin[0],origin[1],0,false,8,mat,x,y,enabled);
        return g;
    }
    private static String fills(Class<?> graphics,Object g) throws Exception {
        StringBuilder result=new StringBuilder();
        for(Object value:(List<?>)graphics.getField("events").get(g)) {Object[] e=(Object[])value;if(e.length==1 && e[0] instanceof int[] fill) result.append(Arrays.toString(fill));}
        return result.toString();
    }
    private static String log(Class<?> graphics,Object g) throws Exception { return graphics.getField("log").get(g).toString(); }
    private static void assertHoverSlice(Class<?> graphics,Object g,int x,int y,boolean hover,String label,List<String> failures) throws Exception {
        List<?> events=(List<?>)graphics.getField("events").get(g);
        int count=0;
        for(Object value:events) {
            Object[] event=(Object[])value;
            if(event.length!=2 || !event[0].equals("slice")) continue;
            Object[] a=(Object[])event[1];
            if(!a[0].toString().contains("gui/button.png")) continue;
            count++;
            if(!hover || !a[1].equals(x) || !a[2].equals(y) || !a[3].equals(18) || !a[4].equals(18) || !a[10].equals(40)) failures.add(label+" unexpected button atlas/geometry "+Arrays.toString(a));
            int index=events.indexOf(value);
            boolean iconAfter=false;
            for(int j=index+1;j<events.size();j++) {Object[] icon=(Object[])events.get(j);if(icon.length>=9&&icon[1].equals(x)&&icon[2].equals(y)) iconAfter=true;}
            if(!iconAfter) failures.add(label+" hovered atlas not followed by icon");
        }
        if(count!=(hover?1:0)) failures.add(label+" expected hover atlas count="+(hover?1:0)+" got="+count);
    }
}
