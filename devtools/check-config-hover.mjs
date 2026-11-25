// Run: node devtools/check-config-hover.mjs (JDK 21; JAVA_HOME or javac/java on PATH).
// Executes complete production mouse-aware render bodies and the actual shared helper.
// Only external Minecraft/Mekanism drawing/world APIs are recording stubs: no game/GPU.
import {readFileSync, writeFileSync, mkdtempSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
const root='../src/main/java/c_y/meknh_core/common/gui/';
const read=p=>readFileSync(new URL(root+p,import.meta.url),'utf8');
const side=read('element/SideConfigPanel.java'), transport=read('element/TransporterPanel.java');
function block(text, signature) {
 const start=text.indexOf(signature); if(start<0) throw Error('Production seam missing: '+signature);
 const brace=text.indexOf('{',start); let end=brace+1, depth=1;
 while(depth && end<text.length) {if(text[end]==='{') depth++; if(text[end]==='}') depth--; end++;}
 if(depth) throw Error('Unterminated production seam: '+signature);
 return text.slice(start,end).replaceAll('net.minecraft.resources.ResourceLocation','String');
}
function render(text) {
 const signature=/public void render\(GuiGraphics graphics, Font font, int left, int top, Context context,\s*MachineBackgroundMaterial material, int mouseX, int mouseY\)/.exec(text)?.[0];
 if(!signature) throw Error('Missing mouse-aware production render overload');
 return block(text,signature);
}
const helper=read('render/MekButtonHover.java').replace(/^package .*;\s*$/m,'').replace(/^import .*;\s*$/gm,'').replace('public final class MekButtonHover','static final class MekButtonHover');
const snippets=(s, names)=>names.filter(n=>n!=='private static void drawConfigTypeTabHover('||s.includes(n)).map(n=>block(s,n)).join('\n');
const java=`import java.util.*;
public class ConfigHoverProbe {
static void check(boolean b,String m) {if(!b) throw new AssertionError(m);}
record Event(String kind,String texture,int x,int y,int w,int h,int v,int color,String tint) {}
static class Pose { void pushPose() {} void popPose() {} void translate(float... a) {} void scale(float... a) {} }
static class GuiGraphics {
 List<Event> events=new ArrayList<>(); String tint="1.0,1.0,1.0,1.0"; Pose pose=new Pose(); int depth;
 void flush() {} Pose pose() {return pose;}
 void setColor(float r,float g,float b,float a) {tint=r+","+g+","+b+","+a;}
 void fill(int x,int y,int xx,int yy,int color) {events.add(new Event("fill","",x,y,xx-x,yy-y,0,color,tint));}
 void blit(String t,int x,int y,int w,int h,int... uv) {events.add(new Event("icon",t,x,y,w,h,0,0,tint));}
 void drawString(Object... a) {}
 void renderItem(ItemStack s,int x,int y) {events.add(new Event("item","item",x,y,16,16,0,0,tint));}
}
static class GuiDraw {
 static void nineSlice(GuiGraphics g,String t,int x,int y,int w,int h,int... a) {g.events.add(new Event("slice",t,x,y,w,h,0,0,g.tint));}
 static void nineSliceXY(GuiGraphics g,String t,int x,int y,int w,int h,int... a) {g.events.add(new Event("slice",t,x,y,w,h,a[5],0,g.tint));}
}
static class Font {int width(Object c) {return 20;}}
record Component(String key) {static Component translatable(String k,Object... a) {return new Component(k);}}
${block(read('render/MachineBackgroundMaterial.java'),'public enum MachineBackgroundMaterial')}
static class MetalMachineBackground {static void renderBase(GuiGraphics g,int x,int y,int w,int h,MachineBackgroundMaterial m) {g.events.add(new Event("material",m.name(),x,y,w,h,0,0,g.tint));}}
static class MekGuiTextures {
 static final String WINDOW_SHADOW="shadow", WINDOW_BASE="window", BUTTON_CLOSE="close", PINNED="pin", UNPINNED="unpin", TAB_BUTTON="button", BUTTON_AUTO_EJECT="eject",BUTTON_CLEAR_SIDES="clear",INNER_SCREEN="inner",TAB_HOLDER_LEFT="holder",CONFIG_FLUID="fluid",CONFIG_CHEMICAL="chemical",CONFIG_ENERGY="energy",CONFIG_ITEM="itemType",BUTTON_EXCLAMATION="strict",SLOT_NORMAL="slot";
}
enum Direction {UP,DOWN,NORTH,SOUTH,EAST,WEST; Direction getOpposite(){return values()[ordinal()^1];} Direction getClockWise(){return EAST;} Direction getCounterClockWise(){return WEST;}}
enum SideMode {NONE,INPUT,OUTPUT,BOTH; static SideMode byOrdinal(int n){return values()[Math.floorMod(n,4)];}}
enum EnumColor {DARK_RED,DARK_BLUE,PURPLE,GRAY; int getPackedColor(){return 0x557799+ordinal();}}
static class MekanismLang {static final MekanismLang NO_EJECT=new MekanismLang(); Component translate(){return Component.translatable("noEject");}}
static class BlockPos {BlockPos relative(Direction d){return this;} Object getCenter(){return this;}}
static class Level {BlockState getBlockState(BlockPos p){return new BlockState();}}
static class BlockState {boolean isAir(){return false;} ItemStack getCloneItemStack(Object... a){return new ItemStack();}}
static class ItemStack {boolean isEmpty(){return false;}}
static class BlockHitResult {BlockHitResult(Object... a){}}
static class Minecraft {Object player=new Object(); static Minecraft getInstance(){return new Minecraft();}}
${helper}
static class Side {
 static final int WIDTH=156,HEIGHT=135,TYPE_TAB=26,BUTTON_INSET=4;
 boolean open=true,pinned; int configType,dragOffsetX,dragOffsetY,lastLeft,lastTop;
 static class Context {
 boolean eject=true; int mode;
 boolean supports(int t){return true;} boolean canEject(int t){return eject;} boolean autoEject(int t){return true;}
 int sideMode(int t,Direction d){return mode;} Direction facing(){return Direction.NORTH;}
 Level level(){return new Level();} BlockPos pos(){return new BlockPos();}
 }
 ${snippets(side,['public enum RelSide','private int originX(','private int originY(','private void ensureType(','private static String typeKey(','private static int color(','private static void drawConfigTypeTabHover(','private static void drawConfigTypeTab(','private static void drawButtonBase('])}
 ${render(side)}
}
static class Transport {
 static final int WIDTH=156,HEIGHT=119; boolean open=true;
 ${block(transport,'private static final int[] COLORS')};
 static class Context {int color,strict; int strictInput(){return strict;} int outputColor(){return color;} int inputColor(Direction d){return color;} Direction facing(){return Direction.NORTH;}}
 ${block(transport,'private enum Face')}
 ${render(transport)}
}
interface Renderer {GuiGraphics render(int x,int y);}
static Event base(GuiGraphics g,int x,int y,int w,int h) {
 return g.events.stream().filter(e->e.kind.equals("slice")&&e.texture.equals("button")&&e.x==x&&e.y==y&&e.w==w&&e.h==h).findFirst().orElseThrow(()->new AssertionError("missing button "+x+","+y+" "+w+"x"+h));
}
static int cases;
static void boundaries(Renderer r,int x,int y,int w,int h,boolean enabled) {
 for(int px=x-1;px<=x+w;px++) for(int py=y-1;py<=y+h;py++) {
  GuiGraphics g=r.render(px,py); Event e=base(g,x,y,w,h);
  boolean hit=px>=x&&px<x+w&&py>=y&&py<y+h;
  check(e.v==(enabled?(hit?40:20):0),"atlas hover/disabled half-open boundary "+e+" mouse="+px+","+py);
  check(g.tint.equals("1.0,1.0,1.0,1.0"),"render leaked tint "+g.tint); cases++;
 }
}
static void order(GuiGraphics g) {order(g,true);}
static void order(GuiGraphics g,boolean items) {
 for(int i=0;i<g.events.size();i++) {
  Event e=g.events.get(i);
  if(e.kind.equals("icon") && Set.of("close","pin","unpin","eject","clear","strict").contains(e.texture)) {
   int bi=g.events.indexOf(base(g,e.x,e.y,e.w,e.h)); check(bi<i,"base covers icon "+e);
   for(int j=i+1;j<g.events.size();j++){Event q=g.events.get(j);if(q.kind.equals("slice")||q.kind.equals("fill")) check(q.x+q.w<=e.x||q.x>=e.x+e.w||q.y+q.h<=e.y||q.y>=e.y+e.h,"late base/overlay covers icon");}
  }
  if(e.kind.equals("item")) for(int j=i+1;j<g.events.size();j++) {
   Event q=g.events.get(j); if(q.kind.equals("slice")||q.kind.equals("fill"))
    check(q.x+q.w<=e.x||q.x>=e.x+16||q.y+q.h<=e.y||q.y>=e.y+16,"late base/hover covers neighbor item");
  }
 }
 if(items) check(g.events.stream().filter(e->e.kind.equals("item")).count()==6,"neighbor preview path not executed");
 for(int i=0;i<g.events.size();i++){Event e=g.events.get(i);if(e.kind.equals("icon")&&Set.of("fluid","chemical","energy","itemType").contains(e.texture)) for(int j=i+1;j<g.events.size();j++){Event q=g.events.get(j);if(q.kind.equals("slice")||q.kind.equals("fill"))check(q.x+q.w<=e.x||q.x>=e.x+e.w||q.y+q.h<=e.y||q.y>=e.y+e.h,"hover covers type icon");}}
}
public static void main(String[] args) {
 for(boolean enabled:new boolean[]{false,true}) for(int[] r:new int[][]{{-15,7,8,8},{7,-21,16,8},{100,150,22,22}}) {
  Renderer helperRender=(x,y)->{var g=new GuiGraphics();MekButtonHover.drawBase(g,r[0],r[1],r[2],r[3],x,y,enabled);return g;};
  boundaries(helperRender,r[0],r[1],r[2],r[3],enabled);
 }
 var tinted=new GuiGraphics();tinted.setColor(0.2F,0.3F,0.4F,0.35F);
 MekButtonHover.drawBase(tinted,0,0,22,22,0,0,true);
 check(tinted.tint.equals("0.2,0.3,0.4,0.35"),"helper must preserve caller tint");
 var overlay=new GuiGraphics();MekButtonHover.drawColorOverlay(overlay,10,20,16,16,10,20);
 check(overlay.events.size()==1&&overlay.events.get(0).color==0x80FFFFFF&&overlay.events.get(0).w==16&&overlay.events.get(0).h==16,"slot overlay geometry/alpha");
 for(var material:new MachineBackgroundMaterial[]{null,MachineBackgroundMaterial.METAL,MachineBackgroundMaterial.STEAM_HIGH,MachineBackgroundMaterial.STEAM_BRONZE,MachineBackgroundMaterial.PRIMITIVE_BRICK,MachineBackgroundMaterial.COKE_BRICK}) for(int[] origin:new int[][]{{0,0,0,0},{125,44,37,-19},{339,172,-21,13}}) {
 int left=origin[0],top=origin[1]; var s=new Side(); s.dragOffsetX=origin[2];s.dragOffsetY=origin[3]; var sc=new Side.Context();
 int ox=s.originX(left),oy=s.originY(top);
 Renderer sr=(x,y)->{var g=new GuiGraphics();s.render(g,new Font(),left,top,sc,material,x,y);return g;};
 for(int[] b:new int[][]{{6,6,8,8},{16,6,16,8},{136,6,14,14},{136,95,14,14},{67,46,22,22},{67,69,22,22},{67,92,22,22},{44,69,22,22},{90,69,22,22},{44,92,22,22}}) boundaries(sr,ox+b[0],oy+b[1],b[2],b[3],true);
 sc.eject=false;boundaries(sr,ox+136,oy+6,14,14,false);sc.eject=true;
 for(int mode=0;mode<4;mode++){sc.mode=mode; s.pinned=mode%2==0;order(sr.render(ox+67,oy+46));}
 for(int type=1;type<4;type++) {
 int tx=ox-26,ty=oy+2+type*28; var normalTab=sr.render(-999,-999).events;
 var tabHit=sr.render(tx,ty);check(tabHit.events.stream().anyMatch(e->e.texture.equals("button")&&e.x==tx+4&&e.y==ty+4&&e.w==18&&e.h==18&&e.v==40),"type tab must use actual v40 base");order(tabHit);
 for(int x=tx-1;x<=tx+26;x++) for(int y=ty-1;y<=ty+26;y++) {boolean hit=x>=tx&&x<tx+26&&y>=ty&&y<ty+26;check((!sr.render(x,y).events.equals(normalTab))==hit,"type tab half-open feedback");cases++;}
 }
 s.open=false;check(sr.render(ox+6,oy+6).events.isEmpty(),"closed side renders");
 var t=new Transport();var tc=new Transport.Context();int tx=left+10,ty=top+15;
 Renderer tr=(x,y)->{var g=new GuiGraphics();t.render(g,new Font(),left,top,tc,material,x,y);return g;};
 for(int[] b:new int[][]{{6,6,8,8},{136,6,14,14},{41,34,22,22},{41,57,22,22},{41,80,22,22},{18,57,22,22},{64,57,22,22},{18,80,22,22}}) boundaries(tr,tx+b[0],ty+b[1],b[2],b[3],true);
 int normal=tr.render(-999,-999).events.size();
 for(int x=tx+111;x<=tx+128;x++) for(int y=ty+48;y<=ty+65;y++) {
  var g=tr.render(x,y);boolean hit=x>=tx+112&&x<tx+128&&y>=ty+49&&y<ty+65;
  check((g.events.size()>normal)==hit,"output overlay must match 16x16 half-open click area");cases++;
 }
 for(int color=-1;color<=16;color++){tc.color=color;tc.strict=color&1;var g=tr.render(tx+41,ty+34);order(g,false);int rgb=Transport.COLORS[Math.floorMod(color,Transport.COLORS.length)];String expected=(rgb>>16&255)/255.0F+","+(rgb>>8&255)/255.0F+","+(rgb&255)/255.0F+",1.0";check(base(g,tx+41,ty+34,22,22).tint.equals(expected),"hover lost face color");check(g.tint.equals("1.0,1.0,1.0,1.0"),"transporter tint leaked");}
 t.open=false;check(tr.render(tx+6,ty+6).events.isEmpty(),"closed transporter renders");
 }
 System.out.println("PASS: "+cases+" production render boundary cases; close/pin/eject/disabled/clear/six faces/type tabs, transporter strict/output; moved origins/drag/materials/tint/item ordering");
}
}`;
const executable=name=>process.env.JAVA_HOME?join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;
const temp=mkdtempSync(join(tmpdir(),'config-hover-probe-'));
try {
 const path=join(temp,'ConfigHoverProbe.java');writeFileSync(path,java);
 execFileSync(executable('javac'),['--release','21','-encoding','UTF-8','-d',temp,path],{stdio:'inherit'});
 execFileSync(executable('java'),['-cp',temp,'ConfigHoverProbe'],{stdio:'inherit'});
} finally {rmSync(temp,{recursive:true,force:true});}
