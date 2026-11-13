package c_y.meknh_core.common.integration.jade;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockPartType;
import mekanism.common.integration.lookingat.EnergyElement;
import mekanism.common.integration.lookingat.jade.JadeLookingAtHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 能源仓的 Jade 数据桥接。
 *
 * <p>能源仓的方块实体不是 Mekanism 的 {@code TileEntityUpdateable}，
 * Mekanism 自带的 Jade provider 不会读取它；这里按锅炉的方式写入
 * {@code MEK_DATA}，客户端仍由 Mekanism 的 {@code JadeTooltipRenderer} 绘制储能条。</p>
 */
public final class EnergyHatchJadeDataProvider implements IServerDataProvider<BlockAccessor> {

    public static final EnergyHatchJadeDataProvider INSTANCE = new EnergyHatchJadeDataProvider();

    private static final ResourceLocation UID = MekNHCore.rl("energy_hatch_data");

    private EnergyHatchJadeDataProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MultiblockPartBlockEntity part)) {
            return;
        }
        if (part.partType() != MultiblockPartType.ENERGY_INPUT
                && part.partType() != MultiblockPartType.ENERGY_OUTPUT) {
            return;
        }
        if (part.energyStorage() == null) {
            return;
        }
        JadeLookingAtHelper helper = new JadeLookingAtHelper(accessor.getLevel().registryAccess());
        helper.addEnergyElement(new EnergyElement(
                part.energyStorage().getEnergyStored(),
                part.energyStorage().getMaxEnergyStored()));
        helper.finalizeData(data);
    }
}
