package c_y.meknh_core.trigger.eco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class MultiblockOrientation {

    public static final MultiblockOrientation[] ALL = new MultiblockOrientation[8];

    static {
        int index = 0;
        // 不镜像的 4 个旋转优先，保证「结构没被镜像」时能匹配到最自然的朝向
        for (int turns = 0; turns < 4; turns++) {
            ALL[index++] = new MultiblockOrientation(turns, false);
        }
        for (int turns = 0; turns < 4; turns++) {
            ALL[index++] = new MultiblockOrientation(turns, true);
        }
    }

    private final int quarterTurns;

    private final boolean mirrored;

    private MultiblockOrientation(int quarterTurns, boolean mirrored) {
        this.quarterTurns = quarterTurns & 3;
        this.mirrored = mirrored;
    }

    public int quarterTurns() {
        return quarterTurns;
    }

    public boolean isMirrored() {
        return mirrored;
    }

    public int footprintX(int sizeX, int sizeZ) {
        return isQuarterTurned() ? sizeZ : sizeX;
    }

    public int footprintZ(int sizeX, int sizeZ) {
        return isQuarterTurned() ? sizeX : sizeZ;
    }

    private boolean isQuarterTurned() {
        return (quarterTurns & 1) != 0;
    }

    public BlockPos transform(int x, int y, int z, int sizeX, int sizeZ) {
        int dx = mirrored ? sizeX - 1 - x : x;
        int dz = z;
        int rx;
        int rz;
        switch (quarterTurns) {
            case 1 -> {
                rx = sizeZ - 1 - dz;
                rz = dx;
            }
            case 2 -> {
                rx = sizeX - 1 - dx;
                rz = sizeZ - 1 - dz;
            }
            case 3 -> {
                rx = dz;
                rz = sizeX - 1 - dx;
            }
            default -> {
                rx = dx;
                rz = dz;
            }
        }
        return new BlockPos(rx, y, rz);
    }

    public Direction transformDirection(int dx, int dz) {
        int lx = mirrored ? -dx : dx;
        int lz = dz;
        int rx;
        int rz;
        switch (quarterTurns) {
            case 1 -> {
                rx = -lz;
                rz = lx;
            }
            case 2 -> {
                rx = -lx;
                rz = -lz;
            }
            case 3 -> {
                rx = lz;
                rz = -lx;
            }
            default -> {
                rx = lx;
                rz = lz;
            }
        }

        if (rx > 0) {
            return Direction.EAST;
        }
        if (rx < 0) {
            return Direction.WEST;
        }
        return rz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    public String toString() {
        return "turns=" + quarterTurns * 90 + ",mirrored=" + mirrored;
    }
}
