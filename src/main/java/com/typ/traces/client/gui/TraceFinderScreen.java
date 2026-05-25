package com.typ.traces.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.typ.traces.component.TrackedNodesComponent;
import com.typ.traces.network.SelectionUpdatePayload;
import com.typ.traces.registry.ModDataComponents;
import com.typ.traces.worldgen.TraceBlockDataMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class TraceFinderScreen extends Screen {

    private static final int PANEL_WIDTH = 240;
    private static final int ROW_HEIGHT = 14;
    private static final int CHECK_SIZE = 10;
    private static final int TITLE_Y = 10;
    private static final int FILTER_Y = 26;
    private static final int LIST_TOP_Y = 50;
    private static final int LIST_BOTTOM_MARGIN = 40;
    private static final int CHECKED_COLOR = 0xFF7BC96F;
    private static final int UNCHECKED_COLOR = 0xFF4A4A4A;
    private static final int ROW_HOVER_COLOR = 0x40FFFFFF;
    private static final int PANEL_BG_COLOR = 0xC0101010;
    private static final int BORDER_COLOR = 0xFF505050;
    private static final int SCREEN_DIM_COLOR = 0x40000000;

    private final InteractionHand hand;
    private final List<NodeOption> allNodes;
    private final Set<ResourceLocation> selected = new HashSet<>();

    private List<NodeOption> filtered;
    private EditBox filterBox;
    private int scrollOffset = 0;
    private int panelX;

    private record NodeOption(ResourceLocation id, Component displayName, String displayText, String searchText) {
        static NodeOption of(ResourceLocation id) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            Component name = block == Blocks.AIR ? Component.literal(id.toString()) : block.getName();
            String display = name.getString();
            String search = (display + " " + id).toLowerCase(Locale.ROOT);
            return new NodeOption(id, name, display, search);
        }
    }

    public TraceFinderScreen(InteractionHand hand) {
        super(Component.translatable("screen.createreautomatedtraces.trace_finder.title"));
        this.hand = hand;

        Set<ResourceLocation> initiallySelected = new HashSet<>();
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            ItemStack stack = p.getItemInHand(hand);
            TrackedNodesComponent comp = stack.get(ModDataComponents.TRACKED_NODES.get());
            if (comp != null) initiallySelected.addAll(comp.selected());
        }
        selected.addAll(initiallySelected);

        // Show data map keys + any already-selected ids no longer in the map (so they can be unselected).
        Set<ResourceLocation> all = new HashSet<>(collectNodeIds());
        all.addAll(initiallySelected);
        List<NodeOption> sorted = new ArrayList<>();
        for (ResourceLocation id : all) {
            sorted.add(NodeOption.of(id));
        }
        sorted.sort(Comparator
                .comparing((NodeOption o) -> o.displayText().toLowerCase(Locale.ROOT))
                .thenComparing(o -> o.id().toString()));
        this.allNodes = Collections.unmodifiableList(sorted);
        this.filtered = allNodes;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;

        this.filterBox = new EditBox(this.font, panelX + 4, FILTER_Y, PANEL_WIDTH - 8, 16,
                Component.translatable("screen.createreautomatedtraces.trace_finder.filter"));
        this.filterBox.setHint(Component.translatable("screen.createreautomatedtraces.trace_finder.filter"));
        this.filterBox.setResponder(this::applyFilter);
        addRenderableWidget(this.filterBox);

        int buttonsY = this.height - 28;
        int buttonW = (PANEL_WIDTH - 12) / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.createreautomatedtraces.trace_finder.track_all"),
                        btn -> {
                            selected.clear();
                            for (NodeOption option : allNodes) selected.add(option.id());
                        })
                .bounds(panelX + 4, buttonsY, buttonW, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.createreautomatedtraces.trace_finder.clear"),
                        btn -> selected.clear())
                .bounds(panelX + 8 + buttonW, buttonsY, buttonW, 20)
                .build());
    }

    private void applyFilter(String text) {
        if (text == null || text.isBlank()) {
            filtered = allNodes;
        } else {
            String lower = text.toLowerCase(Locale.ROOT);
            List<NodeOption> result = new ArrayList<>();
            for (NodeOption option : allNodes) {
                if (option.searchText().contains(lower)) result.add(option);
            }
            filtered = result;
        }
        clampScroll();
    }

    private int listBottomY() {
        return this.height - LIST_BOTTOM_MARGIN;
    }

    private int visibleRows() {
        return Math.max(0, (listBottomY() - LIST_TOP_Y) / ROW_HEIGHT);
    }

    private void clampScroll() {
        int rows = visibleRows();
        int maxScroll = Math.max(0, filtered.size() - rows);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, SCREEN_DIM_COLOR);

        g.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFFFF);

        // Panel background
        int top = LIST_TOP_Y;
        int bottom = listBottomY();
        g.fill(panelX - 1, top - 1, panelX + PANEL_WIDTH + 1, bottom + 1, BORDER_COLOR);
        g.fill(panelX, top, panelX + PANEL_WIDTH, bottom, PANEL_BG_COLOR);

        if (filtered.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.createreautomatedtraces.trace_finder.empty"),
                    this.width / 2, top + 8, 0xFFAAAAAA);
        } else {
            int rows = visibleRows();
            for (int i = 0; i < rows && i + scrollOffset < filtered.size(); i++) {
                NodeOption option = filtered.get(i + scrollOffset);
                int y = top + i * ROW_HEIGHT;
                int rowEndY = y + ROW_HEIGHT;
                boolean hovered = mouseX >= panelX && mouseX < panelX + PANEL_WIDTH
                        && mouseY >= y && mouseY < rowEndY;
                if (hovered) {
                    g.fill(panelX, y, panelX + PANEL_WIDTH, rowEndY, ROW_HOVER_COLOR);
                }
                boolean checked = selected.contains(option.id());
                int cbx = panelX + 4;
                int cby = y + (ROW_HEIGHT - CHECK_SIZE) / 2;
                g.fill(cbx, cby, cbx + CHECK_SIZE, cby + CHECK_SIZE, BORDER_COLOR);
                g.fill(cbx + 1, cby + 1, cbx + CHECK_SIZE - 1, cby + CHECK_SIZE - 1,
                        checked ? CHECKED_COLOR : UNCHECKED_COLOR);
                g.drawString(this.font, option.displayName(), cbx + CHECK_SIZE + 6, y + (ROW_HEIGHT - 8) / 2,
                        0xFFFFFFFF, false);
            }
        }

        for (Renderable renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }

        // Selection count
        int count = selected.size();
        g.drawString(this.font,
                Component.translatable("item.createreautomatedtraces.trace_finder.tooltip.count", count),
                panelX + 4, bottom + 4, 0xFFCCCCCC, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int top = LIST_TOP_Y;
            int bottom = listBottomY();
            if (mouseX >= panelX && mouseX < panelX + PANEL_WIDTH && mouseY >= top && mouseY < bottom) {
                int row = (int) ((mouseY - top) / ROW_HEIGHT);
                int idx = row + scrollOffset;
                if (idx >= 0 && idx < filtered.size()) {
                    ResourceLocation id = filtered.get(idx).id();
                    if (!selected.add(id)) selected.remove(id);
                    playClickSound();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int top = LIST_TOP_Y;
        int bottom = listBottomY();
        if (mouseX >= panelX && mouseX < panelX + PANEL_WIDTH && mouseY >= top && mouseY < bottom) {
            scrollOffset -= (int) Math.signum(scrollY) * 2;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new SelectionUpdatePayload(hand, new HashSet<>(selected)));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static List<ResourceLocation> collectNodeIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (net.minecraft.world.level.block.Block b : BuiltInRegistries.BLOCK) {
            if (b.builtInRegistryHolder().getData(TraceBlockDataMap.TYPE) != null) {
                ids.add(BuiltInRegistries.BLOCK.getKey(b));
            }
        }
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return Collections.unmodifiableList(ids);
    }
}
