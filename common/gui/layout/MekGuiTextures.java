package c_y.meknh_core.common.gui.layout;

import net.minecraft.resources.ResourceLocation;

/**
 * 直接引用的 Mekanism GUI 资源。
 *
 * <p>归属声明见项目根目录 {@code LICENSE-MEKANISM}。这些路径与 Mekanism
 * 的 {@code MekanismUtils.ResourceType} 一致：Mekanism 的 GUI 贴图放在
 * {@code assets/mekanism/gui/} 下，而不是 {@code assets/mekanism/textures/gui/}。</p>
 */
public final class MekGuiTextures {

    /** 熔炉/电力机器进度条：25×27，三行 25×9。 */
    public static final ResourceLocation PROGRESS_BAR = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/progress/bar.png");
    /** 横向化学品/蒸汽条边框。 */
    public static final ResourceLocation CHEMICAL_BAR = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/bar/base.png");
    public static final ResourceLocation HORIZONTAL_RATE = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/bar/horizontal_rate.png");
    public static final ResourceLocation DUMP = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/dump.png");
    public static final ResourceLocation SLOT_NORMAL = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/normal.png");
    public static final ResourceLocation SLOT_INPUT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/input.png");
    public static final ResourceLocation SLOT_INPUT_2 = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/input_2.png");
    public static final ResourceLocation SLOT_OUTPUT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/output.png");
    public static final ResourceLocation SLOT_OUTPUT_WIDE = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/slot/output_wide.png");
    public static final ResourceLocation GAUGE_SHORT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/gauge/small.png");
    public static final ResourceLocation FLAME = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/progress/flame.png");
    public static final ResourceLocation LARGE_RIGHT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/progress/large_right.png");
    public static final ResourceLocation SMALL_RIGHT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/progress/small_right.png");

    /** Mek 机器标签页：左右 holder、按钮底图与图标。 */
    public static final ResourceLocation TAB_HOLDER_LEFT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/holder_left.png");
    public static final ResourceLocation TAB_HOLDER_RIGHT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/holder_right.png");
    public static final ResourceLocation TAB_BUTTON = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/button.png");
    public static final ResourceLocation TAB_CONFIG = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/configuration.png");
    public static final ResourceLocation TAB_TRANSPORTER = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/transporter_config.png");
    public static final ResourceLocation TAB_SECURITY_PUBLIC = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/public.png");
    public static final ResourceLocation TAB_SECURITY_PRIVATE = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/private.png");
    public static final ResourceLocation TAB_REDSTONE_DISABLED = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/redstone_control_disabled.png");
    public static final ResourceLocation TAB_REDSTONE_HIGH = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/redstone_control_high.png");
    public static final ResourceLocation TAB_REDSTONE_LOW = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/redstone_control_low.png");
    public static final ResourceLocation TAB_ENERGY = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/tabs/energy_info_fe.png");
    public static final ResourceLocation TAB_WARNING_LEFT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/tabs/warning_info_left.png");
    public static final ResourceLocation WARNING = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/warning.png");
    public static final ResourceLocation CONFIG_ITEM = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/items.png");
    public static final ResourceLocation CONFIG_FLUID = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/fluids.png");
    public static final ResourceLocation CONFIG_CHEMICAL = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/chemicals.png");
    public static final ResourceLocation CONFIG_ENERGY = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/energy.png");
    public static final ResourceLocation BUTTON_CLEAR_SIDES = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/button/clear_sides.png");
    public static final ResourceLocation BUTTON_AUTO_EJECT = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/button/auto_eject.png");
    public static final ResourceLocation BUTTON_CLOSE = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/button/close.png");
    public static final ResourceLocation BUTTON_EXCLAMATION = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/button/exclamation.png");
    public static final ResourceLocation WINDOW_BASE = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/base.png");
    public static final ResourceLocation WINDOW_SHADOW = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/shadow.png");
    public static final ResourceLocation INNER_SCREEN = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/inner_screen.png");
    public static final ResourceLocation PINNED = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/button/pinned.png");
    public static final ResourceLocation UNPINNED = ResourceLocation.fromNamespaceAndPath("mekanism", "gui/button/unpinned.png");

    private MekGuiTextures() {
    }
}
