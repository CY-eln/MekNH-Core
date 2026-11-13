package c_y.meknh_core;

import c_y.meknh_core.api.chemical.ChemicalDataLoader;
import c_y.meknh_core.api.chemical.ChemicalRegistration;
import c_y.meknh_core.api.chemical.FluidRegistration;
import c_y.meknh_core.api.generator.boiler.BoilerDataLoader;
import c_y.meknh_core.api.generator.boiler.BoilerRegistration;
import c_y.meknh_core.api.machine.MachineDataLoader;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.common.network.MeknhNetwork;
import c_y.meknh_core.common.recipe.MachineRecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 模组主类。
 *
 * <p>启动顺序：先注册配方类型，再注册数据驱动的机器与化学品，
 * 最后扫描各模组数据包里的定义。所有扫描都发生在 {@code RegisterEvent} 之前，
 * 保证注册表内容在客户端与服务端一致。</p>
 */
@Mod(MekNHCore.MODID)
public class MekNHCore {

    /**
     * 模组 ID。
     */
    public static final String MODID = "meknh_core";

    /**
     * 共用日志。
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    public MekNHCore(IEventBus modEventBus) {
        modEventBus.addListener(MeknhNetwork::register);
        c_y.meknh_core.register.Ae2CreativeCellMusicDisc.initialize(modEventBus);
        // 化学品：data/addon/chemicals（每个文件含定义与属性）
        ChemicalRegistration.initialize(modEventBus);
        FluidRegistration.initialize(modEventBus);
        ChemicalDataLoader.loadFromMods();

        // 机器：data/addon/machine（一个文件自动注册 level 指定的全部版本）
        MachineRecipes.initialize(modEventBus);
        MachineRegistration.initialize(modEventBus);
        MachineDataLoader.loadFromMods();

        // 多方块内核：data/addon/multiblock（控制器 + 结构匹配 + 仓室 + 配方执行）
        c_y.meknh_core.api.multiblock.MultiblockRegistration.initialize(modEventBus);
        c_y.meknh_core.api.multiblock.MultiblockDataLoader.loadFromMods();

        // 发电机：data/addon/generator/boiler
        BoilerRegistration.initialize(modEventBus);
        BoilerDataLoader.loadFromMods();

        // 格雷风格外壳：蒸汽机器外壳 / 青铜燃烧室外壳
        c_y.meknh_core.api.casing.CasingRegistration.initialize(modEventBus);
    }

    /**
     * 构造本模组命名空间下的 {@link ResourceLocation}。
     */
    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
