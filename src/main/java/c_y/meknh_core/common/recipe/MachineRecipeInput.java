package c_y.meknh_core.common.recipe;

import java.util.Optional;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 机器配方的输入视图：物品槽 + 流体罐 + 化学品罐。
 *
 * <p>配方匹配与消耗都通过本类完成，方块实体只需要把内部的输入容器交给它。
 * 流体与化学品在 JSON 里既可以写注册名，也可以写 {@code #标签}。</p>
 */
public final class MachineRecipeInput implements RecipeInput {

    private final IItemHandler items;
    @Nullable
    private final IFluidHandler fluids;
    @Nullable
    private final IChemicalHandler chemicals;

    public MachineRecipeInput(IItemHandler items, @Nullable IFluidHandler fluids, @Nullable IChemicalHandler chemicals) {
        this.items = items;
        this.fluids = fluids;
        this.chemicals = chemicals;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.getSlots() ? this.items.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return this.items.getSlots();
    }

    public IItemHandler items() {
        return this.items;
    }

    public Optional<IFluidHandler> fluids() {
        return Optional.ofNullable(this.fluids);
    }

    public Optional<IChemicalHandler> chemicals() {
        return Optional.ofNullable(this.chemicals);
    }

    /**
     * 统计所有输入槽里满足条件的物品总数。
     */
    public int countItems(java.util.function.Predicate<ItemStack> predicate) {
        int found = 0;
        for (int slot = 0; slot < this.items.getSlots(); slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * 从输入槽里扣除满足条件的物品，返回是否扣除成功（不足时不扣任何东西）。
     */
    public boolean removeItems(java.util.function.Predicate<ItemStack> predicate, int count) {
        if (this.countItems(predicate) < count) {
            return false;
        }
        int remaining = count;
        for (int slot = 0; slot < this.items.getSlots() && remaining > 0; slot++) {
            ItemStack stack = this.items.getStackInSlot(slot);
            if (stack.isEmpty() || !predicate.test(stack)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            ItemStack extracted = this.items.extractItem(slot, taken, false);
            remaining -= extracted.getCount();
        }
        return remaining <= 0;
    }

    /**
     * 统计输入流体罐里符合要求的流体总量。
     */
    public long countFluid(String spec) {
        if (this.fluids == null) {
            return 0L;
        }
        long found = 0L;
        for (int tank = 0; tank < this.fluids.getTanks(); tank++) {
            FluidStack stack = this.fluids.getFluidInTank(tank);
            if (matchesFluid(stack, spec)) {
                found += stack.getAmount();
            }
        }
        return found;
    }

    /**
     * 扣除输入流体。
     */
    public boolean removeFluid(String spec, long amount) {
        if (this.fluids == null || this.countFluid(spec) < amount) {
            return false;
        }
        long remaining = amount;
        for (int tank = 0; tank < this.fluids.getTanks() && remaining > 0; tank++) {
            FluidStack stack = this.fluids.getFluidInTank(tank);
            if (!matchesFluid(stack, spec)) {
                continue;
            }
            FluidStack drained = this.fluids.drain(
                    stack.copyWithAmount((int) Math.min(Integer.MAX_VALUE, remaining)),
                    IFluidHandler.FluidAction.EXECUTE
            );
            remaining -= drained.getAmount();
        }
        return remaining <= 0L;
    }

    /**
     * 统计输入化学品罐里符合要求的化学品总量。
     */
    public long countChemical(String spec) {
        if (this.chemicals == null) {
            return 0L;
        }
        long found = 0L;
        for (int tank = 0; tank < this.chemicals.getChemicalTanks(); tank++) {
            ChemicalStack stack = this.chemicals.getChemicalInTank(tank);
            if (matchesChemical(stack, spec)) {
                found += stack.getAmount();
            }
        }
        return found;
    }

    /**
     * 扣除输入化学品。
     */
    public boolean removeChemical(String spec, long amount) {
        if (this.chemicals == null || this.countChemical(spec) < amount) {
            return false;
        }
        long remaining = amount;
        for (int tank = 0; tank < this.chemicals.getChemicalTanks() && remaining > 0; tank++) {
            ChemicalStack stack = this.chemicals.getChemicalInTank(tank);
            if (!matchesChemical(stack, spec)) {
                continue;
            }
            ChemicalStack drained = this.chemicals.extractChemical(tank, remaining, Action.EXECUTE);
            remaining -= drained.getAmount();
        }
        return remaining <= 0L;
    }

    /**
     * 判断流体堆是否匹配 {@code 注册名} 或 {@code #标签} 写法。
     */
    public static boolean matchesFluid(FluidStack stack, String spec) {
        if (stack.isEmpty() || spec == null || spec.isBlank()) {
            return false;
        }
        String trimmed = spec.trim();
        if (trimmed.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(trimmed.substring(1));
            if (tagId == null) {
                return false;
            }
            TagKey<net.minecraft.world.level.material.Fluid> tag = TagKey.create(Registries.FLUID, tagId);
            return stack.getFluidHolder().is(tag);
        }
        ResourceLocation id = ResourceLocation.tryParse(trimmed);
        return id != null && BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(id);
    }

    /**
     * 判断化学品堆是否匹配注册名。
     */
    public static boolean matchesChemical(ChemicalStack stack, String spec) {
        if (stack == null || stack.isEmpty() || spec == null || spec.isBlank()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(spec.trim());
        if (id == null) {
            return false;
        }
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(id);
        return chemical != null && chemical != MekanismAPI.EMPTY_CHEMICAL && stack.getChemical() == chemical;
    }

    /**
     * 把输入化学品按类型扣除，供内部逻辑直接使用。
     */
    public static long shrink(IChemicalTank tank, long amount) {
        return tank.shrinkStack(amount, Action.EXECUTE);
    }

    /**
     * 内部电量换算用的蒸汽判断占位，避免其它类直接依赖 Mekanism 的空化学品。
     */
    public static boolean isSteam(ChemicalStack stack) {
        return matchesChemical(stack, "mekanism:steam");
    }

    /**
     * 自动化类型：机器自身运行视为内部消耗。
     */
    public static final AutomationType INTERNAL = AutomationType.INTERNAL;
}
