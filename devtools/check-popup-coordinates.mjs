// Run: node devtools/check-popup-coordinates.mjs (requires javac/java on PATH).
// Executes production lifecycle/title-click/drag bodies, not source-string assertions.
// Records actual close/pin render-prefix commands and executes utility tooltip blocks.
// GPU/vanilla pose/scissor, side controls, item depth and physical mouse conversion
// are NOT validated; Minecraft/context are stubbed rather than bootstrapped.
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
const source=readFileSync(new URL('../src/main/java/c_y/meknh_core/common/gui/element/SideConfigPanel.java',import.meta.url),'utf8');
function method(signature, text=source) {
 const start=text.indexOf(signature);
 if(start<0) throw new Error(`Production method not found: ${signature}`);
 const brace=text.indexOf('{',start); let end=brace+1,depth=1;
 while(depth) {if(end>=text.length) throw new Error(`Unterminated method: ${signature}`); if(text[end]==='{') depth++; else if(text[end]==='}') depth--; end++;}
 return text.slice(start,end);
}
const tabsSource=readFileSync(new URL('../src/main/java/c_y/meknh_core/common/gui/element/ScreenTabs.java',import.meta.url),'utf8');
const methods=['private int originX(', 'private int originY(', 'public void init(', 'public void toggle(', 'public void close(', 'public boolean mouseDragged(', 'public void mouseReleased(', 'public boolean mouseClicked(', 'public boolean keyPressed(', 'private void ensureType('].map(signature=>method(signature)).join('\n');
// Narrow render seam ends immediately after close/pin drawing; execute those actual
// statements with a graphics command recorder rather than bootstrapping Minecraft.
const render=method('public void render(GuiGraphics graphics, Font font, int left, int top, Context context,\n                       MachineBackgroundMaterial material, int mouseX, int mouseY)');
const utilityRender=render.slice(0,render.indexOf('        boolean canEject'))+'}';
const tooltip=method('public boolean renderTooltip(');
const tooltipStart=tooltip.indexOf('        if (mouseX >= originX + 6');
const utilityTooltip='boolean utilityTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY, int left, int top, Context context) { int originX=this.originX(left), originY=this.originY(top);'+tooltip.slice(tooltipStart,tooltip.indexOf('        for (RelSide side',tooltipStart))+'return false;}';
const java=`import java.util.*;
public class PopupCoordinateProbe {
    static final int WIDTH=156, HEIGHT=135, TYPE_TAB=26;
    static final Map<BlockPos,Boolean> PINNED=new HashMap<>();
    boolean open,pinned,dragging;
    int configType,dragOffsetX,dragOffsetY,dragStartPanelX,dragStartPanelY,lastLeft,lastTop;
    double dragStartX,dragStartY;
    BlockPos pos;
    record BlockPos(int x) {}
    static class Minecraft {
        static Minecraft getInstance() { return new Minecraft(); }
        Minecraft getWindow() { return this; }
        int getGuiScaledWidth() { return 640; }
        int getGuiScaledHeight() { return 360; }
    }
    static class Screen { static boolean hasShiftDown() { return false; } }
    enum Direction { NORTH }
    enum RelSide {
        ;
        int x() { return 0; } int y() { return 0; }
        Direction direction(Direction d) { return d; }
    }
    interface Context {
        boolean supports(int type);
        boolean canEject(int type);
        Direction facing();
        void sendButton(int id);
    }
    void setPinned(boolean value) { pinned=value; }
    static Context context = new Context() {
        public boolean supports(int t) { return true; }
        public boolean canEject(int t) { return true; }
        public Direction facing() { return Direction.NORTH; }
        public void sendButton(int id) { throw new AssertionError("unexpected control"); }
    };
${methods}
${method('public boolean isOpen(')}
${method('public net.minecraft.client.renderer.Rect2i bounds(').replaceAll('net.minecraft.client.renderer.Rect2i','Rect')}
    static class TabsGeometry {
        PopupCoordinateProbe sideConfig;
        TabsGeometry(PopupCoordinateProbe p) { sideConfig=p; }
${method('public java.util.List<net.minecraft.client.renderer.Rect2i> extraAreas(',tabsSource).replaceAll('net.minecraft.client.renderer.Rect2i','Rect')}
    }
    static void extraAreas() {
        for(int[] base:new int[][]{{125,39},{125,44},{339,166},{339,172}}) {
            var p=new PopupCoordinateProbe(); var tabs=new TabsGeometry(p);
            var closed=tabs.extraAreas(base[0],base[1],false);
            check(closed.size()==5,"closed tab count");
            for(int[] t:new int[][]{{-26,6},{-26,34},{-26,137},{176,6},{176,34}})
                check(closed.contains(new Rect(base[0]+t[0],base[1]+t[1],26,26)),"tab exclusion geometry");
            check(tabs.extraAreas(base[0],base[1],true).contains(new Rect(base[0]-26,base[1]+109,26,26)),"warning exclusion");
            p.open=true; p.dragOffsetX=73; p.dragOffsetY=-21;
            Rect bounds=p.bounds(base[0],base[1]);
            check(bounds.equals(new Rect(p.originX(base[0])-26,p.originY(base[1]),182,135)),"dragged popup bounds");
            check(tabs.extraAreas(base[0],base[1],false).contains(bounds),"open popup forwarded");
            p.open=false;
            check(tabs.extraAreas(base[0],base[1],false).size()==5,"closed popup released exclusion");
        }
        System.out.println("PASS: actual side-tab and dragged popup exclusions at 854x510 scales 1/2, warning/open/closed states");
    }
${utilityRender}
${utilityTooltip}
${method('private static void drawButtonBase(')}
${method('private static void drawConfigTypeTabHover(')}
    record Rect(int x,int y,int w,int h) { boolean contains(int px,int py) {return px>=x&&px<x+w&&py>=y&&py<y+h;} }
    record Component(String key) { static Component translatable(String key,Object... args) {return new Component(key);} }
    static class Font {int width(Component c) {return 30;}}
    enum MachineBackgroundMaterial {METAL}
    static class MetalMachineBackground {static void renderBase(Object... args) {}}
    static class MekGuiTextures {static final String WINDOW_SHADOW="shadow", WINDOW_BASE="base", BUTTON_CLOSE="close", PINNED="pin", UNPINNED="unpin", TAB_BUTTON="button";}
    static class GuiGraphics {
        Map<String,Rect> icons=new HashMap<>(); String tooltip;
        int fills;
        void flush() {} void fill(int... args) {fills++;} void setColor(float... args) {}
        void drawString(Object... args) {}
        void blit(String texture,int x,int y,int w,int h,int u,int v,int uw,int vh,int tw,int th) {icons.put(texture,new Rect(x,y,w,h));}
        void renderTooltip(Font f,Component c,int x,int y) {tooltip=c.key();}
    }
    static class GuiDraw {
        static void nineSlice(Object... args) {}
        static void nineSliceXY(Object... args) {}
    }
    static String typeKey(int type) {return "items";}
    static void drawConfigTypeTab(Object... args) {}
    static void utilityRectangles() {
        for(int[] base:new int[][]{{0,0},{100,100},{200,150}}) {
            var p=new PopupCoordinateProbe(); p.open=true; p.dragOffsetX=-4; p.dragOffsetY=7;
            var graphics=new GuiGraphics(); var font=new Font();
            p.render(graphics,font,base[0],base[1],context,null,Integer.MIN_VALUE,Integer.MIN_VALUE);
            Rect close=graphics.icons.get("close"), pin=graphics.icons.get("unpin");
            check(close.w()==8&&close.h()==8&&pin.w()==16&&pin.h()==8,"utility blit dimensions");
            int ox=p.originX(base[0]),oy=p.originY(base[1]);
            int tx=ox-TYPE_TAB,ty=oy+30;
            for(int hx=tx-1;hx<=tx+TYPE_TAB;hx++) for(int hy=ty-1;hy<=ty+TYPE_TAB;hy++) {
                var hover=new GuiGraphics();
                p.render(hover,font,base[0],base[1],context,null,hx,hy);
                boolean hit=hx>=tx&&hx<tx+TYPE_TAB&&hy>=ty&&hy<ty+TYPE_TAB;
                check(hover.fills==(hit?6:1),"type tab hover rectangle or missing feedback");
            }
            for(int x=ox;x<ox+36;x++) for(int y=oy;y<oy+18;y++) {
                graphics.tooltip=null; p.pinned=false; p.open=true; p.dragging=false;
                p.utilityTooltip(graphics,font,x,y,base[0],base[1],context);
                check((graphics.tooltip!=null)==(close.contains(x,y)||pin.contains(x,y)),"render/tooltip rectangle mismatch");
                p.mouseClicked(x+0.25,y+0.25,0,base[0],base[1],context);
                check((!p.open)==close.contains(x,y),"render/close-click rectangle mismatch");
                check(p.pinned==pin.contains(x,y),"render/pin-click rectangle mismatch");
            }
        }
        System.out.println("PASS: close 8x8/pin 16x8 render/tooltip/click rectangles; type-tab hover half-open boundary grid; three origins + drag offset");
    }
    static void check(boolean value,String message) {
        if(!value) throw new AssertionError(message);
    }
    static PopupCoordinateProbe start() {
        var p=new PopupCoordinateProbe(); p.init(new BlockPos(1)); p.toggle();
        p.mouseClicked(180.25,120.25,0,100,100,context);
        check(p.dragging,"real title click begins drag");
        p.mouseDragged(190.25,130.25,0);
        check(p.originX(100)==120 && p.originY(100)==125,"drag translation");
        return p;
    }
    public static void main(String[] args) {
        utilityRectangles();
        extraAreas();
        var resized=start();
        resized.init(new BlockPos(1)); // MachineScreen.init -> ScreenTabs.init on resize
        check(!resized.dragging,"resize cancels gesture before reopening");
        resized.toggle(); // unpinned popup reopened
        int x=resized.originX(200), y=resized.originY(150);
        check(!resized.mouseDragged(191.25,131.25,0),
            "resize/reopen retains stale drag: pointer motion moves popup without title press");
        check(x==resized.originX(200) && y==resized.originY(150),"resize origin stays stable");
        var pinned=start(); PINNED.put(pinned.pos,true); pinned.init(pinned.pos);
        check(pinned.open && !pinned.mouseDragged(191.25,131.25,0),"pinned resize cancels drag without closing popup");
        PINNED.clear();
        var closed=start(); closed.close();
        check(!closed.dragging,"close immediately cancels gesture"); closed.toggle();
        check(!closed.mouseDragged(191.25,131.25,0),"close/reopen retains stale drag");
        var clicked=start();
        clicked.mouseClicked(clicked.originX(100)+7.25,clicked.originY(100)+7.25,0,100,100,context);
        check(!clicked.open && !clicked.dragging,"close button cancels gesture");
        var outside=start();
        outside.mouseClicked(outside.originX(100)-27.25,outside.originY(100)-1.25,0,100,100,context);
        check(!outside.open && !outside.dragging,"outside click cancels gesture");
        var escaped=start(); escaped.keyPressed(256);
        check(!escaped.open && !escaped.dragging,"Escape cancels gesture");
        var toggled=start(); toggled.toggle(); toggled.toggle();
        check(!toggled.mouseDragged(191.25,131.25,0),"toggle/reopen retains stale drag");
        System.out.println("PASS: actual title click/drag; resize, close and toggle cancel drag; stable reopened origins");
    }
}`;
const executable=name=>process.env.JAVA_HOME ? join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')) : name;
const temp=mkdtempSync(join(tmpdir(),'popup-coordinate-probe-'));
try { const path=join(temp,'PopupCoordinateProbe.java');writeFileSync(path,java);const glfw=join(temp,'GLFW.java');writeFileSync(glfw,'package org.lwjgl.glfw; public class GLFW { public static final int GLFW_KEY_ESCAPE=256; }');execFileSync(executable('javac'),['--release','21','-encoding','UTF-8','-d',temp,path,glfw],{stdio:'inherit'});execFileSync(executable('java'),['-cp',temp,'PopupCoordinateProbe'],{stdio:'inherit'}); } finally {rmSync(temp,{recursive:true,force:true});}
