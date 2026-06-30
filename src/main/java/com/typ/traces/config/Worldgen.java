package com.typ.traces.config;

import net.createmod.catnip.config.ConfigBase;

public class Worldgen extends ConfigBase {

    public final ConfigInt tracePlacementRadius = i(
            2,
            0,
            8,
            "tracePlacementRadius",
            "Maximum horizontal offset in blocks between a Node and the Trace template center.");

    public final ConfigBool tracePlacementDiagnostics = b(
            false,
            "tracePlacementDiagnostics",
            "Log one compact INFO summary when Trace placement is skipped for a Node.");

    @Override
    public String getName() {
        return "worldgen";
    }
}
