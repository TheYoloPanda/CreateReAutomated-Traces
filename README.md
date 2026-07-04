# Create ReAutomated: Traces

NeoForge 1.21.1 addon for Create ReAutomated.

It adds visible ore outcrops above buried Ore Nodes, plus a Trace Finder tool that points players toward nearby undiscovered traces.

## What it does

- During new chunk generation, scans blocks in `#createreautomated:ore_nodes`.
- If a Node has a `trace_block_for_node` mapping, places the largest valid trace template above or near it.
- Uses the Node base rock as the outcrop host material.
- Supports submerged traces in water, lava, and modded liquids.
- Avoids unsafe collisions with protected structures, block entities and artificial blocks.

Existing chunks are not scanned or retrofilled.

## Trace Finder

The Trace Finder is a compass-like tool in the tools and utilities creative tab.

- Right-click to choose which Node types to track.
- The item texture points toward the nearest undiscovered matching trace.
- Nearby matching traces can render beacon-style beams.
- Entering the discovery radius marks that trace as found.
- Multiple Trace Finders in one inventory share the same tracking state.

## Configuration

The mod uses a Create-style common config screen/file.

Worldgen:

- `tracePlacementRadius`: default `2`, range `0..8`
- `tracePlacementDiagnostics`: default `false`

Finder:

- `traceFinderRenderRadiusChunks`: default `12`, range `2..12`
- `maxVisibleTraceBeams`: default `64`, range `0..256`; `0` disables the cap

Worldgen config affects only newly generated chunks.

## Custom Nodes

A custom Node needs three things:

1. A real Create ReAutomated Ore Node block.
2. The `createreautomated:ore_nodes` block tag.
3. A trace block data map entry.

Data map path:

```text
data/createreautomatedtraces/data_maps/block/trace_block_for_node.json
```

```json
{
  "replace": false,
  "values": {
    "kubejs:lapis_node": {
      "trace_block": "minecraft:lapis_ore",
      "beam_color": "#2F64D6"
    }
  }
}
```

`beam_color` is optional. It accepts a hex string or an RGB array. If omitted, known vanilla ore blocks use built-in colors and unknown blocks fall back to white.

## Biome Coverage

Traces are added through:

```text
data/createreautomatedtraces/tags/worldgen/biome/has_traces.json
```

By default this covers Overworld and Nether biomes. Add custom biomes or biome tags there when Nodes generate elsewhere.

## External Integrations

For mods that place real Ore Nodes inside structure rewards:

- `TraceWorldgenExclusions.suppressGeneratedTrace(level, nodePos)` skips the generated surface outcrop once.
- `TraceApi.recordExternalNode(serverLevel, nodePos, nodeId)` records the Node for the Trace Finder.

Suppression only skips the visible generated trace. It does not make the Node trackable by itself.
