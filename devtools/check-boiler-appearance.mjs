// Run: node devtools/check-boiler-appearance.mjs (JDK 21; JAVA_HOME supported).
// Executes extracted production background prefix, complete labels/render and ScreenTabs
// forwarding bodies. Minecraft rendering and popup internals are recording stubs, not a
// game/GPU test. Does not validate textures visually, actual fluid/slot layout, registry
// bootstrap, or third-party addon definitions. Bundled definitions are discovered below.
import {readFileSync,writeFileSync,mkdtempSync,rmSync,readdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
const read=p=>readFileSync(new URL('../src/main/'+p,import.meta.url),'utf8');
const screen=read('java/c_y/meknh_core/common/generator/boiler/BoilerScreen.java');
const gui='java/c_y/meknh_core/common/gui/';
function block(text,signature){
 const start=text.indexOf(signature);if(start<0)throw Error('Production seam missing: '+signature);
 const brace=text.indexOf('{',start);let end=brace+1,depth=1;
 while(depth&&end<text.length){if(text[end]==='{')depth++;if(text[end]==='}')depth--;end++;}
 if(depth)throw Error('Unterminated production seam: '+signature);
 return text.slice(start,end);
}
const bg=block(screen,'protected void renderBg(');
const marker=bg.indexOf('for (Slot slot');
if(marker<0)throw Error('Slot boundary missing; review extraction seam');
// Stop immediately before the existing inventory loop. Do not reconstruct the background call.
const bgPrefix=bg.slice(0,marker)+'}';
const tabs=read(gui+'element/ScreenTabs.java');
const tabSignature=/public void renderTabs\(GuiGraphics graphics, int left, int top,\s*int redstoneOrdinal, boolean privateMode, int warningCode, boolean steam,\s*MachineBackgroundMaterial material, int mouseX, int mouseY, boolean hoverEnabled\)/.exec(tabs)?.[0];
const overlaySignature=/public void renderOverlay\(GuiGraphics graphics, Font font, int left, int top,\s*SideConfigPanel.Context context, MachineBackgroundMaterial material,\s*int mouseX, int mouseY\)/.exec(tabs)?.[0];
if(!tabSignature||!overlaySignature)throw Error('Material/mouse forwarding overload missing');
// Supplemental source contract only: not a registry/bootstrap execution.
const registration=read('java/c_y/meknh_core/api/generator/boiler/BoilerRegistration.java');
for(const contract of ['for (BoilerTier tier : BoilerTier.values())','new BoilerBlock(definition, tier, properties)','VARIANTS.put','BOILER_MENU'])if(!registration.includes(contract))throw Error('Review changed registry coverage: '+contract);
const definitions=[];
function walk(url){for(const entry of readdirSync(url,{withFileTypes:true})){const u=new URL(entry.name+(entry.isDirectory()?'/':''),url);if(entry.isDirectory())walk(u);else if(entry.name.endsWith('.json')){const d=JSON.parse(readFileSync(u,'utf8'));const inputs=d.input??[];const type=d.type?.toUpperCase()??(inputs.some(g=>(g.type??'item')==='item'&&(g.slots??1)>0)?'SOLID':inputs.some(g=>['fluid','mek','chemical','gas','化学品','化学'].includes(g.type)&&(g.slots??1)>0)?'LIQUID':'SOLAR');definitions.push({id:entry.name,type});}}}
walk(new URL('../src/main/resources/data/addon/generator/boiler/',import.meta.url));
if(!definitions.length)throw Error('No bundled boiler definitions');
for(const type of ['SOLID','LIQUID','SOLAR'])if(!definitions.some(d=>d.type===type))throw Error('Missing bundled type '+type);
const enumSource=name=>block(read(gui+'render/'+name+'.java'),'public enum '+name);
const constants=[...new Set([...bgPrefix.matchAll(/BoilerMenu\.(DATA_\w+)/g),...block(screen,'public void render(GuiGraphics').matchAll(/BoilerMenu\.(DATA_\w+)/g)].map(m=>m[1]))];
const java=`import java.util.*;
public class BoilerAppearanceProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 ${enumSource('MachineBackgroundMaterial')}
 ${enumSource('MachineBackgroundState')}
 enum BoilerTier {LOW,HIGH}
 record BoilerBlock(BoilerTier tier){}
 record BlockState(Object block){Object getBlock(){return block;}}
 record BlockEntity(BlockState state){BlockState getBlockState(){return state;}}
 static class BoilerMenu {
  static final int ${constants.map((n,i)=>n+'='+i).join(',')};
  int[] data=new int[64];Object block;String type;
  int value(int n){return data[n];}BlockEntity blockEntity(){return new BlockEntity(new BlockState(block));}
  boolean isSolid(){return type.equals("SOLID");}boolean isLiquid(){return type.equals("LIQUID");}boolean isSolar(){return type.equals("SOLAR");}
 }
 record Component(String value){}
 static class Font {int width(Component c){return c.value.length()*7;}}
 record Text(Component text,int x,int y,int color,boolean shadow){}
 static class GuiGraphics {List<Text> texts=new ArrayList<>();void drawString(Font f,Component c,int x,int y,int color,boolean shadow){texts.add(new Text(c,x,y,color,shadow));}void fill(int... a){throw new AssertionError("No title fill");}}
 static class MetalMachineBackground {
  static MachineBackgroundMaterial material;static MachineBackgroundState state;static int[] geometry;
  static void render(GuiGraphics g,int x,int y,int w,int h,MachineBackgroundState s,int titleWidth,MachineBackgroundMaterial m){material=Objects.requireNonNull(m);state=s;geometry=new int[]{x,y,w,h,titleWidth};}
 }
 static class SideConfigPanel {
  interface Context {}boolean open;boolean isOpen(){return open;}
  void render(GuiGraphics g,Font f,int x,int y,Context c,MachineBackgroundMaterial m,int mx,int my){recordPopup("side",x,y,m,mx,my);}
 }
 static List<MachineBackgroundMaterial> popupMaterials=new ArrayList<>();static String popup;static MachineBackgroundMaterial popupMaterial,tabMaterial;static int[] popupXY,tabXY;static boolean hover;
 static void recordPopup(String p,int x,int y,MachineBackgroundMaterial m,int mx,int my){popup=p;popupMaterial=Objects.requireNonNull(m);popupMaterials.add(m);popupXY=new int[]{x,y,mx,my};}
 static class TransporterPanel {
  interface Context {}boolean open;boolean isOpen(){return open;}
  void render(GuiGraphics g,Font f,int x,int y,Context c,MachineBackgroundMaterial m,int mx,int my){recordPopup("transport",x,y,m,mx,my);}
 }
 static class TabStrip {
  static void render(GuiGraphics g,int x,int y,int redstone,boolean priv,int warning,boolean steam,MachineBackgroundMaterial m,int mx,int my,boolean h){check(steam,"boiler tabs must use steam interaction");tabMaterial=Objects.requireNonNull(m);tabXY=new int[]{x,y,mx,my,redstone,priv?1:0,warning};hover=h;}
 }
 static class ScreenTabs {
  SideConfigPanel sideConfig=new SideConfigPanel();boolean isConfigOpen(){return sideConfig.isOpen();}
  ${block(tabs,tabSignature)}
  ${block(tabs,overlaySignature)}
 }
 static class Base {void render(GuiGraphics g,int x,int y,float p){}}
 static class Probe extends Base {
  BoilerMenu menu=new BoilerMenu();Font font=new Font();Component title=new Component("Boiler"),playerInventoryTitle=new Component("Inventory");
  int leftPos,topPos,imageWidth=176,imageHeight=166,inventoryLabelX=8,inventoryLabelY=73;
  ScreenTabs tabs=new ScreenTabs();TransporterPanel transporter=new TransporterPanel();
  SideConfigPanel.Context configContext(){return new SideConfigPanel.Context(){};}
  TransporterPanel.Context transporterContext(){return new TransporterPanel.Context(){};}
  void renderTooltip(GuiGraphics g,int x,int y){}
  ${block(screen,'private MachineBackgroundMaterial backgroundMaterial(')}
  ${bgPrefix}
  ${block(screen,'protected void renderLabels(')}
  ${block(screen,'public void render(GuiGraphics')}
 }
 public static void main(String[] args){int cases=0;
  for(String definition:new String[]{${definitions.map(d=>JSON.stringify(d.id+':'+d.type)).join(',')}})
  for(BoilerTier tier:BoilerTier.values())for(int[] origin:new int[][]{{0,0},{125,44},{339,172},{-21,13}})
  for(int[] power:new int[][]{{0,0},{0,50},{1,0},{1,50},{-1,0}})
  for(int modal=0;modal<4;modal++)for(int titleLength:new int[]{1,6,38}){
   var p=new Probe();p.menu.type=definition.split(":")[1];p.menu.block=new BoilerBlock(tier);p.leftPos=origin[0];p.topPos=origin[1];p.title=new Component("X".repeat(titleLength));
   p.menu.data[BoilerMenu.DATA_ACTIVE]=power[0];p.menu.data[BoilerMenu.DATA_HEAT]=power[1];
   p.menu.data[BoilerMenu.DATA_WARNING]=2;p.menu.data[BoilerMenu.DATA_REDSTONE]=3;p.menu.data[BoilerMenu.DATA_PRIVATE]=1;
   p.tabs.sideConfig.open=(modal&1)!=0;p.transporter.open=(modal&2)!=0;
   var expected=tier==BoilerTier.HIGH?MachineBackgroundMaterial.STEAM_HIGH:MachineBackgroundMaterial.STEAM_BRONZE;
   check(p.backgroundMaterial()==expected,definition+" tier material");var g=new GuiGraphics();
   p.renderBg(g,0,origin[0]-13,origin[1]+11);
   check(MetalMachineBackground.material==expected,"background material");
   check(Arrays.equals(MetalMachineBackground.geometry,new int[]{origin[0],origin[1],176,166,titleLength*7}),"background coordinates/title width");
   var state=power[0]!=0?MachineBackgroundState.WORKING:power[1]>0?MachineBackgroundState.IDLE:MachineBackgroundState.UNPOWERED;
   check(MetalMachineBackground.state==state,"synced active/idle/unpowered");p.renderLabels(g,0,0);
   check(g.texts.size()==2,"title/inventory labels only");var text=g.texts.get(0);
   check(text.text==p.title&&text.x==(176-titleLength*7)/2&&text.y==6&&text.color==(tier==BoilerTier.HIGH?0xFFF4F4F0:0xFFFFF8E7)&&!text.shadow,"title color/position/shadow");
   var inv=g.texts.get(1);check(inv.x==8&&inv.y==73&&inv.color==4210752&&!inv.shadow,"inventory label unchanged");
   int mx=origin[0]-13,my=origin[1]+11;popupMaterials.clear();p.render(g,mx,my,0);check(!popupMaterials.isEmpty()&&popupMaterials.stream().allMatch(m->m==expected),"every popup material");
   check(tabMaterial==expected&&popupMaterial==expected,"all consumers share non-null material");
   check(Arrays.equals(tabXY,new int[]{origin[0],origin[1],mx,my,3,1,2}),"tab origin/mouse/data forwarding");
   check(Arrays.equals(popupXY,new int[]{origin[0],origin[1],mx,my}),"popup origin/mouse forwarding");
   check(hover==(modal==0),"modal must suppress lower tab hover");check(popup.equals(p.transporter.open?"transport":"side"),"modal overlay precedence");cases++;
  }
  var fallback=new Probe();fallback.menu.block=new Object();check(fallback.backgroundMaterial()==MachineBackgroundMaterial.STEAM_BRONZE,"non-boiler safe fallback");
  System.out.println("PASS: "+cases+" extracted production appearance cases; definitions="+args[0]+"; tiers/types, material, title, coordinates, synced state, modal gates, non-null forwarding");
 }
}`;
const executable=name=>process.env.JAVA_HOME?join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;
const temp=mkdtempSync(join(tmpdir(),'boiler-appearance-'));
try{const path=join(temp,'BoilerAppearanceProbe.java');writeFileSync(path,java);execFileSync(executable('javac'),['--release','21','-encoding','UTF-8','-d',temp,path],{stdio:'inherit'});execFileSync(executable('java'),['-cp',temp,'BoilerAppearanceProbe',definitions.map(d=>d.id).join(',')],{stdio:'inherit'});}finally{rmSync(temp,{recursive:true,force:true});}
