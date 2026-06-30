package com.typ.traces.config;

import net.createmod.catnip.config.ConfigBase;

public class Finder extends ConfigBase {

    public final ConfigInt traceFinderRenderRadiusChunks = i(
            12,
            2,
            12,
            "traceFinderRenderRadiusChunks",
            "Maximum chunk radius used by the Trace Finder when sending visible Trace beams to the client.");

    public final ConfigInt maxVisibleTraceBeams = i(
            64,
            0,
            256,
            "maxVisibleTraceBeams",
            "Maximum number of Trace Finder beams shown at once. Set to 0 for no limit.");

    @Override
    public String getName() {
        return "finder";
    }
}
