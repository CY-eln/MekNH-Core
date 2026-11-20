import c_y.meknh_core.common.gui.render.MachineBackgroundMaterial;
import javax.imageio.ImageIO;
import java.nio.file.Path;

public final class MachineBackgroundMaterialProbe {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(MachineBackgroundMaterial.forSteam(false,false) == MachineBackgroundMaterial.METAL, "electric unchanged");
        check(MachineBackgroundMaterial.forSteam(false,true) == MachineBackgroundMaterial.METAL, "pressure flag irrelevant outside steam");
        check(MachineBackgroundMaterial.forSteam(true,false) == MachineBackgroundMaterial.STEAM_BRONZE, "low pressure bronze");
        check(MachineBackgroundMaterial.forSteam(true,true) == MachineBackgroundMaterial.STEAM_HIGH, "high pressure dark metal");
        for (var material : MachineBackgroundMaterial.values()) {
            var image = ImageIO.read(Path.of("src/main/resources/assets/meknh_core", material.texture()).toFile());
            check(image != null && image.getWidth() == 176 && image.getHeight() == 177, "load material resource");
            if (material.tiled()) check(image.getHeight() >= 166, "native-scale crop covers furnace panels");
        }
        check(MachineBackgroundMaterial.PRIMITIVE_BRICK.tiled() && MachineBackgroundMaterial.COKE_BRICK.tiled(), "brick crop without stretch");
        System.out.println("PASS: electric/bronze/high-pressure material selection, five texture paths, native-scale furnace coverage.");
    }
}
