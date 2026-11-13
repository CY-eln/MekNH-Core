package c_y.meknh_core.common.gui.menu;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.machine.MachineBlockEntity;
import c_y.meknh_core.api.machine.MachineDefinition;
import c_y.meknh_core.api.machine.MachineRegistration;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.gui.layout.MachineGuiLayout;
import c_y.meknh_core.common.gui.layout.SteamGuiLayout;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class MachineMenu extends AbstractContainerMenu {
   public static final int DATA_SIZE = 44;
   public static final int DATA_ENERGY = 0;
   public static final int DATA_STEAM = 1;
   public static final int DATA_PROGRESS = 2;
   public static final int DATA_TOTAL_TICKS = 3;
   public static final int DATA_FLUID_IN = 4;
   public static final int DATA_FLUID_IN_ID = 5;
   public static final int DATA_FLUID_OUT = 6;
   public static final int DATA_FLUID_OUT_ID = 7;
   public static final int DATA_GAS_IN = 8;
   public static final int DATA_GAS_IN_ID = 9;
   public static final int DATA_GAS_OUT = 10;
   public static final int DATA_GAS_OUT_ID = 11;
   public static final int DATA_SIDE_BASE = 12;
   public static final int DATA_REDSTONE = 18;
   public static final int DATA_PRIVATE = 19;
   public static final int DATA_WARNING = 20;
   public static final int DATA_STEAM_PER_TICK = 21;
   public static final int DATA_SIDE_FLUID = 22;
   public static final int DATA_SIDE_CHEMICAL = 28;
   public static final int DATA_SIDE_ENERGY = 34;
   public static final int DATA_AUTO_EJECT = 40;
   public static final int DATA_ENERGY_PER_TICK = 44;
   public static final int DATA_RUNNING = 45;
   public static final int DATA_STRICT_INPUT = 46;
   public static final int DATA_OUTPUT_COLOR = 47;
   public static final int DATA_INPUT_COLOR = 48;
   private static final int BUTTON_FLUID_IN = 0;
   private static final int BUTTON_FLUID_OUT = 1;
   public static final int BUTTON_SIDE_BASE = 100;
   public static final int BUTTON_REDSTONE = 200;
   public static final int BUTTON_PRIVATE = 201;
   public static final int BUTTON_DUMP_STEAM = 202;
   @Nullable
   private final MachineBlockEntity machine;
   @Nullable
   private final MachineDefinition definition;
   @Nullable
   private final MachineLevel level;
   private final ContainerData data;
   private final int machineSlotCount;
   private final int inputSlotCount;

   public MachineMenu(int windowId, Inventory playerInventory, @Nullable MachineBlockEntity machine) {
      super((MenuType)MachineRegistration.MACHINE_MENU.get(), windowId);
      this.machine = machine;
      this.definition = machine == null ? null : machine.definition();
      this.level = machine == null ? null : machine.machineLevel();
      this.data = (ContainerData)(machine != null && !playerInventory.player.level().isClientSide()
         ? new MachineMenu.MachineData(machine)
         : new SimpleContainerData(54));
      int inputSlots = this.definition == null ? 0 : this.definition.itemInputSlots();
      int outputSlots = this.definition == null ? 0 : this.definition.itemOutputSlots();
      this.inputSlotCount = inputSlots;
      boolean steamLayout = this.usesSteamLayout();
      // Mek 电力机器没有电池物品槽，能量只通过机器内部缓存 + 竖向能量条显示。
      this.machineSlotCount = inputSlots + outputSlots;
      if (machine != null && this.definition != null) {
         if (steamLayout) {
            // 蒸汽机器：两输入槽 - 进度条 - 输出槽，横向居中；输入/输出各自与进度条留 8px。
            for (int index = 0; index < inputSlots; index++) {
               this.addSlot(new SlotItemHandler(machine.inventory(), index,
                     SteamGuiLayout.inputX(inputSlots, outputSlots, index) + 1, SteamGuiLayout.SLOT_Y));
            }
            for (int index = 0; index < outputSlots; index++) {
               this.addSlot(new SlotItemHandler(machine.inventory(), inputSlots + index,
                     SteamGuiLayout.outputX(inputSlots, outputSlots, index) + 1, SteamGuiLayout.SLOT_Y));
            }
         } else {
            int gaugeCount = MachineGuiLayout.gauges(this.definition, this.level).size();
            int outputX = MachineGuiLayout.outputColumnX(gaugeCount);
            int inputTop = MachineGuiLayout.columnTop(inputSlots);
            int outputTop = MachineGuiLayout.outputTop(outputSlots);

            for (int index = 0; index < inputSlots; index++) {
               this.addSlot(new SlotItemHandler(machine.inventory(), index, 64, inputTop + index * 20));
            }

            for (int index = 0; index < outputSlots; index++) {
               this.addSlot(new SlotItemHandler(machine.inventory(), inputSlots + index, outputX, outputTop + index * 20));
            }
         }
      }

      this.addPlayerInventory(playerInventory, steamLayout ? 95 : 84, steamLayout ? 153 : 142);
      this.addDataSlots(this.data);
   }

   /**
    * 合金炉的电力与蒸汽版本共用同一套 GUI 布局；其他机器仍按各自类型。
    */
   public boolean usesSteamLayout() {
      return this.isSteam()
            || (this.definition != null && (this.definition.id().equals(MekNHCore.rl("alloy_smelter"))
            || this.definition.id().equals(MekNHCore.rl("macerator"))
            || this.definition.id().equals(MekNHCore.rl("compressor"))
            || this.definition.id().equals(MekNHCore.rl("electric_stove"))
            || this.definition.id().equals(MekNHCore.rl("forge_hammer"))
            || this.definition.id().equals(MekNHCore.rl("extractor"))));
   }

   /**
    * 对应 Mek GuiSideConfiguration 只给 tile.getConfig().getTransmissions() 建 tab：
    * 没有对应槽位的传输类型不在配置窗口中显示。
    */
   public boolean supportsConfigType(int type) {
      if (this.definition == null) {
         return false;
      }
      return switch (type) {
         case 0 -> this.definition.itemInputSlots() + this.definition.itemOutputSlots() > 0;
         case 1 -> this.machine != null
               ? this.machine.inputFluidTank() != null || this.machine.outputFluidTank() != null
               : this.definition.fluidInputCapacity() + this.definition.fluidOutputCapacity() > 0L;
         case 2 -> this.isSteam()
               || (this.machine != null
                     ? this.machine.inputGasTank() != null || this.machine.outputGasTank() != null
                     : this.definition.chemicalInputCapacity() + this.definition.chemicalOutputCapacity() > 0L);
         case 3 -> !this.isSteam() && this.level != null && this.level.energyCapacity() > 0L;
         default -> false;
      };
   }

   public static MachineMenu fromNetwork(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
      BlockPos pos = buffer.readBlockPos();
      MachineBlockEntity machine = playerInventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity found ? found : null;
      return new MachineMenu(windowId, playerInventory, machine);
   }

   private void addPlayerInventory(Inventory playerInventory, int inventoryY, int hotbarY) {
      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, 9 + row * 9 + column, 8 + column * 18, inventoryY + row * 18));
         }
      }

      for (int column = 0; column < 9; column++) {
         this.addSlot(new Slot(playerInventory, column, 8 + column * 18, hotbarY));
      }
   }

   public int data(int index) {
      return this.data.get(index);
   }

   /**
    * 侧面数据不是严格按 type*6 连续排列：红石/安全/警告插在物品与流体之间。
    */
   public int sideDataBase(int type) {
      return switch (type) {
         case 1 -> DATA_SIDE_FLUID;
         case 2 -> DATA_SIDE_CHEMICAL;
         case 3 -> DATA_SIDE_ENERGY;
         default -> DATA_SIDE_BASE;
      };
   }

   public int machineSlotCount() {
      return this.machineSlotCount;
   }

   @Nullable
   public MachineDefinition definition() {
      return this.definition;
   }

   @Nullable
   public MachineLevel level() {
      return this.level;
   }

   @Nullable
   public MachineBlockEntity machine() {
      return this.machine;
   }

   public boolean isSteam() {
      return this.level != null && this.level.isSteam();
   }

   public long fluidCapacity(boolean input) {
      if (this.definition == null) {
         return 0L;
      } else {
         return input ? this.definition.fluidInputCapacity() : this.definition.fluidOutputCapacity();
      }
   }

   public long chemicalCapacity(boolean input) {
      if (this.definition == null) {
         return 0L;
      } else {
         return input ? this.definition.chemicalInputCapacity() : this.definition.chemicalOutputCapacity();
      }
   }

   public boolean stillValid(Player player) {
      if (this.machine != null && !this.machine.isRemoved()) {
         BlockPos pos = this.machine.getBlockPos();
         return player.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) <= 64.0;
      } else {
         return false;
      }
   }

   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = (Slot)this.slots.get(index);
      if (slot != null && slot.hasItem()) {
         ItemStack stack = slot.getItem();
         ItemStack original = stack.copy();
         if (index < this.machineSlotCount) {
            if (!this.moveItemStackTo(stack, this.machineSlotCount, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }
         } else if (this.inputSlotCount <= 0 || !this.moveItemStackTo(stack, 0, this.inputSlotCount, false)) {
            return ItemStack.EMPTY;
         }

         if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }

         return original;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public boolean clickMenuButton(Player player, int id) {
      if (this.machine == null) {
         return false;
      } else if (id == 0 && this.machine.inputFluidTank() != null) {
         return FluidUtil.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, this.machine.inputFluidTank());
      } else if (id == 1 && this.machine.outputFluidTank() != null) {
         return FluidUtil.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, this.machine.outputFluidTank());
      } else if (id == BUTTON_DUMP_STEAM) {
         if (this.machine.steamTank() == null) {
            return false;
         }
         this.machine.steamTank().setEmpty();
         return true;
      } else if (id >= 100 && id < 124) {
         int code = id - 100;
         this.machine.cycleSideMode(
               c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[code / 6],
               Direction.values()[code % 6]);
         return true;
      } else if (id >= 150 && id < 174) {
         int code = id - 150;
         this.machine.cycleSideMode(
               c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[code / 6],
               Direction.values()[code % 6], true);
         return true;
      } else if (id >= 130 && id < 134) {
         this.machine.clearSideModes(c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 130]);
         return true;
      } else if (id >= 180 && id < 184) {
         this.machine.batchCycleSideModes(c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 180], false);
         return true;
      } else if (id >= 184 && id < 188) {
         this.machine.batchCycleSideModes(c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 184], true);
         return true;
      } else if (id == 188) {
         this.machine.clearAllSideModes();
         return true;
      } else if (id >= 140 && id < 144) {
         this.machine.toggleAutoEject(c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 140]);
         return true;
      } else if (id == 200) {
         this.machine.cycleRedstoneMode();
         return true;
      } else if (id == 210) {
         this.machine.toggleStrictInput();
         return true;
      } else if (id == 211) {
         this.machine.cycleOutputColor();
         return true;
      } else if (id >= 212 && id < 218) {
         this.machine.cycleInputColor(Direction.values()[id - 212]);
         return true;
      } else if (id == 201) {
         if (!this.machine.hasOwner()) {
            this.machine.setOwner(player);
         }
         if (this.machine.isOwner(player)) {
            this.machine.togglePrivateMode();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static final class MachineData implements ContainerData {
      private final MachineBlockEntity machine;

      private MachineData(MachineBlockEntity machine) {
         this.machine = machine;
      }

      public int get(int index) {
         if (index >= 12 && index < 18) {
            return this.sideData(c_y.meknh_core.common.machine.config.MachineConfigType.ITEM, index - 12);
         } else if (index >= 22 && index < 28) {
            return this.sideData(c_y.meknh_core.common.machine.config.MachineConfigType.FLUID, index - 22);
         } else if (index >= 28 && index < 34) {
            return this.sideData(c_y.meknh_core.common.machine.config.MachineConfigType.CHEMICAL, index - 28);
         } else if (index >= 34 && index < 40) {
            return this.sideData(c_y.meknh_core.common.machine.config.MachineConfigType.ENERGY, index - 34);
         } else {
            return switch (index) {
               case 0 -> this.machine.energyStorage() == null ? 0 : clamp((long)this.machine.energyStorage().getEnergyStored());
               case 1 -> this.machine.steamTank() == null ? 0 : clamp(this.machine.steamTank().getStored());
               case 2 -> this.machine.progress();
               case 3 -> this.machine.totalTicks();
               case 4 -> this.machine.inputFluidTank() == null ? 0 : this.machine.inputFluidTank().getFluidAmount();
               case 5 -> fluidId(this.machine.inputFluidTank() == null ? FluidStack.EMPTY : this.machine.inputFluidTank().getFluid());
               case 6 -> this.machine.outputFluidTank() == null ? 0 : this.machine.outputFluidTank().getFluidAmount();
               case 7 -> fluidId(this.machine.outputFluidTank() == null ? FluidStack.EMPTY : this.machine.outputFluidTank().getFluid());
               case 8 -> this.machine.inputGasTank() == null ? 0 : clamp(this.machine.inputGasTank().getStored());
               case 9 -> chemicalId(this.machine.inputGasTank());
               case 10 -> this.machine.outputGasTank() == null ? 0 : clamp(this.machine.outputGasTank().getStored());
               case 11 -> chemicalId(this.machine.outputGasTank());
               default -> 0;
               case 18 -> this.machine.redstoneMode().ordinal();
               case 19 -> this.machine.privateMode() ? 1 : 0;
               case 20 -> this.machine.warningCode();
               case 21 -> clamp(this.machine.steamPerTick());
               case 40, 41, 42, 43 -> this.machine.autoEject(
                     c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[index - 40]) ? 1 : 0;
               case 44 -> this.machine.isRunning() ? clamp(this.machine.energyPerTick()) : 0;
               case 45 -> this.machine.isRunning() ? 1 : 0;
               case 46 -> this.machine.strictInput() ? 1 : 0;
               case 47 -> this.machine.outputColor();
               case 48, 49, 50, 51, 52, 53 -> this.machine.inputColor(Direction.values()[index - 48]);
            };
         }
      }

      private int sideData(c_y.meknh_core.common.machine.config.MachineConfigType type, int sideIndex) {
         return this.machine.sideMode(type, Direction.values()[sideIndex]).ordinal();
      }

      public void set(int index, int value) {
      }

      public int getCount() {
         return 54;
      }

      private static int clamp(long value) {
         return (int)Math.max(0L, Math.min(2147483647L, value));
      }

      private static int fluidId(FluidStack stack) {
         if (stack.isEmpty()) {
            return -1;
         } else {
            Fluid fluid = stack.getFluid();
            return BuiltInRegistries.FLUID.getId(fluid);
         }
      }

      private static int chemicalId(@Nullable IChemicalTank tank) {
         if (tank != null && !tank.isEmpty()) {
            Chemical chemical = tank.getStack().getChemical();
            return MekanismAPI.CHEMICAL_REGISTRY.getId(chemical);
         } else {
            return -1;
         }
      }
   }
}
