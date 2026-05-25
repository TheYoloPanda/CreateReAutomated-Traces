package com.typ.traces.item;

import com.typ.traces.component.TrackedNodesComponent;
import com.typ.traces.network.OpenTraceFinderPayload;
import com.typ.traces.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class TraceFinderItem extends Item {

    public TraceFinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new OpenTraceFinderPayload(hand));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TrackedNodesComponent comp = stack.get(ModDataComponents.TRACKED_NODES.get());
        int count = comp == null ? 0 : comp.selected().size();
        tooltip.add(Component.translatable(
                "item.createreautomatedtraces.trace_finder.tooltip.count", count)
                .withStyle(ChatFormatting.GRAY));
    }
}
