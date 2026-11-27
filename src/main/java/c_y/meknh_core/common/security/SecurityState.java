package c_y.meknh_core.common.security;

import java.util.Objects;
import java.util.UUID;
import mekanism.api.security.SecurityMode;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 方块实体的「拥有者 + 安全等级」数据。
 *
 * <p>对齐 Mekanism 的模型：拥有者在**放下时**由放置者写入，物品形态没有拥有者；
 * 安全等级三档 {@link SecurityMode#PUBLIC} / {@link SecurityMode#PRIVATE} / {@link SecurityMode#TRUSTED}，
 * 其中 TRUSTED 依赖 Mekanism 安全终端（Security Desk）里为该拥有者维护的可信玩家列表。</p>
 */
public final class SecurityState {

    /** NBT 键：拥有者 UUID。 */
    public static final String NBT_OWNER = "SecurityOwner";
    /** NBT 键：安全等级（{@code public}/{@code private}/{@code trusted}）。 */
    public static final String NBT_MODE = "SecurityMode";

    private final Runnable onChanged;
    @Nullable
    private UUID owner;
    private SecurityMode mode = SecurityMode.PUBLIC;

    public SecurityState(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Nullable
    public UUID owner() {
        return this.owner;
    }

    public void setOwner(@Nullable UUID owner) {
        if (!Objects.equals(this.owner, owner)) {
            this.owner = owner;
            this.onChanged.run();
        }
    }

    public SecurityMode mode() {
        return this.mode;
    }

    public void setMode(@Nullable SecurityMode mode) {
        SecurityMode target = mode == null ? SecurityMode.PUBLIC : mode;
        if (this.mode != target) {
            this.mode = target;
            this.onChanged.run();
        }
    }

    /**
     * 按 Mekanism 的顺序循环：PUBLIC → PRIVATE → TRUSTED → PUBLIC。
     */
    public void cycleMode() {
        setMode(this.mode.getNext());
    }

    public void save(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID(NBT_OWNER, this.owner);
        }
        tag.putString(NBT_MODE, this.mode.getSerializedName());
    }

    public void load(CompoundTag tag) {
        if (tag.hasUUID(NBT_OWNER)) {
            this.owner = tag.getUUID(NBT_OWNER);
        } else if (tag.hasUUID("Owner")) {
            // 旧存档：机器用 "Owner"
            this.owner = tag.getUUID("Owner");
        } else if (tag.hasUUID("owner")) {
            // 旧存档：锅炉用 "owner"
            this.owner = tag.getUUID("owner");
        } else {
            this.owner = null;
        }
        if (!tag.contains(NBT_MODE)) {
            // 旧存档：只有布尔私有开关
            boolean wasPrivate = tag.getBoolean("PrivateMode") || tag.getBoolean("private");
            this.mode = wasPrivate ? SecurityMode.PRIVATE : SecurityMode.PUBLIC;
            return;
        }
        String raw = tag.getString(NBT_MODE);
        SecurityMode parsed = SecurityMode.PUBLIC;
        for (SecurityMode candidate : SecurityMode.values()) {
            if (candidate.getSerializedName().equals(raw)) {
                parsed = candidate;
                break;
            }
        }
        this.mode = parsed;
    }
}
