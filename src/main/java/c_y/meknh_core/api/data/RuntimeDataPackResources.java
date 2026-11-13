package c_y.meknh_core.api.data;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * 把 {@link RuntimeLootTables} 生成的战利品表包成服务端数据包。
 */
public final class RuntimeDataPackResources implements PackResources {

    private static final String NAMESPACE = "meknh_core";

    private final PackLocationInfo location;
    private final Map<ResourceLocation, byte[]> resources;

    public RuntimeDataPackResources(PackLocationInfo location) {
        this.location = location;
        this.resources = RuntimeLootTables.build();
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation resourceLocation) {
        if (type != PackType.SERVER_DATA) {
            return null;
        }
        byte[] data = this.resources.get(resourceLocation);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
        if (type != PackType.SERVER_DATA || !NAMESPACE.equals(namespace)) {
            return;
        }
        String prefix = path.isEmpty() ? "" : path + "/";
        this.resources.forEach((id, data) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                resourceOutput.accept(id, () -> new ByteArrayInputStream(data));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(NAMESPACE) : Set.of();
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
    }
}
