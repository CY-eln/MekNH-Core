package c_y.meknh_core.common.terminal;

import c_y.meknh_core.MekNHCore;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlock;
import c_y.meknh_core.api.multiblock.MultiblockControllerBlockEntity;
import c_y.meknh_core.api.multiblock.MultiblockDefinition;
import c_y.meknh_core.api.multiblock.MultiblockMatcher;
import c_y.meknh_core.api.multiblock.MultiblockStructure;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 多方块终端：潜行右键控制器，按结构 JSON 自动搭好外壳。
 *
 * <p>创造模式不消耗物品；生存模式先检查背包，缺料时在聊天栏用红字逐条打印
 * “缺少 方块名 x数量”，并且**一块都不放**。默认不放仓室（{@code part:} 位置跳过），
 * 避免自动摆错输入/输出仓。</p>
 */
public final class MultiblockTerminalRegistration {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekNHCore.MODID);

    public static final DeferredItem<Item> TERMINAL = ITEMS.registerItem("multiblock_terminal", Item::new);

    private MultiblockTerminalRegistration() {
    }

    public static synchronized void initialize(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(MultiblockTerminalRegistration::addCreativeTab);
        NeoForge.EVENT_BUS.addListener(MultiblockTerminalRegistration::onRightClickBlock);
    }

    private static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(TERMINAL.get());
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(TERMINAL.get())) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller)) {
            return;
        }
        // 只在服务端执行，客户端不 cancel（避免阻断发包）。
        if (level instanceof ServerLevel serverLevel && event.getEntity() instanceof ServerPlayer player) {
            build(serverLevel, player, controller);
            event.setCanceled(true);
        }
    }

    /**
     * 按结构 JSON 生成建造计划并放置。
     */
    private static void build(ServerLevel level, ServerPlayer player, MultiblockControllerBlockEntity controller) {
        MultiblockDefinition definition = controller.definition();
        Direction facing = controller.getBlockState().getValue(MultiblockControllerBlock.FACING);

        List<Placement> plan = new ArrayList<>();
        Map<Block, Integer> needed = new LinkedHashMap<>();
        MultiblockStructure structure = definition.structure();
        for (MultiblockStructure.Cell cell : structure.cells()) {
            MultiblockMatcher matcher = cell.matcher();
            BlockPos target = MultiblockStructure.offset(controller.getBlockPos(),
                    cell.dx(), cell.dy(), cell.dz(), facing);
            // 结构要求空气的位置：把挡路的方块清掉，否则永远配不上。
            if (matcher instanceof MultiblockMatcher.Air) {
                if (!level.getBlockState(target).isAir()) {
                    plan.add(new Placement(target, Blocks.AIR));
                }
                continue;
            }
            // 控制器、仓室、任意方块：不自动摆（默认不放仓室），也不覆盖已经合法的位置。
            if (matcher instanceof MultiblockMatcher.Controller
                    || matcher instanceof MultiblockMatcher.Part
                    || matcher instanceof MultiblockMatcher.Any) {
                continue;
            }
            if (structure.accepts(level, target, cell)) {
                continue;
            }
            Block block = resolveBlock(matcher);
            if (block == null || block == Blocks.AIR) {
                continue;
            }
            plan.add(new Placement(target, block));
            needed.merge(block, 1, Integer::sum);
        }

        if (plan.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.meknh_core.multiblock_terminal.complete")
                    .withStyle(ChatFormatting.GREEN), false);
            return;
        }

        if (!player.isCreative()) {
            List<Component> missing = new ArrayList<>();
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                int have = count(player, entry.getKey());
                int lack = entry.getValue() - have;
                if (lack > 0) {
                    missing.add(Component.translatable("message.meknh_core.multiblock_terminal.missing",
                            entry.getKey().getName(), lack).withStyle(ChatFormatting.RED));
                }
            }
            if (!missing.isEmpty()) {
                for (Component line : missing) {
                    player.displayClientMessage(line, false);
                }
                return;
            }
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                consume(player, entry.getKey(), entry.getValue());
            }
        }

        for (Placement placement : plan) {
            if (placement.block() == Blocks.AIR) {
                level.removeBlock(placement.pos(), false);
            } else {
                level.setBlockAndUpdate(placement.pos(), placement.block().defaultBlockState());
            }
        }
        controller.invalidateStructure();
        player.displayClientMessage(Component.translatable("message.meknh_core.multiblock_terminal.built",
                plan.size()).withStyle(ChatFormatting.GREEN), false);
    }

    /**
     * 结构里某个位置应该放什么方块；标签取第一个可用的。
     */
    private static Block resolveBlock(MultiblockMatcher matcher) {
        if (matcher instanceof MultiblockMatcher.Exact exact) {
            return exact.resolve();
        }
        if (matcher instanceof MultiblockMatcher.Tag tag) {
            return BuiltInRegistries.BLOCK.getTag(tag.tag())
                    .flatMap(set -> set.stream().findFirst())
                    .map(Holder::value)
                    .orElse(null);
        }
        return null;
    }

    private static int count(ServerPlayer player, Block block) {
        Item item = block.asItem();
        if (item == Items.AIR) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consume(ServerPlayer player, Block block, int amount) {
        Item item = block.asItem();
        if (item == Items.AIR) {
            return;
        }
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.getInventory().setChanged();
    }

    private record Placement(BlockPos pos, Block block) {
    }
}
