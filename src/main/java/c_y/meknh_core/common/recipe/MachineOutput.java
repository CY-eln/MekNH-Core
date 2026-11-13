package c_y.meknh_core.common.recipe;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 机器配方的产物入口。方块实体实现本接口，把产物写进输出槽/输出罐。
 *
 * <p>所有方法都带 {@code simulate} 参数，机器在开始处理前会先模拟一次，
 * 确认输出空间足够，避免产物卡在进度条上。</p>
 */
public interface MachineOutput {

    /**
     * 尝试插入物品，返回实际插入数量。
     */
    int insertItem(ItemStack stack, boolean simulate);

    /**
     * 尝试插入流体，返回实际插入量（mB）。
     */
    long insertFluid(FluidStack stack, boolean simulate);

    /**
     * 尝试插入化学品，返回实际插入量（mB）。
     */
    long insertChemical(ChemicalStack stack, boolean simulate);

    /**
     * 解析物品注册名，找不到时返回 {@code null}。
     */
    @Nullable
    static Item findItem(ResourceLocation id) {
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    /**
     * 解析化学品注册名，找不到时返回 {@code null}。
     */
    @Nullable
    static Chemical findChemical(ResourceLocation id) {
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(id);
        return chemical == null || chemical == MekanismAPI.EMPTY_CHEMICAL ? null : chemical;
    }

    /**
     * 产物的统一接口：{@code chance} 为掉落概率（1.0 表示必定产出）。
     */
    sealed interface Product {

        String TYPE_ITEM = "item";
        String TYPE_FLUID = "fluid";
        String TYPE_GAS = "gas";

        com.mojang.serialization.Codec<Product> CODEC =
                com.mojang.serialization.Codec.STRING.dispatch("type", Product::type, Product::codecFor);

        String type();

        double chance();

        boolean canProduce(MachineOutput output);

        void produce(MachineOutput output, RandomSource random);

        /**
         * 并行模拟：一次检查多份产物是否放得下。
         */
        default boolean canProduce(MachineOutput output, int multiplier) {
            return canProduce(output);
        }

        /**
         * 并行产出：默认逐份调用，保证概率产物独立判定。
         */
        default void produce(MachineOutput output, RandomSource random, int multiplier) {
            for (int i = 0; i < multiplier; i++) {
                produce(output, random);
            }
        }

        Component describe();

        static com.mojang.serialization.MapCodec<? extends Product> codecFor(String type) {
            return switch (type.toLowerCase(java.util.Locale.ROOT)) {
                case TYPE_ITEM -> ItemProduct.CODEC;
                case TYPE_FLUID -> FluidProduct.CODEC;
                case TYPE_GAS, "chemical" -> GasProduct.CODEC;
                default -> throw new IllegalArgumentException("Unknown machine product type '" + type + "'");
            };
        }
    }

    /**
     * 物品产物，例如 {@code {"type":"item","id":"minecraft:iron_block","count":1,"chance":1.0}}。
     */
    record ItemProduct(ResourceLocation id, int count, double chance) implements Product {

        public static final com.mojang.serialization.MapCodec<ItemProduct> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(ItemProduct::id),
                        com.mojang.serialization.Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(ItemProduct::count),
                        com.mojang.serialization.Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(ItemProduct::chance)
                ).apply(instance, ItemProduct::new));

        public ItemProduct {
            if (count < 1) {
                throw new IllegalArgumentException("Machine product 'item': count must be at least 1");
            }
            if (chance <= 0.0D || chance > 1.0D) {
                throw new IllegalArgumentException("Machine product 'item': chance must be in (0, 1]");
            }
        }

        @Override
        public String type() {
            return TYPE_ITEM;
        }

        @Override
        public boolean canProduce(MachineOutput output) {
            return output.insertItem(this.stack(), true) > 0 || this.chance < 1.0D;
        }

        @Override
        public boolean canProduce(MachineOutput output, int multiplier) {
            if (this.chance < 1.0D) {
                return true;
            }
            net.minecraft.world.item.Item item = findItem(this.id);
            if (item == null) {
                return false;
            }
            int total = this.count * multiplier;
            return output.insertItem(new ItemStack(item, total), true) >= total;
        }

        @Override
        public void produce(MachineOutput output, RandomSource random) {
            if (random.nextDouble() < this.chance) {
                output.insertItem(this.stack(), false);
            }
        }

        @Override
        public void produce(MachineOutput output, RandomSource random, int multiplier) {
            if (this.chance >= 1.0D) {
                net.minecraft.world.item.Item item = findItem(this.id);
                if (item != null) {
                    output.insertItem(new ItemStack(item, this.count * multiplier), false);
                }
            } else {
                Product.super.produce(output, random, multiplier);
            }
        }

        private ItemStack stack() {
            net.minecraft.world.item.Item item = findItem(this.id);
            return item == null ? ItemStack.EMPTY : new ItemStack(item, this.count);
        }

        @Override
        public Component describe() {
            net.minecraft.world.item.Item item = findItem(this.id);
            Component name = item == null ? Component.literal(this.id.toString()) : Component.translatable(item.getDescriptionId());
            String suffix = this.chance >= 1.0D ? "" : " (" + Math.round(this.chance * 100.0D) + "%)";
            return Component.literal(this.count + "x ").append(name).append(Component.literal(suffix));
        }
    }

    /**
     * 流体产物，例如 {@code {"type":"fluid","fluid":"minecraft:water","amount":1000,"chance":1.0}}。
     */
    record FluidProduct(String fluid, int amount, double chance) implements Product {

        public static final com.mojang.serialization.MapCodec<FluidProduct> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        com.mojang.serialization.Codec.STRING.fieldOf("fluid").forGetter(FluidProduct::fluid),
                        com.mojang.serialization.Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", 1000).forGetter(FluidProduct::amount),
                        com.mojang.serialization.Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(FluidProduct::chance)
                ).apply(instance, FluidProduct::new));

        public FluidProduct {
            if (amount < 1) {
                throw new IllegalArgumentException("Machine product 'fluid': amount must be at least 1");
            }
            if (chance <= 0.0D || chance > 1.0D) {
                throw new IllegalArgumentException("Machine product 'fluid': chance must be in (0, 1]");
            }
        }

        @Override
        public String type() {
            return TYPE_FLUID;
        }

        @Override
        public boolean canProduce(MachineOutput output) {
            return output.insertFluid(this.stack(), true) > 0L || this.chance < 1.0D;
        }

        @Override
        public boolean canProduce(MachineOutput output, int multiplier) {
            if (this.chance < 1.0D) {
                return true;
            }
            FluidStack stack = this.stackWithAmount(this.amount * multiplier);
            return !stack.isEmpty() && output.insertFluid(stack, true) >= stack.getAmount();
        }

        @Override
        public void produce(MachineOutput output, RandomSource random) {
            if (random.nextDouble() < this.chance) {
                output.insertFluid(this.stack(), false);
            }
        }

        @Override
        public void produce(MachineOutput output, RandomSource random, int multiplier) {
            if (this.chance >= 1.0D) {
                output.insertFluid(this.stackWithAmount(this.amount * multiplier), false);
            } else {
                Product.super.produce(output, random, multiplier);
            }
        }

        private FluidStack stack() {
            return this.stackWithAmount(this.amount);
        }

        private FluidStack stackWithAmount(int amount) {
            ResourceLocation id = ResourceLocation.tryParse(this.fluid);
            if (id == null) {
                return FluidStack.EMPTY;
            }
            net.minecraft.world.level.material.Fluid fluid = BuiltInRegistries.FLUID.get(id);
            return new FluidStack(fluid, amount);
        }

        @Override
        public Component describe() {
            return Component.literal(this.amount + "mB " + this.fluid);
        }
    }

    /**
     * 化学品产物，例如 {@code {"type":"gas","gas":"mekanism:hydrogen","amount":100,"chance":0.5}}。
     */
    record GasProduct(String gas, int amount, double chance) implements Product {

        public static final com.mojang.serialization.MapCodec<GasProduct> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        com.mojang.serialization.Codec.STRING.fieldOf("gas").forGetter(GasProduct::gas),
                        com.mojang.serialization.Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("amount", 1000).forGetter(GasProduct::amount),
                        com.mojang.serialization.Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 1.0D).forGetter(GasProduct::chance)
                ).apply(instance, GasProduct::new));

        public GasProduct {
            if (amount < 1) {
                throw new IllegalArgumentException("Machine product 'gas': amount must be at least 1");
            }
            if (chance <= 0.0D || chance > 1.0D) {
                throw new IllegalArgumentException("Machine product 'gas': chance must be in (0, 1]");
            }
        }

        @Override
        public String type() {
            return TYPE_GAS;
        }

        @Override
        public boolean canProduce(MachineOutput output) {
            return output.insertChemical(this.stack(), true) > 0L || this.chance < 1.0D;
        }

        @Override
        public boolean canProduce(MachineOutput output, int multiplier) {
            if (this.chance < 1.0D) {
                return true;
            }
            ChemicalStack stack = this.stackWithAmount(this.amount * multiplier);
            return !stack.isEmpty() && output.insertChemical(stack, true) >= stack.getAmount();
        }

        @Override
        public void produce(MachineOutput output, RandomSource random) {
            if (random.nextDouble() < this.chance) {
                output.insertChemical(this.stack(), false);
            }
        }

        @Override
        public void produce(MachineOutput output, RandomSource random, int multiplier) {
            if (this.chance >= 1.0D) {
                output.insertChemical(this.stackWithAmount(this.amount * multiplier), false);
            } else {
                Product.super.produce(output, random, multiplier);
            }
        }

        private ChemicalStack stack() {
            return this.stackWithAmount(this.amount);
        }

        private ChemicalStack stackWithAmount(int amount) {
            ResourceLocation id = ResourceLocation.tryParse(this.gas);
            Chemical chemical = id == null ? null : findChemical(id);
            return chemical == null ? ChemicalStack.EMPTY : new ChemicalStack(chemical, amount);
        }

        @Override
        public Component describe() {
            return Component.literal(this.amount + "mB " + this.gas);
        }
    }
}
