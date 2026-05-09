package com.typ.traces.worldgen;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.typ.traces.CreateReAutomatedTraces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

public class TracePlaceholderProcessor extends StructureProcessor {

    public static final MapCodec<TracePlaceholderProcessor> CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                    BuiltInRegistries.BLOCK.byNameCodec().fieldOf("trace_block").forGetter(p -> p.traceBlock),
                    BuiltInRegistries.BLOCK.byNameCodec().fieldOf("host").forGetter(p -> p.host)
            ).apply(inst, TracePlaceholderProcessor::new));

    public static final StructureProcessorType<TracePlaceholderProcessor> TYPE = () -> CODEC;

    private final Block traceBlock;
    private final Block host;

    public TracePlaceholderProcessor(Block traceBlock, Block host) {
        this.traceBlock = traceBlock;
        this.host = host;
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
                    currentBlockInfo.pos(), host.defaultBlockState(), currentBlockInfo.nbt());
        }
        if (currentBlockInfo.state().is(Blocks.BLACK_CONCRETE)) {
            return new StructureTemplate.StructureBlockInfo(
                    currentBlockInfo.pos(), traceBlock.defaultBlockState(), currentBlockInfo.nbt());
        }
        if (currentBlockInfo.state().isAir()
                && level.getFluidState(currentBlockInfo.pos()).is(FluidTags.WATER)) {
            return null;
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
                        ResourceLocation.fromNamespaceAndPath(CreateReAutomatedTraces.MODID, "trace_placeholder"),
                        TYPE));
    }
}
