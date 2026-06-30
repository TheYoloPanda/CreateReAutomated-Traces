# Changelog

## [0.2.1]

### Added
- Added a Create-style common config screen with `tracePlacementRadius` for trace placement.
- Added deterministic template profiles, exact support masks, and a bundled 2x2 fallback Trace.
- Added Trace Finder render tuning options for beam render radius and maximum visible beams.
- Added `TraceWorldgenExclusions`, a public runtime API for suppressing generated trace outcrops at exact node positions.
- Added `TraceApi`, a public facade for external node-only integrations to record node positions in the Trace Finder index.

### Changed
- Trace Finder beams are now rendered in client-side batches to reduce buffer flushes and draw overhead when many beams are visible.
- Runtime Trace worldgen suppressions are now consumed automatically when the matching Node position is seen, so node-only integrations do not accumulate stale suppression state for the lifetime of the server.
- Trace placement now defaults to a 2-block horizontal radius from the Node to the Trace template center; setting `tracePlacementRadius` to 8 restores the previous wider placement range.
- `tracePlacementRadius` now measures the real horizontal distance from the Node to the Trace template center. For example, radius 1 allows only the center block plus the four cardinal neighboring blocks, while diagonal placements are no longer counted as being within radius 1.
- Trace placement now uses fixed natural and terraced cut/fill limits instead of weighted terrain heuristics.
- The placement planner now records the exact cut, fill, and cleanup blocks that the placer applies.
- Terraced placement now cuts terrain only where non-air template blocks actually overlap it; adjacent dirt, grass, sand, and gravel are preserved.
- Terrain cutting now uses explicit Overworld and Nether whitelists, so common natural terrain can be shaped while ores and artificial blocks reject the candidate.
- Trace generation now runs at `fluid_springs`, after Create ReAutomated Node placement and before tree or vegetation decoration.
- Logs and stems now reject a placement candidate instead of triggering bounded tree or canopy cleanup.
- Trace placement now rejects candidates where any occupied support column would start below the local surface, preventing large templates from spawning partially underground.
- If all normal candidates fail for steep or awkward surface-shape reasons, placement now tries `small_trace_1` with its visible Trace block aligned directly above the Node, evaluates the four cardinal rotations, and chooses the one needing the least safe host-stone fill under the occupied footprint.
- Trace placement now ignores Strongholds, Trial Chambers, and Trail Ruins in the structure guard, matching the other structure exceptions that should not suppress Trace outcrops.
- Trace template selection and placement randomness are now stable for the world seed and Node position.
- Trace Finder index resolution now supports records that point directly at compatible ore node blocks, and skips unloaded chunks without removing records.
- Trace indexing no longer performs an automatic backfill or rescan of existing chunks; only newly placed traces and API-recorded nodes are indexed.

## [0.2.0]

### Added
- Trace Finder discoveries are now dimension-aware, so traces at the same coordinates in different dimensions no longer conflict.
- Trace index scan versioning now forces a one-time conservative rescan of older indexed chunks.

### Changed
- Trace generation now supports underwater and lava-covered terrain by anchoring traces on the solid bed below the fluid.
- Ice-covered water now behaves like underwater terrain, so traces spawn on the water body's bed instead of on top of sea ice or iceberg ice.
- New traces are indexed at the actual mapped trace block inside the generated template instead of an estimated center/top position.
- Create is now declared as a required dependency because bundled recipes and default zinc trace mappings reference `create:*` resources.
- Network payload protocol bumped to version `3`.

### Fixed
- Fixed stale Trace Finder beams and compass targets after changing dimension.
- Fixed valid traces being removed from the index when the saved position did not exactly match the visible trace block.
- Fixed Trace Finder accepting arbitrary client-sent node ids.
- Fixed generated features reporting success even when no trace was actually placed.
- Fixed trace placement rejecting fluid-covered terrain entirely.
- Fixed traces clearing logs while looking for or preparing an anchor.

## [0.1.1]

### Changed
- Bumped CreateReAutomated dependency from `0.1.1` to `0.2.0`. No API surface affected: `OreNodeBlock.baseRock`, the `createreautomated:ore_nodes` block tag, and all node block IDs are preserved upstream.

## [0.1.0]

### Released
