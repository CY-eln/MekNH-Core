package c_y.meknh_core.common.blast;

/** Container property packets transport signed shorts; mask both halves on receipt. */
public final class BlastProgressSync {
    private BlastProgressSync() { }
    public static int low(int value) { return value & 0xFFFF; }
    public static int high(int value) { return (value >>> 16) & 0xFFFF; }
    public static int join(int low, int high) { return (low & 0xFFFF) | ((high & 0xFFFF) << 16); }
}
