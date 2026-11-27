package c_y.meknh_core.common.security;

import mekanism.api.security.ISecurityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 方块交互侧的安全判定工具。
 *
 * <p>统一三个入口：</p>
 * <ul>
 *   <li>右键打开 GUI / 用桶取流体前：{@link #checkAccess(Level, BlockPos, Player)}；</li>
 *   <li>主人空手 Shift+右键：{@link #tryCycle(Level, BlockPos, Player)} 循环安全等级；</li>
 *   <li>挖掘：方块覆盖 {@code getDestroyProgress}，无权限时返回 0（挖不动）。</li>
 * </ul>
 */
public final class SecurityInteractions {

    private SecurityInteractions() {
    }

    /**
     * 无权访问时给玩家一条提示。
     *
     * @return 是否有权限
     */
    public static boolean checkAccess(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof SecureBlockEntity secure
                && !secure.meknh_core$canAccess(player)) {
            if (!level.isClientSide) {
                ISecurityUtils.INSTANCE.displayNoAccess(player);
            }
            return false;
        }
        return true;
    }

    /**
     * 主人空手 Shift+右键：循环 PUBLIC → PRIVATE → TRUSTED → PUBLIC。
     *
     * <p>无主方块（旧存档）由第一次 Shift+右键的玩家认领并顺便循环一次。</p>
     *
     * @return 是否消费了这次交互
     */
    public static boolean tryCycle(Level level, BlockPos pos, Player player) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof SecureBlockEntity secure)) {
            return false;
        }
        if (level.isClientSide) {
            // 客户端吞掉这次右键，结果由服务端决定
            return true;
        }
        if (secure.getOwnerUUID() == null) {
            secure.setOwnerUUID(player.getUUID());
            secure.meknh_core$cycleSecurity(player);
            return true;
        }
        if (!secure.ownerMatches(player)) {
            ISecurityUtils.INSTANCE.displayNoAccess(player);
            return true;
        }
        return secure.meknh_core$cycleSecurity(player);
    }
}
