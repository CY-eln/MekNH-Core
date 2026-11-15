/** PBF resource regression check. Optional --generate adds missing resources only.
 * Usage: node devtools/check-pbf-assets.mjs [--generate] [GT checkout]
 * GT evidence: FirstDegreeMaterials.java Ash 55-60 / DarkAsh 204-209;
 * Material.java dust default DULL. Templates are primary + secondary alpha-over.
 * A JDK is used only for PNG decoding/composition; no external image dependency.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const generate = process.argv.includes('--generate');
const sourceRoot = path.resolve(process.argv.slice(2).find(a => a !== '--generate') ?? path.join(root, '../GregTech-Modern-1.20.1'));
const source = path.join(sourceRoot, 'src/main/resources/assets/gtceu/textures');
const resources = path.join(root, 'src/main/resources');
const assets = 'assets/meknh_core/';
const id = 'primitive_blast_furnace';
const brick = id + '_bricks';
const ns = 'meknh_core:';
const copies = ['block/casings/solid/machine_primitive_bricks.png', ...['overlay_front.png', 'overlay_front_emissive.png', 'overlay_front_active.png', 'overlay_front_active_emissive.png', 'overlay_front_active_emissive.png.mcmeta'].map(f => 'block/multiblock/' + id + '/' + f)];
const expected = new Map();
const json = (p, data) => expected.set(p, data);
const casing = ns + 'block/casings/solid/machine_primitive_bricks';
const variants = {};
for (const [facing, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
  for (const formed of [false, true]) for (const active of [false, true]) {
    variants['active=' + active + ',facing=' + facing + ',formed=' + formed] = { model: ns + 'block/machine/' + id + (active ? '_active' : ''), y };
  }
}
json(assets + 'blockstates/' + id + '.json', { variants });
for (const active of [false, true]) {
  const suffix = active ? '_active' : '';
  const face = (texture) => ({ uv: [0, 0, 16, 16], texture, cullface: 'north' });
  json(assets + 'models/block/machine/' + id + suffix + '.json', {
    parent: 'minecraft:block/block', render_type: 'minecraft:cutout',
    textures: { all: casing, particle: '#all', overlay: ns + 'block/multiblock/' + id + '/overlay_front' + suffix, overlay_emissive: ns + 'block/multiblock/' + id + '/overlay_front' + suffix + '_emissive' },
    elements: [
      { from: [0, 0, 0], to: [16, 16, 16], faces: Object.fromEntries(['down','up','north','south','west','east'].map(d => [d, { texture: '#all', cullface: d }])) },
      { from: [0,0,-0.01], to: [16,16,-0.01], faces: { north: face('#overlay') } },
      { from: [0,0,-0.03], to: [16,16,-0.03], shade: false, neoforge_data: { block_light: 15, sky_light: 15, ambient_occlusion: false }, faces: { north: face('#overlay_emissive') } }
    ]
  });
}
json(assets + 'models/item/' + id + '.json', { parent: ns + 'block/machine/' + id });
json(assets + 'blockstates/' + brick + '.json', { variants: { '': { model: ns + 'block/' + brick } } });
json(assets + 'models/block/' + brick + '.json', { parent: 'minecraft:block/cube_all', textures: { all: casing } });
json(assets + 'models/item/' + brick + '.json', { parent: ns + 'block/' + brick });
for (const name of ['dust_tiny_dark_ash', 'dust_ash']) json(assets + 'models/item/' + name + '.json', { parent: 'minecraft:item/generated', textures: { layer0: ns + 'item/' + name } });
json('data/meknh_core/loot_table/blocks/' + brick + '.json', {
  type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: ns + brick }], conditions: [{ condition: 'minecraft:survives_explosion' }] }]
});
// GT start(FRONT, UP, RIGHT): aisles back-to-front; rows bottom-to-top.
// Mek's production parser treats the LAST aisle as the front: preserve source back-to-front order.
const structure = [['bbb','bbb','bbb','bbb'], ['bbb','b&b','b#b','b#b'], ['bbb','b@b','bbb','bbb']];
json('data/addon/multiblock/' + id + '.json', { id, level: 'none', time: 1800, '结构': structure, '组成': { b: [ns + brick], '&': ['air_or_snow'] }, controller_block: ns + id });
// Processing recipes are maintained manually; do not generate or constrain them here.
if (generate) {
  // Never overwrite pre-existing files, including user dirty files.
  for (const [p, data] of expected) {
    const dest = path.join(resources, p);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    if (!fs.existsSync(dest)) fs.writeFileSync(dest, JSON.stringify(data, null, 2) + '\n', { flag: 'wx' });
  }
  for (const p of copies) {
    const dest = path.join(resources, assets, 'textures', p);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(path.join(source, p), dest, fs.constants.COPYFILE_EXCL);
  }
}
for (const [p, data] of expected) assert.deepEqual(JSON.parse(fs.readFileSync(path.join(resources, p), 'utf8')), data, p);
for (const p of copies) assert.deepEqual(fs.readFileSync(path.join(resources, assets, 'textures', p)), fs.readFileSync(path.join(source, p)), 'GT byte parity: ' + p);
assert.equal(Object.keys(variants).length, 16);
assert.ok(!fs.existsSync(path.join(resources, 'data/meknh_core/loot_table/blocks/' + id + '.json')), 'Controller loot must remain dynamic');
const counts = Object.fromEntries(['b','@','&','#'].map(c => [c, structure.flat().join('').split(c).length - 1]));
assert.deepEqual(counts, { b: 32, '@': 1, '&': 1, '#': 2 });
assert.deepEqual(structure[2][1], 'b@b');
assert.deepEqual(structure[1].slice(1), ['b&b','b#b','b#b']);
// Use ImageIO to independently recompute every output pixel from GT's two templates.
const java = `import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
class PbfPixels {
 public static void main(String[] args) throws Exception {
  Path src=Path.of(args[0]), dst=Path.of(args[1]); boolean generate=Boolean.parseBoolean(args[2]);
  String[] names={"dust_ash","dust_tiny_dark_ash"}, templates={"dust","dust_tiny"};
  int[] primary={0xd1d1d1,0x8b8989}, secondary={0x8b8989,0x555353};
  for(int i=0;i<2;i++) {
   BufferedImage p=ImageIO.read(src.resolve("item/material_sets/dull/"+templates[i]+".png").toFile());
   BufferedImage q=ImageIO.read(src.resolve("item/material_sets/dull/"+templates[i]+"_secondary.png").toFile());
   BufferedImage out=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_ARGB);
   for(int y=0;y<p.getHeight();y++) for(int x=0;x<p.getWidth();x++) {
    int a=p.getRGB(x,y), b=q.getRGB(x,y); double ap=(a>>>24)/255.0, aq=(b>>>24)/255.0, alpha=aq+ap*(1-aq); int pixel=(int)Math.round(alpha*255)<<24;
    if(alpha>0) for(int shift:new int[]{16,8,0}) {
     double value=(((b>>>shift)&255)*((secondary[i]>>>shift)&255)/255.0*aq+((a>>>shift)&255)*((primary[i]>>>shift)&255)/255.0*ap*(1-aq))/alpha;
     pixel|=((int)Math.floor(value))<<shift;
    }
    out.setRGB(x,y,pixel);
   }
   Path target=dst.resolve(names[i]+".png");
   if(generate) { Files.createDirectories(dst); if(Files.exists(target)) throw new IllegalStateException("Refuse overwrite "+target); ImageIO.write(out,"PNG",target.toFile()); }
   BufferedImage actual=ImageIO.read(target.toFile());
   if(actual.getWidth()!=out.getWidth()||actual.getHeight()!=out.getHeight()) throw new AssertionError("dimensions");
   for(int y=0;y<out.getHeight();y++) for(int x=0;x<out.getWidth();x++) if(actual.getRGB(x,y)!=out.getRGB(x,y)) throw new AssertionError(names[i]+" pixel "+x+","+y);
   System.out.println("PASS GT DULL primary+secondary tint pixel parity: "+names[i]);
  }
 }
}`;
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'pbf-assets-'));
try {
  const file = path.join(temp, 'PbfPixels.java');
  fs.writeFileSync(file, java);
  const result = spawnSync('java', [file, source, path.join(resources, assets, 'textures/item'), String(generate)], { encoding: 'utf8' });
  process.stdout.write(result.stdout ?? '');
  assert.equal(result.status, 0, result.stderr || result.error?.message);
} finally { fs.rmSync(temp, { recursive: true, force: true }); }
// Ensure every directly referenced local texture exists (including animated sheets).
for (const [p, data] of expected) if (p.includes('/models/')) {
  for (const texture of Object.values(data.textures ?? {})) if (texture.startsWith(ns)) {
    assert.ok(fs.existsSync(path.join(resources, assets, 'textures', texture.slice(ns.length) + '.png')), texture);
  }
}
console.log('PASS PBF: 16 states, layered idle/active emissive models, 32 bricks + controller + 3 cavity cells, six byte-identical GT assets');
