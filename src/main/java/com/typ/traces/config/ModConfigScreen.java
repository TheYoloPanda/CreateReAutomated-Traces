package com.typ.traces.config;

import com.typ.traces.CreateReAutomatedTraces;

import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;

public class ModConfigScreen extends BaseConfigScreen {

    public ModConfigScreen(ModContainer ignoredModContainer, Screen parent) {
        super(parent, CreateReAutomatedTraces.MODID);
    }
}
