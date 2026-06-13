package com.typ.traces.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {

    private static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

    private static Common common;

    private Config() {}

    public static Common common() {
        return common;
    }

    private static <T extends ConfigBase> T register(Supplier<T> factory, ModConfig.Type type) {
        Pair<T, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(builder -> {
            T config = factory.get();
            config.registerAll(builder);
            return config;
        });

        T config = specPair.getLeft();
        config.specification = specPair.getRight();
        CONFIGS.put(type, config);
        return config;
    }

    public static void register(ModContainer container) {
        common = register(Common::new, ModConfig.Type.COMMON);

        for (Map.Entry<ModConfig.Type, ConfigBase> entry : CONFIGS.entrySet()) {
            container.registerConfig(entry.getKey(), entry.getValue().specification);
        }
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        for (ConfigBase config : CONFIGS.values()) {
            if (config.specification == event.getConfig().getSpec()) {
                config.onLoad();
            }
        }
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        for (ConfigBase config : CONFIGS.values()) {
            if (config.specification == event.getConfig().getSpec()) {
                config.onReload();
            }
        }
    }
}
