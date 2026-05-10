# Create ReAutomated: Traces

A NeoForge 1.21.1 addon for Create ReAutomated. It places small ore outcrops on the surface above buried Ore Nodes, the way real ore deposits leave traces on the ground. The point is to give you something to spot at world level so a Node feels worth digging for.

## How it works

During chunk generation, at the `top_layer_modification` step, the mod scans the chunk for any block in the `createreautomated:ore_nodes` tag. Each Node found gets a small surface trace centered above it, picked from one of three bundled `.nbt` templates. The host stone of the trace matches the Node's base rock, with cobblestone smoothed back to stone and cobbled deepslate to deepslate so the result reads as a natural outcrop instead of a pile of mining drops. The visible accent block on top is whatever you assign in the data map.

If the surface directly above the Node is unusable (cave entrance, cliff, terrain too uneven across the footprint) the mod searches outward up to 8 blocks for the nearest flat spot. If nothing in that radius works, the trace is skipped. Anchors that would overlap a vanilla structure (village, witch hut, nether fortress, woodland mansion, stronghold) are rejected before placement, so a trace never damages an existing structure. Foliage in and around the trace is cleared, with structure pieces left untouched.

Detection runs only on freshly generated chunks. Old worlds are not retrofitted.

## Setup for modpack developers

Three pieces wire any block up as a Node the mod will place a trace for. Stock Create ReAutomated Nodes already cover the first two, so for those you only fill in the data map. For your own custom Nodes (sibling mod, KubeJS, or otherwise) you usually need all three.

### 1. Register the Ore Node block

The block has to extend `createreautomated:ore_node` and declare its base stone. The mod reads `baseRock` straight off the block to pick the host material under the outcrop, so pass whatever stone makes sense for that Node.

NeoForge 1.21.1 cannot register new blocks from a pure datapack, you need a sibling mod or KubeJS. With KubeJS, the registration goes in a startup script (any file under `kubejs/startup_scripts/`):

```js
StartupEvents.registry('block', event => {
    event.create('kubejs:lapis_node', 'createreautomated:ore_node')
        .baseStone('minecraft:stone')
        .yield(200)
        .hardness(3.0)
        .resistance(3.0)
        .requiresTool(true)
        .tagBlock('minecraft:mineable/pickaxe')
        .tagBlock('minecraft:needs_iron_pickaxe')
})
```

### 2. Add the block to the Ore Node tag

The block has to carry the `createreautomated:ore_nodes` tag. Without it the chunk scan skips the block and no trace will ever appear.

Path: `data/createreautomated/tags/block/ore_nodes.json`

```json
{
  "replace": false,
  "values": [
    "kubejs:lapis_node",
    "kubejs:deepslate_lapis_node",
    "kubejs:redstone_node",
    "kubejs:deepslate_redstone_node"
  ]
}
```

Stock Create ReAutomated Nodes are already in this tag, you only edit it when adding your own.

### 3. Declare the trace block in the data map

Each Node id needs an entry in the `createreautomatedtraces:trace_block_for_node` data map mapping it to a single block under the `trace_block` field. Anything goes there, not just ores: a refined ingot block, raw material, polished decorative variant, whatever fits the modpack tone. If an entry is missing, the mod logs a warning once for that Node id and skips placement.

Path: `data/createreautomatedtraces/data_maps/block/trace_block_for_node.json`

```json
{
  "replace": false,
  "values": {
    "kubejs:lapis_node":              { "trace_block": "minecraft:lapis_ore" },
    "kubejs:deepslate_lapis_node":    { "trace_block": "minecraft:deepslate_lapis_ore" },
    "kubejs:redstone_node":           { "trace_block": "minecraft:redstone_ore" },
    "kubejs:deepslate_redstone_node": { "trace_block": "minecraft:deepslate_redstone_ore" },
    "kubejs:osmium_node":             { "trace_block": "mekanism:block_osmium" }
  }
}
```

Worldgen of the Node itself (configured feature, placed feature, biome targeting, height range, density) is yours to control through Create ReAutomated or vanilla mechanisms. As long as the Node ends up in the chunk during generation and is in the tag, the trace will follow.

## Extending biome coverage

By default traces are added to `#minecraft:is_overworld` and `#minecraft:is_nether` through the `createreautomatedtraces:has_traces` biome tag. If your Nodes spawn in modded biomes outside vanilla overworld and nether (custom dimensions, addon biomes), add the biome or its parent tag to that file in a datapack.

Path: `data/createreautomatedtraces/tags/worldgen/biome/has_traces.json`

```json
{
  "replace": false,
  "values": [
    "#aether:is_aether",
    "twilightforest:dense_forest"
  ]
}
```