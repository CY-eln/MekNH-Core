// Executes production click, server color dispatch, and color-cycle method bodies.
// Minecraft networking/rendering are not bootstrapped; Direction and dirty tracking are stubs.
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {execFileSync} from 'node:child_process';
const root='src/main/java/c_y/meknh_core/';
const read=p=>readFileSync(root+p,'utf8');
function block(s,signature){const start=s.indexOf(signature);if(start<0)throw Error(signature);let end=s.indexOf('{',start)+1,depth=1;while(depth){const c=s[end++];if(c==='{')depth++;if(c==='}')depth--;if(end>s.length)throw Error('unclosed');}return s.slice(start,end);}
const panel=read('common/gui/element/TransporterPanel.java');
const variants=[['Machine','api/machine/MachineBlockEntity.java','common/gui/menu/MachineMenu.java','machine'],['Boiler','common/generator/boiler/BoilerBlockEntity.java','common/generator/boiler/BoilerMenu.java','blockEntity']];
const classes=variants.map(([name,entity,menu,field])=>{
const s=read(entity).replaceAll('net.minecraft.core.Direction','Direction');
const methods=[...s.matchAll(/public void cycle(?:Input|Output)Color\([^)]*\)/g)].map(m=>block(s,m[0])).join('\n');
const m=read(menu).replaceAll('net.minecraft.core.Direction','Direction');
const a=m.indexOf('} else if (id == 210)'),b=m.indexOf('} else if (',m.indexOf('this.'+field+'.cycleInputColor',a));
// Include all transporter actions up to the next unrelated action (including new reverse actions).
let end=b;while(/id (?:== 221|>= 222)/.test(m.slice(end,end+65)))end=m.indexOf('} else if (',end+1);
const dispatch='if (false) {'+m.slice(a,end)+'} return false;';
return 'static class '+name+' {int outputColor; int[] inputColors=new int[6]; int dirty; void setChanged(){dirty++;} void toggleStrictInput(){throw new AssertionError("strict");} '+methods+' '+name+' '+field+'=this; boolean click(int id){'+dispatch+'}}';
}).join('\n');
const java=`import java.util.*;
public class TransporterReverseProbe {
enum Direction {DOWN,UP,NORTH,SOUTH,WEST,EAST; Direction getOpposite(){return values()[ordinal()^1];} Direction getClockWise(){return switch(this){case NORTH->EAST;case EAST->SOUTH;case SOUTH->WEST;default->NORTH;};} Direction getCounterClockWise(){return getClockWise().getOpposite();}}
static final int WIDTH=156,HEIGHT=119; boolean open=true;
${block(panel,'public interface Context')}
${block(panel,'private enum Face')}
${block(panel,'public boolean mouseClicked(')}
record Rect2i(int x,int y,int width,int height) {}
${block(panel,'public net.minecraft.client.renderer.Rect2i bounds(').replaceAll('net.minecraft.client.renderer.Rect2i','Rect2i')}
${classes}
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
public static void main(String[] args){
for(boolean boiler:new boolean[]{false,true}) for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
Machine m=new Machine(); Boiler b=new Boiler(); int[] sent={0},lastId={-1};
TransporterReverseProbe p=new TransporterReverseProbe();
Context c=new Context(){public int strictInput(){return 0;}public int outputColor(){return boiler?b.outputColor:m.outputColor;}public int inputColor(Direction d){return (boiler?b.inputColors:m.inputColors)[d.ordinal()];}public Direction facing(){return facing;}public void sendButton(int id){lastId[0]=id;sent[0]++;check(boiler?b.click(id):m.click(id),"unhandled "+id);}};
for(int color=0;color<16;color++)for(Face face:Face.values())for(int button=0;button<3;button++){
Arrays.fill(m.inputColors,color);Arrays.fill(b.inputColors,color);int before=sent[0];int dirty=boiler?b.dirty:m.dirty;
check(p.bounds(37,29).equals(new Rect2i(47,44,WIDTH,HEIGHT)),"overlay bounds");
p.mouseClicked(47+face.x+1,44+face.y+1,button,37,29,c);
int[] colors=boiler?b.inputColors:m.inputColors;int target=face.direction(facing).ordinal();
for(int i=0;i<6;i++)check(colors[i]==(i==target&&button<2?(color+(button==0?1:15))%16:color),"face "+face+" color="+color+" button="+button+" got="+colors[i]);
if(button<2)check(lastId[0]==(button==0?212:222)+target,"input packet ID");
check(sent[0]-before==(button<2?1:0),"one packet per click");check((boiler?b.dirty:m.dirty)-dirty==(button<2?1:0),"dirty once");
}
for(int color=0;color<16;color++)for(int button=0;button<3;button++) {m.outputColor=b.outputColor=color;int before=sent[0],dirty=boiler?b.dirty:m.dirty;p.mouseClicked(123,65,button,0,0,c);check(c.outputColor()==(button<2?(color+(button==0?1:15))%16:color),"output reverse");if(button<2)check(lastId[0]==(button==0?211:221),"output packet ID");check(sent[0]-before==(button<2?1:0),"output packet count");check((boiler?b.dirty:m.dirty)-dirty==(button<2?1:0),"output dirty once");}
int before=sent[0];p.mouseClicked(147,22,1,0,0,c);check(sent[0]==before,"right strict ignored");
p.open=false;check(!p.mouseClicked(52,50,0,0,0,c),"closed panel");
}System.out.println("PASS transporter: machine+boiler, 4 facings, 16 colors, all 6 faces, left/right/middle, output and dirty/packet counts");}
}`;
const dir=mkdtempSync(join(tmpdir(),'transporter-reverse-'));
try{writeFileSync(join(dir,'TransporterReverseProbe.java'),java);execFileSync('javac',[join(dir,'TransporterReverseProbe.java')],{stdio:'inherit'});execFileSync('java',['-cp',dir,'TransporterReverseProbe'],{stdio:'inherit'});}finally{rmSync(dir,{recursive:true,force:true});}
