package c_y.meknh_core.api.generator.boiler;

import net.minecraft.util.StringRepresentable;

/**
 * 锅炉压力版本：低压 {@code _ulv}，高压 {@code _ulv_high}。
 */
public enum BoilerTier implements StringRepresentable {
    LOW("ulv", 1),
    HIGH("ulv_high", 0);

    private final String name;
    private final int multiplier;

    BoilerTier(String name, int multiplier) {
        this.name = name;
        this.multiplier = multiplier;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public int fixedMultiplier() {
        return this.multiplier;
    }
}
