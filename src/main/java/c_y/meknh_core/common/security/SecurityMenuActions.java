package c_y.meknh_core.common.security;

import mekanism.api.security.SecurityMode;
import net.minecraft.world.entity.player.Player;

/**
 * GUI 侧的安全等级操作：菜单按钮 → 循环等级。
 *
 * <p>只有主人能改；非主人调用返回 {@code false}（按钮无效）。</p>
 */
public final class SecurityMenuActions {

    /** 安全按钮的菜单 id（与机器/锅炉的私有按钮保持一致）。 */
    public static final int BUTTON_SECURITY = 201;

    private SecurityMenuActions() {
    }

    public static boolean cycle(Player player, @org.jetbrains.annotations.Nullable Object holder) {
        if (holder instanceof SecureBlockEntity secure && secure.meknh_core$isOwner(player)) {
            secure.securityState().cycleMode();
            return true;
        }
        return false;
    }

    public static SecurityMode modeOf(@org.jetbrains.annotations.Nullable Object holder) {
        return holder instanceof SecureBlockEntity secure ? secure.securityState().mode() : SecurityMode.PUBLIC;
    }
}
