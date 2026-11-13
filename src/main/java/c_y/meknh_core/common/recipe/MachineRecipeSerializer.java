package c_y.meknh_core.common.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * 机器配方的序列化器。
 *
 * <p>JSON 解析直接用 {@link MachineRecipe#CODEC}；网络同步则把配方编码成 NBT 后传输，
 * 这样一套 Codec 可以同时服务数据包加载与客户端同步。</p>
 */
public class MachineRecipeSerializer implements RecipeSerializer<MachineRecipe> {

    private static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public MachineRecipe decode(RegistryFriendlyByteBuf buffer) {
            CompoundTag tag = buffer.readNbt();
            if (tag == null) {
                throw new IllegalStateException("Machine recipe payload is missing");
            }
            return MachineRecipe.CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow(
                    error -> new IllegalStateException("Failed to read machine recipe: " + error)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MachineRecipe recipe) {
            CompoundTag tag = (CompoundTag) MachineRecipe.CODEC
                    .codec()
                    .encodeStart(NbtOps.INSTANCE, recipe)
                    .getOrThrow(error -> new IllegalStateException("Failed to write machine recipe: " + error));
            buffer.writeNbt(tag);
        }
    };

    @Override
    public MapCodec<MachineRecipe> codec() {
        return MachineRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() {
        return STREAM_CODEC;
    }

    /**
     * 供调试使用：把配方编码成 NBT，失败返回 {@code null}。
     */
    @Nullable
    public static CompoundTag toTag(MachineRecipe recipe) {
        return (CompoundTag) MachineRecipe.CODEC
                .codec()
                .encodeStart(NbtOps.INSTANCE, recipe)
                .result()
                .orElse(null);
    }
}
