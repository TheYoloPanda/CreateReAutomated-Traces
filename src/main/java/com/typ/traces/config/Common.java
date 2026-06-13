package com.typ.traces.config;

import net.createmod.catnip.config.ConfigBase;

public class Common extends ConfigBase {

    public final Worldgen worldgen = nested(1, Worldgen::new, "Configure trace generation.");

    @Override
    public String getName() {
        return "common";
    }
}
