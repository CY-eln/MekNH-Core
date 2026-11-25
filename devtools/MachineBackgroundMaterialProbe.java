import c_y.meknh_core.common.gui.render.MachineBackgroundMaterial;
import javax.imageio.ImageIO;
import java.nio.file.Files;
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
            int expected = switch (material) {
                case STEAM_BRONZE -> 0xFFFFF8E7;
                case STEAM_HIGH -> 0xFFF4F4F0;
                default -> 0xFF404040;
            };
            check(material.titleColor() == expected, "only steam lettering palette changes");
        }
        // Source contract check, not a GPU test: the title path adds neither a scrim nor a text shadow.
        String screen = Files.readString(Path.of("src/main/java/c_y/meknh_core/common/gui/screen/MachineScreen.java"));
        String labels = screen.substring(screen.indexOf("protected void renderLabels("), screen.indexOf("protected void renderSlot("));
        check(!labels.contains("graphics.fill("), "no dark lettering-area backdrop");
        check(labels.contains("material.titleColor(), false)"), "material title color without text shadow");
        check(MachineBackgroundMaterial.PRIMITIVE_BRICK.tiled() && MachineBackgroundMaterial.COKE_BRICK.tiled(), "brick crop without stretch");
        System.out.println("PASS: steam title colors only, no title scrim/shadow; five material textures and selection unchanged.");
    }
}
