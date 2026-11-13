package c_y.meknh_core.common.gui.layout;

import c_y.meknh_core.api.machine.MachineDefinition;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class MachineGuiLayout {
   public static final int IMAGE_WIDTH = 176;
   public static final int IMAGE_HEIGHT = 166;
   public static final int SLOT_SIZE = 18;
   public static final int SLOT_STEP = 20;
   public static final int BODY_TOP = 16;
   public static final int BODY_HEIGHT = 58;
   public static final int BODY_CENTER = 45;
   public static final int INPUT_COLUMN_X = 64;
   public static final int INPUT_TOP = 17;
   public static final int OUTPUT_COLUMN_X = 116;
   public static final int OUTPUT_TOP = 35;
   public static final int ENERGY_SLOT_X = 64;
   public static final int ENERGY_SLOT_Y = 53;
   public static final int OUTPUT_COLUMN_GAP = 6;
   public static final int PROGRESS_WIDTH = 32;
   public static final int PROGRESS_HEIGHT = 8;
   public static final int PROGRESS_Y = 38;
   public static final int GAUGE_WIDTH = 16;
   public static final int GAUGE_HEIGHT = 54;
   public static final int GAUGE_TOP = 16;
   public static final int GAUGE_RIGHT = 174;
   public static final int GAUGE_STEP = 18;
   public static final int INVENTORY_X = 8;
   public static final int INVENTORY_Y = 84;
   public static final int HOTBAR_Y = 142;

   private MachineGuiLayout() {
   }

   public static List<MachineGuiLayout.Gauge> gauges(@Nullable MachineDefinition definition, @Nullable MachineLevel level) {
      List<MachineGuiLayout.Gauge> gauges = new ArrayList<>(4);
      if (definition != null) {
         if (definition.fluidInputCapacity() > 0L) {
            gauges.add(MachineGuiLayout.Gauge.FLUID_IN);
         }

         if (definition.chemicalInputCapacity() > 0L) {
            gauges.add(MachineGuiLayout.Gauge.GAS_IN);
         }

         if (definition.fluidOutputCapacity() > 0L) {
            gauges.add(MachineGuiLayout.Gauge.FLUID_OUT);
         }

         if (definition.chemicalOutputCapacity() > 0L) {
            gauges.add(MachineGuiLayout.Gauge.GAS_OUT);
         }
      }

      gauges.add(level != null && level.isSteam() ? MachineGuiLayout.Gauge.STEAM : MachineGuiLayout.Gauge.ENERGY);
      return List.copyOf(gauges);
   }

   public static int gaugeX(int index, int count) {
      return 164 - (count - 1 - index) * 18;
   }

   public static int outputColumnX(int gaugeCount) {
      return 116;
   }

   public static int progressX(int gaugeCount) {
      return 86;
   }

   public static int columnTop(int slots) {
      return 17;
   }

   public static int energySlotY(int inputSlots) {
      return Math.max(53, 17 + inputSlots * 20 + 2);
   }

   public static int outputTop(int slots) {
      return 35;
   }

   public static enum Gauge {
      ENERGY,
      STEAM,
      FLUID_IN,
      FLUID_OUT,
      GAS_IN,
      GAS_OUT;
   }
}
