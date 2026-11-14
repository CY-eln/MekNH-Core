package c_y.meknh_core.api.multiblock;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块仓室（hatch）的功能类型。
 *
 * <p>结构 JSON 的 {@code 组成} 里用 {@code "part:<类型>"} 声明一个位置必须是仓室，
 * 例如 {@code "H": "part:item_input"}。控制器成型时只会把对应类型的仓室接进多方块，
 * 并据此决定仓室参与“输入”还是“输出”。</p>
 *
 * <p>本枚举只描述功能，不描述等级；等级由具体仓室方块的电压决定
 * （见 {@code MultiblockRegistration} 注册的 {@code lv_item_input_hatch} 等）。</p>
 */
public enum MultiblockPartType {

    /** 物品输入仓：外部只能插入，控制器可以抽取。 */
    ITEM_INPUT("item_input", true, false),
    /** 物品输出仓：外部只能抽取，控制器写入产物。 */
    ITEM_OUTPUT("item_output", false, true),

    /** 流体输入仓：内部是 MEK 逻辑槽，可存流体或化学品；外部只能注入，控制器可以抽取。 */
    FLUID_INPUT("fluid_input", true, false),
    /** 流体输出仓：内部是 MEK 逻辑槽，可存流体或化学品；外部只能抽取，控制器写入产物。 */
    FLUID_OUTPUT("fluid_output", false, true),

    /** 蒸汽输入仓：只接受 {@code mekanism:steam}，蒸汽多方块用它当能源仓。 */
    STEAM_INPUT("steam_input", true, false),

    /** 能量输入仓：外部只能输入 FE，控制器每 tick 扣除。 */
    ENERGY_INPUT("energy_input", true, false),
    /** 能量输出仓：预留给发电机类多方块，当前内核不主动输出。 */
    ENERGY_OUTPUT("energy_output", false, true),

    /**
     * 焦炉仓：焦炉专属仓室，代理焦炉控制器的输入/输出槽与输出罐。
     * 没有 GUI、没有自动搬运，只靠漏斗/管道交互。
     */
    COKE_OVEN_HATCH("coke_oven_hatch", true, true);

    private final String id;
    private final boolean input;
    private final boolean output;

    MultiblockPartType(String id, boolean input, boolean output) {
        this.id = id;
        this.input = input;
        this.output = output;
    }

    /**
     * 是否有右键 GUI；焦炉仓没有。
     */
    public boolean hasGui() {
        return this != COKE_OVEN_HATCH;
    }

    /**
     * JSON 里 {@code part:<id>} 使用的名字。
     */
    public String id() {
        return this.id;
    }

    /**
     * 该仓室是否可以把外部资源送进多方块（物品/流体/化学品/能量）。
     */
    public boolean isInput() {
        return this.input;
    }

    /**
     * 该仓室是否可以把多方块产物送出去。
     */
    public boolean isOutput() {
        return this.output;
    }

    /**
     * 按 JSON 名称查找类型；找不到返回 {@code null}。
     */
    @Nullable
    public static MultiblockPartType byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (MultiblockPartType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
