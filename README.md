# Create ReAutomated: Traces

A NeoForge 1.21.1 addon for Create ReAutomated. It places small ore outcrops on the surface, or on the solid bed below water and lava, above buried Ore Nodes. The point is to give you something visible at world level so a Node feels worth digging for.

The mod also adds a Trace Finder item: a compass-like tool that can track nearby traces for selected Node types and render beacon-style beams over discovered trace candidates.

## How trace generation works

During chunk generation, at the `top_layer_modification` step, the mod scans the chunk for any block in the `createreautomated:ore_nodes` tag. Each Node found gets a small trace above it, picked from the bundled `.nbt` templates. On dry terrain the trace is placed on the surface; under water or lava it is placed on the solid bed below the fluid. Ice-covered water is treated as underwater terrain, including packed and blue ice over water, so traces go on the sea, lake, or river bed instead of on top of the ice. The host stone of the trace matches the Node's base rock, with cobblestone smoothed back to stone and cobbled deepslate to deepslate so the result reads as a natural outcrop instead of a pile of mining drops. The visible accent block on top is whatever you assign in the data map.

If the terrain directly above the Node is awkward (cave entrance, cliff, terrain too uneven across the footprint), the mod searches outward for a better placement. The `tracePlacementRadius` common config controls how far the geometric center of the Trace template may move from the Node in X/Z: it defaults to 2 blocks, accepts values from 0 to 8, and setting it back to 8 restores the wider legacy placement range. A value of 0 keeps the 5x5 template center directly above the Node, even if the terrain looks less natural. Placement candidates use circular distance, so radius 1 allows the center above the Node or one block north, south, east, or west, but not diagonally. Placements that would overlap existing structures are rejected before placement, so a trace does not damage generated structure pieces. Foliage and snow around the trace are cleared with a small margin, with structure pieces left untouched. Logs are not cleared as foliage.

New traces are recorded in a per-dimension SavedData index when they are placed. The index stores the actual visible trace block from the generated template, not a guessed center position. Some templates can put the highest visible ore accent away from the 5x5 center, so the placement radius is a center-of-template guarantee, not a guarantee that digging under the visible ore block always hits the Node. Existing chunks can be backfilled conservatively when they are loaded: the mod scans loaded old chunks for trace blocks near the surface or fluid bed and records them only when it can match the trace block to an Ore Node below or nearby. This helps old worlds, but it is intentionally conservative and may not recover every trace.

## Trace Finder

The Trace Finder is registered in the tools and utilities creative tab. Right-clicking it opens a GUI listing every Node block present in the `createreautomatedtraces:trace_block_for_node` data map. The list uses the translated block names, so entries appear as names like `Zinc Node` or `Deepslate Diamond Node` instead of raw ids. The search box matches both display names and ids.

Selected Node types are stored directly on the item through a custom Data Component. The item tracks traces whose Node id matches one of the selected entries.

Tracking is intentionally aggregated per player inventory. If a player carries multiple Trace Finders, their selected Node types and discovered traces are combined into one radar state. Discovered traces are stored with their dimension, so matching coordinates in different dimensions do not collide. The compass target and visible beams use that combined state, not a separate target per individual item stack.

Every 10 server ticks, the server looks through the trace index around the player and chooses the nearest undiscovered matching trace in range. The client receives only target and visible-trace updates:

- the item texture rotates like a compass toward the nearest matching trace
- matching traces within the configured render radius show a beacon-style beam
- entering an 8-block horizontal radius marks that trace discovered on every carried Trace Finder that tracks that Node type
- discovered traces no longer produce beams and are ignored by the compass target for those Finders

## Configuration

The mod adds a Create-style in-game config screen backed by the common config file. The worldgen option `tracePlacementRadius` controls how far the center of a Trace template may move horizontally from its Node while looking for a natural nearby placement. The default is `2`, the minimum is `0`, and the maximum is `8`. This affects only newly generated chunks; existing Traces and saved Trace Finder discoveries are not moved. If an old local test config still contains the obsolete `traceSearchRadius` key and the screen looks stale, delete `run/config/createreautomatedtraces-common.toml` and let NeoForge/Catnip regenerate it.

## External node-only integrations

Mods that place real Create ReAutomated Ore Nodes as structure rewards can keep those blocks in `createreautomated:ore_nodes` without forcing this mod to generate a visible surface Trace above them. Call `TraceWorldgenExclusions.suppressGeneratedTrace(level, nodePos)` before placing the node during worldgen, then schedule `TraceApi.recordExternalNode(serverLevel, nodePos, nodeId)` on the main server thread. The Trace Finder will treat that indexed position as a valid node-only record when the chunk is loaded.

This path is intended for integrations such as Roost Traces. It avoids reflection, mixins into the Trace Finder, or direct imports from internal packages like `com.typ.traces.index` and `com.typ.traces.worldgen`.

## Setup for modpack developers

Three pieces wire any block up as a Node the mod will place and track a trace for. Stock Create ReAutomated Nodes already cover the first two, so for those you only fill in the data map. For your own custom Nodes (sibling mod, KubeJS, or otherwise) you usually need all three.

### 1. Register the Ore Node block

The block has to extend `createreautomated:ore_node` and declare its base stone. The mod reads `baseRock` straight off the block to pick the host material under the outcrop, so pass whatever stone makes sense for that Node.

NeoForge 1.21.1 cannot register new blocks from a pure datapack, so you need a sibling mod or KubeJS. With KubeJS, the registration goes in a startup script (any file under `kubejs/startup_scripts/`):

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

Stock Create ReAutomated Nodes are already in this tag, so you only edit it when adding your own.

### 3. Declare the trace block in the data map

Each Node id needs an entry in the `createreautomatedtraces:trace_block_for_node` data map mapping it to a single block under the `trace_block` field. Anything goes there, not just ores: a refined ingot block, raw material, polished decorative variant, or whatever fits the modpack tone. If an entry is missing, the mod logs a warning once for that Node id and skips placement.

Path: `data/createreautomatedtraces/data_maps/block/trace_block_for_node.json`

```json
{
  "replace": false,
  "values": {
    "kubejs:lapis_node":              { "trace_block": "minecraft:lapis_ore" },
    "kubejs:deepslate_lapis_node":    { "trace_block": "minecraft:deepslate_lapis_ore" },
    "kubejs:redstone_node":           { "trace_block": "minecraft:redstone_ore" },
    "kubejs:deepslate_redstone_node": { "trace_block": "minecraft:deepslate_redstone_ore" },
    "kubejs:osmium_node":             { "trace_block": "mekanism:block_osmium", "beam_color": "#B7D7D9" }
  }
}
```

Worldgen of the Node itself (configured feature, placed feature, biome targeting, height range, density) is yours to control through Create ReAutomated or vanilla mechanisms. As long as the Node ends up in the chunk during generation, is in the tag, and has a data map entry, the trace will follow.

## Beam colors

The beam color is resolved server-side and synced to the client with visible trace updates. The client does not need to know the server datapack's data map contents.

Vanilla trace blocks use built-in colors:

- redstone ore -> red
- coal ore -> gray
- lapis ore -> blue
- copper ore -> orange
- iron ore -> pink
- diamond ore -> cyan
- gold ore and nether gold ore -> yellow
- emerald ore -> green
- nether quartz ore -> white
- ancient debris -> brown

For trace blocks not covered by the built-in mapping, add optional `beam_color` to the data map entry. It accepts either a hex string or RGB array:

```json
{
  "values": {
    "kubejs:osmium_node": {
      "trace_block": "mekanism:block_osmium",
      "beam_color": "#B7D7D9"
    },
    "kubejs:tin_node": {
      "trace_block": "mekanism:block_tin",
      "beam_color": [180, 190, 205]
    }
  }
}
```

If no built-in color and no `beam_color` are available, the beam falls back to white.

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
