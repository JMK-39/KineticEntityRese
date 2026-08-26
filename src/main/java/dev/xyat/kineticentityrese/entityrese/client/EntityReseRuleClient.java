package dev.xyat.kineticentityrese.entityrese.client;

import dev.xyat.kineticentityrese.entityrese.client.gui.EntityResetRuleEditScreen;
import dev.xyat.kineticentityrese.entityrese.client.gui.EntityResetRuleListScreen;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class EntityReseRuleClient {
    private EntityReseRuleClient() {
    }

    public static void handleSnapshot(List<String> rules) {
        EntityReseConfig.applyRemoteRules(rules);
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof EntityResetRuleListScreen listScreen) {
            listScreen.onRemoteRulesUpdated();
        }
    }

    public static void handleOperationResult(byte result, List<String> rules) {
        EntityReseConfig.applyRemoteRules(rules);
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof EntityResetRuleEditScreen editScreen) {
            editScreen.onServerOperationResult(result);
        } else if (screen instanceof EntityResetRuleListScreen listScreen) {
            listScreen.onServerOperationResult(result);
        }
    }
}
