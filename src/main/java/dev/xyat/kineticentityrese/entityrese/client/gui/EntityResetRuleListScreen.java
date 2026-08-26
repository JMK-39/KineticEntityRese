package dev.xyat.kineticentityrese.entityrese.client.gui;

import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.AdvancedSearchUtil;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kineticcore.api.client.PinyinUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.entity.EntityPreviewRenderer;
import dev.xyat.kineticcore.api.client.gui.ConfigScrollbarTheme;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfig;
import dev.xyat.kineticentityrese.entityrese.network.EntityReseRuleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class EntityResetRuleListScreen extends ScaledScreen {
    private static final int PANEL_X = 20;
    private static final int PANEL_Y = 12;
    private static final int PANEL_W = 600;
    private static final int PANEL_H = 342;
    private static final int GRID_X = 38;
    private static final int GRID_Y = 66;
    private static final int COLS = 9;
    private static final int CELL_W = 60;
    private static final int CELL_H = 57;
    private static final int VISIBLE_ROWS = 4;
    private static final int GRID_W = COLS * CELL_W;
    private static final int GRID_H = VISIBLE_ROWS * CELL_H;
    private static final int SCROLL_X = GRID_X + GRID_W + 6;
    private static final int SCROLL_W = 6;

    private final Screen parent;
    private final List<String> allEntityIds = new ArrayList<>();
    private final List<String> filteredEntityIds = new ArrayList<>();
    private final Map<String, String> searchData = new HashMap<>();
    private final GridScrollController scroll = new GridScrollController();
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer();

    private EditBox searchBox;
    private String searchQuery = "";
    private List<Component> deferredTooltip;
    private boolean syncRequested;
    private boolean initialSyncPending;

    public EntityResetRuleListScreen(Screen parent) {
        super(Component.translatable("gui.kineticentityrese.rule_list.title"));
        this.parent = parent;
        configureResponsiveCanvas(640F, 360F, 6);
        rebuildEntityData();
    }

    @Override
    protected void initScaled() {
        searchBox = addRenderableWidget(new EditBox(
                font,
                GRID_X,
                38,
                GRID_W,
                20,
                Component.translatable("gui.kineticentityrese.rule_list.search_hint")
        ));
        searchBox.setMaxLength(256);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value == null ? "" : value;
            updateSearch(searchQuery);
        });

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticentityrese.rule_list.back"),
                        button -> closeToParent())
                .bounds(500, 328, 104, 20)
                .build());

        updateSearch(searchQuery);
        requestServerRulesOnce();
    }

    private void requestServerRulesOnce() {
        if (syncRequested || Minecraft.getInstance().getConnection() == null) return;
        syncRequested = true;
        initialSyncPending = true;
        EntityReseRuleNetwork.requestRules();
    }

    private void rebuildEntityData() {
        buildEntityList();
        buildSearchData();
    }

    private void buildEntityList() {
        allEntityIds.clear();
        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .sorted(ResourceLocation::compareTo)
                .forEach(id -> {
                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
                    String value = id.toString();
                    if (EntityReseConfig.hasRule(value)
                            || (type != null && type.getCategory() != MobCategory.MISC)) {
                        allEntityIds.add(value);
                    }
                });

        for (String configuredId : EntityReseConfig.ENTITY_RULES_CACHE.keySet()) {
            if (!allEntityIds.contains(configuredId)) {
                allEntityIds.add(configuredId);
            }
        }
    }

    private void buildSearchData() {
        searchData.clear();
        for (String id : allEntityIds) {
            String name = entityName(id);
            String raw = id + " " + name;
            searchData.put(id, (raw + " " + PinyinUtil.getSearchData(raw)).toLowerCase(Locale.ROOT));
        }
    }

    private void updateSearch(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredEntityIds.clear();
        for (String id : allEntityIds) {
            if (normalized.isEmpty()
                    || AdvancedSearchUtil.match(searchData.getOrDefault(id, id.toLowerCase(Locale.ROOT)), normalized)) {
                filteredEntityIds.add(id);
            }
        }
        filteredEntityIds.sort((left, right) -> {
            int configuredCompare = Boolean.compare(
                    EntityReseConfig.ENTITY_RULES_CACHE.containsKey(right),
                    EntityReseConfig.ENTITY_RULES_CACHE.containsKey(left)
            );
            return configuredCompare != 0 ? configuredCompare : left.compareToIgnoreCase(right);
        });
        scroll.reset();
        updateScrollRange();
    }

    private void updateScrollRange() {
        int totalRows = (filteredEntityIds.size() + COLS - 1) / COLS;
        scroll.updateRange(Math.max(0, totalRows - VISIBLE_ROWS), totalRows, VISIBLE_ROWS);
    }

    @Override
    protected void renderScaledBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        deferredTooltip = null;
        graphics.fillGradient(0, 0, vWidth, vHeight, 0xFF171717, 0xFF0E0E0E);
        GuiRenderUtil.drawStandardPanel(graphics, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        AdaptiveItemGridRenderer.drawGrid(graphics, GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8, 6);
        graphics.renderOutline(GRID_X - 4, GRID_Y - 4, GRID_W + 8, GRID_H + 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, title, vWidth / 2, 18, 0xFFFFFFFF);
        renderGrid(graphics, mouseX, mouseY);
        ConfigScrollbarTheme.render(
                scroll,
                graphics,
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                SCROLL_W,
                GRID_H,
                18
        );

        if (filteredEntityIds.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("gui.kineticentityrese.rule_list.empty"),
                    GRID_X + GRID_W / 2,
                    GRID_Y + GRID_H / 2,
                    0xFFFFFFFF
            );
        }
    }

    @Override
    protected void renderScaledForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (searchBox != null && searchBox.visible && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            String hint = font.plainSubstrByWidth(
                    Component.translatable("gui.kineticentityrese.rule_list.search_hint").getString(),
                    Math.max(0, searchBox.getWidth() - 10)
            );
            graphics.drawString(
                    font,
                    hint,
                    searchBox.getX() + 5,
                    searchBox.getY() + (searchBox.getHeight() - font.lineHeight) / 2,
                    0xFFFFFFFF,
                    false
            );
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int first = scroll.offset() * COLS;
        int last = Math.min(first + VISIBLE_ROWS * COLS, filteredEntityIds.size());

        for (int index = first; index < last; index++) {
            int localIndex = index - first;
            int x = GRID_X + localIndex % COLS * CELL_W;
            int y = GRID_Y + localIndex / COLS * CELL_H;
            String id = filteredEntityIds.get(index);
            boolean configured = EntityReseConfig.hasRule(id);
            boolean hovered = mouseX >= x && mouseX < x + CELL_W
                    && mouseY >= y && mouseY < y + CELL_H;

            AdaptiveItemGridRenderer.drawSlot(graphics, x, y, CELL_W, CELL_H, 4, hovered);
            if (configured) {
                graphics.renderOutline(x, y, CELL_W, CELL_H, 0xFF55FF55);
                graphics.renderOutline(x + 1, y + 1, CELL_W - 2, CELL_H - 2, 0xFF55FF55);
            }

            boolean rendered = previewRenderer.render(
                    graphics,
                    id,
                    "entityrese:list:" + id,
                    x + 3,
                    y + 3,
                    CELL_W - 6,
                    CELL_H - 6,
                    guiScale,
                    offsetX,
                    offsetY,
                    hovered
            );
            if (!rendered) {
                graphics.drawCenteredString(font, "?", x + CELL_W / 2, y + CELL_H / 2 - 4, 0xFFFFFFFF);
            }

            if (hovered) {
                deferredTooltip = buildTooltip(id);
            }
        }
    }

    private List<Component> buildTooltip(String id) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(entityName(id)));
        tooltip.add(Component.literal(id));

        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(id);
        if (rule == null) {
            tooltip.add(Component.translatable("gui.kineticentityrese.rule_list.rule.none"));
        } else {
            tooltip.add(Component.translatable("gui.kineticentityrese.rule_list.rule.configured"));
            tooltip.add(Component.translatable(
                    "gui.kineticentityrese.rule_list.rule.threshold",
                    Component.literal(String.valueOf(rule.threshold))
            ));
            tooltip.add(Component.translatable(
                    "gui.kineticentityrese.rule_list.rule.real",
                    switchState(rule.countRealDeath)
            ));
            tooltip.add(Component.translatable(
                    "gui.kineticentityrese.rule_list.rule.prevented",
                    switchState(rule.countPreventedDeath)
            ));
            tooltip.add(Component.translatable(
                    "gui.kineticentityrese.rule_list.rule.cancelled",
                    switchState(rule.countCancelledDeath)
            ));
        }

        tooltip.add(Component.translatable("gui.kineticentityrese.rule_list.left_click_hint"));
        tooltip.add(Component.translatable("gui.kineticentityrese.rule_list.zoom_hint"));
        return tooltip;
    }

    private Component switchState(boolean enabled) {
        return Component.translatable(enabled
                ? "gui.kineticentityrese.rule_list.rule.cancelled.on"
                : "gui.kineticentityrese.rule_list.rule.cancelled.off");
    }

    private String entityName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
        return type == null ? id : type.getDescription().getString();
    }

    private boolean inGrid(double mouseX, double mouseY) {
        return mouseX >= GRID_X && mouseX < GRID_X + GRID_W
                && mouseY >= GRID_Y && mouseY < GRID_Y + GRID_H;
    }

    private int entityIndex(double mouseX, double mouseY) {
        int column = (int) ((mouseX - GRID_X) / CELL_W);
        int row = (int) ((mouseY - GRID_Y) / CELL_H);
        if (column < 0 || column >= COLS || row < 0 || row >= VISIBLE_ROWS) return -1;
        return scroll.offset() * COLS + row * COLS + column;
    }

    private void openRuleEditor(String entityId) {
        if (minecraft == null || initialSyncPending) return;
        minecraft.setScreen(new EntityResetRuleEditScreen(this, entityId));
    }

    public void onServerOperationResult(byte result) {
        rebuildEntityData();
        updateSearch(searchQuery);
        if (result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS) {
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_list.cleared"));
        } else if (result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS) {
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_edit.saved"));
        } else {
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_list.save_failed"));
        }
    }

    public void onRemoteRulesUpdated() {
        initialSyncPending = false;
        rebuildEntityData();
        updateSearch(searchQuery);
    }

    void onRuleSaved() {
        rebuildEntityData();
        updateSearch(searchQuery);
    }

    @Override
    protected boolean universalMouseClicked(double mouseX, double mouseY, int button) {
        if (super.universalMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && scroll.beginDrag(
                mouseX,
                mouseY,
                SCROLL_X,
                GRID_Y,
                SCROLL_W,
                GRID_H,
                18,
                2
        )) {
            return true;
        }

        if (button == 0 && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                openRuleEditor(filteredEntityIds.get(index));
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean universalMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, GRID_Y, GRID_H, 18)
                || super.universalMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean universalMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button)
                || super.universalMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean universalMouseScrolled(double mouseX, double mouseY, double delta) {
        if (Screen.hasControlDown() && inGrid(mouseX, mouseY)) {
            int index = entityIndex(mouseX, mouseY);
            if (index >= 0 && index < filteredEntityIds.size()) {
                String id = filteredEntityIds.get(index);
                previewRenderer.adjustZoom("entityrese:list:" + id, delta);
                return true;
            }
        }
        if (inGrid(mouseX, mouseY) && scroll.scroll(delta)) {
            return true;
        }
        return super.universalMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        if (deferredTooltip != null) {
            graphics.renderComponentTooltip(font, deferredTooltip, mouseX, mouseY);
        }
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public void removed() {
        previewRenderer.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
