package c_y.meknh_core.common.recipe;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.common.data.voltage.VoltageTier;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * 机器配方（{@code 机器配方注册.txt}）。
 *
 * <p>JSON 位于 {@code data/<命名空间>/recipe/machine/<机器ID>/*.json}（多方块用
 * {@code recipe/multiblock/<多方块ID>/}），目录只用于组织，实际归属看 {@code id} 字段。字段：</p>
 *
 * <pre>{@code
 * {
 *   "type": "meknh_core:machine",          // 序列化器类型，必需
 *   "id": "alloy_smelter",                 // 目标机器，必需；省略时取目录名
 *   "level": "LV",                         // 最低电压要求，默认 ULV
 *   "time": "400",                         // 可为 "400"（tick）或 "10s"（秒）
 *   "energy": 0.25,                        // 每 tick 电流（A）
 *   "input":  [ ... ],                     // 见 MachineIngredient
 *   "output": [ ... ],                     // 见 MachineOutput.Product，可带概率
 *   "extra_type": ["other_machine"]        // 额外可以执行本配方的机器
 * }
 * }</pre>
 *
 * <p>{@code time} 与 {@code energy} 省略时使用机器 JSON 的默认值。
 * 配方不会在低于自身 {@code level} 的机器上运行。</p>
 */
public class MachineRecipe implements Recipe<MachineRecipeInput> {

    /**
     * 允许 {@code time} 写成数字或 {@code "10s"} 形式的秒数。
     */
    public static final Codec<Integer> TIME_CODEC = Codec.either(Codec.INT, Codec.STRING).xmap(
            either -> either.map(value -> value, MachineRecipe::parseTicks),
            Either::left
    );

    /**
     * 机器配方的默认等级：配方没写 {@code level} 时按最低等级处理。
     */
    public static final VoltageTier DEFAULT_LEVEL = VoltageTier.ULV;

    /**
     * 机器 ID 解析：写成 {@code "alloy_smelter"} 时补上本模组命名空间，
     * 写成 {@code "other_mod:foo"} 时原样使用。
     */
    public static final Codec<ResourceLocation> MACHINE_ID_CODEC = Codec.STRING.xmap(
            MachineRecipe::parseMachineId,
            ResourceLocation::toString
    );
    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            MACHINE_ID_CODEC.fieldOf("id").forGetter(MachineRecipe::machine),
            VoltageTier.CODEC.optionalFieldOf("level", DEFAULT_LEVEL).forGetter(MachineRecipe::level),
            TIME_CODEC.optionalFieldOf("time", -1).forGetter(MachineRecipe::time),
            Codec.DOUBLE.optionalFieldOf("energy", -1.0D).forGetter(MachineRecipe::energy),
            MachineIngredient.CODEC.listOf().optionalFieldOf("input", List.of()).forGetter(MachineRecipe::inputs),
            MachineOutput.Product.CODEC.listOf().optionalFieldOf("output", List.of()).forGetter(MachineRecipe::outputs),
            MACHINE_ID_CODEC.listOf().optionalFieldOf("extra_type", List.of()).forGetter(MachineRecipe::extraTypes),
            Codec.STRING.optionalFieldOf("vanilla", "").forGetter(recipe -> recipe.vanilla() == null ? "" : recipe.vanilla().toString())
    ).apply(instance, MachineRecipe::new));

    private final ResourceLocation machine;
    private final VoltageTier level;
    private final int time;
    private final double energy;
    private final List<MachineIngredient> inputs;
    private final List<MachineOutput.Product> outputs;
    private final List<ResourceLocation> extraTypes;
    @Nullable
    private final ResourceLocation vanilla;

    public MachineRecipe(ResourceLocation machine, VoltageTier level, int time, double energy,
                         List<MachineIngredient> inputs, List<MachineOutput.Product> outputs,
                         List<ResourceLocation> extraTypes) {
        this(machine, level, time, energy, inputs, outputs, extraTypes, null);
    }

    public MachineRecipe(ResourceLocation machine, VoltageTier level, int time, double energy,
                         List<MachineIngredient> inputs, List<MachineOutput.Product> outputs,
                         List<ResourceLocation> extraTypes, String vanilla) {
        if (machine == null) {
            throw new IllegalArgumentException("Machine recipe: 'id' is required");
        }
        this.machine = machine;
        this.level = level == null ? DEFAULT_LEVEL : level;
        this.time = time < 0 ? -1 : Math.max(1, time);
        this.energy = energy < 0.0D ? -1.0D : energy;
        this.inputs = inputs == null ? List.of() : List.copyOf(inputs);
        this.outputs = outputs == null ? List.of() : List.copyOf(outputs);
        this.extraTypes = extraTypes == null ? List.of() : List.copyOf(extraTypes);
        this.vanilla = vanilla == null || vanilla.isBlank() ? null : ResourceLocation.tryParse(vanilla);
        if (this.inputs.isEmpty() && this.outputs.isEmpty() && this.vanilla == null) {
            throw new IllegalArgumentException("Machine recipe for " + machine + ": needs at least one input or output");
        }
    }

    /**
     * 非 {@code null} 时表示这是一条“转发给原版配方类型”的模板配方，
     * 例如电炉的 {@code minecraft:smelting}。
     */
    @Nullable
    public ResourceLocation vanilla() {
        return this.vanilla;
    }

    /**
     * 把 {@code "400"}（tick）或 {@code "10s"}（秒）解析成 tick。
     */
    public static int parseTicks(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return -1;
        }
        try {
            if (trimmed.endsWith("s")) {
                double seconds = Double.parseDouble(trimmed.substring(0, trimmed.length() - 1).trim());
                return Math.max(1, (int)Math.round(seconds * 20.0D));
            }
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Machine recipe: invalid time value '" + value + "'", e);
        }
    }

    /**
     * 解析机器 ID：没有命名空间时补 {@code meknh_core}。
     *
     * <p>这是必须的：{@code ResourceLocation.CODEC} 会把 {@code "alloy_smelter"}
     * 解析成 {@code minecraft:alloy_smelter}，与机器 ID 永远对不上，配方就永远匹配不到。</p>
     */
    public static ResourceLocation parseMachineId(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Machine recipe: 'id' must not be empty");
        }
        if (!trimmed.contains(":")) {
            return ResourceLocation.fromNamespaceAndPath(MekNHCore.MODID, trimmed);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(trimmed);
        if (parsed == null) {
            throw new IllegalArgumentException("Machine recipe: invalid machine id '" + value + "'");
        }
        return parsed;
    }

    public ResourceLocation machine() {
        return this.machine;
    }

    public VoltageTier level() {
        return this.level;
    }

    /**
     * 配方时间（tick）；{@code -1} 表示使用机器默认值。
     */
    public int time() {
        return this.time;
    }

    /**
     * 配方电流（A/t）；{@code -1} 表示使用机器默认值。
     */
    public double energy() {
        return this.energy;
    }

    public List<MachineIngredient> inputs() {
        return this.inputs;
    }

    public List<MachineOutput.Product> outputs() {
        return this.outputs;
    }

    /**
     * 额外可以执行本配方的机器（JSON 的 {@code extra_type}）。
     */
    public List<ResourceLocation> extraTypes() {
        return this.extraTypes;
    }

    /**
     * 本配方是否属于指定机器。
     */
    public boolean belongsTo(ResourceLocation machineId) {
        return this.machine.equals(machineId) || this.extraTypes.contains(machineId);
    }

    /**
     * 机器等级是否足以执行本配方。
     */
    public boolean matchesLevel(VoltageTier machineTier) {
        return machineTier != null && machineTier.level() >= this.level.level();
    }

    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        for (MachineIngredient ingredient : this.inputs) {
            if (!ingredient.test(input)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 确认输出空间足够放下全部必定产出的产物。
     */
    public boolean canFitOutputs(MachineOutput output) {
        for (MachineOutput.Product product : this.outputs) {
            if (product.chance() >= 1.0D && !product.canProduce(output)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 当前输入最多能并行执行多少份本配方。
     */
    public int maxCrafts(MachineRecipeInput input) {
        int max = Integer.MAX_VALUE;
        for (MachineIngredient ingredient : this.inputs) {
            int available;
            if (ingredient instanceof MachineIngredient.Item item) {
                available = item.count() <= 0 ? 0 : input.countItems(item.ingredient()) / item.count();
            } else if (ingredient instanceof MachineIngredient.Fluid fluid) {
                available = (int)Math.min(Integer.MAX_VALUE, input.countFluid(fluid.fluid()) / fluid.amount());
            } else if (ingredient instanceof MachineIngredient.Gas gas) {
                available = (int)Math.min(Integer.MAX_VALUE, input.countChemical(gas.gas()) / gas.amount());
            } else {
                available = 0;
            }
            max = Math.min(max, available);
            if (max <= 0) {
                return 0;
            }
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    /**
     * 确认输出空间足够放下指定并行数的全部必定产出的产物。
     */
    public boolean canFitOutputs(MachineOutput output, int multiplier) {
        for (MachineOutput.Product product : this.outputs) {
            if (product.chance() >= 1.0D && !product.canProduce(output, multiplier)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 消耗全部输入。
     */
    public void consumeInputs(MachineRecipeInput input) {
        this.consumeInputs(input, 1);
    }

    /**
     * 并行消耗多份输入。
     */
    public void consumeInputs(MachineRecipeInput input, int multiplier) {
        for (MachineIngredient ingredient : this.inputs) {
            ingredient.consume(input, multiplier);
        }
    }

    /**
     * 产出全部产物（含概率判定）。
     */
    public void produce(MachineOutput output, net.minecraft.util.RandomSource random) {
        this.produce(output, random, 1);
    }

    /**
     * 并行产出多份产物。
     */
    public void produce(MachineOutput output, net.minecraft.util.RandomSource random, int multiplier) {
        for (MachineOutput.Product product : this.outputs) {
            product.produce(output, random, multiplier);
        }
    }

    @Override
    public ItemStack assemble(MachineRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (MachineIngredient ingredient : this.inputs) {
            if (ingredient instanceof MachineIngredient.Item item) {
                ingredients.add(item.ingredient());
            }
        }
        return ingredients;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MachineRecipes.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return MachineRecipes.TYPE.get();
    }

    /**
     * 物品输入（供查看器使用）。
     */
    public List<SizedIngredient> itemInputs() {
        return this.inputs.stream()
                .filter(ingredient -> ingredient instanceof MachineIngredient.Item)
                .map(ingredient -> {
                    MachineIngredient.Item item = (MachineIngredient.Item) ingredient;
                    return new SizedIngredient(item.ingredient(), item.count());
                })
                .toList();
    }
}
