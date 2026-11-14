package c_y.meknh_core.client;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.api.generator.boiler.BoilerRegistration;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.common.generator.boiler.BoilerScreen;
import c_y.meknh_core.common.gui.screen.HatchScreen;
import c_y.meknh_core.common.gui.screen.MachineScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

/**
 * 客户端专用事件。
 */
@EventBusSubscriber(modid = MekNHCore.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterSpriteSourceTypes(RegisterSpriteSourceTypesEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(MekNHCore.MODID, "chemicals"), ChemicalAtlasSource.TYPE);
    }

    /**
     * 机器容器对应的界面（原版 {@code AbstractContainerScreen} 机制，与 Mekanism 一致）。
     */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(MachineRegistration.MACHINE_MENU.get(), MachineScreen::create);
        event.register(BoilerRegistration.BOILER_MENU.get(), BoilerScreen::new);
        event.register(MultiblockRegistration.HATCH_MENU.get(), HatchScreen::new);
        event.register(c_y.meknh_core.common.coke.CokeOvenRegistration.COKE_OVEN_MENU.get(),
                c_y.meknh_core.common.coke.CokeOvenScreen::new);
    }
}
