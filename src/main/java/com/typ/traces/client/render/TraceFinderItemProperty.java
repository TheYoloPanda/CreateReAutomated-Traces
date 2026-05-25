package com.typ.traces.client.render;

import java.util.Optional;

import com.typ.traces.client.ClientTraceState;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class TraceFinderItemProperty implements ClampedItemPropertyFunction {

    private final CompassItemPropertyFunction delegate = new CompassItemPropertyFunction((level, stack, entity) -> {
        Optional<BlockPos> target = ClientTraceState.INSTANCE.target();
        return target.map(pos -> GlobalPos.of(level.dimension(), pos)).orElse(null);
    });

    @Override
    public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        return delegate.unclampedCall(stack, level, entity, seed);
    }
}
