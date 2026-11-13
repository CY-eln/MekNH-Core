package c_y.meknh_core.common.gui.menu;

import c_y.meknh_core.api.multiblock.MultiblockPartBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockPartType;
import c_y.meknh_core.api.multiblock.MultiblockRegistration;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 仓室 GUI：物品总线显示槽位，流体/蒸汽/能源仓显示宽罐。
 */
public class HatchMenu extends AbstractContainerMenu {

    public static final int DATA_STORED_LO = 0;
    public static final int DATA_STORED_HI = 1;
    public static final int DATA_CAPACITY_LO = 2;
    public static final int DATA_CAPACITY_HI = 3;
    public static final int DATA_KIND = 4;
    public static final int DATA_ID = 5;
    public static final int DATA_AUTO_IO = 6;
    public static final int DATA_SIZE = 7;

    public static final int KIND_EMPTY = 0;
    public static final int KIND_FLUID = 1;
    public static final int KIND_CHEMICAL = 2;
    public static final int KIND_ENERGY = 3;

    private static final int PANEL_WIDTH = 176;
    private static final int PLAYER_INV_HEIGHT = 3 * 18 + 4 + 18;

    @Nullable
    private final MultiblockPartBlockEntity part;
    private final MultiblockPartType partType;
    private final int machineSlots;
    private final int columns;
    private final int gridY;
    private final int inventoryY;
    private final int imageHeight;
    private final ContainerData data;
    private final boolean bus;

    /**
     * 服务端构造。
     */
    public HatchMenu(int windowId, Inventory playerInventory, MultiblockPartBlockEntity part) {
        this(windowId, playerInventory, part, new PartData(part),
                part.items() == null ? 0 : part.items().getSlots());
    }

    private HatchMenu(int windowId, Inventory playerInventory, MultiblockPartBlockEntity part, ContainerData data,
                      int requestedSlots) {
        super(MultiblockRegistration.HATCH_MENU.get(), windowId);
        this.part = part;
        this.partType = part.partType();
        this.bus = this.partType == MultiblockPartType.ITEM_INPUT
                || this.partType == MultiblockPartType.ITEM_OUTPUT;
        int handlerSlots = part.items() == null ? 0 : part.items().getSlots();
        // 槽位数由服务端写进缓冲区，客户端不再自行推断，避免容器同步包索引错位。
        this.machineSlots = Math.min(requestedSlots, handlerSlots);
        this.columns = columns(this.machineSlots);
        this.gridY = 18;
        this.inventoryY = this.bus
                ? (this.machineSlots == 0 ? 84 : this.gridY + rows(this.machineSlots) * 18 + 10)
                : 84;
        this.imageHeight = this.inventoryY + PLAYER_INV_HEIGHT + 6;
        this.data = data;

        if (this.bus && this.machineSlots > 0) {
            int startX = gridX(this.columns);
            for (int index = 0; index < this.machineSlots; index++) {
                int column = index % this.columns;
                int row = index / this.columns;
                this.addSlot(new SlotItemHandler(part.items(), index,
                        startX + column * 18, this.gridY + row * 18));
            }
        } else if (!this.bus && this.machineSlots >= 2) {
            if (this.partType == MultiblockPartType.ENERGY_INPUT || this.partType == MultiblockPartType.ENERGY_OUTPUT) {
                // 能量立方：放电槽 17,35 / 充电槽 143,35
                this.addSlot(new SlotItemHandler(part.items(), 0, 17, 35));
                this.addSlot(new SlotItemHandler(part.items(), 1, 143, 35));
            } else {
                // 流体储罐：输入 146,19 / 输出 146,51
                this.addSlot(new SlotItemHandler(part.items(), 0, 146, 19));
                this.addSlot(new SlotItemHandler(part.items(), 1, 146, 51));
            }
        }
        this.addPlayerInventory(playerInventory, this.inventoryY);
        this.addDataSlots(this.data);
    }

    /**
     * 客户端构造：方块位置 + 服务端槽位数，均由缓冲区提供。
     */
    public HatchMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, buf.readBlockPos(), new SimpleContainerData(DATA_SIZE), buf.readVarInt());
    }

    private HatchMenu(int windowId, Inventory playerInventory, BlockPos pos, ContainerData data, int slots) {
        this(windowId, playerInventory, resolve(playerInventory.player, pos), data, slots);
    }

    private static MultiblockPartBlockEntity resolve(Player player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        if (be instanceof MultiblockPartBlockEntity part) {
            return part;
        }
        throw new IllegalStateException("Hatch menu opened for non-hatch block at " + pos);
    }

    private void addPlayerInventory(Inventory playerInventory, int inventoryY) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, 9 + row * 9 + column, 8 + column * 18,
                        inventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, inventoryY + 3 * 18 + 4));
        }
    }

    public static int columns(int slots) {
        return Math.max(1, Math.min(9, (int) Math.ceil(Math.sqrt(Math.max(1, slots)))));
    }

    public static int rows(int slots) {
        return (Math.max(1, slots) + columns(slots) - 1) / columns(slots);
    }

    public static int gridX(int columns) {
        return (PANEL_WIDTH - columns * 18) / 2;
    }

    public int machineSlots() {
        return this.machineSlots;
    }

    public int columns() {
        return this.columns;
    }

    public int gridY() {
        return this.gridY;
    }

    public int inventoryY() {
        return this.inventoryY;
    }

    public int imageHeight() {
        return this.imageHeight;
    }

    public MultiblockPartType partType() {
        return this.partType;
    }

    /**
     * 是否为物品总线；否则是 MEK/能源仓。
     */
    public boolean isBus() {
        return this.bus;
    }

    /**
     * GUI 标题：直接使用仓室方块物品的显示名。
     */
    public net.minecraft.network.chat.Component title() {
        if (this.part == null) {
            return net.minecraft.network.chat.Component.empty();
        }
        return new ItemStack(this.part.getBlockState().getBlock()).getHoverName();
    }

    @Nullable
    public MachineLevel level() {
        return this.part == null ? null : this.part.level();
    }

    public int amp() {
        return this.part == null ? 2 : this.part.amp();
    }

    public int data(int index) {
        return this.data.get(index);
    }

    public long stored() {
        return (long) this.data(DATA_STORED_LO) | ((long) this.data(DATA_STORED_HI) << 32);
    }

    public long capacity() {
        return (long) this.data(DATA_CAPACITY_LO) | ((long) this.data(DATA_CAPACITY_HI) << 32);
    }

    /**
     * 仓室方块坐标，供客户端 Tab 发包使用。
     */
    public BlockPos blockPos() {
        return this.part == null ? BlockPos.ZERO : this.part.getBlockPos();
    }

    /**
     * 自动搬运开关状态（服务端同步）：输出仓是自动弹出，输入仓是自动吸入。
     */
    public boolean autoIo() {
        return this.data(DATA_AUTO_IO) != 0;
    }

    /**
     * 是否为输出仓（决定 Tab 提示文本是「自动弹出」还是「自动吸入」）。
     */
    public boolean isOutputHatch() {
        return this.partType.isOutput();
    }

    @Nullable
    public FluidStack fluid() {
        if (this.data(DATA_KIND) != KIND_FLUID) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.byId(this.data(DATA_ID));
        return fluid == null ? null : new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, this.stored()));
    }

    @Nullable
    public ChemicalStack chemical() {
        if (this.data(DATA_KIND) != KIND_CHEMICAL) {
            return null;
        }
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.byId(this.data(DATA_ID));
        return chemical == null ? null : new ChemicalStack(chemical, this.stored());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < this.machineSlots) {
            if (!this.moveItemStackTo(stack, this.machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (this.machineSlots > 0) {
            if (!this.moveItemStackTo(stack, 0, this.machineSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.part == null) {
            return false;
        }
        return player.distanceToSqr(this.part.getBlockPos().getX() + 0.5,
                this.part.getBlockPos().getY() + 0.5, this.part.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    /**
     * 服务端数据：把 MEK 槽/能量仓内容压缩成 6 个 int。
     */
    private static final class PartData implements ContainerData {

        private final MultiblockPartBlockEntity part;

        private PartData(MultiblockPartBlockEntity part) {
            this.part = part;
        }

        @Override
        public int get(int index) {
            long stored = this.stored();
            long capacity = this.capacity();
            int kind = this.kind();
            int id = this.id(kind);
            return switch (index) {
                case DATA_STORED_LO -> (int) stored;
                case DATA_STORED_HI -> (int) (stored >>> 32);
                case DATA_CAPACITY_LO -> (int) capacity;
                case DATA_CAPACITY_HI -> (int) (capacity >>> 32);
                case DATA_KIND -> kind;
                case DATA_ID -> id;
                case DATA_AUTO_IO -> this.part.autoTransfer() ? 1 : 0;
                default -> 0;
            };
        }

        private long stored() {
            if (this.part.energyStorage() != null) {
                return this.part.energyStorage().getEnergyStored();
            }
            if (this.part.fluidTank() != null && !this.part.fluidTank().isEmpty()) {
                return this.part.fluidTank().getFluidAmount();
            }
            if (this.part.chemicalTank() != null && this.part.chemicalTank().getStored() > 0L) {
                return this.part.chemicalTank().getStored();
            }
            return 0L;
        }

        private long capacity() {
            if (this.part.energyStorage() != null) {
                return this.part.energyStorage().getMaxEnergyStored();
            }
            if (this.part.fluidTank() != null) {
                return this.part.fluidTank().getCapacity();
            }
            if (this.part.chemicalTank() != null) {
                return this.part.chemicalTank().getCapacity();
            }
            return 0L;
        }

        private int kind() {
            if (this.part.energyStorage() != null) {
                return KIND_ENERGY;
            }
            if (this.part.fluidTank() != null && !this.part.fluidTank().isEmpty()) {
                return KIND_FLUID;
            }
            if (this.part.chemicalTank() != null && this.part.chemicalTank().getStored() > 0L) {
                return KIND_CHEMICAL;
            }
            return KIND_EMPTY;
        }

        private int id(int kind) {
            if (kind == KIND_FLUID && this.part.fluidTank() != null) {
                return BuiltInRegistries.FLUID.getId(this.part.fluidTank().getFluid().getFluid());
            }
            if (kind == KIND_CHEMICAL && this.part.chemicalTank() != null) {
                return MekanismAPI.CHEMICAL_REGISTRY.getId(this.part.chemicalTank().getStack().getChemical());
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    }
}
