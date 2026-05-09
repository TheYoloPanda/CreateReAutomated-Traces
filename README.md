# Create ReAutomated: Traces

A NeoForge 1.21.1 add-on for Create ReAutomated that scatters small ore outcrops on the surface above buried Ore Nodes. The point is simple: give you something to spot at world level, a hint that there's a Node worth digging for underneath, the way real ore deposits leave traces on the ground.

## How it works

When a chunk is generated, the mod scans it once for any block in the `createreautomated:ore_nodes` tag. For each Node it finds, it queues placement of a trace structure on the next tick. A random `.nbt` template is picked from those bundled with the mod, an exposed surface anchor is located above the Node, foliage in the way is cleared, and the structure is stamped in. White concrete inside the template is swapped for the Node's host stone (read from `OreNodeBlock.baseRock`, with cobblestone smoothed back to stone and cobbled deepslate to deepslate so traces look like natural outcrops rather than mining drops). Black concrete is swapped for whatever block is declared by the `createreautomatedtraces:trace_block_for_node` data map. The foundation underneath is filled with the same host stone so the trace sits flush even on rough terrain.

Detection runs only on freshly generated chunks. Existing worlds won't be retrofitted, and Nodes placed by hand or by tick events after worldgen won't trigger placement either, since the chunk has already been scanned.

## Working with custom Nodes 🪨

The mod doesn't care who registered the Node or how it ended up in the chunk, only that the block carries the right tag and has a matching data map entry. Three pieces are needed, all data-driven, no Java required.

Register the block extending `createreautomated:ore_node` (via KubeJS, a datapack, or a sibling mod), and set its base stone with `baseStone(...)`. The mod reads `baseRock` straight off the block to decide what host material the trace should use, so whatever you pass there is what you get under the outcrop.

Then add the block to the `createreautomated:ore_nodes` block tag at `data/createreautomated/tags/block/ore_nodes.json`. Without the tag the chunk scan skips it entirely, and no trace will ever appear.

Finally, declare which block should fill the trace by writing an entry into the data map at `data/createreautomatedtraces/data_maps/block/trace_block_for_node.json`. The format is one entry per Node block id, each mapping to a single block under the `trace_block` field. Any block goes here, not just ores: a block of refined ingots, raw material, polished decorative variant, whatever fits the modpack's tone. If the entry is missing the mod logs a warning once for that Node id and moves on without placing anything.

Worldgen is yours to control. Feature type, placement modifiers, biome targeting, density, height range, all up to you. As long as the Node ends up in the chunk during generation and is in the tag, the trace will follow.

## Requirements

NeoForge 1.21.1 and Create ReAutomated 0.1.1. Create is pulled in transitively through Create ReAutomated and isn't a direct dependency of this mod.
