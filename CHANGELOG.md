# Changelog

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
