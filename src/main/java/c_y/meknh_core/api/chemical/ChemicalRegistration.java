package c_y.meknh_core.api.chemical;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registration.impl.ChemicalDeferredRegister;
import mekanism.common.registration.impl.DeferredChemical;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Mekanism 化学品的快速注册入口（内部实现，对外请使用
 * {@link c_y.meknh_core.api.MeknhCoreAPI#registerChemical}）。
 *
 * <p>化学品既可以通过数据包 JSON 注册（见 {@link ChemicalDefinition}），
 * 也可以在其它模组的构造阶段直接调用本类的方法注册。</p>
 *
 * <p><b>注意：</b>Mekanism 的化学品注册表是静态注册表，客户端与服务端必须拥有完全相同的条目。
 * 因此本 API 只从「模组自带的资源包数据」（{@code data/addon/chemicals/*.json}）读取定义与属性，
 * 并在注册阶段（{@code RegisterEvent} 之前）完成注册；存档数据包中的定义不会在这里生效。</p>
 */
public final class ChemicalRegistration {

    /**
     * 本模组数据包内容的根目录（相对于 {@code data/}）。
     */
    public static final String ADDON_DATA_DIRECTORY = "addon";

    /**
     * 化学品定义目录（相对于 {@code data/addon/}）。
     */
    public static final String CHEMICAL_DIRECTORY = "chemicals";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, DeferredChemical<Chemical>> CHEMICALS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ChemicalDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, ChemicalDeferredRegister> REGISTERS = new HashMap<>();

    @Nullable
    private static IEventBus modBus;

    private ChemicalRegistration() {
    }

    /**
     * 由主模组类在构造阶段调用，绑定模组事件总线并补交在此之前登记的定义。
     * 重复调用会抛出异常。
     */
    public static synchronized void initialize(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (modBus != null) {
            throw new IllegalStateException("ChemicalRegistration has already been initialized");
        }
        modBus = bus;
        REGISTERS.values().forEach(register -> register.register(bus));
    }

    /**
     * 注册一份数据驱动的化学品定义（含属性）。
     *
     * @param definition 化学品定义
     * @return 对应的延迟化学品对象
     * @throws IllegalStateException 当同一注册名已被注册时
     */
    public static synchronized DeferredChemical<Chemical> register(ChemicalDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definition.fluid()) {
            throw new IllegalArgumentException("ChemicalDefinition " + definition.id() + " is marked as fluid; use FluidAttributeRegistry");
        }
        DeferredChemical<Chemical> chemical = register(definition.id(), () -> new Chemical(definition.baseBuilder()));
        DEFINITIONS.put(definition.id(), definition);
        return chemical;
    }

    /**
     * 使用给定的构造器注册化学品。
     *
     * @param id      注册名
     * @param builder 化学品构造器（贴图与颜色）
     * @return 对应的延迟化学品对象
     */
    public static DeferredChemical<Chemical> register(ResourceLocation id, ChemicalBuilder builder) {
        Objects.requireNonNull(builder, "builder");
        return register(id, () -> new Chemical(builder));
    }

    /**
     * 使用给定的工厂注册化学品。工厂会在 {@code RegisterEvent} 触发时调用。
     *
     * @param id      注册名
     * @param factory 化学品工厂
     * @return 对应的延迟化学品对象
     * @throws IllegalStateException 当同一注册名已被注册时
     */
    public static synchronized DeferredChemical<Chemical> register(ResourceLocation id, Supplier<Chemical> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (CHEMICALS.containsKey(id)) {
            throw new IllegalStateException("Chemical " + id + " has already been registered");
        }
        ChemicalDeferredRegister register = REGISTERS.computeIfAbsent(id.getNamespace(), namespace -> {
            ChemicalDeferredRegister created = new ChemicalDeferredRegister(namespace);
            if (modBus != null) {
                created.register(modBus);
            }
            return created;
        });
        DeferredChemical<Chemical> chemical = register.register(id.getPath(), factory);
        CHEMICALS.put(id, chemical);
        LOGGER.debug("Queued data-driven chemical {}", id);
        return chemical;
    }

    /**
     * 是否已经登记过某个化学品。
     */
    public static synchronized boolean isRegistered(ResourceLocation id) {
        return CHEMICALS.containsKey(id);
    }

    /**
     * 获取登记过的延迟化学品对象（可能尚未完成注册）。
     */
    public static synchronized Optional<DeferredChemical<Chemical>> getDeferred(ResourceLocation id) {
        return Optional.ofNullable(CHEMICALS.get(id));
    }

    /**
     * 从 Mekanism 化学品注册表中查询化学品。
     *
     * @return 化学品；若不存在则为 {@code null}
     */
    @Nullable
    public static Chemical getChemical(ResourceLocation id) {
        return MekanismAPI.CHEMICAL_REGISTRY.get(id);
    }

    /**
     * 从 Mekanism 化学品注册表中查询指定注册名的 Holder。
     */
    public static Optional<Holder.Reference<Chemical>> getHolder(ResourceLocation id) {
        return MekanismAPI.CHEMICAL_REGISTRY.getHolder(key(id));
    }

    /**
     * 判断化学品堆叠是否为指定注册名的化学品。
     */
    public static boolean is(ChemicalStack stack, ResourceLocation id) {
        if (stack.isEmpty()) {
            return false;
        }
        Chemical chemical = getChemical(id);
        return chemical != null && stack.is(chemical);
    }

    /**
     * 已登记的所有化学品（需要在注册表冻结之后调用）。
     */
    public static synchronized List<Chemical> getChemicals() {
        List<Chemical> chemicals = new ArrayList<>(CHEMICALS.size());
        for (DeferredChemical<Chemical> chemical : CHEMICALS.values()) {
            chemicals.add(chemical.value());
        }
        return List.copyOf(chemicals);
    }

    /**
     * 已登记的所有注册名。
     */
    public static synchronized Collection<ResourceLocation> getChemicalIds() {
        return List.copyOf(CHEMICALS.keySet());
    }

    /**
     * 已登记的所有化学品定义（含属性数据）。
     */
    public static synchronized Collection<ChemicalDefinition> getDefinitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    /**
     * 该化学品是否被定义为气态。
     */
    public static synchronized boolean isGaseous(ResourceLocation id) {
        ChemicalDefinition definition = DEFINITIONS.get(id);
        return definition != null && definition.gaseous();
    }

    /**
     * 某个化学品对应的注册表键。
     */
    public static ResourceKey<Chemical> key(ResourceLocation id) {
        return ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id);
    }
}
