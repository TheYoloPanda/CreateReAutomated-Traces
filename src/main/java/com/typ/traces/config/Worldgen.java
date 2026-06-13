package com.typ.traces.config;

import net.createmod.catnip.config.ConfigBase;

public class Worldgen extends ConfigBase {

    public final ConfigInt tracePlacementRadius = i(
            2,
            0,
            8,
            "tracePlacementRadius",
            "Maximum horizontal offset in blocks between a Node and the Trace template center.");

    @Override
    public String getName() {
        return "worldgen";
    }
}
