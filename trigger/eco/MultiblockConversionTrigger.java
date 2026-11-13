package c_y.meknh_core.trigger.eco;

import c_y.meknh_core.MekNHCore;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = MekNHCore.MODID)
public final class MultiblockConversionTrigger {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MultiblockConversionTrigger() {
    }

    static {
        StringBuilder summary = new StringBuilder();
        for (EcoMultiblockStructures.Entry entry : EcoMultiblockStructures.RECIPES) {
            MultiblockPattern pattern = entry.pattern();
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append(entry.id()).append(": ")
                    .append(pattern.sizeX()).append('x').append(pattern.sizeY()).append('x').append(pattern.sizeZ())
                    .append(" / ").append(pattern.cellCount()).append('格')
                    .append(" / ").append(pattern.anchorCount()).append("个锚点");
        }
        LOGGER.info("[meknh_core] 多方块替换触发器已加载，触发物品 {}，共 {} 套结构：{}",
                EcoMultiblockStructures.TRIGGER_ITEM, EcoMultiblockStructures.RECIPES.size(), summary);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isTriggerItem(event.getItemStack())) {
            return;
        }
        Player player = event.getEntity();
        if (player.isSpectator()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        List<EcoMultiblockStructures.Entry> matched = new ArrayList<>();
        MultiblockPattern.Placement placement = null;
        for (EcoMultiblockStructures.Entry entry : EcoMultiblockStructures.RECIPES) {
            Optional<MultiblockPattern.Placement> candidate = entry.pattern().findPlacement(level, pos);
            if (candidate.isPresent()) {
                matched.add(entry);
                placement = candidate.get();
            }
        }

        if (matched.isEmpty()) {
            reportMismatch(level, player, pos);
            return;
        }

        if (matched.size() > 1) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            if (!level.isClientSide) {
                List<String> ids = new ArrayList<>();
                for (EcoMultiblockStructures.Entry entry : matched) {
                    ids.add(entry.id());
                }
                LOGGER.warn("位置 {} 同时匹配到多套结构 {}，为避免混用已拒绝执行",
                        pos.toShortString(), ids);
                player.displayClientMessage(Component.translatable("meknh_core.trigger.ambiguous"), true);
            }
            return;
        }

        EcoMultiblockStructures.Entry recipe = matched.get(0);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (MultiblockReplacementQueue.isBusy(serverLevel, pos)) {
            LOGGER.info("结构 {} 位于 {} 的替换任务仍在进行中，忽略这次触发", recipe.id(), pos.toShortString());
            return;
        }

        List<MultiblockPattern.Step> steps = recipe.pattern().plan(placement);
        if (steps.isEmpty()) {
            LOGGER.warn("结构 {} 匹配成功，但替换方块解析失败，未执行替换", recipe.id());
            return;
        }
        LOGGER.info("结构 {} 匹配成功 @{}，朝向 {}，开始逐刻替换 {} 个方块",
                recipe.id(), pos.toShortString(), placement.orientation(), steps.size());
        MultiblockReplacementQueue.schedule(serverLevel,
                new MultiblockReplacementTask(pos, steps));
    }

    private static void reportMismatch(Level level, Player player, BlockPos pos) {
        net.minecraft.world.level.block.Block clicked = level.getBlockState(pos).getBlock();
        MultiblockPattern.Diagnosis best = null;
        String bestId = null;
        for (EcoMultiblockStructures.Entry entry : EcoMultiblockStructures.RECIPES) {
            if (!entry.pattern().isAnchorBlock(clicked)) {
                continue;
            }
            MultiblockPattern.Diagnosis diagnosis = entry.pattern().diagnose(level, pos);
            if (best == null || diagnosis.matched() > best.matched()) {
                best = diagnosis;
                bestId = entry.id();
            }
        }
        if (best == null) {
            return;
        }
        if (level.isClientSide) {
            return;
        }
        LOGGER.info("[{}] {}", bestId, best.detail());
        player.displayClientMessage(Component.translatable("meknh_core.trigger.structure_mismatch",
                bestId, best.matched(), best.total()), true);
    }

    private static boolean isTriggerItem(ItemStack stack) {
        return !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(EcoMultiblockStructures.TRIGGER_ITEM);
    }
}
