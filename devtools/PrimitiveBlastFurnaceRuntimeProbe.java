import c_y.meknh_core.api.multiblock.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Actual production parser/rotation checks, without starting a game server or client. */
public class PrimitiveBlastFurnaceRuntimeProbe {
    public static void main(String[] args) throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/addon/multiblock/primitive_blast_furnace.json"))).getAsJsonObject();
        String id = "meknh_core:primitive_blast_furnace";
        if (!json.get("controller_block").getAsString().equals(id)) throw new AssertionError("Explicit furnace controller binding");
        var plan = ControllerBindingPlan.plan(java.util.List.of(new ControllerBindingPlan.Request(
                id, true, json.get("controller_block").getAsString(), java.util.List.of(id))), java.util.List.of());
        if (!plan.isEmpty()) throw new AssertionError("Native self-binding must retain generated controller registration");
        System.out.println("PASS explicit primitive furnace controller native self-binding");
        var structure = MultiblockStructure.parse(ResourceLocation.fromNamespaceAndPath("meknh_core", "primitive_blast_furnace"), json);
        var hearth = structure.cells().stream().filter(c -> c.matcher() instanceof MultiblockMatcher.AirOrSnow).findFirst().orElseThrow();
        var pos = new BlockPos(14, 70, -19);
        for (var direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            var actual = MultiblockStructure.offset(pos, hearth.dx(), hearth.dy(), hearth.dz(), direction);
            var expected = pos.relative(direction.getOpposite());
            if (!actual.equals(expected)) throw new AssertionError("Hearth must be behind controller for " + direction + ": " + actual + " != " + expected);
            long strictAir = structure.cells().stream().filter(c -> c.matcher() instanceof MultiblockMatcher.Air).count();
            if (strictAir != 2) throw new AssertionError("Two strict air cells required");
            for (var cell : structure.cells()) if (cell.matcher() instanceof MultiblockMatcher.Air) {
                var air = MultiblockStructure.offset(pos, cell.dx(), cell.dy(), cell.dz(), direction);
                if (!(air.equals(expected.above()) || air.equals(expected.above(2)))) throw new AssertionError("Open chimney geometry: " + air);
            }
        }
        if (!(MultiblockMatcher.parse("meknh_core", "air_or_snow") instanceof MultiblockMatcher.AirOrSnow)) throw new AssertionError("Air-or-snow parser rule");
        System.out.println("PASS production parser: four-direction hearth behind controller, two chimney air cells and air-or-snow rule");
    }
}
