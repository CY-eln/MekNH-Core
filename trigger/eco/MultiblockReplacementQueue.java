package c_y.meknh_core.trigger.eco;

import c_y.meknh_core.MekNHCore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = MekNHCore.MODID)
public final class MultiblockReplacementQueue {

    private static final Map<ResourceKey<Level>, List<MultiblockReplacementTask>> PENDING = new HashMap<>();

    private MultiblockReplacementQueue() {
    }

    public static boolean isBusy(Level level, BlockPos controllerPos) {
        List<MultiblockReplacementTask> tasks = PENDING.get(level.dimension());
        if (tasks == null) {
            return false;
        }
        for (MultiblockReplacementTask task : tasks) {
            if (task.controllerPos().equals(controllerPos)) {
                return true;
            }
        }
        return false;
    }

    public static void schedule(ServerLevel level, MultiblockReplacementTask task) {
        PENDING.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(task);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<ResourceKey<Level>, List<MultiblockReplacementTask>>> iterator =
                PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceKey<Level>, List<MultiblockReplacementTask>> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                iterator.remove();
                continue;
            }
            List<MultiblockReplacementTask> tasks = entry.getValue();
            // 每个任务每刻替换一个方块，完成后从列表里移除
            tasks.removeIf(task -> {
                task.tick(level);
                return task.isFinished();
            });
            if (tasks.isEmpty()) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
