package c_y.meknh_core.decor.description;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 机器描述与提示文本。
 */
public final class MachineDescriptions {

    private MachineDescriptions() {
    }

    public static String descriptionKey(ResourceLocation machineId) {
        return "machine." + MekNHCore.MODID + "." + machineId.getPath() + ".description";
    }

    public static Component description(ResourceLocation machineId) {
        return Component.translatable(descriptionKey(machineId));
    }

    /**
     * 详细页里的等级文本：LV/MV/HV/EV/IV，蒸汽为 LP/HP。
     */
    public static String tierText(MachineLevel level) {
        if (level.isSteam()) {
            return level.idPrefix().startsWith("hp") ? "HP" : "LP";
        }
        return level.voltage().getSerializedName().toUpperCase(java.util.Locale.ROOT);
    }
}
