package c_y.meknh_core.register;

import c_y.meknh_core.MekNHCore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 给 AE2 创造存储元件挂上唱片组件。
 */
@EventBusSubscriber(modid = MekNHCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class Ae2CreativeCellMusicDisc {

    public static final ResourceKey<JukeboxSong> SONG = ResourceKey.create(
            Registries.JUKEBOX_SONG, MekNHCore.rl("desultory_signals"));

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MekNHCore.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DESULTORY_SIGNALS =
            SOUNDS.register("music_disc.desultory_signals",
                    () -> SoundEvent.createVariableRangeEvent(MekNHCore.rl("music_disc.desultory_signals")));

    private static final ResourceLocation AE2_CREATIVE_CELL =
            ResourceLocation.fromNamespaceAndPath("ae2", "creative_storage_cell");

    private Ae2CreativeCellMusicDisc() {
    }

    public static void initialize(IEventBus bus) {
        SOUNDS.register(bus);
    }

    @SubscribeEvent
    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        Item item = BuiltInRegistries.ITEM.get(AE2_CREATIVE_CELL);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            MekNHCore.LOGGER.warn("AE2 creative storage cell is missing; music disc component not applied");
            return;
        }
        event.modify(item, builder -> builder.set(DataComponents.JUKEBOX_PLAYABLE,
                new JukeboxPlayable(new EitherHolder<>(SONG), true)));
    }
}
