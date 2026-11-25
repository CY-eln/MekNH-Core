// Run: node devtools/check-brick-nameplate.mjs (Java 21; JAVA_HOME or PATH).
// Executes the production renderer with recording stubs, not Minecraft/GPU/font rasterization.
import {mkdtempSync,mkdirSync,writeFileSync,rmSync,readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,dirname} from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=new URL('../',import.meta.url);
const production=new URL('src/main/java/c_y/meknh_core/common/gui/render/BrickMachineNameplate.java',root);
const temp=mkdtempSync(join(tmpdir(),'brick-nameplate-'));
const files=[];
function source(name,body) {const file=join(temp,name);mkdirSync(dirname(file),{recursive:true});writeFileSync(file,body);files.push(file);}
const executable=name=>process.env.JAVA_HOME?join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;
try {
 source('net/minecraft/network/chat/Component.java','package net.minecraft.network.chat; public record Component(int width) {}');
 source('net/minecraft/client/gui/Font.java','package net.minecraft.client.gui; import net.minecraft.network.chat.Component; public class Font {public int lineHeight=9; public int width(Component c) {return c.width();}}');
 source('net/minecraft/client/gui/GuiGraphics.java',
  'package net.minecraft.client.gui; import java.util.*; import net.minecraft.network.chat.Component; public class GuiGraphics {'+
  'public List<int[]> fills=new ArrayList<>(); public int[] text; public boolean shadow; public Component title;'+
  'public void fill(int l,int t,int r,int b,int color) {if(text!=null) throw new AssertionError("background must precede title"); fills.add(new int[]{l,t,r,b,color});}'+
  'public int drawString(Font f,Component c,int x,int y,int color,boolean shadow) {this.title=c; this.text=new int[]{x,y,color}; this.shadow=shadow;return x+c.width();}}');
 source('NameplateProbe.java',
  'import net.minecraft.client.gui.*; import net.minecraft.network.chat.Component; import c_y.meknh_core.common.gui.render.BrickMachineNameplate;'+
  'public class NameplateProbe {'+
  'static void check(boolean b,String s) {if(!b) throw new AssertionError(s);}'+
  'static double channel(int c) {double v=c/255.0;return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);}'+
  'static double luma(int c) {return .2126*channel(c>>16&255)+.7152*channel(c>>8&255)+.0722*channel(c&255);}'+
  'public static void main(String[] args) {for(int width:new int[]{18,27,48,132}) {'+
  'var g=new GuiGraphics(); var font=new Font(); var title=new Component(width); BrickMachineNameplate.render(g,font,title,176,6);'+
  'check(g.fills.size()==3,"three-layer plaque"); var outer=g.fills.get(0); var inner=g.fills.get(1);'+
  'check(outer[0]==(176-width)/2-5 && outer[2]==(176-width)/2+width+5,"centered width with 5px margins");'+
  'check(outer[0]>=0 && outer[2]<=176 && outer[1]==4 && outer[3]==17,"inside header, above input slots");'+
  'check(inner[0]==outer[0]+1 && inner[2]==outer[2]-1 && inner[1]==5 && inner[3]==16,"border inset");'+
  'for(var fill:g.fills) check(fill[4]>>>24==255,"opaque plaque hides brick texture");'+
  'check(g.text[0]==(176-width)/2 && g.text[1]==6 && g.title==title && g.shadow,"unchanged centered title baseline with shadow");'+
  'check((luma(g.text[2])+.05)/(luma(inner[4])+.05)>7,"high-contrast title");'+
  '} System.out.println("PASS: actual brick nameplate renderer, 4 title widths, opaque background, header bounds, centered baseline and contrast >7:1");}}');
 execFileSync(executable('javac'),['--release','21','-encoding','UTF-8','-d',temp,fileURLToPath(production),...files],{stdio:'inherit'});
 execFileSync(executable('java'),['-cp',temp,'NameplateProbe'],{stdio:'inherit'});
 for(const screen of ['common/coke/CokeOvenScreen.java','common/blast/PrimitiveBlastFurnaceScreen.java']) {
  const text=readFileSync(new URL('src/main/java/c_y/meknh_core/'+screen,root),'utf8');
  if(!text.includes('BrickMachineNameplate.render(graphics, this.font, this.title, this.imageWidth, this.titleLabelY);')||text.includes('this.renderTitleText(graphics);')) throw Error('Missing title-only integration: '+screen);
 }
} finally {rmSync(temp,{recursive:true,force:true});}
