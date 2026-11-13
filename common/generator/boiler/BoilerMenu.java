package c_y.meknh_core.common.generator.boiler;

import c_y.meknh_core.api.generator.boiler.BoilerRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BoilerMenu extends AbstractContainerMenu {

    public static final int DATA_WATER = 0;
    public static final int DATA_WATER_CAP = 1;
    public static final int DATA_STEAM = 2;
    public static final int DATA_STEAM_CAP = 3;
    public static final int DATA_FUEL = 4;
    public static final int DATA_FUEL_CAP = 5;
    public static final int DATA_HEAT = 6;
    public static final int DATA_TEMPERATURE = 7;
    public static final int DATA_TEMPERATURE_MAX = 8;
    public static final int DATA_BURN_TIME = 9;
    public static final int DATA_BURN_DURATION = 10;
    public static final int DATA_SIDE_BASE = 11;
    public static final int DATA_REDSTONE = 35;
    public static final int DATA_PRIVATE = 36;
    public static final int DATA_AUTO_EJECT = 37;
    public static final int DATA_STRICT_INPUT = 41;
    public static final int DATA_OUTPUT_COLOR = 42;
    public static final int DATA_INPUT_COLOR = 43;
    public static final int DATA_WARNING = 49;
    public static final int DATA_BLOCKED = 50;
    public static final int DATA_ACTIVE = 51;
    public static final int BUTTON_SIDE_BASE = 100;
    public static final int BUTTON_REDSTONE = 200;
    public static final int BUTTON_PRIVATE = 201;

    private final BoilerBlockEntity blockEntity;
    private final ContainerData data;

    public BoilerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, blockEntity(inventory, buffer.readBlockPos()));
    }

    public BoilerMenu(int id, Inventory inventory, BoilerBlockEntity blockEntity) {
        super(BoilerRegistration.BOILER_MENU.get(), id);
        this.blockEntity = blockEntity;
        int[] synced = new int[52];
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                // 服务端读方块实体；客户端返回数据包缓存值。
                if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide) {
                    synced[index] = switch (index) {
                        case 0 -> blockEntity.waterAmount();
                        case 1 -> blockEntity.waterCapacity();
                        case 2 -> blockEntity.steamAmount();
                        case 3 -> blockEntity.steamCapacity();
                        case 4 -> blockEntity.fuelAmount();
                        case 5 -> blockEntity.fuelCapacity();
                        case 6 -> blockEntity.heatTime();
                        case 7 -> blockEntity.temperature();
                        case 8 -> blockEntity.temperatureMax();
                        case 9 -> blockEntity.burnTime();
                        case 10 -> blockEntity.burnDuration();
                        case 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                             23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34 ->
                                blockEntity.sideMode(
                                        c_y.meknh_core.common.machine.config.MachineConfigType
                                                .VALUES[(index - DATA_SIDE_BASE) / 6],
                                        net.minecraft.core.Direction.values()[(index - DATA_SIDE_BASE) % 6]).ordinal();
                        case 35 -> blockEntity.redstoneMode().ordinal();
                        case 36 -> blockEntity.privateMode() ? 1 : 0;
                        case 37, 38, 39, 40 -> blockEntity.autoEject(
                                c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[index - DATA_AUTO_EJECT]) ? 1 : 0;
                        case 41 -> blockEntity.strictInput() ? 1 : 0;
                        case 42 -> blockEntity.outputColor();
                        case 43, 44, 45, 46, 47, 48 -> blockEntity.inputColor(
                                net.minecraft.core.Direction.values()[index - DATA_INPUT_COLOR]);
                        case 49 -> blockEntity.warningCode();
                        case 50 -> blockEntity.isBlocked() ? 1 : 0;
                        case 51 -> blockEntity.isActive() ? 1 : 0;
                        default -> 0;
                    };
                }
                return synced[index];
            }

            @Override
            public void set(int index, int value) {
                synced[index] = value;
            }

            @Override
            public int getCount() {
                return 52;
            }
        };
        if (blockEntity.getBlockState().getBlock() instanceof BoilerBlock block
                && block.definition().itemInputSlots() > 0) {
            this.addSlot(new SlotItemHandler(blockEntity.inventory(), 0, 47, 51));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        this.addDataSlots(this.data);
    }

    private static BoilerBlockEntity blockEntity(Inventory inventory, BlockPos pos) {
        BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
        if (blockEntity instanceof BoilerBlockEntity boiler) {
            return boiler;
        }
        throw new IllegalStateException("Boiler menu opened without boiler block entity");
    }

    public int value(int index) {
        return this.data.get(index);
    }

    public int sideDataBase(int type) {
        return DATA_SIDE_BASE + type * 6;
    }

    public BoilerBlockEntity blockEntity() {
        return this.blockEntity;
    }

    public net.neoforged.neoforge.fluids.FluidStack inputFluid() {
        return this.blockEntity.inputFluidDisplay();
    }

    public net.neoforged.neoforge.fluids.FluidStack waterFluid() {
        return this.blockEntity.waterFluidDisplay();
    }

    public net.neoforged.neoforge.fluids.FluidStack fuelFluid() {
        return this.blockEntity.fuelFluidDisplay();
    }

    public mekanism.api.chemical.ChemicalStack fuelChemical() {
        return this.blockEntity.fuelChemicalDisplay();
    }

    public mekanism.api.chemical.ChemicalStack inputChemical() {
        return this.blockEntity.inputChemicalDisplay();
    }

    public mekanism.api.chemical.ChemicalStack outputChemical() {
        return this.blockEntity.outputChemicalDisplay();
    }

    public int steamPerTick() {
        int temperature = this.value(DATA_TEMPERATURE);
        int max = Math.max(1, this.value(DATA_TEMPERATURE_MAX));
        if (temperature < 100) {
            return 0;
        }
        return (int)Math.round(this.blockEntity.nominalSteamPerTick()
                * (temperature / (double)max));
    }

    public boolean isLiquid() {
        return this.blockEntity.getBlockState().getBlock() instanceof BoilerBlock block
                && block.definition().type() == c_y.meknh_core.api.generator.boiler.BoilerDefinition.Type.LIQUID;
    }

    public boolean isSolid() {
        return this.blockEntity.getBlockState().getBlock() instanceof BoilerBlock block
                && block.definition().type() == c_y.meknh_core.api.generator.boiler.BoilerDefinition.Type.SOLID;
    }

    public boolean isSolar() {
        return this.blockEntity.getBlockState().getBlock() instanceof BoilerBlock block
                && block.definition().type() == c_y.meknh_core.api.generator.boiler.BoilerDefinition.Type.SOLAR;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = this.isSolid() ? 1 : 0;
        if (index < machineSlots) {
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 100 && id < 124) {
            int code = id - 100;
            this.blockEntity.cycleSideMode(
                    c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[code / 6],
                    net.minecraft.core.Direction.values()[code % 6], false);
            return true;
        } else if (id >= 150 && id < 174) {
            int code = id - 150;
            this.blockEntity.cycleSideMode(
                    c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[code / 6],
                    net.minecraft.core.Direction.values()[code % 6], true);
            return true;
        } else if (id >= 130 && id < 134) {
            this.blockEntity.clearSideModes(
                    c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 130]);
            return true;
        } else if (id >= 140 && id < 144) {
            this.blockEntity.toggleAutoEject(
                    c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 140]);
            return true;
        } else if (id >= 180 && id < 184) {
            this.blockEntity.batchCycleSideModes(
                    c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 180], false);
            return true;
        } else if (id >= 184 && id < 188) {
            this.blockEntity.batchCycleSideModes(
                    c_y.meknh_core.common.machine.config.MachineConfigType.VALUES[id - 184], true);
            return true;
        } else if (id == 188) {
            this.blockEntity.clearAllSideModes();
            return true;
        } else if (id == 210) {
            this.blockEntity.toggleStrictInput();
            return true;
        } else if (id == 211) {
            this.blockEntity.cycleOutputColor();
            return true;
        } else if (id >= 212 && id < 218) {
            this.blockEntity.cycleInputColor(net.minecraft.core.Direction.values()[id - 212]);
            return true;
        } else if (id == BUTTON_REDSTONE) {
            this.blockEntity.cycleRedstoneMode();
            return true;
        } else if (id == BUTTON_PRIVATE) {
            this.blockEntity.togglePrivateMode();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity.getLevel() != null
                && player.distanceToSqr(this.blockEntity.getBlockPos().getCenter()) <= 64.0D;
    }
}
