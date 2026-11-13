package c_y.meknh_core.devtools;

import c_y.meknh_core.api.machine.MachineDefinition;
import c_y.meknh_core.api.machine.MachineItemHandler;
import c_y.meknh_core.common.data.overclock.Overclock;
import c_y.meknh_core.common.data.voltage.MachineLevel;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import c_y.meknh_core.common.machine.steam.SteamVariant;
import c_y.meknh_core.common.recipe.MachineIngredient;
import c_y.meknh_core.common.recipe.MachineOutput;
import c_y.meknh_core.common.recipe.MachineRecipe;
import c_y.meknh_core.common.recipe.MachineRecipeInput;
import c_y.meknh_core.decor.description.MachineNames;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 离线数据探针：不启动游戏，直接校验 {@code data/addon/machine} 与
 * {@code data/<命名空间>/recipe/<机器ID>} 下的 JSON 是否能被正确解析、能否匹配机器。
 *
 * <p>运行：{@code ./gradlew machineProbe}。任何一条 CHECK 失败都会让任务以非 0 退出。</p>
 *
 * <p>覆盖点：机器定义解析、等级展开、内部 ID、超频/时间/能耗公式、配方解析（含 NBT 往返，
 * 即客户端同步用的那条路径）、原料匹配与消耗、产物放进输出槽。</p>
 *
 * <p>注意：探针只加载了原版注册表（没有 Mekanism 等模组），引用模组物品的配方会被标记 SKIP，
 * 结构校验仍然照做。</p>
 */
public final class MachineProbe {

    private static final String MACHINE_DIR = "src/main/resources/data/addon/machine";
    private static final String RECIPE_DIR = "src/main/resources/data/meknh_core/recipe";

    private static int checks;
    private static int failures;
    private static int skipped;

    public static void main(String[] args) throws Exception {
        prepareMinecraftEnvironment();

        Map<ResourceLocation, MachineDefinition> machines = loadMachines();
        checkFormulas(machines);
        checkItemHandler();
        checkEnergySerialization();
        checkSyntheticRecipe(machines);

        List<Path> recipes = listJson(RECIPE_DIR);
        if (recipes.isEmpty()) {
            fail("no recipe files found under " + RECIPE_DIR);
        }
        for (Path path : recipes) {
            checkRecipe(path, machines);
        }

        System.out.println();
        System.out.println("checks=" + checks + " failures=" + failures + " skipped=" + skipped);
        if (failures > 0) {
            System.out.println("MACHINE PROBE FAILED");
            System.exit(1);
        }
        System.out.println("MACHINE PROBE PASSED");
    }

    private static void prepareMinecraftEnvironment() {
        // NeoForge 的 FeatureFlagLoader 需要一个非 null 的 LoadingModList，否则 Bootstrap 直接 NPE
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<ResourceLocation, MachineDefinition> loadMachines() throws IOException {
        Map<ResourceLocation, MachineDefinition> machines = new LinkedHashMap<>();
        for (Path path : listJson(MACHINE_DIR)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                ResourceLocation fileId = ResourceLocation.parse("meknh_core:" + stripJson(path.getFileName().toString()));
                MachineDefinition definition = MachineDefinition.parse(fileId, json);
                machines.put(definition.id(), definition);
                System.out.println("[machine] " + definition.id()
                        + " levels=" + definition.levels().size()
                        + " in=" + definition.itemInputSlots()
                        + " out=" + definition.itemOutputSlots()
                        + " fluid=" + definition.fluidInputCapacity() + "/" + definition.fluidOutputCapacity()
                        + " chem=" + definition.chemicalInputCapacity() + "/" + definition.chemicalOutputCapacity()
                        + " time=" + definition.time() + " energy=" + definition.energy());
                check("machine " + definition.id() + " has variants", !definition.levels().isEmpty());
                check("machine " + definition.id() + " has an input", !definition.inputs().isEmpty());
                check("machine " + definition.id() + " has an output", !definition.outputs().isEmpty());
            } catch (RuntimeException e) {
                fail("machine " + path + " failed to parse: " + e.getMessage());
            }
        }
        if (machines.isEmpty()) {
            fail("no machine files found under " + MACHINE_DIR);
        }
        return machines;
    }

    private static void checkFormulas(Map<ResourceLocation, MachineDefinition> machines) {
        MachineDefinition definition = machines.values().stream().findFirst().orElse(null);
        if (definition == null) {
            return;
        }
        MachineLevel lv = MachineLevel.electric(VoltageTier.LV);
        check("LV+ULV => 0 overclocks", Overclock.count(lv, VoltageTier.ULV) == 0);
        check("LV+LV => 0 overclocks", Overclock.count(lv, VoltageTier.LV) == 0);
        check("MV+LV => 1 overclock", Overclock.count(MachineLevel.electric(VoltageTier.MV), VoltageTier.LV) == 1);
        check("0.25A LV => 8 FE/t", Overclock.energyPerTick(0.25D, VoltageTier.LV, 0) == 8L);
        check("0.25A LV x2 => 128 FE/t", Overclock.energyPerTick(0.25D, VoltageTier.LV, 2) == 128L);
        check("400t x1 => 200t", Overclock.ticks(400, 1, lv) == 200);
        check("1t stays >= 1", Overclock.ticks(1, 2, lv) == 1);
        check("low steam 400t => 800t", Overclock.ticks(400, 0, MachineLevel.steam(SteamVariant.LOW)) == 800);
        check("high steam 400t => 200t", Overclock.ticks(400, 0, MachineLevel.steam(SteamVariant.HIGH)) == 200);
        check("low steam 2FE => 2mB", MachineLevel.steam(SteamVariant.LOW).steamPerTick(2L) == 2L);
        check("high steam 2FE => 4mB", MachineLevel.steam(SteamVariant.HIGH).steamPerTick(2L) == 4L);

        check("LV internal id", ("lv_" + definition.id().getPath()).equals(
                MachineNames.internalPath(definition.id(), MachineLevel.electric(VoltageTier.LV))));
        check("low steam internal id", ("lp_steam_" + definition.id().getPath()).equals(
                MachineNames.internalPath(definition.id(), MachineLevel.steam(SteamVariant.LOW))));
    }

    /**
     * 输出槽不能用 {@code ItemStackHandler#insertItem} 插入（它按 isItemValid 校验），
     * 这里把这条约定和 {@link MachineItemHandler#insertOutput} 的可用性一起测出来。
     */
    private static void checkItemHandler() {
        MachineItemHandler handler = new MachineItemHandler(List.of(64, 64), List.of(64), () -> {
        });
        check("2 input slots", handler.inputSlots() == 2);
        check("3 total slots", handler.getSlots() == 3);
        check("input accepts item", handler.isItemValid(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
        check("output rejects foreign insert", !handler.isItemValid(2, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
        check("insertItem cannot fill output",
                handler.insertItem(2, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1), true).getCount() == 1);
        check("insertOutput fills output",
                handler.insertOutput(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3), true) == 3);
        handler.insertOutput(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3), false);
        check("output slot holds 3 ingots", handler.getStackInSlot(2).getCount() == 3);
        check("output can be extracted", !handler.extractItem(2, 1, true).isEmpty());
        check("input cannot be extracted", handler.extractItem(0, 1, true).isEmpty());

        // 自动化视图：输入可插不可抽、输出可抽不可插；内部视图允许玩家取回输入
        var external = handler.externalView();
        handler.setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 4));
        check("external can insert into input",
                external.insertItem(0, new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 1), true).getCount() == 0);
        check("external cannot insert into output",
                external.insertItem(2, new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 1), true).getCount() == 1);
        check("external cannot extract input", external.extractItem(0, 1, true).isEmpty());
        check("external can extract output", !external.extractItem(2, 1, true).isEmpty());
        check("internal can take input back (GUI)", !handler.extractItem(0, 1, true).isEmpty());
    }

    /**
     * 用原版物品搭一份和真实配方同构的配方（2 输入 / 1 输出 / 有概率字段），
     * 走一遍匹配 → 输出空间 → 扣料 → 产出，确保整条链路不依赖模组物品。
     */
    private static void checkSyntheticRecipe(Map<ResourceLocation, MachineDefinition> machines) {
        MachineDefinition definition = machines.get(ResourceLocation.parse("meknh_core:alloy_smelter"));
        if (definition == null) {
            return;
        }
        MachineRecipe recipe = new MachineRecipe(
                ResourceLocation.parse("meknh_core:alloy_smelter"),
                VoltageTier.ULV,
                400,
                0.25D,
                List.of(
                        new MachineIngredient.Item(Ingredient.of(net.minecraft.world.item.Items.COPPER_INGOT), 3),
                        new MachineIngredient.Item(Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT), 1)
                ),
                List.of(new MachineOutput.ItemProduct(ResourceLocation.parse("minecraft:gold_ingot"), 4, 1.0D)),
                List.of()
        );

        MachineItemHandler inventory = new MachineItemHandler(
                definition.itemInputLimits(), definition.itemOutputLimits(), () -> {
        });
        inventory.setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 3));
        inventory.setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1));
        MachineRecipeInput input = new MachineRecipeInput(new InputView(inventory), null, null);

        check("synthetic recipe matches", recipe.matches(input, null));
        check("synthetic recipe output fits", recipe.canFitOutputs(new ProbeOutput(inventory)));
        recipe.consumeInputs(input);
        check("synthetic recipe empties inputs",
                inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty());
        recipe.produce(new ProbeOutput(inventory), RandomSource.create(7L));
        ItemStack outputSlot = inventory.getStackInSlot(2);
        check("synthetic recipe produced 4 gold ingots",
                outputSlot.is(net.minecraft.world.item.Items.GOLD_INGOT) && outputSlot.getCount() == 4);
    }

    /**
     * FE 缓存的 NBT 回归：NeoForge 的 {@code EnergyStorage} 序列化出来是数值 Tag，
     * 用 {@code getCompound} 读会抛异常并让客户端在世界加载时断线。
     */
    private static void checkEnergySerialization() {
        c_y.meknh_core.common.machine.energy.MachineEnergyStorage storage =
                new c_y.meknh_core.common.machine.energy.MachineEnergyStorage(2_048L, () -> {
                });
        storage.setEnergyStored(777L);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("Energy", storage.serializeNBT(net.minecraft.core.RegistryAccess.EMPTY));
        check("energy tag is numeric", tag.get("Energy") instanceof net.minecraft.nbt.NumericTag);
        check("energy tag reads 777", tag.getInt("Energy") == 777);

        c_y.meknh_core.common.machine.energy.MachineEnergyStorage restored =
                new c_y.meknh_core.common.machine.energy.MachineEnergyStorage(2_048L, () -> {
                });
        restored.setEnergyStored(tag.getInt("Energy"));
        check("energy restored from int tag", restored.getEnergyStored() == 777);
    }

    private static void checkRecipe(Path path, Map<ResourceLocation, MachineDefinition> machines) {        MachineRecipe recipe;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            recipe = MachineRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        } catch (IOException | RuntimeException e) {
            fail("recipe " + path + " failed to parse: " + e.getMessage());
            return;
        }

        System.out.println("[recipe] " + path.getParent().getFileName() + "/" + path.getFileName()
                + " -> " + recipe.machine() + " level=" + recipe.level().getSerializedName()
                + " time=" + recipe.time() + " energy=" + recipe.energy()
                + " in=" + recipe.inputs().size() + " out=" + recipe.outputs().size());

        MachineDefinition definition = machines.get(recipe.machine());
        if (definition == null) {
            fail("recipe " + path + " targets unknown machine " + recipe.machine());
            return;
        }
        check("recipe " + path.getFileName() + " belongs to its machine", recipe.belongsTo(definition.id()));

        MachineLevel runnable = definition.levels().stream()
                .filter(level -> recipe.matchesLevel(level.recipeTier()))
                .findFirst().orElse(null);
        if (runnable == null) {
            fail("recipe " + path + " needs level " + recipe.level().getSerializedName()
                    + " but no variant of " + definition.id() + " reaches it");
            return;
        }

        boolean needsFluid = recipe.inputs().stream().anyMatch(i -> i instanceof MachineIngredient.Fluid);
        boolean needsGas = recipe.inputs().stream().anyMatch(i -> i instanceof MachineIngredient.Gas);
        boolean outputsFluid = recipe.outputs().stream().anyMatch(p -> p instanceof MachineOutput.FluidProduct);
        boolean outputsGas = recipe.outputs().stream().anyMatch(p -> p instanceof MachineOutput.GasProduct);
        check("recipe " + path.getFileName() + " fluid input supported",
                !needsFluid || definition.fluidInputCapacity() > 0L);
        check("recipe " + path.getFileName() + " gas input supported",
                !needsGas || definition.chemicalInputCapacity() > 0L);
        check("recipe " + path.getFileName() + " fluid output supported",
                !outputsFluid || definition.fluidOutputCapacity() > 0L);
        check("recipe " + path.getFileName() + " gas output supported",
                !outputsGas || definition.chemicalOutputCapacity() > 0L);

        // 构造一份刚好的输入，验证匹配、消耗与产出空间
        MachineItemHandler inventory = new MachineItemHandler(
                definition.itemInputLimits(), definition.itemOutputLimits(), () -> {
        });
        List<ItemStack> required = new ArrayList<>();
        boolean allVanilla = true;
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (!(ingredient instanceof MachineIngredient.Item item)) {
                continue;
            }
            ItemStack[] items = item.ingredient().getItems();
            if (items.length == 0 || items[0].isEmpty()) {
                allVanilla = false;
                break;
            }
            required.add(new ItemStack(items[0].getItem(), item.count()));
        }
        if (!allVanilla) {
            skipped++;
            System.out.println("         SKIP input simulation (recipe uses items from a mod that is not loaded)");
            return;
        }

        int slot = 0;
        for (ItemStack stack : required) {
            if (slot >= definition.itemInputSlots()) {
                break;
            }
            inventory.setStackInSlot(slot++, stack);
        }
        MachineRecipeInput input = new MachineRecipeInput(new InputView(inventory), null, null);
        boolean matches = recipe.matches(input, null);
        check("recipe " + path.getFileName() + " matches its own inputs", matches);
        if (!matches) {
            return;
        }

        ProbeOutput output = new ProbeOutput(inventory);
        check("recipe " + path.getFileName() + " output fits", recipe.canFitOutputs(output));
        recipe.consumeInputs(input);
        boolean consumed = true;
        for (int index = 0; index < definition.itemInputSlots(); index++) {
            consumed &= inventory.getStackInSlot(index).isEmpty();
        }
        check("recipe " + path.getFileName() + " consumes inputs", consumed);
        recipe.produce(output, RandomSource.create(1234L));
        check("recipe " + path.getFileName() + " produces something",
                !recipe.outputs().isEmpty());

        // NBT 往返：客户端同步用的就是这条 Codec 路径
        var nbt = MachineRecipe.CODEC.codec().encodeStart(NbtOps.INSTANCE, recipe).getOrThrow();
        MachineRecipe roundTrip = MachineRecipe.CODEC.codec().parse(NbtOps.INSTANCE, nbt).getOrThrow();
        check("recipe " + path.getFileName() + " survives nbt roundtrip",
                roundTrip.machine().equals(recipe.machine())
                        && roundTrip.inputs().size() == recipe.inputs().size()
                        && roundTrip.level() == recipe.level());
    }

    private static List<Path> listJson(String directory) throws IOException {
        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static String stripJson(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    private static void fail(String message) {
        checks++;
        failures++;
        System.out.println("FAIL " + message);
    }

    /**
     * 只暴露输入槽的物品视图（与机器内部实现一致）。
     */
    private record InputView(MachineItemHandler handler) implements net.neoforged.neoforge.items.IItemHandler {

        @Override
        public int getSlots() {
            return this.handler.inputSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < this.getSlots() ? this.handler.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= 0 && slot < this.getSlots() ? this.handler.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= this.getSlots()) {
                return ItemStack.EMPTY;
            }
            ItemStack current = this.handler.getStackInSlot(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, current.getCount());
            ItemStack result = current.copyWithCount(taken);
            if (!simulate) {
                this.handler.setStackInSlot(slot, taken >= current.getCount()
                        ? ItemStack.EMPTY
                        : current.copyWithCount(current.getCount() - taken));
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.handler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < this.getSlots();
        }
    }

    /**
     * 产物端：物品走 {@link MachineItemHandler#insertOutput}，与真实机器一致。
     */
    private record ProbeOutput(MachineItemHandler items) implements MachineOutput {

        @Override
        public int insertItem(ItemStack stack, boolean simulate) {
            return this.items.insertOutput(stack, simulate);
        }

        @Override
        public long insertFluid(FluidStack stack, boolean simulate) {
            return 0L;
        }

        @Override
        public long insertChemical(ChemicalStack stack, boolean simulate) {
            return 0L;
        }
    }

    private MachineProbe() {
    }
}
