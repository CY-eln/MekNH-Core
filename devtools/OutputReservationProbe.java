import c_y.meknh_core.api.machine.MachineItemHandler;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.recipe.MachineOutput;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.common.recipe.OutputReservation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Offline integration of the real recipe/reservation/handler implementations, NOT a BE tick test.
 * Run with: gradlew -I devtools/output-reservation-probe.init.gradle outputReservationProbe
 * Uses MachineProbe's NeoForge-aware vanilla bootstrap. Chemical fixtures are registered only
 * in this isolated JVM; no mod lifecycle or world is started. --skip-chemical is an explicitly
 * partial diagnostic run, never a full PASS. No energy/progress behavior is asserted here.
 */
public final class OutputReservationProbe {
    private static int checks;

    public static void main(String[] args) {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        checkItems();
        checkFluids();
        if (Arrays.asList(args).contains("--skip-chemical")) {
            System.out.println("PARTIAL: chemical checks explicitly skipped; checks=" + checks);
            return;
        }
        checkChemicals();
        checkHatches();
        checkOutputLimitedParallel();
        System.out.println("OUTPUT RESERVATION PROBE PASSED checks=" + checks
                + " (offline recipe/storage integration only; no BE energy/progress test)");
    }

    private static void checkOutputLimitedParallel() {
        Sink out = sink(List.of(64), null, null);
        MachineRecipe batch = recipe(item("iron_ingot", 16, 1));
        for (int fits = 0; fits <= 4; fits++) {
            int occupied = 64 - fits * 16;
            out.items.setStackInSlot(0, occupied == 0 ? ItemStack.EMPTY : new ItemStack(Items.IRON_INGOT, occupied));
            expect("limit four selects " + fits + " crafts from remaining space", batch.limitParallelByOutputs(out, 4) == fits);
            expect("batch selection leaves real output unchanged " + fits, out.items.getStackInSlot(0).getCount() == occupied);
        }
        expect("input or machine upper limit still respected", batch.limitParallelByOutputs(out, 2) == 2);
        expect("zero upper limit does not start", batch.limitParallelByOutputs(out, 0) == 0);
        Sink multi = sink(List.of(64, 64), null, null);
        multi.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 32));
        multi.items.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 48));
        expect("least available product space limits batch", recipe(item("iron_ingot", 16, 1), item("gold_ingot", 16, 1))
                .limitParallelByOutputs(multi, 4) == 1);
        expect("fluid space reduces limit four to two", recipe(fluid("water", 250, 1))
                .limitParallelByOutputs(sink(List.of(), new FluidTank(600), null), 4) == 2);
        IChemicalTank chemical = BasicChemicalTank.createModern(1100, stack -> true, stack -> true, () -> {});
        expect("chemical space reduces limit four to two", recipe(gas("alpha", 500, 1))
                .limitParallelByOutputs(sink(List.of(), null, chemical), 4) == 2);
    }

    private static void checkItems() {
        MachineOutput.Product iron = item("iron_ingot", 1, 1);
        MachineOutput.Product gold = item("gold_ingot", 1, 1);
        Sink one = sink(List.of(64), null, null);
        expect("different items contend for one empty slot", !recipe(iron, gold).canFitOutputs(one));
        expect("failed check leaves real inventory empty", one.items.getStackInSlot(0).isEmpty());
        Sink two = sink(List.of(64, 64), null, null);
        expect("different items fit two slots", recipe(iron, gold).canFitOutputs(two));
        expect("successful check leaves both slots empty", two.items.getStackInSlot(0).isEmpty()
                && two.items.getStackInSlot(1).isEmpty());
        expect("repeated checks use fresh snapshots", recipe(iron, gold).canFitOutputs(two));
        expect("same item cumulative overflow", !recipe(item("iron_ingot", 40, 1),
                item("iron_ingot", 25, 1)).canFitOutputs(one));
        expect("same item exact capacity", recipe(item("iron_ingot", 40, 1),
                item("iron_ingot", 24, 1)).canFitOutputs(one));
        one.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 63));
        expect("partial item acceptance is insufficient", !recipe(item("iron_ingot", 2, 1)).canFitOutputs(one));
        expect("existing contents preserved", one.items.getStackInSlot(0).getCount() == 63);
        Sink limited = sink(List.of(8), null, null);
        expect("configured slot limit respected", !recipe(item("iron_ingot", 9, 1)).canFitOutputs(limited));
        expect("probability products also reserve full count", !recipe(iron,
                item("gold_ingot", 1, 0.01)).canFitOutputs(sink(List.of(64), null, null)));
        expect("probability count is not expected value", !recipe(item("iron_ingot", 65, 0.01))
                .canFitOutputs(sink(List.of(64), null, null)));
        MachineRecipe batch = recipe(item("iron_ingot", 40, 1));
        expect("one craft fits", batch.canFitOutputs(sink(List.of(64), null, null), 1));
        expect("two crafts do not fit", !batch.canFitOutputs(sink(List.of(64), null, null), 2));
        expect("item multiplier overflow rejected", !recipe(item("iron_ingot", 1 << 30, 1))
                .canFitOutputs(sink(List.of(64), null, null), 4));
        MachineOutput shadow = two.outputSnapshot();
        expect("shadow simulation accepts", shadow.insertItem(new ItemStack(Items.IRON_INGOT, 64), true) == 64);
        expect("shadow simulation does not reserve", shadow.insertItem(new ItemStack(Items.GOLD_INGOT, 64), false) == 64);
        expect("shadow execution reserves", shadow.insertItem(new ItemStack(Items.IRON_INGOT, 64), false) == 64);
        expect("shadow execution accumulates", shadow.insertItem(new ItemStack(Items.DIAMOND), false) == 0);
        expect("shadow writes never touch real output", two.items.getStackInSlot(0).isEmpty()
                && two.items.getStackInSlot(1).isEmpty());
    }

    private static void checkFluids() {
        FluidTank tank = new FluidTank(1000);
        Sink out = sink(List.of(), tank, null);
        expect("fluid partial acceptance rejected", !recipe(fluid("water", 1001, 1)).canFitOutputs(out));
        expect("same fluid cumulative overflow", !recipe(fluid("water", 600, 1), fluid("water", 401, 1)).canFitOutputs(out));
        expect("same fluid exact capacity", recipe(fluid("water", 600, 1), fluid("water", 400, 1)).canFitOutputs(out));
        expect("different fluids mutually exclusive", !recipe(fluid("water", 1, 1), fluid("lava", 1, 1)).canFitOutputs(out));
        expect("probability fluid reserves", !recipe(fluid("water", 600, 1), fluid("water", 401, 0.01)).canFitOutputs(out));
        expect("fluid one craft fits", recipe(fluid("water", 600, 1)).canFitOutputs(out, 1));
        expect("fluid multiple crafts rejected", !recipe(fluid("water", 600, 1)).canFitOutputs(out, 2));
        expect("fluid multiplier overflow rejected", !recipe(fluid("water", 1 << 30, 1)).canFitOutputs(out, 4));
        expect("checks leave real fluid empty", tank.isEmpty());
        tank.fill(new FluidStack(Fluids.WATER, 900), FluidAction.EXECUTE);
        expect("partially occupied fluid capacity", !recipe(fluid("water", 101, 1)).canFitOutputs(out));
        expect("occupied fluid type exclusion", !recipe(fluid("lava", 1, 1)).canFitOutputs(out));
        expect("existing fluid unchanged", tank.getFluidAmount() == 900 && tank.getFluid().getFluid() == Fluids.WATER);
    }

    private static void checkChemicals() {
        // Use real registered chemicals so GasProduct follows its normal registry lookup path.
        Registry.register(MekanismAPI.CHEMICAL_REGISTRY, id("reservation_probe:alpha"),
                new Chemical(ChemicalBuilder.builder(id("mekanism:chemical/chemical"))));
        Registry.register(MekanismAPI.CHEMICAL_REGISTRY, id("reservation_probe:beta"),
                new Chemical(ChemicalBuilder.builder(id("mekanism:chemical/chemical"))));
        IChemicalTank tank = BasicChemicalTank.createModern(1000, stack -> true, stack -> true, () -> {});
        Sink out = sink(List.of(), null, tank);
        expect("chemical partial acceptance rejected", !recipe(gas("alpha", 1001, 1)).canFitOutputs(out));
        expect("same chemical cumulative overflow", !recipe(gas("alpha", 600, 1), gas("alpha", 401, 1)).canFitOutputs(out));
        expect("same chemical exact capacity", recipe(gas("alpha", 600, 1), gas("alpha", 400, 1)).canFitOutputs(out));
        expect("different chemicals mutually exclusive", !recipe(gas("alpha", 1, 1), gas("beta", 1, 1)).canFitOutputs(out));
        expect("probability chemical reserves", !recipe(gas("alpha", 600, 1), gas("alpha", 401, 0.01)).canFitOutputs(out));
        expect("chemical one craft fits", recipe(gas("alpha", 600, 1)).canFitOutputs(out, 1));
        expect("chemical multiple crafts rejected", !recipe(gas("alpha", 600, 1)).canFitOutputs(out, 2));
        expect("chemical large multiplier rejected by capacity", !recipe(gas("alpha", 1 << 30, 1)).canFitOutputs(out, 4));
        expect("checks leave real chemical empty", tank.isEmpty());
        tank.setStack(new ChemicalStack(MachineOutput.findChemical(id("reservation_probe:alpha")), 900));
        expect("partially occupied chemical capacity", !recipe(gas("alpha", 101, 1)).canFitOutputs(out));
        expect("occupied chemical type exclusion", !recipe(gas("beta", 1, 1)).canFitOutputs(out));
        expect("existing chemical unchanged", tank.getStored() == 900
                && tank.getStack().getChemical() == MachineOutput.findChemical(id("reservation_probe:alpha")));
    }

    private static void checkHatches() {
        Chemical alpha = MachineOutput.findChemical(id("reservation_probe:alpha"));
        FluidTank fluidA = new FluidTank(1000);
        IChemicalTank chemicalA = BasicChemicalTank.createModern(1000, stack -> true, stack -> true, () -> {});
        MachineRecipe mixed = recipe(fluid("water", 100, 1), gas("alpha", 100, 1));
        expect("one empty hatch cannot reserve fluid then chemical", !mixed.canFitOutputs(
                OutputReservation.snapshotHatch(null, fluidA, chemicalA)));
        expect("one empty hatch cannot reserve chemical then fluid", !recipe(gas("alpha", 100, 1),
                fluid("water", 100, 1)).canFitOutputs(OutputReservation.snapshotHatch(null, fluidA, chemicalA)));
        expect("failed mixed hatch checks leave real tanks empty", fluidA.isEmpty() && chemicalA.isEmpty());

        FluidTank fluidB = new FluidTank(1000);
        IChemicalTank chemicalB = BasicChemicalTank.createModern(1000, stack -> true, stack -> true, () -> {});
        MachineOutput combined = OutputReservation.combine(List.of(
                OutputReservation.snapshotHatch(null, fluidA, chemicalA),
                OutputReservation.snapshotHatch(null, fluidB, chemicalB)));
        expect("two hatches split fluid and chemical reservations", mixed.canFitOutputs(combined));
        expect("two hatches split reversed product order", recipe(gas("alpha", 100, 1), fluid("water", 100, 1))
                .canFitOutputs(combined));
        expect("combined checks can repeat with fresh shadows", mixed.canFitOutputs(combined));
        expect("combined checks leave all real tanks empty", fluidA.isEmpty() && chemicalA.isEmpty()
                && fluidB.isEmpty() && chemicalB.isEmpty());

        fluidA.setFluid(new FluidStack(Fluids.WATER, 100));
        expect("source fluid blocks hatch chemical reservation", !recipe(gas("alpha", 1, 1))
                .canFitOutputs(OutputReservation.snapshotHatch(null, fluidA, chemicalA)));
        expect("source fluid remains unchanged", fluidA.getFluidAmount() == 100 && chemicalA.isEmpty());
        fluidA.setFluid(FluidStack.EMPTY);
        chemicalA.setStack(new ChemicalStack(alpha, 100));
        expect("source chemical blocks hatch fluid reservation", !recipe(fluid("water", 1, 1))
                .canFitOutputs(OutputReservation.snapshotHatch(null, fluidA, chemicalA)));
        expect("source chemical remains unchanged", fluidA.isEmpty() && chemicalA.getStored() == 100
                && chemicalA.getStack().getChemical() == alpha);

        FluidTank waterOnly = new FluidTank(1000, stack -> stack.getFluid() == Fluids.WATER);
        expect("hatch snapshot preserves fluid validator rejection", !recipe(fluid("lava", 1, 1))
                .canFitOutputs(OutputReservation.snapshotHatch(null, waterOnly, null)));
        expect("hatch snapshot preserves fluid validator acceptance", recipe(fluid("water", 1, 1))
                .canFitOutputs(OutputReservation.snapshotHatch(null, waterOnly, null)));
        expect("validator checks leave real tank empty", waterOnly.isEmpty());

        // Mirror the controller PUMP_HATCH snapshot: its real output path accepts fluids only.
        FluidTank pumpA = new FluidTank(1000);
        FluidTank pumpB = new FluidTank(1000);
        MachineOutput pump = OutputReservation.snapshotHatch(null, pumpA, null);
        expect("fluid-only pump accepts existing water recipe", recipe(fluid("water", 250, 1)).canFitOutputs(pump));
        expect("fluid-only pump rejects chemical products", !recipe(gas("alpha", 1, 1)).canFitOutputs(pump));
        MachineOutput pumps = OutputReservation.combine(List.of(
                pump, OutputReservation.snapshotHatch(null, pumpB, null)));
        expect("two pumps accept cumulative exact fluid capacity", recipe(fluid("water", 1250, 1),
                fluid("water", 750, 1)).canFitOutputs(pumps));
        expect("two pumps reject cumulative fluid overflow", !recipe(fluid("water", 1250, 1),
                fluid("water", 751, 1)).canFitOutputs(pumps));
        expect("pump reservations leave both real tanks unchanged", pumpA.isEmpty() && pumpB.isEmpty());
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static MachineOutput.Product item(String name, int count, double chance) {
        return new MachineOutput.ItemProduct(id("minecraft:" + name), count, chance);
    }
    private static MachineOutput.Product fluid(String name, int amount, double chance) {
        return new MachineOutput.FluidProduct("minecraft:" + name, amount, chance);
    }
    private static MachineOutput.Product gas(String name, int amount, double chance) {
        return new MachineOutput.GasProduct("reservation_probe:" + name, amount, chance);
    }
    private static MachineRecipe recipe(MachineOutput.Product... products) {
        return new MachineRecipe(id("reservation_probe:test"), VoltageTier.ULV, 20, 1,
                List.of(), List.of(products), List.of());
    }
    private static Sink sink(List<Integer> slots, FluidTank fluid, IChemicalTank chemical) {
        return new Sink(new MachineItemHandler(List.of(), slots, () -> {}), fluid, chemical);
    }
    private static void expect(String name, boolean condition) {
        checks++;
        if (!condition) { throw new AssertionError("FAIL " + name); }
        System.out.println("PASS " + name);
    }
    private record Sink(MachineItemHandler items, FluidTank fluid, IChemicalTank chemical) implements MachineOutput {
        @Override public MachineOutput outputSnapshot() {
            return OutputReservation.snapshot(items, fluid, chemical);
        }
        @Override public int insertItem(ItemStack stack, boolean simulate) {
            return items.insertOutput(stack, simulate);
        }
        @Override public long insertFluid(FluidStack stack, boolean simulate) {
            return fluid == null ? 0 : fluid.fill(stack, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
        }
        @Override public long insertChemical(ChemicalStack stack, boolean simulate) {
            return chemical == null ? 0 : stack.getAmount() - chemical.insert(stack,
                    simulate ? Action.SIMULATE : Action.EXECUTE, AutomationType.INTERNAL).getAmount();
        }
    }
    private OutputReservationProbe() {}
}
