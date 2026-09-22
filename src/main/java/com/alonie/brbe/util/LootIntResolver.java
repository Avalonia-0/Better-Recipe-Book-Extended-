package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.storage.loot.providers.number.ints.Absolute;
import net.minecraft.world.level.storage.loot.providers.number.AggregateProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConditionalValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.Difference;
import net.minecraft.world.level.storage.loot.providers.number.ints.Maximum;
import net.minecraft.world.level.storage.loot.providers.number.ints.Minimum;
import net.minecraft.world.level.storage.loot.providers.number.ints.Negate;
import net.minecraft.world.level.storage.loot.providers.number.ints.NumberDispatcher;
import net.minecraft.world.level.storage.loot.providers.number.ints.Product;
import net.minecraft.world.level.storage.loot.providers.number.ints.Quotient;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.level.storage.loot.providers.number.ints.Sum;
import net.minecraft.world.level.storage.loot.providers.number.ints.WeightedListValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side approximation of the "context-dependent integer providers" that
 * Minecraft 26.3 introduced for fuel burn times and compost layers.
 *
 * <p>26.2 read these from plain tables ({@code FuelValues}, {@code
 * ComposterBlock.COMPOSTABLES}).  26.3 replaced both with the data-pack
 * registry {@code minecraft:context_int_provider}: an item carries a
 * {@code ResolvableInt} that is either a constant or a reference to a provider
 * evaluated against a {@code LootContext} (which needs a {@code ServerLevel}
 * plus the interacting block state — unavailable on the client).
 *
 * <p>For display purposes we resolve the provider tree <em>structurally</em>
 * and return an expected value:
 * <ul>
 *   <li>{@code weighted_list} → probability-weighted mean.  For the compost
 *       providers this reproduces the historical vanilla chances exactly
 *       (e.g. {@code compostable/low} → 0.3, {@code always_add_one} → 1.0);</li>
 *   <li>{@code number_dispatcher} → its {@code default} branch (contextual
 *       cases such as "composter at level 0 always adds one layer" cannot be
 *       evaluated client-side);</li>
 *   <li>{@code conditional} → its {@code on_false} branch, i.e. no fast-cooking
 *       bonus — this is why {@code cooking/time_coal} resolves to
 *       {@code 1600 / 1 = 1600} ticks, the normal furnace burn time;</li>
 *   <li>the arithmetic providers ({@code + - * /}, min, max, abs, negate) are
 *       evaluated recursively.</li>
 * </ul>
 *
 * <p>Anything unresolvable yields 0, so callers degrade to "unknown" instead of
 * showing a wrong number.
 */
public final class LootIntResolver {

    private LootIntResolver() {
    }

    /** Expected value of a resolvable reference ({@code Constant} → itself,
     *  {@code Reference} → provider tree from the client's / integrated
     *  server's registry, or the built-in vanilla table as a last resort). */
    public static double expected(ResolvableInt value) {
        if (value instanceof ResolvableInt.Constant constant) return constant.value();
        if (value instanceof ResolvableInt.Reference reference) {
            ContextIntProvider provider = lookup(reference.key());
            if (provider != null) return expected(provider);
            Double fallback = fallback(reference.key());
            return fallback == null ? 0.0 : fallback;
        }
        return 0.0;
    }

    /**
     * Whether {@link #expected(ResolvableInt)} yields a <b>known</b> value.
     *
     * <p>26.3 的 provider 注册表不参与网络同步：多人/局域网下客户端与集成服务端
     * 都拿不到，只有内置兜底表里有的（原版）provider 才算已知。调用方据此
     * "未知就不显示"——而不是显示一个误导性的 0（用户 2026-09-22 反馈的
     * "概率清一色 0%"）。</p>
     */
    public static boolean resolvable(ResolvableInt value) {
        if (value instanceof ResolvableInt.Constant) return true;
        if (value instanceof ResolvableInt.Reference reference) {
            return lookup(reference.key()) != null || fallback(reference.key()) != null;
        }
        return false;
    }

    /** Built-in vanilla expected value for a provider key, or null when the key
     *  is unknown (datapack/mod providers are not in the table). */
    private static Double fallback(ResourceKey<ContextIntProvider> key) {
        return key == null || key.identifier() == null
                ? null
                : ContextIntProviderFallbacks.expected(key.identifier().toString());
    }

    public static double expected(Holder<ContextIntProvider> holder) {
        return holder == null ? 0.0 : expected(holder.value());
    }

    public static double expected(ContextIntProvider provider) {
        if (provider == null) return 0.0;
        if (provider instanceof ConstantValue constant) return constant.value();
        if (provider instanceof WeightedListValue weighted) {
            double totalWeight = 0.0;
            double weightedSum = 0.0;
            for (Weighted<Holder<ContextIntProvider>> entry : weighted.distribution().unwrap()) {
                totalWeight += entry.weight();
                weightedSum += entry.weight() * expected(entry.value());
            }
            return totalWeight == 0.0 ? 0.0 : weightedSum / totalWeight;
        }
        // Contextual branch we cannot evaluate on the client → use the fallback.
        if (provider instanceof NumberDispatcher dispatcher) return expected(dispatcher.defaultValue());
        if (provider instanceof ConditionalValue conditional) return expected(conditional.onFalse());
        if (provider instanceof Quotient quotient) {
            double right = expected(quotient.right());
            return right == 0.0 ? 0.0 : expected(quotient.left()) / right;
        }
        if (provider instanceof Difference difference) {
            return expected(difference.left()) - expected(difference.right());
        }
        if (provider instanceof Product product) return aggregate(product, AggregateKind.PRODUCT);
        if (provider instanceof Sum sum) return aggregate(sum, AggregateKind.SUM);
        if (provider instanceof Minimum minimum) return aggregate(minimum, AggregateKind.MINIMUM);
        if (provider instanceof Maximum maximum) return aggregate(maximum, AggregateKind.MAXIMUM);
        if (provider instanceof Negate negate) return -expected(negate.input());
        if (provider instanceof Absolute absolute) return Math.abs(expected(absolute.input()));
        return 0.0;
    }

    private enum AggregateKind { PRODUCT, SUM, MINIMUM, MAXIMUM }

    private static double aggregate(AggregateProvider<ContextIntProvider> provider, AggregateKind kind) {
        List<Double> values = new ArrayList<>();
        for (Holder<ContextIntProvider> input : provider.inputs()) {
            values.add(expected(input));
        }
        if (values.isEmpty()) return 0.0;
        return switch (kind) {
            case PRODUCT -> values.stream().reduce(1.0, (a, b) -> a * b);
            case SUM -> values.stream().reduce(0.0, Double::sum);
            case MINIMUM -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            case MAXIMUM -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        };
    }

    /**
     * The provider behind a data-pack key.
     *
     * <p>⚠️ 26.3 的 {@code minecraft:context_int_provider} 在
     * {@code RegistryDataLoader.RELOADABLE_REGISTRIES} 里，<b>不在</b>
     * {@code SYNCHRONIZED_REGISTRIES} 里 → 客户端注册表没有它。所以先看客户端
     * level 的注册表（若将来同步/在服务端上下文里调用），再回落到<b>单人集成
     * 服务端</b>的注册表（RELOADABLE 层都在）；LAN/多机两个都拿不到，调用方走
     * 内置兜底表 {@link ContextIntProviderFallbacks}。</p>
     */
    private static ContextIntProvider lookup(ResourceKey<ContextIntProvider> key) {
        if (key == null) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return null;
        ContextIntProvider provider = lookup(minecraft.level == null ? null : minecraft.level.registryAccess(), key);
        if (provider != null) return provider;
        try {
            MinecraftServer server = minecraft.getSingleplayerServer();
            return server == null ? null : lookup(server.registryAccess(), key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ContextIntProvider lookup(RegistryAccess access, ResourceKey<ContextIntProvider> key) {
        if (access == null || key == null) return null;
        try {
            Registry<ContextIntProvider> registry = access.lookupOrThrow(Registries.CONTEXT_INT_PROVIDER);
            return registry == null ? null : registry.getValue(key);
        } catch (Throwable t) {
            // 注册表不存在（客户端未同步）→ 交给调用方兜底
            return null;
        }
    }
}
