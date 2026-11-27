package c_y.meknh_core.common.security;

import mekanism.api.security.SecurityMode;

/**
 * 安全等级与 lang 键的映射（GUI / 提示共用）。
 */
public final class SecurityText {

    private SecurityText() {
    }

    /**
     * 安全 tab 的悬停文本键。
     *
     * @param ordinal {@link SecurityMode#ordinal()}
     */
    public static String tabKey(int ordinal) {
        return switch (ordinal) {
            case 1 -> "gui.meknh_core.security.private";
            case 2 -> "gui.meknh_core.security.trusted";
            default -> "gui.meknh_core.security.public";
        };
    }
}