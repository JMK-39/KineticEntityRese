package dev.xyat.kineticentityrese.entityrese.event;

import dev.xyat.kineticentityrese.KineticEntityRese;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = KineticEntityRese.MODID)
public final class EntityResetHandler {
    private static final String NBT_SNAPSHOT = "kineticentityrese_snapshot";
    private static final String NBT_DEATH_COUNT = "kineticentityrese_death_count";
    private static final String NBT_IS_TRACKING = "kineticentityrese_tracking";
    private static final long COOLDOWN_MS = 1000L;

    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, List<LivingEntity>> deathSnapshot = new ConcurrentHashMap<>();

    private EntityResetHandler() {
    }

    private enum DeathTrigger {
        REAL_DEATH,
        PREVENTED_DEATH,
        CANCELLED_DEATH
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (event.getEntity().level().isClientSide) return;
        if (event.getAmount() <= 0) return;

        LivingEntity target = event.getEntity();
        ResourceLocation targetId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (targetId == null || !EntityReseConfig.ENTITY_RULES_CACHE.containsKey(targetId.toString())) return;
        if (!isPlayerOrMinion(event.getSource())) return;

        CompoundTag data = target.getPersistentData();
        if (!data.contains(NBT_IS_TRACKING)) {
            saveSnapshot(target);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeathPre(LivingDeathEvent event) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        List<LivingEntity> trackedBosses = getNearbyTrackedBosses(player);
        if (!trackedBosses.isEmpty()) {
            deathSnapshot.put(player.getUUID(), trackedBosses);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerDeathPost(LivingDeathEvent event) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        List<LivingEntity> bosses = deathSnapshot.remove(player.getUUID());
        if (bosses == null || bosses.isEmpty()) return;

        processDeathLogic(
                player,
                bosses,
                event.isCanceled() ? DeathTrigger.CANCELLED_DEATH : DeathTrigger.REAL_DEATH
        );
    }

    public static void onTotemProtectionTriggered(Player player) {
        if (!EntityReseConfig.enableEntityReset) return;
        if (player.level().isClientSide) return;

        List<LivingEntity> trackedBosses = getNearbyTrackedBosses(player);
        if (trackedBosses.isEmpty()) return;

        processDeathLogic(player, trackedBosses, DeathTrigger.PREVENTED_DEATH);
    }

    private static List<LivingEntity> getNearbyTrackedBosses(Player player) {
        double radius = EntityReseConfig.checkRadius;
        AABB area = player.getBoundingBox().inflate(radius);
        List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(LivingEntity.class, area);
        List<LivingEntity> trackedBosses = new ArrayList<>();

        for (LivingEntity entity : nearbyEntities) {
            if (entity != player && entity.getPersistentData().contains(NBT_IS_TRACKING)) {
                trackedBosses.add(entity);
            }
        }
        return trackedBosses;
    }

    private static void processDeathLogic(Player player, List<LivingEntity> bosses, DeathTrigger trigger) {
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUUID(), 0L);
        if (now - last < COOLDOWN_MS) return;

        boolean countedAny = false;
        for (LivingEntity boss : bosses) {
            if (boss == null || !boss.isAlive() || boss.isDeadOrDying()) continue;

            ResourceLocation bossId = ForgeRegistries.ENTITY_TYPES.getKey(boss.getType());
            if (bossId == null) continue;

            EntityReseConfig.EntityRule rule = EntityReseConfig.getRule(bossId.toString());
            if (rule == null || !shouldCount(rule, trigger)) continue;

            CompoundTag data = boss.getPersistentData();
            int currentCount = data.getInt(NBT_DEATH_COUNT) + 1;
            data.putInt(NBT_DEATH_COUNT, currentCount);
            countedAny = true;

            if (currentCount >= rule.threshold) {
                resetBossState(boss);
                data.putInt(NBT_DEATH_COUNT, 0);
            }
        }

        if (countedAny) {
            cooldowns.put(player.getUUID(), now);
        }
    }

    private static boolean shouldCount(EntityReseConfig.EntityRule rule, DeathTrigger trigger) {
        return switch (trigger) {
            case REAL_DEATH -> rule.countRealDeath;
            case PREVENTED_DEATH -> rule.countPreventedDeath;
            case CANCELLED_DEATH -> rule.countCancelledDeath;
        };
    }

    private static void saveSnapshot(LivingEntity entity) {
        CompoundTag snapshot = new CompoundTag();
        entity.saveWithoutId(snapshot);

        CompoundTag data = entity.getPersistentData();
        data.put(NBT_SNAPSHOT, snapshot);
        data.putInt(NBT_DEATH_COUNT, 0);
        data.putBoolean(NBT_IS_TRACKING, true);
    }

    private static void resetBossState(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBT_SNAPSHOT)) return;

        CompoundTag snapshot = data.getCompound(NBT_SNAPSHOT);
        ListTag currentPos = createDoubleList(entity.getX(), entity.getY(), entity.getZ());
        ListTag currentRot = createFloatList(entity.getYRot(), entity.getXRot());
        ListTag currentMotion = createDoubleList(
                entity.getDeltaMovement().x,
                entity.getDeltaMovement().y,
                entity.getDeltaMovement().z
        );

        CompoundTag applyTag = snapshot.copy();
        applyTag.put("Pos", currentPos);
        applyTag.put("Rotation", currentRot);
        applyTag.put("Motion", currentMotion);
        applyTag.putFloat("FallDistance", entity.fallDistance);
        applyTag.putUUID("UUID", entity.getUUID());

        entity.load(applyTag);
        entity.setHealth(entity.getMaxHealth());

        if (entity.level() instanceof ServerLevel serverLevel) {
            Component message = Component.translatable(
                    "msg.kineticentityrese.entity_reset.broadcast",
                    entity.getDisplayName()
            );
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static boolean isPlayerOrMinion(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player) return true;
        if (attacker instanceof OwnableEntity ownable) {
            return ownable.getOwner() instanceof Player;
        }
        return false;
    }

    private static ListTag createDoubleList(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    private static ListTag createFloatList(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }
}
