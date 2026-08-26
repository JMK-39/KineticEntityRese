package dev.xyat.kineticentityrese;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.config.server.KTServerConfigSpec;
import dev.xyat.kineticentityrese.entityrese.command.EntityReseCommandExtension;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfig;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfigGui;
import dev.xyat.kineticentityrese.entityrese.network.EntityReseRuleNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(KineticEntityRese.MODID)
public final class KineticEntityRese {
    public static final String MODID = "kineticentityrese";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KineticEntityRese() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        KTServerConfigApi.register(KTServerConfigSpec.builder("kineticentityrese:entityrese")
                .booleanValue("enable_entity_reset", () -> EntityReseConfig.enableEntityReset, value -> EntityReseConfig.enableEntityReset = value)
                .intValue("check_radius", () -> EntityReseConfig.checkRadius, value -> EntityReseConfig.checkRadius = value, 0, Integer.MAX_VALUE)
                .onSave(EntityReseConfig::save)
                .build());
        EntityReseRuleNetwork.register();
        EntityReseCommandExtension.install();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EntityReseConfigGui.load());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(EntityReseConfig::load);
    }
}
