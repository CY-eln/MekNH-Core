import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const root = new URL('../src/main/resources/assets/meknh_core/', import.meta.url);
const json = async path => JSON.parse(await readFile(new URL(path, root), 'utf8'));
const deck = await json('models/block/pump_deck.json');
const pump = await json('models/block/machine/primitive_pump.json');
const pumpState = await json('blockstates/primitive_pump.json');
for (const [facing, y] of Object.entries({ north: 0, east: 90, south: 180, west: 270 })) {
    const variant = pumpState.variants['facing=' + facing];
    assert.equal(variant.y ?? 0, y);
    assert.equal(variant.uvlock, true, 'pump top must not rotate with facing');
    assert.equal(variant.model, 'meknh_core:block/machine/primitive_pump');
}
assert.equal(pump.elements.length, 2);
assert.deepEqual(pump.elements[1].from, [-0.01, -0.01, -0.01], 'source front overlay bounds');
assert.deepEqual(pump.elements[1].to, [16.01, 16.01, 16.01], 'source front overlay bounds');
assert.deepEqual(pump.elements[1].faces.north.uv, [0, 0, 16, 16]);
console.log('PASS pump four-way UV lock and source overlay bounds');
const hatch = await json('models/block/machine/pump_hatch.json');
const hatchBase = await json('models/block/machine/pump_hatch_base.json');
const hatchOverlay = await json('models/block/machine/pump_hatch_overlay.json');
const hatchState = await json('blockstates/pump_hatch.json');
const fixed = hatchState.multipart.filter(part => !part.when);
assert.deepEqual(fixed, [{ apply: { model: 'meknh_core:block/machine/pump_hatch_base' } }], 'hatch shell must not rotate');
const facings = { north: [0, 0], east: [0, 90], south: [0, 180], west: [0, 270], up: [-90, 0], down: [90, 0] };
assert.equal(hatchState.multipart.length, 7);
for (const [facing, [x, y]] of Object.entries(facings)) {
    const parts = hatchState.multipart.filter(part => part.when?.facing === facing);
    assert.equal(parts.length, 1);
    const { apply } = parts[0];
    assert.equal(apply.model, 'meknh_core:block/machine/pump_hatch_overlay');
    assert.equal(apply.x ?? 0, x);
    assert.equal(apply.y ?? 0, y);
}
assert.deepEqual(hatchBase.elements, hatch.elements.slice(0, 1), 'world and item shells agree');
assert.deepEqual(hatchOverlay.elements, hatch.elements.slice(1), 'world and item overlays agree');
assert.equal(hatchOverlay.elements.length, 3);
for (const [i, element] of hatchOverlay.elements.entries()) {
    assert.deepEqual(element.from, [0, 0, -(i + 1) / 100]);
    assert.ok(element.faces.north);
    assert.deepEqual(element.faces.north.uv, [0, 0, 16, 16]);
}
const emissive = hatchOverlay.elements.at(-1);
assert.equal(emissive.neoforge_data.block_light, 15);
assert.equal(emissive.neoforge_data.sky_light, 15);
assert.equal(emissive.neoforge_data.ambient_occlusion, false);
assert.equal(emissive.shade, false);
for (const model of [pump, hatch, hatchBase]) {
    assert.equal(model.textures.top, deck.textures.top);
    assert.equal(model.elements[0].faces.up.texture, '#top');
}
console.log('PASS fixed hatch shell, all six overlay directions, three layers and full-bright metadata');
for (const id of ['primitive_pump', 'pump_deck', 'treated_wood_frame', 'pump_hatch']) {
    const item = await json('models/item/' + id + '.json');
    const expected = 'meknh_core:block/' + (['primitive_pump', 'pump_hatch'].includes(id) ? 'machine/' : '') + id;
    assert.equal(item.parent, expected);
    if (id === 'pump_deck' || id === 'treated_wood_frame') {
        const state = await json('blockstates/' + id + '.json');
        assert.equal(state.variants[''].model, expected);
    }
}
const frame = await json('models/block/treated_wood_frame.json');
assert.equal(frame.render_type, 'minecraft:cutout', 'binary-alpha frame does not need translucent sorting');
for (const [path, frames, frametime] of [
    ['block/multiblock/primitive_pump/overlay_front.png', 4, 1],
    ['block/overlay/machine/overlay_pipe_out_emissive.png', 6, 2],
]) {
    const png = await readFile(new URL('textures/' + path, root));
    assert.equal(png.readUInt32BE(16), 16);
    assert.equal(png.readUInt32BE(20), 16 * frames);
    assert.equal((await json('textures/' + path + '.mcmeta')).animation.frametime, frametime);
}
console.log('PASS four item models, frame render layer and animation dimensions/timing');
console.log('Resource contract only; in-game visual acceptance is still required.');
