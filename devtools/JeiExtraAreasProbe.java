import java.util.*;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;

/** Runs actual cached JEI layout bytecode, not a copied collision algorithm. */
public class JeiExtraAreasProbe {
    public static void main(String[] args) throws Exception {
        boolean forwarded = true;
        for (int scale : new int[]{2, 1}) {
            int width = 854 / scale, height = 510 / scale;
            int left = (width - 176) / 2, top = (height - 166) / 2;
            var tab = new ImmutableRect2i(left - 26, top + 6, 26, 26);
            // Left bookmark grid fixture: populated slots immediately left of the container.
            var grid = new ImmutableRect2i(left - 108, 18, 108, height - 36);
            var missing = IngredientGridLayout.calculateSlots(grid, Set.of(), null, 0);
            var screen = new c_y.meknh_core.common.gui.screen.MachineScreen();
            screen.areas = List.of(new net.minecraft.client.renderer.Rect2i(tab.x(), tab.y(), tab.width(), tab.height()));
            var actualAreas = c_y.meknh_core.client.jei.MeknhJeiPlugin.extra(screen);
            var boiler = new c_y.meknh_core.common.generator.boiler.BoilerScreen();
            boiler.areas = screen.areas;
            forwarded &= c_y.meknh_core.client.jei.MeknhJeiPlugin.extra(boiler).equals(screen.areas);
            if (!c_y.meknh_core.client.jei.MeknhJeiPlugin.extra(new c_y.meknh_core.common.coke.CokeOvenScreen()).isEmpty())
                throw new AssertionError("unrelated CokeOven behavior changed");
            var exclusions = new HashSet<ImmutableRect2i>();
            for (var rect : actualAreas) exclusions.add(new ImmutableRect2i(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight()));
            var excluded = IngredientGridLayout.calculateSlots(grid, Set.of(tab), null, 0);
            var registered = IngredientGridLayout.calculateSlots(grid, exclusions, null, 0);
            forwarded &= registered.stream().noneMatch(s -> !s.blocked() && s.area().intersects(tab));
            long before = missing.stream().filter(s -> !s.blocked() && s.area().intersects(tab)).count();
            long after = excluded.stream().filter(s -> !s.blocked() && s.area().intersects(tab)).count();
            if (before == 0 || after != 0) throw new AssertionError("invalid JEI fixture");
            System.out.println("854x510 scale=" + scale + " GUI=" + width + "x" + height +
                " active JEI slots over tab: missing=" + before + " excluded=" + after +
                " registered=" + registered.stream().filter(s -> !s.blocked() && s.area().intersects(tab)).count());
        }
        if (!forwarded) throw new AssertionError("Machine handler inherits empty exclusions: JEI bookmark slots remain active over machine tab");
        System.out.println("PASS machine handler forwards exclusions (geometry tested separately)");
    }
}
