package c_y.meknh_core.common.machine.steam;

import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.registries.MekanismChemicals;

/**
 * 蒸汽机器的蒸汽缓存（{@code 运行，超频与能耗.txt}）。
 *
 * <p>两种蒸汽机器都是 16000 mB；蒸汽通过 Mekanism 的化学品能力输入，
 * 只接受 {@code mekanism:steam}，不允许抽出。</p>
 */
public final class SteamTank {

    private SteamTank() {
    }

    /**
     * 创建只进不出的蒸汽罐。
     *
     * @param capacity  容量（mB）
     * @param onChanged 内容变化回调
     */
    public static IChemicalTank create(long capacity, Runnable onChanged) {
        if (capacity <= 0L) {
            throw new IllegalArgumentException("Steam tank capacity must be greater than zero");
        }
        return BasicChemicalTank.createModern(
                capacity,
                stack -> false,
                stack -> stack.is(MekanismChemicals.STEAM),
                onChanged::run
        );
    }
}
