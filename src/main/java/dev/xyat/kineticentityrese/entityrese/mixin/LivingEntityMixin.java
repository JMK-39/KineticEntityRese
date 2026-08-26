package dev.xyat.kineticentityrese.entityrese.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.xyat.kineticentityrese.entityrese.event.EntityResetHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyExpressionValue(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;checkTotemDeathProtection(Lnet/minecraft/world/damagesource/DamageSource;)Z"
            )
    )
    private boolean kineticentityrese$captureFinalTotemProtectionResult(
            boolean original,
            DamageSource source
    ) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (original && self instanceof Player player) {
            EntityResetHandler.onTotemProtectionTriggered(player);
        }

        return original;
    }
}