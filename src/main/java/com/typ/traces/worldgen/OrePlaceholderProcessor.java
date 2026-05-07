package com.typ.traces.worldgen;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

public class OrePlaceholderProcessor extends StructureProcessor {

    public static final MapCodec<OrePlaceholderProcessor> CODEC =
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("target")
                    .xmap(OrePlaceholderProcessor::new, p -> p.target);

    public static final StructureProcessorType<OrePlaceholderProcessor> TYPE = () -> CODEC;

    private final Block target;

    public OrePlaceholderProcessor(Block target) {
        this.target = target;
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos offset,
            BlockPos pos,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo currentBlockInfo,
            StructurePlaceSettings settings) {
        if (currentBlockInfo.state().is(Blocks.WHITE_CONCRETE)) {
            return new StructureTemplate.StructureBlockInfo(
                    currentBlockInfo.pos(),
                    target.defaultBlockState(),
                    currentBlockInfo.nbt());
        }
        return currentBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return TYPE;
    }

    public static void onRegister(RegisterEvent event) {
        event.register(Registries.STRUCTURE_PROCESSOR, helper ->
                helper.register(
                        ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "ore_placeholder"),
                        TYPE));
    }
}
