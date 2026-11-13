package c_y.meknh_core.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 配方原料：物品、流体或 Mekanism 化学品（{@code 机器配方注册.txt}）。
 *
 * <p>JSON 写法（{@code input} 数组的每一项）：</p>
 *
 * <pre>{@code
 * { "type": "item",  "ingredient": "minecraft:iron_ingot", "count": 1 }
 * { "type": "item",  "ingredient": { "tag": "c:ingots/iron" }, "count": 2 }
 * { "type": "fluid", "fluid": "minecraft:water", "amount": 1000 }
 * { "type": "gas",   "gas": "mekanism:oxygen",   "amount": 100 }
 * }</pre>
 *
 * <p>{@code count} 默认 1，{@code amount} 默认 1000。流体与化学品也支持 {@code #标签} 写法。</p>
 */
public sealed interface MachineIngredient {

    /**
     * 物品原料的类型名。
     */
    String TYPE_ITEM = "item";
    /**
     * 流体原料的类型名。
     */
    String TYPE_FLUID = "fluid";
    /**
     * 化学品原料的类型名。
     */
    String TYPE_GAS = "gas";

    Codec<MachineIngredient> CODEC = Codec.STRING.dispatch("type", MachineIngredient::type, MachineIngredient::codecFor);

    /**
     * 原料类型名，用于 JSON 的 {@code type} 字段。
     */
    String type();

    /**
     * 输入是否满足这条原料。
     */
    boolean test(MachineRecipeInput input);

    /**
     * 从输入中扣除这条原料，调用前必须确保 {@link #test} 通过。
     */
    void consume(MachineRecipeInput input);

    /**
     * 并行处理时一次扣除多份原料。
     */
    default void consume(MachineRecipeInput input, int multiplier) {
        for (int i = 0; i < multiplier; i++) {
            consume(input);
        }
    }

    /**
     * 用于提示文本的简短描述。
     */
    Component describe();

    static MapCodec<? extends MachineIngredient> codecFor(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case TYPE_ITEM -> Item.CODEC;
            case TYPE_FLUID -> Fluid.CODEC;
            case TYPE_GAS, "chemical" -> Gas.CODEC;
            default -> throw new IllegalArgumentException("Unknown machine ingredient type '" + type + "'");
        };
    }

    /**
     * 物品原料。
     */
    record Item(Ingredient ingredient, int count) implements MachineIngredient {

        private static final Codec<Ingredient> INGREDIENT_CODEC = Codec.either(Ingredient.CODEC, ResourceLocation.CODEC)
                .xmap(
                        either -> either.map(java.util.function.Function.identity(), id -> Ingredient.of(BuiltInRegistries.ITEM.get(id))),
                        com.mojang.datafixers.util.Either::left
                );

        public static final MapCodec<Item> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                INGREDIENT_CODEC.fieldOf("ingredient").forGetter(Item::ingredient),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(Item::count)
        ).apply(instance, Item::new));

        public Item {
            if (count < 1) {
                throw new IllegalArgumentException("Machine ingredient 'item': count must be at least 1");
            }
        }

        @Override
        public String type() {
            return TYPE_ITEM;
        }

        @Override
        public boolean test(MachineRecipeInput input) {
            return input.countItems(this.ingredient) >= this.count;
        }

        @Override
        public void consume(MachineRecipeInput input) {
            input.removeItems(this.ingredient, this.count);
        }

        @Override
        public Component describe() {
            return Component.literal(this.count + "x ")
                    .append(Component.translatable(this.ingredient.getItems()[0].getDescriptionId()));
        }
    }

    /**
     * 流体原料。
     */
    record Fluid(String fluid, int amount) implements MachineIngredient {

        public static final MapCodec<Fluid> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("fluid").forGetter(Fluid::fluid),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", 1000).forGetter(Fluid::amount)
        ).apply(instance, Fluid::new));

        public Fluid {
            if (amount < 1) {
                throw new IllegalArgumentException("Machine ingredient 'fluid': amount must be at least 1");
            }
        }

        @Override
        public String type() {
            return TYPE_FLUID;
        }

        @Override
        public boolean test(MachineRecipeInput input) {
            return input.countFluid(this.fluid) >= this.amount;
        }

        @Override
        public void consume(MachineRecipeInput input) {
            input.removeFluid(this.fluid, this.amount);
        }

        @Override
        public Component describe() {
            return Component.literal(this.amount + "mB " + this.fluid);
        }
    }

    /**
     * 化学品原料（Mekanism 气体等）。
     */
    record Gas(String gas, int amount) implements MachineIngredient {

        public static final MapCodec<Gas> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("gas").forGetter(Gas::gas),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", 1000).forGetter(Gas::amount)
        ).apply(instance, Gas::new));

        public Gas {
            if (amount < 1) {
                throw new IllegalArgumentException("Machine ingredient 'gas': amount must be at least 1");
            }
        }

        @Override
        public String type() {
            return TYPE_GAS;
        }

        @Override
        public boolean test(MachineRecipeInput input) {
            return input.countChemical(this.gas) >= this.amount;
        }

        @Override
        public void consume(MachineRecipeInput input) {
            input.removeChemical(this.gas, this.amount);
        }

        @Override
        public Component describe() {
            return Component.literal(this.amount + "mB " + this.gas);
        }
    }
}
