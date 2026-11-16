package c_y.meknh_core.common.gui.layout;

import net.minecraft.resources.ResourceLocation;

public final class MachineGuiTextures {
   public static final ResourceLocation BASE = texture("base.png");
   public static final ResourceLocation SLOT = texture("slot/normal.png");
   public static final ResourceLocation SLOT_SHEET = texture("slot/slots.png");
   public static final ResourceLocation SLOT_SHEET_DARK = texture("slot/slots_dark.png");
   public static final ResourceLocation SLOT_INPUT = texture("slot/input.png");
   public static final ResourceLocation SLOT_OUTPUT = texture("slot/output.png");
   public static final ResourceLocation SLOT_POWER = texture("slot/power.png");
   public static final ResourceLocation GAUGE = texture("gauge/standard.png");
   public static final ResourceLocation GAUGE_NORMAL = texture("gauge/normal.png");
   public static final ResourceLocation GAUGE_WIDE = texture("gauge/wide.png");
   public static final ResourceLocation GAUGE_ENERGY = texture("gauge/orange.png");
   public static final ResourceLocation GAUGE_STEAM = texture("gauge/aqua.png");
   public static final ResourceLocation GAUGE_INPUT = texture("gauge/red.png");
   public static final ResourceLocation GAUGE_OUTPUT = texture("gauge/blue.png");
   public static final ResourceLocation GAUGE_GAS_INPUT = texture("gauge/yellow.png");
   public static final ResourceLocation GAUGE_GAS_OUTPUT = texture("gauge/blue.png");
   public static final ResourceLocation PROGRESS = texture("progress/right.png");
   public static final ResourceLocation PROGRESS_BAR = texture("progress/bar.png");
   public static final ResourceLocation POWER_BAR = texture("bar/base.png");
   public static final ResourceLocation POWER_FILL = texture("bar/vertical_power.png");
   public static final ResourceLocation UP_ARROW = texture("up_arrow.png");

   private MachineGuiTextures() {
   }

   private static ResourceLocation texture(String path) {
      return ResourceLocation.fromNamespaceAndPath("mekanism", "gui/" + path);
   }
}
