package c_y.meknh_core.client.structure;

import c_y.meknh_core.api.multiblock.*;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only representative states; candidate validation uses an isolated, non-ticking world. */
public record StructurePreview(MultiblockVariant variant) {
    public MultiblockStructure structure() { return variant.definition().structure(); }
    public Component title() { return variant.item().get().getDescription(); }
    public String dimensions() { return structure().sizeX() + " × " + structure().sizeY() + " × " + structure().sizeZ(); }
    public List<BlockState> candidates(MultiblockMatcher matcher) {
        if (matcher instanceof MultiblockMatcher.Controller) return List.of(variant.block().get().defaultBlockState());
        if (matcher instanceof MultiblockMatcher.Exact exact) return List.of(exact.resolve().defaultBlockState());
        if (matcher instanceof MultiblockMatcher.Tag tag) {
            return BuiltInRegistries.BLOCK.stream().filter(b -> b.defaultBlockState().is(tag.tag()))
                    .map(Block::defaultBlockState).toList();
        }
        if (matcher instanceof MultiblockMatcher.Part part) {
            return MultiblockRegistration.partBlocks().stream()
                    .filter(b -> MultiblockRegistration.partType(b) == part.type()).map(Block::defaultBlockState).toList();
        }
        return List.of(); // Air and Any are intentionally not rendered as solid blocks.
    }
    /** Preserve symbol-specific overrides by asking the runtime structure, not reparsing its JSON. */
    public List<BlockState> candidates(MultiblockStructure.Cell cell) {
        var result = new java.util.LinkedHashSet<>(candidates(cell.matcher()));
        if (cell.matcher() instanceof MultiblockMatcher.Exact || cell.matcher() instanceof MultiblockMatcher.Tag) {
            var probe = new com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld();
            try {
                for (Block block : MultiblockRegistration.partBlocks()) {
                    BlockState state = block.defaultBlockState();
                    probe.clear();
                    probe.addBlock(net.minecraft.core.BlockPos.ZERO,
                            new com.lowdragmc.lowdraglib2.utils.data.BlockInfo(state, true));
                    if (structure().accepts(probe, net.minecraft.core.BlockPos.ZERO, cell)) result.add(state);
                }
            } finally {
                probe.clear();
            }
        }
        return List.copyOf(result);
    }

    public static int candidateGroup(BlockState state) {
        var type = MultiblockRegistration.partType(state.getBlock());
        if (type == null) return 0;
        return switch (type.name()) {
            case "ITEM_INPUT", "ITEM_OUTPUT", "FLUID_INPUT", "FLUID_OUTPUT" -> 1;
            case "ENERGY_INPUT", "ENERGY_OUTPUT", "STEAM_INPUT" -> 2;
            // Includes future registered maintenance, muffler and parallel part types.
            default -> 3;
        };
    }

    public BlockState state(MultiblockMatcher matcher) {
        List<BlockState> states = candidates(matcher);
        return states.isEmpty() ? Blocks.AIR.defaultBlockState() : states.getFirst();
    }
}
