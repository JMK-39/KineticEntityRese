package dev.xyat.kineticentityrese.entityrese.command;

import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.kineticentityrese.KineticEntityRese;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfig;
import net.minecraft.commands.CommandSourceStack;

public final class EntityReseCommandExtension implements KTCommandExtension {
    private EntityReseCommandExtension() {
    }

    public static void install() {
        KTCommandApi.register(KineticEntityRese.MODID, new EntityReseCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        EntityReseConfig.load();
    }
}
