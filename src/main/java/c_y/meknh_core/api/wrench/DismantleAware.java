package c_y.meknh_core.api.wrench;

import net.minecraft.world.item.ItemStack;

/**
 * 扳手拆除时的通用扩展点：方块实体可以在掉落物已经写入 BE 数据后收到通知，
 * 用于避免拆除时把已经保存进物品的内容再次掉落。
 */
public interface DismantleAware {

    void onDismantleSaved(ItemStack drop);
}
