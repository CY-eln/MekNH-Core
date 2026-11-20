// Offline regression: actual JEI jar, actual nested plugin handler; only Minecraft screens/Rect2i are fixtures.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
const cache = path.join(os.homedir(), '.gradle/caches/modules-2/files-2.1');
const jei = process.env.JEI_JAR || path.join(cache,'maven.modrinth/jei/1vC6z8wV/363f10ce1d8918381a125cc1e8d800caf1bcb40e/jei-1vC6z8wV.jar');
const guava = process.env.GUAVA_JAR || path.join(cache,'com.google.guava/guava/32.1.2-jre/5e64ec7e056456bef3a4bc4c6fdaef71e8ab6318/guava-32.1.2-jre.jar');
for (const [name,file] of [['JEI_JAR',jei],['GUAVA_JAR',guava]]) {
 if (!fs.existsSync(file)) throw Error('Missing cached dependency '+file+'; set '+name+' to an existing local jar. No downloads are performed.');
}
const executable = name => process.env[name.toUpperCase()] || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')) : name);
const out = 'build/devtools/jei-extra-areas';
fs.mkdirSync(out,{recursive:true});
const sources=[];
function source(name,text){const file=path.join(out,name+'.java');fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,text);sources.push(file);}
source('net/minecraft/client/gui/screens/inventory/AbstractContainerScreen','package net.minecraft.client.gui.screens.inventory; public abstract class AbstractContainerScreen<T> {}');
source('net/minecraft/client/renderer/Rect2i',
'package net.minecraft.client.renderer; public record Rect2i(int x,int y,int width,int height) { public int getX(){return x;} public int getY(){return y;} public int getWidth(){return width;} public int getHeight(){return height;} }');
for(const [pkg,name] of [['common.gui.screen','MachineScreen'],['common.generator.boiler','BoilerScreen'],['common.coke','CokeOvenScreen']]) {
 const fq='c_y.meknh_core.'+pkg;
 source(fq.replaceAll('.','/')+'/'+name,
 'package '+fq+'; public class '+name+' extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<Object> {'+
 ' public java.util.List<net.minecraft.client.renderer.Rect2i> areas=java.util.List.of(); public java.util.List<net.minecraft.client.renderer.Rect2i> jeiExtraAreas(){return areas;}'+
 ' public java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> jeiTankIngredient(mezz.jei.api.gui.builder.IClickableIngredientFactory f,double x,double y){return java.util.Optional.empty();} }');
}
const plugin=fs.readFileSync('src/main/java/c_y/meknh_core/client/jei/MeknhJeiPlugin.java','utf8');
const start=plugin.indexOf('    private static final class TankIngredientHandler');
if(start<0) throw Error('handler not found');
let nested=plugin.slice(start,plugin.lastIndexOf('}'));
// Mutation stays in generated fixture, never edits production source.
if (process.argv.includes('--missing-exclusions')) {
 nested=nested.replace(/        @Override\s+public List<Rect2i> getGuiExtraAreas\(T screen\) \{[\s\S]*?\n        \}\n/, '');
}
source('c_y/meknh_core/client/jei/MeknhJeiPlugin',
'package c_y.meknh_core.client.jei; import java.util.*; import net.minecraft.client.renderer.Rect2i; import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen; import c_y.meknh_core.common.gui.screen.MachineScreen; import c_y.meknh_core.common.generator.boiler.BoilerScreen; import mezz.jei.api.gui.handlers.IGuiContainerHandler; import mezz.jei.api.gui.builder.IClickableIngredientFactory; import mezz.jei.api.runtime.IClickableIngredient; public class MeknhJeiPlugin {'+
' public static List<Rect2i> extra(AbstractContainerScreen<?> screen){return new TankIngredientHandler<AbstractContainerScreen<?>>().getGuiExtraAreas(screen);}' + nested + '}');
const cp=[out,jei,guava].join(path.delimiter);
function run(cmd,args){const p=spawnSync(cmd,args,{stdio:'inherit'}); if(p.error)throw p.error; if(p.status)process.exit(p.status);}
run(executable('javac'),['--release','21','-cp',cp,'-d',out,...sources,'devtools/JeiExtraAreasProbe.java']);
run(executable('java'),['-cp',cp,'JeiExtraAreasProbe']);
