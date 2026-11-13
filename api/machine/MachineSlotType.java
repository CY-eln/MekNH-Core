package c_y.meknh_core.api.machine;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * 机器槽位类型（{@code 批量注册机器.txt} 注解 1）。
 *
 * <table border="1">
 *     <caption>槽位类型</caption>
 *     <tr><th>JSON</th><th>说明</th></tr>
 *     <tr><td>{@code item}</td><td>物品槽</td></tr>
 *     <tr><td>{@code fluid}</td><td>流体罐</td></tr>
 *     <tr><td>{@code gas}</td><td>Mekanism 化学品罐（Mekanism 里统一叫化学品）</td></tr>
 *     <tr><td>{@code mek}</td><td>复合槽：同时支持流体与化学品输入</td></tr>
 * </table>
 */
public enum MachineSlotType implements StringRepresentable {

    ITEM("item", true, false, false),
    FLUID("fluid", false, true, false),
    GAS("gas", false, false, true),
    MEK("mek", false, true, true);

    private final String name;
    private final boolean item;
    private final boolean fluid;
    private final boolean chemical;

    MachineSlotType(String name, boolean item, boolean fluid, boolean chemical) {
        this.name = name;
        this.item = item;
        this.fluid = fluid;
        this.chemical = chemical;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean isItem() {
        return this.item;
    }

    public boolean isFluid() {
        return this.fluid;
    }

    public boolean isChemical() {
        return this.chemical;
    }

    /**
     * 按 JSON 名称查找，未找到返回 {@code null}。
     */
    public static MachineSlotType find(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (MachineSlotType type : values()) {
            if (type.name.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
