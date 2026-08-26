package dev.xyat.kineticentityrese.entityrese.client.gui;

import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.entity.EntityPreviewRenderer;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfig;
import dev.xyat.kineticentityrese.entityrese.network.EntityReseRuleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public final class EntityResetRuleEditScreen extends ScaledScreen {
    private static final int PREVIEW_X = 46;
    private static final int PREVIEW_Y = 62;
    private static final int PREVIEW_W = 224;
    private static final int PREVIEW_H = 220;
    private static final int EDIT_X = 304;
    private static final int EDIT_Y = 62;
    private static final int EDIT_W = 260;
    private static final int EDIT_H = 220;

    private final EntityResetRuleListScreen parent;
    private final String entityId;
    private final EntityPreviewRenderer previewRenderer = new EntityPreviewRenderer();

    private EditBox thresholdBox;
    private Button realDeathButton;
    private Button preventedDeathButton;
    private Button cancelledDeathButton;
    private Button clearButton;
    private Button saveButton;
    private boolean countRealDeath;
    private boolean countPreventedDeath;
    private boolean countCancelledDeath;
    private boolean waitingForServer;

    public EntityResetRuleEditScreen(EntityResetRuleListScreen parent, String entityId) {
        super(Component.translatable("gui.kineticentityrese.rule_edit.title"));
        this.parent = parent;
        this.entityId = entityId;

        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(entityId);
        if (rule == null) {
            countRealDeath = true;
            countPreventedDeath = false;
            countCancelledDeath = false;
        } else {
            countRealDeath = rule.countRealDeath;
            countPreventedDeath = rule.countPreventedDeath;
            countCancelledDeath = rule.countCancelledDeath;
        }
        configureResponsiveCanvas(640F, 360F, 6);
    }

    @Override
    protected void initScaled() {
        EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(entityId);
        int threshold = rule == null ? 1 : Math.max(1, rule.threshold);

        thresholdBox = addRenderableWidget(new EditBox(
                font,
                338,
                108,
                192,
                20,
                Component.translatable("gui.kineticentityrese.rule_edit.threshold")
        ));
        thresholdBox.setMaxLength(9);
        thresholdBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        thresholdBox.setValue(String.valueOf(threshold));

        realDeathButton = addRenderableWidget(Button.builder(
                        realDeathMessage(),
                        button -> {
                            countRealDeath = !countRealDeath;
                            button.setMessage(realDeathMessage());
                        })
                .bounds(326, 146, 216, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticentityrese.rule_edit.count_real.tooltip")))
                .build());

        preventedDeathButton = addRenderableWidget(Button.builder(
                        preventedDeathMessage(),
                        button -> {
                            countPreventedDeath = !countPreventedDeath;
                            button.setMessage(preventedDeathMessage());
                        })
                .bounds(326, 174, 216, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticentityrese.rule_edit.count_prevented.tooltip")))
                .build());

        cancelledDeathButton = addRenderableWidget(Button.builder(
                        cancelledDeathMessage(),
                        button -> {
                            countCancelledDeath = !countCancelledDeath;
                            button.setMessage(cancelledDeathMessage());
                        })
                .bounds(326, 202, 216, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.kineticentityrese.rule_edit.count_cancelled.tooltip")))
                .build());

        clearButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticentityrese.rule_list.clear"),
                        button -> clearRule())
                .bounds(326, 244, 68, 20)
                .build());

        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticentityrese.rule_edit.save"),
                        button -> saveRule())
                .bounds(400, 244, 68, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.kineticentityrese.rule_edit.back"),
                        button -> closeToParent())
                .bounds(474, 244, 68, 20)
                .build());

        updateControlsEnabled();
    }

    private Component realDeathMessage() {
        return Component.translatable(
                countRealDeath
                        ? "gui.kineticentityrese.rule_edit.count_real.on"
                        : "gui.kineticentityrese.rule_edit.count_real.off"
        );
    }

    private Component preventedDeathMessage() {
        return Component.translatable(
                countPreventedDeath
                        ? "gui.kineticentityrese.rule_edit.count_prevented.on"
                        : "gui.kineticentityrese.rule_edit.count_prevented.off"
        );
    }

    private Component cancelledDeathMessage() {
        return Component.translatable(
                countCancelledDeath
                        ? "gui.kineticentityrese.rule_edit.count_cancelled.on"
                        : "gui.kineticentityrese.rule_edit.count_cancelled.off"
        );
    }

    private void saveRule() {
        if (waitingForServer) return;

        int threshold;
        try {
            threshold = Integer.parseInt(thresholdBox.getValue().trim());
        } catch (Exception ignored) {
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_edit.invalid_threshold"));
            return;
        }

        if (threshold < 1) {
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_edit.invalid_threshold"));
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_list.save_failed"));
            return;
        }

        waitingForServer = true;
        updateControlsEnabled();
        EntityReseRuleNetwork.saveRule(
                entityId,
                threshold,
                countRealDeath,
                countPreventedDeath,
                countCancelledDeath
        );
    }

    private void clearRule() {
        if (waitingForServer || !EntityReseConfig.hasRule(entityId)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_list.save_failed"));
            return;
        }

        waitingForServer = true;
        updateControlsEnabled();
        EntityReseRuleNetwork.removeRule(entityId);
    }

    public void onServerOperationResult(byte result) {
        if (!waitingForServer) return;
        waitingForServer = false;
        updateControlsEnabled();

        if (result == EntityReseRuleNetwork.RESULT_SAVE_SUCCESS) {
            parent.onRuleSaved();
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_edit.saved"));
            closeToParent();
            return;
        }

        if (result == EntityReseRuleNetwork.RESULT_REMOVE_SUCCESS) {
            parent.onRuleSaved();
            GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_list.cleared"));
            closeToParent();
            return;
        }

        GuiToastUtil.showToast(Component.translatable("msg.kineticentityrese.rule_list.save_failed"));
    }

    private void updateControlsEnabled() {
        boolean enabled = !waitingForServer;
        if (thresholdBox != null) thresholdBox.active = enabled;
        if (realDeathButton != null) realDeathButton.active = enabled;
        if (preventedDeathButton != null) preventedDeathButton.active = enabled;
        if (cancelledDeathButton != null) cancelledDeathButton.active = enabled;
        if (clearButton != null) clearButton.active = enabled && EntityReseConfig.hasRule(entityId);
        if (saveButton != null) saveButton.active = enabled;
    }

    @Override
    protected void renderScaledBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, vWidth, vHeight, 0xFF171717, 0xFF0E0E0E);
        GuiRenderUtil.drawStandardPanel(graphics, 24, 18, 592, 324);
        AdaptiveItemGridRenderer.drawGrid(graphics, PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H, 6);
        graphics.renderOutline(PREVIEW_X, PREVIEW_Y, PREVIEW_W, PREVIEW_H, 0xFFFFFFFF);
        GuiRenderUtil.drawDarkPanel(graphics, EDIT_X, EDIT_Y, EDIT_W, EDIT_H);

        graphics.drawCenteredString(font, title, vWidth / 2, 30, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal(entityName()), PREVIEW_X + PREVIEW_W / 2, 46, 0xFFFFFFFF);

        boolean hovered = inPreview(mouseX, mouseY);
        previewRenderer.render(
                graphics,
                entityId,
                "entityrese:edit:" + entityId,
                PREVIEW_X + 8,
                PREVIEW_Y + 8,
                PREVIEW_W - 16,
                PREVIEW_H - 16,
                guiScale,
                offsetX,
                offsetY,
                hovered
        );

        graphics.drawString(
                font,
                Component.translatable("gui.kineticentityrese.rule_edit.entity_id", Component.literal(entityId)),
                318,
                78,
                0xFFFFFFFF,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("gui.kineticentityrese.rule_edit.threshold"),
                318,
                94,
                0xFFFFFFFF,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("gui.kineticentityrese.rule_edit.zoom_hint"),
                PREVIEW_X + 8,
                PREVIEW_Y + PREVIEW_H - 14,
                0xFFFFFFFF,
                false
        );
    }

    private boolean inPreview(double mouseX, double mouseY) {
        return mouseX >= PREVIEW_X && mouseX < PREVIEW_X + PREVIEW_W
                && mouseY >= PREVIEW_Y && mouseY < PREVIEW_Y + PREVIEW_H;
    }

    private String entityName() {
        ResourceLocation location = ResourceLocation.tryParse(entityId);
        EntityType<?> type = location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
        return type == null ? entityId : type.getDescription().getString();
    }

    @Override
    protected boolean universalMouseScrolled(double mouseX, double mouseY, double delta) {
        if (inPreview(mouseX, mouseY)) {
            previewRenderer.adjustZoom("entityrese:edit:" + entityId, delta);
            return true;
        }
        return super.universalMouseScrolled(mouseX, mouseY, delta);
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
