package dev.xyat.kineticentityrese.entityrese.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import dev.xyat.kineticentityrese.KineticEntityRese;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityReseConfig {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("kineticcore/entity_rese.toml");
    private static CommentedFileConfig configData;

    public static final class EntityRule {
        public final int threshold;
        public final boolean countRealDeath;
        public final boolean countPreventedDeath;
        public final boolean countCancelledDeath;

        public EntityRule(
                int threshold,
                boolean countRealDeath,
                boolean countPreventedDeath,
                boolean countCancelledDeath
        ) {
            this.threshold = threshold;
            this.countRealDeath = countRealDeath;
            this.countPreventedDeath = countPreventedDeath;
            this.countCancelledDeath = countCancelledDeath;
        }
    }

    public static final Map<String, EntityRule> ENTITY_RULES_CACHE = new ConcurrentHashMap<>();
    public static boolean enableEntityReset = true;
    public static int checkRadius = 64;
    public static final List<String> configRulesRaw = new ArrayList<>();

    private EntityReseConfig() {
    }

    public static synchronized void load() {
        closeConfig();
        try {
            configData = CommentedFileConfig.builder(CONFIG_PATH)
                    .sync()
                    .preserveInsertionOrder()
                    .writingMode(WritingMode.REPLACE)
                    .build();
            configData.load();
            setupConfig();
            readValues();
            configData.set("general.rules", new ArrayList<>(configRulesRaw));
            configData.save();
        } catch (Exception exception) {
            KineticEntityRese.LOGGER.error("Failed to load entity reset config", exception);
            closeConfig();
        }
    }

    private static void closeConfig() {
        if (configData == null) return;
        try {
            configData.close();
        } catch (Throwable ignored) {
        }
        configData = null;
    }

    private static void setupConfig() {
        configData.setComment("general", """
                生物状态重置机制配置
                Entity Reset Mechanics Configuration""");

        define("general.enable", true, """
                是否开启生物重置系统。
                Whether to enable the Entity Reset system.""");

        define("general.radius", 64, """
                检测玩家死亡的半径 (方块)。
                Detection radius (blocks).""");

        define("general.rules", Arrays.asList(
                "minecraft:warden;1;true;true;true",
                "minecraft:wither;1;true;true;true",
                "minecraft:ender_dragon;3;true;false;false"
        ), """
                生物重置规则。格式: "实体ID;死亡阈值;正常死亡;免死触发;取消死亡"
                Entity Reset Rules. Format: "EntityID;Threshold;RealDeath;PreventedDeath;CancelledDeath"
                三个死亡条件均为独立开关，可分别设为 true 或 false。
                The three death conditions are independent switches and can each be true or false.
                旧版两段格式 "实体ID;死亡阈值" 与三段格式 "实体ID;死亡阈值;是否计入免死" 仍可读取并自动转换。
                Legacy two-field and three-field rules remain readable and are converted automatically.
                格式错误、死亡阈值无效或当前不存在的实体 ID 会在读取时自动从配置中清理。
                Malformed rules, invalid thresholds, and entity IDs that do not currently exist are removed automatically when loaded.""");
    }

    private static void define(String path, Object def, String comment) {
        if (!configData.contains(path)) {
            configData.set(path, def);
        }
        configData.setComment(path, " " + comment.trim());
    }

    private static void readValues() {
        if (configData == null) return;
        enableEntityReset = configData.getOrElse("general.enable", true);
        checkRadius = Math.max(0, configData.getOrElse("general.radius", 64));

        List<String> values = new ArrayList<>();
        List<?> rawList = configData.get("general.rules");
        if (rawList != null) {
            for (Object obj : rawList) {
                values.add(String.valueOf(obj));
            }
        }
        replaceRulesInMemory(values);
    }

    public static synchronized void applyRemoteRules(List<String> values) {
        replaceRulesInMemory(values == null ? List.of() : values);
    }

    private static void replaceRulesInMemory(List<String> values) {
        configRulesRaw.clear();
        ENTITY_RULES_CACHE.clear();

        for (String raw : values) {
            EntityRuleCodec.ParsedRule parsed = EntityRuleCodec.parse(raw);
            if (parsed == null) {
                KineticEntityRese.LOGGER.warn("Removed invalid Entity Rule: {}", raw);
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(parsed.entityId());
            if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                KineticEntityRese.LOGGER.warn("Removed Entity Rule with unknown target: {}", parsed.entityId());
                continue;
            }

            EntityRule rule = new EntityRule(
                    parsed.threshold(),
                    parsed.countRealDeath(),
                    parsed.countPreventedDeath(),
                    parsed.countCancelledDeath()
            );
            String serialized = EntityRuleCodec.serialize(new EntityRuleCodec.ParsedRule(
                    id.toString(),
                    rule.threshold,
                    rule.countRealDeath,
                    rule.countPreventedDeath,
                    rule.countCancelledDeath
            ));
            configRulesRaw.add(serialized);
            ENTITY_RULES_CACHE.put(id.toString(), rule);
        }
    }

    public static boolean areValidRules(List<String> values) {
        if (values == null) return false;
        for (String raw : values) {
            EntityRuleCodec.ParsedRule parsed = EntityRuleCodec.parse(raw);
            if (parsed == null) return false;
            ResourceLocation id = ResourceLocation.tryParse(parsed.entityId());
            if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) return false;
        }
        return true;
    }

    public static EntityRule getRule(String entityId) {
        if (entityId == null) return null;
        return ENTITY_RULES_CACHE.get(entityId.trim());
    }

    public static boolean hasRule(String entityId) {
        return getRule(entityId) != null;
    }

    public static synchronized List<String> snapshotRules() {
        return new ArrayList<>(configRulesRaw);
    }

    public static synchronized boolean saveRuleAuthoritative(
            String entityId,
            int threshold,
            boolean countRealDeath,
            boolean countPreventedDeath,
            boolean countCancelledDeath
    ) {
        List<String> before = snapshotRules();
        try {
            upsertRule(
                    entityId,
                    threshold,
                    countRealDeath,
                    countPreventedDeath,
                    countCancelledDeath
            );
            save();
            return true;
        } catch (Throwable throwable) {
            replaceRulesInMemory(before);
            if (configData != null) configData.set("general.rules", new ArrayList<>(before));
            KineticEntityRese.LOGGER.error("Failed to save entity reset rule for {}", entityId, throwable);
            return false;
        }
    }

    public static synchronized boolean removeRuleAuthoritative(String entityId) {
        List<String> before = snapshotRules();
        try {
            removeRule(entityId);
            save();
            return true;
        } catch (Throwable throwable) {
            replaceRulesInMemory(before);
            if (configData != null) configData.set("general.rules", new ArrayList<>(before));
            KineticEntityRese.LOGGER.error("Failed to remove entity reset rule for {}", entityId, throwable);
            return false;
        }
    }

    public static void upsertRule(
            String entityId,
            int threshold,
            boolean countRealDeath,
            boolean countPreventedDeath,
            boolean countCancelledDeath
    ) {
        String normalized = normalizeEntityId(entityId);
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least 1");
        }

        String serialized = EntityRuleCodec.serialize(new EntityRuleCodec.ParsedRule(
                normalized,
                threshold,
                countRealDeath,
                countPreventedDeath,
                countCancelledDeath
        ));
        int existingIndex = findRuleIndex(normalized);
        if (existingIndex >= 0) {
            configRulesRaw.set(existingIndex, serialized);
        } else {
            configRulesRaw.add(serialized);
        }
        rebuildCacheFromRaw();
    }

    public static void removeRule(String entityId) {
        if (entityId == null || entityId.isBlank()) return;
        String normalized = entityId.trim();
        configRulesRaw.removeIf(raw -> ruleEntityId(raw).equals(normalized));
        rebuildCacheFromRaw();
    }

    private static void rebuildCacheFromRaw() {
        List<String> current = new ArrayList<>(configRulesRaw);
        replaceRulesInMemory(current);
    }

    private static String normalizeEntityId(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entity id is blank");
        }
        ResourceLocation id = ResourceLocation.tryParse(entityId.trim());
        if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            throw new IllegalArgumentException("unknown entity id: " + entityId);
        }
        return id.toString();
    }

    private static int findRuleIndex(String entityId) {
        for (int i = 0; i < configRulesRaw.size(); i++) {
            if (ruleEntityId(configRulesRaw.get(i)).equals(entityId)) {
                return i;
            }
        }
        return -1;
    }

    private static String ruleEntityId(String raw) {
        EntityRuleCodec.ParsedRule parsed = EntityRuleCodec.parse(raw);
        return parsed == null ? "" : parsed.entityId();
    }

    public static synchronized void save() {
        if (configData == null) {
            throw new IllegalStateException("Entity reset config is not loaded");
        }
        configData.set("general.enable", enableEntityReset);
        configData.set("general.radius", checkRadius);
        configData.set("general.rules", new ArrayList<>(configRulesRaw));
        configData.save();
        readValues();
    }
}
