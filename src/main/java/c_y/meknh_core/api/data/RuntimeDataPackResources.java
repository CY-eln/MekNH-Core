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
 * 把 {@link RuntimeLootTables} 与 {@link RuntimeBlockTags} 生成的战利品表 / 挖掘标签包成服务端数据包。
 */
public final class RuntimeDataPackResources implements PackResources {

    private final PackLocationInfo location;
    private final Map<ResourceLocation, byte[]> resources;

    public RuntimeDataPackResources(PackLocationInfo location) {
        this.location = location;
        Map<ResourceLocation, byte[]> merged = new java.util.LinkedHashMap<>(RuntimeLootTables.build());
        merged.putAll(RuntimeBlockTags.build());
        this.resources = merged;
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
        if (type != PackType.SERVER_DATA) {
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
        if (type != PackType.SERVER_DATA) {
            return Set.of();
        }
        Set<String> namespaces = new java.util.LinkedHashSet<>();
        for (ResourceLocation id : this.resources.keySet()) {
            namespaces.add(id.getNamespace());
        }
        return namespaces;
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
