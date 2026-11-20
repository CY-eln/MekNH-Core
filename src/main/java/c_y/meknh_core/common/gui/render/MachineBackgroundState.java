package c_y.meknh_core.common.gui.render;

/** Presentation only; the server's running flag is authoritative, even after consuming the last energy unit. */
public enum MachineBackgroundState {
    UNPOWERED,
    IDLE,
    WORKING;

    public static MachineBackgroundState fromSynced(boolean running, long storedPower) {
        if (running) {
            return WORKING;
        }
        return storedPower > 0 ? IDLE : UNPOWERED;
    }
}
