package c_y.meknh_core.common.security;

import java.util.UUID;
import mekanism.api.security.ISecurityObject;
import mekanism.api.security.ISecurityUtils;
import mekanism.api.security.SecurityMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 本模组的安全方块实体（机器 / 多方块控制器 / 仓室 / 锅炉）。
 *
 * <p>直接实现 Mekanism 的 {@link ISecurityObject}，因此：</p>
 * <ul>
 *   <li>Mekanism 的 {@link ISecurityUtils#canAccessObject(Player, ISecurityObject)} 可以直接用，
 *       包括 {@code allowProtection} 配置、OP 绕过、TRUSTED 档的可信玩家列表；</li>
 *   <li>把 owner/security 能力注册出去之后，安全终端（Security Desk）维护的可信列表会对本模组生效。</li>
 * </ul>
 *
 * <p>拥有者由方块 {@code setPlacedBy} 写入（放下后才有拥有者），物品形态永远没有拥有者。</p>
 */
public interface SecureBlockEntity extends ISecurityObject {

    SecurityState securityState();

    @Override
    default UUID getOwnerUUID() {
        return securityState().owner();
    }

    /**
     * 本模组不缓存玩家名（Mekanism 只在 UI 里显示），需要显示时由调用方按 UUID 查询。
     */
    @Nullable
    @Override
    default String getOwnerName() {
        return null;
    }

    @Override
    default void setOwnerUUID(@Nullable UUID owner) {
        securityState().setOwner(owner);
    }

    @Override
    default SecurityMode getSecurityMode() {
        return securityState().mode();
    }

    @Override
    default void setSecurityMode(SecurityMode mode) {
        securityState().setMode(mode);
    }

    /**
     * 外部访问检查：走 Mekanism 的判定（配置开关、OP 绕过、PUBLIC/PRIVATE/TRUSTED）。
     */
    default boolean meknh_core$canAccess(@Nullable Player player) {
        if (player == null) {
            return true;
        }
        return ISecurityUtils.INSTANCE.canAccessObject(player, this);
    }

    default boolean meknh_core$isOwner(Player player) {
        return ownerMatches(player);
    }

    /**
     * 主人空手 Shift+右键：循环安全等级并提示。
     *
     * @return 是否消费了这次交互
     */
    default boolean meknh_core$cycleSecurity(Player player) {
        if (!ownerMatches(player)) {
            return false;
        }
        securityState().cycleMode();
        player.displayClientMessage(Component.translatable("gui.meknh_core.security.set",
                getSecurityMode().getTextComponent()), true);
        return true;
    }

    default void meknh_core$saveSecurity(CompoundTag tag) {
        securityState().save(tag);
    }

    default void meknh_core$loadSecurity(CompoundTag tag) {
        securityState().load(tag);
    }

    /**
     * 安全数据变化：标记存档并把状态同步给客户端（客户端要用 owner 判定挖掘进度）。
     */
    default void meknh_core$markSecurityChanged() {
        if (this instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            be.setChanged();
            net.minecraft.world.level.Level level = be.getLevel();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 2);
            }
        }
    }
}
