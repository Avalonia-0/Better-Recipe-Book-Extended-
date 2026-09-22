package com.alonie.brbe.util;

import java.util.Map;

/**
 * 26.3 燃料/堆肥数值的**内置兜底表**（由 {@code tools/context-int-provider-fallback/gen.py}
 * 从客户端 jar 的 {@code data/minecraft/context_int_provider/**.json} 生成，勿手改）。
 *
 * <p>为什么需要：{@code minecraft:context_int_provider} 位于
 * {@code RegistryDataLoader.RELOADABLE_REGISTRIES}，<b>不参与网络同步</b>
 * （{@code SYNCHRONIZED_REGISTRIES} 里没有它）——客户端注册表查不到，单人靠
 * 集成服务端注册表，LAN/多机只能回落到这张表。表里是每个 provider 的<b>结构化
 * 期望值</b>（weighted_list → 加权均值、number_dispatcher → default、
 * conditional → on_false），与原版旧表（{@code ComposterBlock.COMPOSTABLES} /
 * {@code FuelValues}）显示的数值一致。</p>
 */
public final class ContextIntProviderFallbacks {

    private ContextIntProviderFallbacks() {
    }

    /** provider 资源位置 → 结构化期望值（原版数据；数据包改写后此表不跟随）。 */
    public static final Map<String, Double> EXPECTED = Map.ofEntries(
            Map.entry("minecraft:brewing/uses_default", 20.0),
            Map.entry("minecraft:compostable/always_add_one", 1.0),
            Map.entry("minecraft:compostable/low", 0.3),
            Map.entry("minecraft:compostable/low_medium", 0.5),
            Map.entry("minecraft:compostable/medium", 0.65),
            Map.entry("minecraft:compostable/medium_high", 0.85),
            Map.entry("minecraft:cooking/fast_burn_time_reduction_factor", 2.0),
            Map.entry("minecraft:cooking/normal_burn_time_reduction_factor", 1.0),
            Map.entry("minecraft:cooking/time_bamboo", 50.0),
            Map.entry("minecraft:cooking/time_blaze_rod", 2400.0),
            Map.entry("minecraft:cooking/time_boats", 1200.0),
            Map.entry("minecraft:cooking/time_coal", 1600.0),
            Map.entry("minecraft:cooking/time_coal_block", 16000.0),
            Map.entry("minecraft:cooking/time_dried_kelp_block", 4001.0),
            Map.entry("minecraft:cooking/time_dry_plants", 100.0),
            Map.entry("minecraft:cooking/time_hanging_signs", 800.0),
            Map.entry("minecraft:cooking/time_lava_bucket", 20000.0),
            Map.entry("minecraft:cooking/time_roots", 300.0),
            Map.entry("minecraft:cooking/time_wood_blocks", 300.0),
            Map.entry("minecraft:cooking/time_wood_items_extra_small", 100.0),
            Map.entry("minecraft:cooking/time_wood_items_large", 200.0),
            Map.entry("minecraft:cooking/time_wood_items_small", 300.0),
            Map.entry("minecraft:cooking/time_wood_slabs", 150.0),
            Map.entry("minecraft:cooking/time_wool", 100.0),
            Map.entry("minecraft:cooking/time_wool_carpets", 67.0),
            Map.entry("minecraft:cooking/time_wool_slabs", 50.0)
    );

    /** 已知 provider 的期望值；未知（mod 数据包新增）返回 null。 */
    public static Double expected(String location) {
        return location == null ? null : EXPECTED.get(location);
    }
}
