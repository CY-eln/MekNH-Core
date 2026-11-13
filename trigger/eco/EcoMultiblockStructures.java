package c_y.meknh_core.trigger.eco;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class EcoMultiblockStructures {

    public static final ResourceLocation TRIGGER_ITEM =
            ResourceLocation.parse("neoecoae:cryotheum_solution_bucket");

    private EcoMultiblockStructures() {
    }

    public record Entry(String id, MultiblockPattern pattern) {
    }

    public static final MultiblockPattern CRAFTING_F9 = MultiblockPattern.builder("f9_crafting")
            // Y = 0
            .layer("711444444444441",
                    "111222222222221")
            // Y = 1
            .layer("811555555555551",
                    "131666666666661")
            // Y = 2
            .layer("911444444444441",
                    "111222222222221")
            .map('1', "minecraft:black_concrete", "neoecoae:crafting_casing")
            .map('2', "ae2:pattern_provider", "neoecoae:crafting_parallel_core_l9")
            .map('3', "ae2:crafting_monitor", "neoecoae:crafting_system_l9")
            .map('4', "extendedae:assembler_matrix_crafter", "neoecoae:crafting_pattern_bus")
            .map('5', "mekanism:pressure_disperser", "neoecoae:crafting_vent")
            .map('6', "ae2:molecular_assembler", "neoecoae:crafting_worker")
            .map('7', "mekanismgenerators:fission_reactor_port", "neoecoae:output_hatch")
            .map('8', "ae2:controller", "neoecoae:crafting_interface")
            .map('9', "minecraft:cauldron", "neoecoae:input_hatch")
            // 合成监视器或 ME 控制器右键都行
            .anchors('3', '8')
            .build();

    public static final MultiblockPattern COMPUTATION_C9 = MultiblockPattern.builder("c9_computation")
            // Y = 0
            .layer("QQQAAAAAAAAAAAQ",
                    "QQQSSSSSSSSSSSQ")
            // Y = 1
            .layer("KQQPPPPPPPPPPPQ",
                    "QEQFFFFFFFFFFFI")
            // Y = 2
            .layer("QQQAAAAAAAAAAAQ",
                    "QQQSSSSSSSSSSSQ")
            .map('Q', "ae2:quartz_block", "neoecoae:computation_casing")
            .map('A', "ae2:crafting_accelerator", "neoecoae:computation_parallel_core_l9")
            .map('S', "ae2:1k_crafting_storage", "neoecoae:computation_drive")
            .map('P', "mekanism:pressure_disperser", "neoecoae:computation_threading_core_l9")
            .map('F', "ae2:fluix_block", "neoecoae:computation_transmitter")
            .map('E', "ae2:energy_cell", "neoecoae:computation_system_l9")
            .map('I', "minecraft:blue_ice", "neoecoae:computation_cooling_controller_l9")
            .map('K', "ae2:controller", "neoecoae:computation_interface")
            // 尾巴上的冷却控制器固定朝「离开控制器」的方向（局部 +X），不跟随「朝外」规则
            .facing('I', 1, 0)
            // 能源元件或 ME 控制器右键都行
            .anchors('E', 'K')
            .build();

    public static final MultiblockPattern STORAGE_L9 = MultiblockPattern.builder("l9_storage")
            // Y = 0
            .layer("QQQEEEEEEEEEEEQ",
                    "QQQDDDDDDDDDDDQ")
            // Y = 1
            .layer("KQQPPPPPPPPPPPQ",
                    "QCQDDDDDDDDDDDQ")
            // Y = 2
            .layer("QQQEEEEEEEEEEEQ",
                    "QQQDDDDDDDDDDDQ")
            .map('Q', "ae2:quartz_block", "neoecoae:storage_casing")
            .map('E', "ae2:energy_cell", "neoecoae:energy_cell_l9")
            .map('D', "ae2:drive", "neoecoae:eco_drive")
            .map('P', "mekanism:pressure_disperser", "neoecoae:storage_vent")
            .map('C', "ae2:chest", "neoecoae:storage_system_l9")
            .map('K', "ae2:controller", "neoecoae:storage_interface")
            // 箱子或 ME 控制器右键都行
            .anchors('C', 'K')
            .build();

    public static final List<Entry> RECIPES = List.of(
            new Entry("F9", CRAFTING_F9),
            new Entry("C9", COMPUTATION_C9),
            new Entry("L9", STORAGE_L9));
}
