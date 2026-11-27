package c_y.meknh_core.common.security;

import mekanism.api.security.IBlockSecurityUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 把本模组的安全方块实体注册到 Mekanism 的 owner / security 能力上。
 *
 * <p>注册之后，Mekanism 的 {@code SecurityUtils}、安全终端（Security Desk）维护的可信玩家列表、
 * 以及各种读取这两个能力的工具（Jade、扳手、其他附属）都能识别我们的机器。</p>
 */
public final class SecurityCapabilities {

    private SecurityCapabilities() {
    }

    public static <T extends BlockEntity> void register(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(IBlockSecurityUtils.INSTANCE.ownerCapability(), type,
                (be, ctx) -> be instanceof SecureBlockEntity secure ? secure : null);
        event.registerBlockEntity(IBlockSecurityUtils.INSTANCE.securityCapability(), type,
                (be, ctx) -> be instanceof SecureBlockEntity secure ? secure : null);
    }
}
