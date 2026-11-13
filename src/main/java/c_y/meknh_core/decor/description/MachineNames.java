package c_y.meknh_core.decor.description;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 机器的名字：内部 ID 与显示名的拼装规则（{@code 批量注册机器.txt}）。
 *
 * <p>内部 ID：蒸汽机器是 {@code lp_steam_<机器ID>} / {@code hp_steam_<机器ID>}，
 * 电力机器是 {@code <电压>_<机器ID>}。</p>
 *
 * <p>显示名：蒸汽是「低/高压蒸汽 + 机器名」；
 * 电力是「电压内部名称映射 + 机器名 + 附加项目」。电压映射表：</p>
 *
 * <table border="1">
 *     <caption>电压名称映射</caption>
 *     <tr><th>电压</th><th>名称</th><th>附加项目</th></tr>
 *     <tr><td>LV</td><td>基础</td><td>-</td></tr>
 *     <tr><td>MV</td><td>进阶</td><td>-</td></tr>
 *     <tr><td>HV</td><td>进阶</td><td>II</td></tr>
 *     <tr><td>EV</td><td>进阶</td><td>III</td></tr>
 *     <tr><td>IV</td><td>精英</td><td>-</td></tr>
 * </table>
 */
public final class MachineNames {

    private MachineNames() {
    }

    /**
     * 机器版本在注册表中的路径，例如 {@code lv_alloy_smelter}。
     */
    public static String internalPath(ResourceLocation machineId, MachineLevel level) {
        return level.idPrefix() + "_" + machineId.getPath();
    }

    /**
     * 机器版本的注册名，命名空间固定为本模组。
     */
    public static ResourceLocation internalId(ResourceLocation machineId, MachineLevel level) {
        return ResourceLocation.fromNamespaceAndPath(MekNHCore.MODID, internalPath(machineId, level));
    }

    /**
     * 机器名的翻译键，例如 {@code machine.meknh_core.alloy_smelter}。
     */
    public static String machineKey(ResourceLocation machineId) {
        return "machine." + MekNHCore.MODID + "." + machineId.getPath();
    }

    /**
     * 电压等级名的翻译键，例如 {@code machine.meknh_core.tier.lv}。
     */
    public static String tierKey(MachineLevel level) {
        return "machine." + MekNHCore.MODID + ".tier." + level.idPrefix();
    }

    /**
     * 电压附加项目（HV 的 {@code II}、EV 的 {@code III}），没有则为空串。
     */
    public static String suffix(MachineLevel level) {
        if (level.isSteam()) {
            return "";
        }
        return switch (level.voltage()) {
            case HV -> "II";
            case EV -> "III";
            default -> "";
        };
    }

    /**
     * 电压等级显示名，例如「基础」「进阶 II」「低压蒸汽」。
     */
    public static MutableComponent tierName(MachineLevel level) {
        MutableComponent name = Component.translatable(tierKey(level));
        String suffix = suffix(level);
        if (!suffix.isEmpty()) {
            name.append(Component.literal(" " + suffix));
        }
        return name;
    }

    /**
     * GT 风格的电压前缀，例如 {@code §6HV§r}、{@code §8ULV§r}。
     *
     * <p>只用于多方块仓室：物品是「总线」，流体/化学品是「仓」，与 GT 的命名保持一致。</p>
     */
    public static MutableComponent gtTierName(MachineLevel level) {
        if (level.isSteam()) {
            return Component.literal("§8ULV§r");
        }
        String color = switch (level.voltage()) {
            case LV -> "§7";
            case MV -> "§b";
            case HV -> "§6";
            case EV -> "§5";
            case IV -> "§9";
            default -> "§8";
        };
        return Component.literal(color + level.voltage().name() + "§r");
    }

    /**
     * 机器完整显示名，例如「基础合金炉」「进阶合金炉 II」「低压蒸汽合金炉」。
     */
    public static MutableComponent displayName(ResourceLocation machineId, MachineLevel level) {
        String machineKey = machineKey(machineId);
        if (level.isSteam() && machineId.getPath().equals("electric_stove")) {
            machineKey = "machine." + MekNHCore.MODID + ".electric_stove_steam";
        }
        MutableComponent name = Component.empty()
                .append(Component.translatable(tierKey(level)))
                .append(Component.translatable(machineKey));
        String suffix = suffix(level);
        if (!suffix.isEmpty()) {
            name.append(Component.literal(" " + suffix));
        }
        return name;
    }

    /**
     * 电压的纯文本展示，例如 {@code 512V}。
     */
    public static String voltageText(MachineLevel level) {
        VoltageTier tier = level.voltage();
        return tier.voltage() + "V";
    }
}
