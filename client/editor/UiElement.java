package c_y.meknh_core.client.editor;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器里的一个界面元素：位置、大小、类型，以及可选的贴图。
 *
 * <p>坐标是相对机器底板左上角的像素坐标，和正式界面用同一套坐标系。
 * 贴图以字符串存进 JSON（Gson 对 {@link ResourceLocation} 的反射不可靠），
 * 运行时再解析成 {@link ResourceLocation}。</p>
 */
public final class UiElement {

    private UiElementKind kind = UiElementKind.CUSTOM;
    /** 自定义名称，空字符串表示用类型名。 */
    private String name = "";
    private int x;
    private int y;
    private int width = 18;
    private int height = 18;
    @Nullable
    private String texture;

    private transient ResourceLocation textureLocation;
    private transient boolean textureParsed;

    public UiElement(UiElementKind kind, int x, int y, int width, int height) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Gson 用。 */
    private UiElement() {
    }

    public UiElementKind kind() {
        return this.kind == null ? UiElementKind.CUSTOM : this.kind;
    }

    /**
     * 自定义名称；没有设置时为空字符串。
     */
    public String name() {
        return this.name == null ? "" : this.name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * 显示名：有自定义名称用名称，否则用类型名。
     */
    public net.minecraft.network.chat.Component displayName() {
        String custom = this.name();
        return custom.isEmpty() ? this.kind().displayName() : net.minecraft.network.chat.Component.literal(custom);
    }

    public int x() {
        return this.x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int y() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int width() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = Math.max(4, width);
    }

    public int height() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = Math.max(4, height);
    }

    @Nullable
    public String texturePath() {
        return this.texture;
    }

    @Nullable
    public ResourceLocation texture() {
        if (!this.textureParsed) {
            this.textureParsed = true;
            this.textureLocation = this.texture == null ? null : ResourceLocation.tryParse(this.texture);
        }
        return this.textureLocation;
    }

    public void setTexture(@Nullable ResourceLocation texture) {
        this.texture = texture == null ? null : texture.toString();
        this.textureLocation = texture;
        this.textureParsed = true;
    }

    public boolean contains(double localX, double localY) {
        return localX >= this.x && localX < this.x + this.width
                && localY >= this.y && localY < this.y + this.height;
    }

    public UiElement copy() {
        UiElement copy = new UiElement(this.kind(), this.x, this.y, this.width, this.height);
        copy.name = this.name();
        copy.texture = this.texture;
        return copy;
    }
}
