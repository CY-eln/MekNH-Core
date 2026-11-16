package c_y.meknh_core.api.multiblock;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 服务端多方块结构方块占用表。
 *
 * <p>空气和仓室位置不登记；仓室互斥由控制器在绑定前单独检查。只有结构定义
 * 使用 {@code "share": "true"}，且已有结构也允许共享时，同一个结构方块才能被多个控制器使用。</p>
 */
public final class MultiblockStructureClaims {

    private static final Map<Level, Claims> CLAIMS = Collections.synchronizedMap(new WeakHashMap<>());

    private MultiblockStructureClaims() {
    }

    public static boolean canClaim(Level level, BlockPos controller, boolean share, Set<BlockPos> cells) {
        Claims claims = CLAIMS.get(level);
        if (claims == null) {
            return true;
        }
        return claims.canClaim(controller, share, cells);
    }

    public static void claim(Level level, BlockPos controller, boolean share, Set<BlockPos> cells) {
        CLAIMS.computeIfAbsent(level, ignored -> new Claims()).claim(controller, share, cells);
    }

    public static void release(Level level, BlockPos controller) {
        if (level == null) {
            return;
        }
        Claims claims = CLAIMS.get(level);
        if (claims != null) {
            claims.release(controller);
        }
    }

    private static final class Claims {

        private final Map<BlockPos, Map<BlockPos, Boolean>> owners = new HashMap<>();

        synchronized boolean canClaim(BlockPos controller, boolean share, Set<BlockPos> cells) {
            for (BlockPos pos : cells) {
                Map<BlockPos, Boolean> atPos = this.owners.get(pos);
                if (atPos == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, Boolean> owner : atPos.entrySet()) {
                    if (owner.getKey().equals(controller)) {
                        continue;
                    }
                    if (!share || !owner.getValue()) {
                        return false;
                    }
                }
            }
            return true;
        }

        synchronized void claim(BlockPos controller, boolean share, Set<BlockPos> cells) {
            for (BlockPos pos : cells) {
                this.owners.computeIfAbsent(pos, ignored -> new HashMap<>()).put(controller, share);
            }
        }

        synchronized void release(BlockPos controller) {
            Set<BlockPos> empty = new HashSet<>();
            for (Map.Entry<BlockPos, Map<BlockPos, Boolean>> entry : this.owners.entrySet()) {
                entry.getValue().remove(controller);
                if (entry.getValue().isEmpty()) {
                    empty.add(entry.getKey());
                }
            }
            for (BlockPos pos : empty) {
                this.owners.remove(pos);
            }
        }
    }
}
