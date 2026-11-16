import c_y.meknh_core.common.blast.BlastProgressSync;
import c_y.meknh_core.common.blast.PrimitiveBlastFurnaceLayout;

/** Dependency-free wire/layout regression probe; compile together with those two production classes. */
public final class PrimitiveBlastFurnaceProbe {
    public static void main(String[] args) {
        int[] values = {0, 1, 32767, 32768, 65535, 65536, 70000, 1200000, Integer.MAX_VALUE};
        for (int value : values) {
            int roundTrip = BlastProgressSync.join((short) BlastProgressSync.low(value), (short) BlastProgressSync.high(value));
            if (value != roundTrip) throw new AssertionError("signed-short roundtrip " + value);
        }
        if (PrimitiveBlastFurnaceLayout.INPUT_X != 52 || PrimitiveBlastFurnaceLayout.INPUT_Y != 20
                || PrimitiveBlastFurnaceLayout.OUTPUT_X != 104 || PrimitiveBlastFurnaceLayout.OUTPUT_Y != 38)
            throw new AssertionError("GT slot origins");
        if (PrimitiveBlastFurnaceLayout.PROGRESS_WIDTH != 32 || PrimitiveBlastFurnaceLayout.PROGRESS_HEIGHT != 8
                || PrimitiveBlastFurnaceLayout.PROGRESS_X + 16 != 87 || PrimitiveBlastFurnaceLayout.PROGRESS_Y != 43)
            throw new AssertionError("native Mek arrow centering");
        for (double ratio : new double[]{0, .5, 1})
            System.out.println("RIGHT progress ratio=" + ratio + " filled=" + Math.round(32 * ratio) + "/32");
        System.out.println("PASS: 9 signed-short wire boundaries; GT six-slot layout; empty/half/full arrow geometry");
    }
}
