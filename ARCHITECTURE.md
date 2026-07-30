# BRBE 26.2 架构重写设计文档

## 1. 动机

当前架构存在六项结构性问题，每项都源自 1.21.1 时代逐步叠加的功能：

| # | 问题 | 现状 | 影响 |
|---|------|------|------|
| 1 | **三层泛型过度抽象** | `GenericRecipeBookComponent<M, C, R>` 仅 3 个有效组合 | 每次声明需写 3 个类型参数，IDE 自动补全 15 行 |
| 2 | **Mixin 碎片化** | 61 个 Mixin 文件，8 个同名 `RecipeBookComponentMixin` 分散在 8 个目录 | 注入顺序脆弱，同一目标方法被多个 Mixin 拦截 |
| 3 | **双轨制配置访问** | `BetterRecipeBook.config`（静态）+ `AppContext.ctx().config()`（DI）并存 | 50+ 处走静态路径，DI 投入被架空 |
| 4 | **布局系统半迁移** | `BookLayout`/`BookGeometry` 已定义但未被使用 | 30+ 处仍用硬编码 `147`/`166`/`86`/`162` |
| 5 | **双管道重复实现** | 原版 `CollectionPipeline` + `GenericRecipeBookComponent` 内联管道实现相同逻辑 | 搜索/固定/排序在 2 处独立维护 |
| 6 | **SPI 休眠** | `RecipeViewer` + `RecipeViewerRegistry` 已定义且注入 `AppContext`，但零次 `.register()` | 活跃路径仍是 `ItemViewCompat` + 反射 |

## 2. 设计原则

1. **接口优先于泛型** — `interface Recipe` / `interface RecipeGroup` / `interface RecipeSource` 替代三层泛型
2. **数据/视图/交互 MVC 分离** — Model（纯数据）、View（渲染）、Controller（输入）不交叉
3. **单一路径配置** — `AppContext` 唯一入口，移除 `BetterRecipeBook.config` 等静态全局
4. **集中布局计算** — `BookLayout.compute()` 为唯一坐标来源
5. **插件化配方来源** — 每种工作台实现 `RecipeSource`，不继承 God Component
6. **最小 Mixin 面** — 从 61 → ~12 个核心注入点，功能开关在 Java 层而非 Mixin 层

## 3. 模块结构

```
com.alonie.brbe/
├── Brbe.java                       # 入口（替代 BetterRecipeBook）
│
├── config/                         # 配置系统
│   ├── BrbeConfig.java             # 不可变记录（已存在，待接入）
│   ├── AppContext.java             # DI 根（简化，移除重复）
│   └── ConfigAdapter.java          # Cloth Config ↔ BrbeConfig 适配器
│
├── model/                          # 纯数据模型（零 Minecraft 依赖）
│   ├── Recipe.java                 # 配方接口：id(), result(), searchString()
│   ├── RecipeGroup.java            # 配方组：recipes(), category()
│   └── RecipeSource.java           # SPI：id(), categories(), getGroups()
│
├── source/                         # RecipeSource 实现
│   ├── CraftingSource.java         # 原版合成配方发现
│   ├── BrewingSource.java          # 药水酿造
│   └── SmithingSource.java         # 锻造升级+纹饰
│
├── state/                          # 可变状态
│   ├── BookState.java              # 搜索文本、过滤开关、当前页、选中标签
│   ├── PinStore.java               # 固定配方持久化
│   └── CraftState.java             # 库存追踪、部分可合成检测
│
├── layout/                         # 布局引擎
│   ├── BookLayout.java             # 约束计算（保留并简化）
│   └── BookGeometry.java           # 计算结果记录
│
├── view/                           # 渲染（纯输出，无输入处理）
│   ├── RecipeBookView.java         # 主配方书渲染
│   ├── ButtonGrid.java             # 按钮网格
│   ├── TabStrip.java               # 标签页条
│   ├── GhostRenderer.java          # 幽灵配方
│   └── Sprites.java                # 纹理常量（原 BRBTextures）
│
├── control/                        # 输入处理
│   ├── BookInput.java              # 鼠标/键盘事件分发
│   ├── SearchEngine.java           # 搜索查询解析（原 search/ 包）
│   └── ScrollHandler.java          # 滚轮翻页
│
├── compat/                         # 外部模组兼容
│   ├── RecipeViewer.java           # JEI/REI/EMI SPI（保留）
│   ├── RecipeViewerRegistry.java   # 注册表（保留）
│   └── OverlayHider.java           # HUD 覆盖层控制
│
├── mixins/                          # ~12 个核心 Mixin
│   ├── GuiMixin.java               # ScreenGUI 级别钩子
│   ├── ContainerScreenMixin.java   # 容器屏幕注入
│   ├── BrewingScreenMixin.java     # 酿造台屏幕
│   ├── SmithingScreenMixin.java    # 锻造台屏幕
│   ├── RecipeSyncMixin.java        # 配方同步（ClientPacketListener）
│   ├── RecipeUnlockMixin.java      # 配方解锁
│   ├── InstantCraftMixin.java      # 即时合成
│   └── accessors/                  # 访问器接口
│
└── platform/                       # 平台特定（fabric/neoforge 模块）
    └── PlatformBrewing.java        # 药水反射（替代 PlatformPotionUtil 静态全局）
```

## 4. 核心 API 契约

### 4.1 RecipeSource（配方来源插件）

```java
public interface RecipeSource {
    /** 唯一标识（"crafting", "brewing", "smithing"） */
    String id();

    /** 配方书标题 */
    Component displayName();

    /** 标签页类别列表（含 SEARCH 占位） */
    List<Category> categories();

    /** 获取所有配方组 */
    List<RecipeGroup> getGroups(RegistryAccess registries);

    /** 是否适用于当前屏幕 */
    boolean appliesTo(Screen screen);

    /** 配方放置逻辑（幽灵配方或自动合成） */
    void placeRecipe(Recipe recipe, ContainerMenu menu, LocalPlayer player);
}
```

### 4.2 Recipe / RecipeGroup（数据契约）

```java
public interface Recipe {
    ResourceLocation id();
    ItemStack result(RegistryAccess registries);
    String searchString();
    boolean hasMaterials(InventorySnapshot inv);
    boolean hasPartialMaterials(InventorySnapshot inv);
}

public interface RecipeGroup {
    List<? extends Recipe> recipes();
    Category category();
    default boolean anyCraftable(InventorySnapshot inv) { ... }
    default boolean anyPartiallyCraftable(InventorySnapshot inv) { ... }
}
```

关键变更：`hasMaterials` 接收**纯数据快照** `InventorySnapshot` 而非 `NonNullList<Slot>`，打破与 Minecraft 容器 API 的耦合。

### 4.3 InventorySnapshot

```java
/** 库存的不可变快照，脱离 Minecraft Slot/ContainerMenu。 */
public record InventorySnapshot(
    Map<Item, Integer> itemCounts
) {
    public static InventorySnapshot from(NonNullList<Slot> slots) { ... }
    public boolean contains(Item item) { ... }
    public int count(Item item) { ... }
}
```

### 4.4 BookState（可变状态记录）

```java
public class BookState {
    String searchText = "";
    SearchQuery activeQuery = SearchQuery.EMPTY;
    boolean filterCraftable = false;
    int currentPage = 0;
    Category selectedTab;
    Recipe ghostRecipe;
    // 不持有 Minecraft 引用——纯数据
}
```

### 4.5 RecipeBookView（视图根）

```java
public class RecipeBookView {
    private final RecipeSource source;
    private final BookState state;
    private final BookLayout layout;
    // 子视图（依赖注入）
    private final BackgroundRenderer background;
    private final TabStrip tabs;
    private final ButtonGrid grid;
    private final GhostRenderer ghost;

    public void render(ScreenRenderContext ctx) {
        if (!state.isVisible()) return;
        BookGeometry geo = layout.compute(ctx.screenRect(), ctx.config());
        background.render(ctx.graphics(), geo);
        tabs.render(ctx.graphics(), geo, source.categories(), state);
        grid.render(ctx.graphics(), geo, state.visibleGroups(), state);
        ghost.render(ctx.graphics(), geo, state.ghostRecipe(), state.selectedTab());
    }

    public boolean handleInput(InputEvent event, BookGeometry geo) {
        return tabs.handleInput(event, geo)
            || grid.handleInput(event, geo, state)
            || searchBox.handleInput(event, geo, state);
    }
}
```

## 5. Mixin 削减策略

从 61 个文件削减到 ~12 个，策略是按**目标类**加**注入点类型**分组：

| 新 Mixin | 替换的原 Mixin（数量） | 注入方式 |
|-----------|----------------------|----------|
| `GuiMixin` | ScreenRenderMixin, CloseOverlaysOnRecipeBookOpenMixin (2) | ScreenGUI 级别渲染/生命周期钩子 |
| `ContainerScreenMixin` | settings/, centered/, incompletecrafting/, ungroup/, search/, scrollablepages/ 中 RecipeBookComponentMixin (6) + RecipeButtonMixin (2) + RecipeBookPageMixin (2) | 单一类，用 switch/factory 模式根据屏幕类型分发 |
| `RecipeButtonMixin` | modname/, pins/, incompatibleenvironment/, incompletecrafting/, instantcraft/ 中 RecipeButtonMixin (5) | 合并为单文件 |
| `BrewingScreenMixin` | BrewingStandScreenMixin (1) | 不变 |
| `SmithingScreenMixin` | SmithingScreenMixin (1) | 不变 |
| `RecipeSyncMixin` | unlockrecipes/ ClientPacketListenerMixin (1) + ClientRecipeBookMixin (1) | 网络包处理 |
| `RecipeManagerMixin` | unlockrecipes/ RecipeManagerMixin (1) + ServerRecipeBookAccessor (1) | 配方解锁 |
| `InstantCraftMixin` | instantcraft/ 中各种 Mixin (~5) | 即时合成 |
| `accessors/*` | 10+ @Accessor 接口 | 压缩到 ~4 个 |

每个合并的 Mixin 内部用**配置守卫 + 功能分发**替代原来的分目录碎片：

```java
@Mixin(RecipeBookComponent.class)
public class ContainerScreenMixin {
    @Inject(method = "initVisuals", at = @At("RETURN"))
    private void brbe$initVisuals(CallbackInfo ci) {
        BrbeConfig c = AppContext.instance().config();
        if (c.keepCentered())     applyCenteredLayout();
        if (c.settingsButton())   addSettingsButton();
        if (c.noGrouped())        ungroupRecipes();
        if (c.scrollAround())     enableScrollWheel();
    }
}
```

## 6. 配置系统改造

### 当前问题

- `Config`（旧、活跃）vs `BrbeConfig`（新、未使用）两个 POJO
- `configHolder.registerSaveListener()` 被注册两次，事件双重发布
- `BetterRecipeBook.config` 可变静态字段无 volatile 保护
- `ensureCategories()` 在 `BetterRecipeBook` 和 `AppContext` 中各一份

### 方案

```
brbe.toml ──► AutoConfig ──► ConfigHolder<BrbeConfig>
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
              ConfigAdapter              AppContext
              (TOML 序列化)              (唯一读取入口)
                                              │
                              ┌───────────────┼───────────────┐
                              ▼               ▼               ▼
                         RecipeBookView   RecipeSource    PinStore
```

**删除** `BetterRecipeBook.config` 静态字段。所有消费者通过 `AppContext.instance().config()` 读取。`BrbeConfig` 改为不可变记录，配置变更时发布事件 → 视图层重新计算。

## 7. 管道统一

消除双管道，统一为单条 `RecipePipeline`：

```java
public class RecipePipeline {
    public List<RecipeGroup> process(
        List<RecipeGroup> raw,
        SearchQuery query,
        InventorySnapshot inv,
        boolean filterCraftable,
        PinStore pins
    ) {
        return Stream.of(raw)
            .filter(g -> query.matchesAny(g, inv))  // 搜索
            .map(g -> ungroupIfNeeded(g))            // 取消分组
            .sorted(pinFirst(pins))                  // 固定置顶
            .sorted(craftableFirst(inv))             // 可合成优先
            .filter(g -> keepIfFilterMatch(g, filterCraftable, inv))
            .toList();
    }
}
```

原版合成台和酿造台/锻造台都走同一条管道，差异仅在于 `RecipeSource.getGroups()` 的返回值。

## 8. 迁移步骤

### Phase 1: 基础设施（不破坏现有功能）
1. 将 `BrbeConfig` 提升为正式配置类，`Config` 废弃
2. 实现 `ConfigAdapter`，接管 TOML 序列化
3. 移除 `BetterRecipeBook.config` 静态字段，所有引用替换为 `AppContext.instance().config()`
4. 删除重复的 save listener 和重复的 `ensureCategories()`

### Phase 2: 数据模型
5. 创建 `model/` 包：`Recipe`, `RecipeGroup`, `RecipeSource`, `InventorySnapshot`
6. 实现 `source/` 包：`CraftingSource`, `BrewingSource`, `SmithingSource`
7. 实现 `state/` 包：`BookState`, `CraftState`
8. 删除 `generic/`、`brewingstand/`、`smithingtable/` 包

### Phase 3: 视图与输入
9. 实现 `view/` 包：`RecipeBookView`, `ButtonGrid`, `TabStrip`, `GhostRenderer`
10. 实现 `control/` 包：`BookInput`, `SearchEngine`, `ScrollHandler`
11. 激活 `BookLayout` + `BookGeometry` 集中布局
12. 删除 `interfaces/`、`widget/` 包

### Phase 4: Mixin 合并
13. 按目标类合并 Mixin（61 → ~12）
14. 更新 mixin JSON 配置文件
15. 编译 + 运行验证

### Phase 5: 兼容层
16. 激活 `RecipeViewer` SPI（`JeiViewer`, `ReiViewer` 实现）
17. 删除 `ItemViewCompat` + `JeiCompat`/`ReiCompat` 旧反射路径
18. 统一平台药水工具到 `PlatformBrewing`（纳入 `AppContext` 管理）

## 9. 兼容性保证

- **JEI/REI 集成**：`RecipeViewer` SPI 保持相同能力（R=查看配方, U=查看用途, 覆盖层隐藏）
- **配置格式**：TOML 字段名称不变，`BrbeConfig` 映射到相同键名
- **固定配方**：`brbe.pins` JSON 格式不变，迁移透明
- **键位绑定**：F/R/U 键位不变
- **RBIP**：保持相同 Mixin 注入点，但通过 `BookState` 而非静态字段传递状态

## 10. 风险与缓解

| 风险 | 缓解 |
|------|------|
| Mixin 合并引入注入顺序 bug | 逐目标类合并，每步 `runClient` 验证 |
| `InventorySnapshot` 性能 | 仅在有库存变更时重建快照（复用现有槽位哈希机制） |
| 酿造/锻造行为退化 | 保持相同的 `GhostRecipe` + `handlePlaceRecipe` 逻辑，仅重构数据模型层 |
| 跨版本移植困难 | 新架构的 Mixin 面更小 → 移植量更少；`model/` 包完全不依赖 Minecraft API |

## 11. 成功标准

- [ ] `BrbeConfig` 为唯一配置类，`BetterRecipeBook.config` 不存在
- [ ] Mixin 文件数 ≤ 15（含 Accessor）
- [ ] 无 `GenericRecipeBookComponent<M, C, R>` 类型参数
- [ ] 所有布局坐标经过 `BookLayout.compute()`
- [ ] `RecipeViewer` SPI 有 ≥ 2 个实现且已注册
- [ ] 合成台 + 酿造台 + 锻造台三种配方书功能等价
- [ ] Fabric + NeoForge 双平台编译通过 + `runClient` 可用
