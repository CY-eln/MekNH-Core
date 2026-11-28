import c_y.meknh_core.client.structure.StructureViewState;
import java.util.HashSet;
import java.util.Random;

/** Headless regression of the actual first-playable preview state. */
public class StructureViewStateProbe {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        StructureViewState state = new StructureViewState();
        float initialYaw = state.yaw(), initialPitch = state.pitch(), initialZoom = state.zoom();
        check(state.layer() == -1, "default all layers");
        Random random = new Random(0x4D454B);
        for (int i = 0; i < 20000; i++) {
            state.rotate(random.nextDouble() * 20000 - 10000, random.nextDouble() * 20000 - 10000);
            state.zoom(random.nextDouble() * 200 - 100);
            check(Float.isFinite(state.yaw()) && Math.abs(state.yaw()) < 360, "wrapped yaw");
            check(Float.isFinite(state.pitch()) && state.pitch() >= -85 && state.pitch() <= 85, "clamped pitch");
            check(Float.isFinite(state.zoom()) && state.zoom() >= 0.25F && state.zoom() <= 4, "clamped zoom");
        }
        state.zoom(Double.MAX_VALUE);
        check(state.zoom() == 4, "extreme zoom in");
        state.zoom(-Double.MAX_VALUE);
        check(state.zoom() == 0.25F, "extreme zoom out");
        for (int height : new int[] {1, 3, 4, 20}) {
            state.reset();
            HashSet<Integer> seen = new HashSet<>();
            for (int i = 0; i <= height; i++) {
                check(seen.add(state.layer()), "unique layer per cycle");
                check(state.layer() >= -1 && state.layer() < height, "layer bounds");
                state.cycleLayer(height);
            }
            check(state.layer() == -1 && seen.size() == height + 1, "complete layer cycle");
        }
        for (int height = 1; height <= 128; height++) {
            check(StructureViewState.stepLayer(-1, height, -1) == height - 1, "back from all is top");
            check(StructureViewState.stepLayer(height - 1, height, 1) == -1, "forward from top is all");
            for (int layer = -1; layer < height; layer++) {
                int next = StructureViewState.stepLayer(layer, height, 1);
                check(StructureViewState.stepLayer(next, height, -1) == layer, "layer forward/back inverse");
            }
        }
        state.cycleLayer(0);
        check(state.layer() == -1, "empty structure layers");
        state.cycleLayer(-1);
        check(state.layer() == -1, "invalid height layers");
        state.reset();
        check(state.yaw() == initialYaw && state.pitch() == initialPitch && state.zoom() == initialZoom && state.layer() == -1, "reset everything");
        System.out.println("PASS StructureViewState: " + checks + " real production state checks");
    }
}
