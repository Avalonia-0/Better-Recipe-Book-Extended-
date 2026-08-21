# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Branch Architecture

This is a **multi-version Minecraft mod**. Each git branch targets a specific Minecraft version:

| Branch | MC Version | Loom Plugin | Java | Mappings |
|--------|-----------|-------------|------|----------|
| `1.21.1` | 1.21.1 | `dev.architectury.loom` | 21 | Mojang official (via Architectury remap) |
| `1.21.11` | 1.21.11 | `net.fabricmc.fabric-loom-remap` 1.14.6 | 21 | Mojang official (fabric remap) |
| `26.1.2` | 26.1.2 | `dev.architectury.loom-no-remap` | 25 | Mojang official（已停止维护） |
| `26.2` | 26.2 | `net.fabricmc.fabric-loom` 1.17.18 | 25 | Mojang official (no-remap) |

**本分支（1.21.11）已是单模块 fabric 工程**（2026-08-18 切割 NeoForge）：从 Architectury 三模块（common/fabric/neoforge）退回普通 fabric-loom 单模块，源码在 `src/main/java`，Gradle 9.5.1。混淆版本（1.21.11 及更旧）必须用插件 ID `net.fabricmc.fabric-loom-remap`（`fabric-loom` 仅用于 26.1+ 非混淆版本）。

**CRITICAL**: `build.gradle` 校验 `minecraft_version` 必须与 git 分支名一致。切换分支后需恢复 `gradle.properties`。

## Build Commands

```bash
# Compile only (fast, for checking errors)
./gradlew compileJava

# Full build (produces JARs)
./gradlew build -x test -x check

# Clean build
./gradlew clean build -x test -x check

# Reset cross-branch cache corruption
./gradlew cleanLoomCache && rm -rf .gradle

# Copy JEI dependency jar from test instance into libs/
./gradlew setupLibs
```

JAR output: `build/libs/brbe-ava-fabric-1.21.11-{version}.jar`

## Project Structure

```
单模块 fabric 工程（无 common/fabric/neoforge 拆分）：
```
src/main/java/com/alonie/
  brbe/                   # Main mod: Better Recipe Book Extended
    fabric/               # Fabric 入口（entrypoints、JEI/REI compat、Fabric 专用 mixin、ModMenu）
    mixins/               # Mixin classes targeting vanilla MC classes
    config/               # Config class + sub-configs
    util/                 # PartialCraftingUtil, IncompatibleCraftingUtil, etc.
    generic/              # GenericRecipeButton, GenericRecipeBookComponent, etc.
    smithingtable/        # Smithing table recipe book
    brewingstand/         # Brewing stand recipe book（含 fabric/ 平台实现）
  recipebookispain_extended/  # RBIP sub-mod: creative tabs in recipe book（fabric/ 实现）

src/main/resources/       # 资源 + fabric.mod.json + mixins 配置 + brbe.common.accesswidener
```
```

## Key Architectural Patterns

### Cross-version API differences (26.1.2/26.2 vs 1.21.x)

The 26.x branches use `loom-no-remap` which compiles against raw Mojang-mapped Minecraft. The 1.21.x branches use `loom` which remaps through Yarn intermediary. This means:
- 1.21.x: Minecraft class names are Yarn-mapped (`class_507` for `RecipeBookComponent`)
- 26.x: Minecraft class names are Mojang official (`RecipeBookComponent` directly)
- 26.x: `Minecraft.screen` was moved to `Minecraft.gui.screen()` (Gui class)
- 26.x: `Minecraft.getOverlay()` → `Minecraft.gui.overlay()`
- 26.x: `I18n.exists()` removed, use `I18n.get()` equality check
- 26.x: `Ingredient.getItems()` → `Ingredient.items()` (returns `Stream<Holder<Item>>`)
- 26.x: `Screen(Minecraft, Font, Component)` constructor (was `Screen(Component)`)

### PartialCraftingUtil — Performance-critical path

This is the central engine for detecting partially-craftable recipes. Key methods:
- `markPartialMaterials(collection, slots)` — scans recipe ingredients against inventory
- `isPartiallyCraftable(collection, recipeId)` — checks PARTIAL_RECIPES WeakHashMap
- `hasPartialMaterials(collection)` — gate check
- `hashInventory(slots)` / `slotHash(slots)` — pre-hashes inventory for O(1) ingredient matching

### RecipeBookComponentMixin (incompletecrafting) — Main updateCollections hook

On 1.21.x: `@Redirect` on `List.forEach(Consumer)` — intercepts the vanilla consumer that populates craftable/fitsDimensions. After vanilla runs, BRBE injects partial recipes into craftable set.

On 26.x: `@Redirect` on `List.removeIf(Predicate)` with `ordinal=0` — 26.1.2/26.2 have three `removeIf` calls in `updateCollections`; only the first (main filter) is intercepted.

### Slot-hash caching (performance optimization)

Added to all branches. `updateCollections` is called on every screen toggle/item pickup. 90%+ of calls have unchanged inventory. The mixin computes a 64-bit hash of slot items+counts and skips both vanilla forEach AND BRBE marking when unchanged, saving ~180ms on large modpacks (~25k recipe collections).

### RBIP (RecipeBookIsPain) — Creative tabs in recipe book

`common/src/main/java/com/alonie/recipebookispain_extended/` is a bundled sub-mod that adds creative-mode tab support to furnace/smoker/blast-furnace recipe books.

### Cloth Config dependency

Cloth Config provides the configuration GUI. It's `implementation`+`include` (bundled in JAR) on fabric, and `implementation`+`include` on neoforge. When unavailable for a specific MC version, all AutoConfig calls are wrapped in try-catch with raw-type casts (Config.java removes `implements ConfigData`).

## Known Issues by Branch

- **1.21.11 fabric-only**: 2026-08-18 切割 NeoForge，退回单模块 fabric 工程。已删除 neoforge 模块、`rbip-neoforge.mixins.json`、`mixins.brbe-jei-common.json`（fabric 端用 `mixins.brbe-jei.json`）。构建工具链：Gradle 9.5.1 + `fabric-loom-remap` 1.14.6。
- **26.2 NeoForge**: NeoForge 26.2.0.0-beta exits silently after classloader build — pre-release beta bug, not BRBE.
- **26.2 Fabric**: Works but Cloth Config is embedded (no official 26.2 release yet).
- **26.1.2 NeoForge**: 分支已停止维护（2026-08-18）。

## 2026-08-21 全量移植（26.2 → 1.21.11）

以 26.2 源码为基线全量移植（约 8 子系统、~150 文件），mod id 统一为 **`zzzbrbe`**（资源/配置/数据文件直接复用 26.2；玩家 `brbe.toml`/`brbe.pins` 换名失效）。已部署 1.21.11-Fabric，runClient 启动验证通过（主菜单无崩溃、mixin 全部应用成功、RecipeViewerEngine 1458 条目重建成功）。

**移植的功能**：
- 配置系统重构：`Config`→`BrbeConfig`（`@Config(name="zzzbrbe")`），键位可配置（KeybindingCodec/GuiRegistrar/KeyMappingSyncMixin），新增字段先行就位
- 拼音搜索（search/Pinyin* + pinyin.txt，中文语言自动开启）
- 翻页动画 + Ctrl 跳页 + 搜索页码跳转命令（^N^）+ 配方书位置记忆
- 自研 R/U 查看引擎生态：recipeviewer/（RecipeViewerEngine/Categories）、RecipeViewerOverlay 浮层、PinOverlay（pin 浮层）、render/（PopupGeometry/PopupRenderer）
- JEI 插件收集代码（jei/plugins）对真实 JEI 27.4.0.22 编译（**未移植** vendored mezz fork；入口有 isModLoaded("jei") 守卫）
- RBIP 创造标签固定（TabPinManager）、RBIP 增量（rbip$getPage/setPage、renderTooltip 还原）
- ClientRecipeBookMixin 接 RecipeViewerIndex.rebuildEngine

**已知降级/警告**：
- **`mixins.brbe-jei.json`（JeiBookmarkOverlayMixin/JeiIngredientListOverlayMixin）target 在 JEI 27 找不到**（mezz.jei.gui.overlay 包路径变化）——1.21.11 既有问题，`hideReiJeiOverlay` 的 JEI 部分不生效，REI 部分正常。
- RecipeButtonAccessor 的 sprite accessor 未标 static（Mixin 警告，功能正常）。

## 2026-08-21 二轮同步（26.2 ↔ 1.21.11 零碎特性补齐）

第一轮全量移植后仍有零碎特性未同步，本轮回补（已编译验证，未部署/未 runClient）：

**26.2 → 1.21.11**：
- 翻页音效滑块（`mixins/soundoptions/SoundOptionsScreenMixin`）：1.21.11 无 `OptionInstance.UnitDouble/xmap`，改为直接实现公共接口 `OptionInstance.ValueSet<Double>` + 自建 `AbstractSliderButton`，行为与 26.2 一致（滑块 0–1 ↔ 音量 0–1.5）。已注册进 `mixins.brbe-common.json`
- `mixins.brbe-common.json` 补注册 4 个已存在但未注册的 accessor（AbstractContainerScreenAccessor / ImageButtonAccessor / OverlayRecipeButtonAccessor / ScreenAccessor）——此前相关代码路径运行时未生效
- 配置界面提示：`ConfigTipsHelper` 修正 `brbe.gui.tip.*` → `zzzbrbe.gui.tip.*` 并补齐 tip 8/9（Ctrl 跳页 / ^N^ 跳页提示）
- 翻译键修正：`brbe.gui.togglePotions.brewable`、`brbe.gui.smithable`、`brbe.gui.environmentIncompatible` → `zzzbrbe.*`（lang 文件早已是 zzzbrbe 命名空间，旧键不生效）
- ghost 配方工具提示支持 `showModName`（GenericGhostRecipe.drawTooltip）
- `CacheableRecipeDisplayEntry.makeSlotDisplay`：数量 >1 的结果改用 `SlotDisplay.ItemStackSlotDisplay`（1.21.11 构造器收 ItemStack，26.2 收 ItemStackTemplate）
- instantcraft 点击加 `RecipeViewerIndex.isViewerActive()` 守卫（浮层打开时不触发即时合成）
- `RecipeBookPageMixin.brbe$closeOverlayOnPageChange` 补上 `RecipeViewerOverlay.close()`（原为空体）
- `pins/AbstractContainerScreenMixin`：查看浮层激活时跳过二次 overlay 绘制、固定键点击音效、可见性检查、**RBIP 创造标签固定**（TabPinManager + 标签重排，与 26.2 一致）
- SmithingScreenMixin 接入 `PinOverlayManager.handleMouseClicked`；BrewingStandScreenMixin 渲染末尾接 `PinOverlayManager.render`
- `BetterRecipeBookJEIPlugin`：`JeiRuntimeBridge.set/clear` + `registerGuiHandlers` 排除区域（查看浮层/弹窗/pin 浮层区域让 JEI 避开）
- RBIP：`ClientRecipeBookMixin` 补 `SearchRecipeBookCategory.CRAFTING` 排除 + 熔炉配方去重（去掉调试日志）；`RecipeBookIsPain` 补固定创造标签置顶排序（withCreativeTabs / withFurnaceCreativeTabs）
- `overlay_pin.png` 更新为 26.2 新版（主资源 + unique_dark 资源包）

**1.21.11 → 26.2**：
- `CollectionPipeline` 改用 `hasPartialMaterialsEvenIfStale` / `isPartiallyCraftableEvenIfStale`（分代推进后不误过滤）
- RBIP `ClientRecipeBookMixin` 补 `RecipeBookIsPainExtendedConfig.enabled()` 守卫
- 清理调试日志：GenericGhostRecipe（保留 showModName 功能）、incompletecrafting/RecipeButtonMixin 的 [BRBE-DIAG] 日志块

**2026-08-21 晚间修复（R/U 查询功能）**：
- `OverlayRecipeButtonAccessor` 的 `@Accessor("this$0")` 在本分支（remap 构建）找不到字段——1.21.11 映射后该合成字段名为 **`field_3113`**（26.2 no-remap 保持 `this$0`），此前导致查看浮层每次打开都抛 `InvalidAccessorException`，R/U 查询完全不可用。已改为 `@Accessor("field_3113")`。⚠️ 修改任何 `@Accessor`/`@Invoker` 的成员名时，须用 `javap -p` 核对 1.21.11 named jar 中的实际字段/方法名（合成成员保留 `field_XXXX`/`method_XXXXX` intermediary 名）。
- 清理调试日志：[BRBE-SQ] 每帧刷屏（RecipeBookPageAnimationMixin）、[BRBE-DUMP] 每次重建 1458 行全量转储（localcache ClientRecipeBookMixin 的 `dumpAllKnown` 调用）。

**2026-08-21 深夜调整（两分支同步）**：
- **查询界面硬模态防穿透**（`RecipeViewerOverlay`，26.2 同改）：
  - 弹窗（Shift 预览）打开期间所有点击被认领：弹窗内**左键**继承按钮完整点击（放置配方+音效），弹窗外点击被吞掉，下层按钮/容器收不到
  - 弹窗打开时滚轮被吞掉（避免翻页重建按钮销毁弹窗）
  - viewer 打开期间滚轮不穿透到容器（`mouseScrolled` 结尾 `return isActive()`）
  - 关闭 viewer 时同时 `RecipePopupLayer.close()`（active 标志不再阻塞下一界面）
- **pin 默认键 F → A**（`BetterRecipeBook.PIN_MAPPING`），7 种语言 tip.2 文案同步
- **pin 创建即显示 tooltip**：移除 `PinOverlayManager` 创建时的 `disarmTooltip()` 防闪现逻辑（`PinOverlay.disarmTooltip` 已删除；`tooltipArmed` 恒 true）

**2026-08-22 早间（两分支同步，4 项）**：
- **燃料 tooltip 补齐图标**（`RecipeViewerOverlay`）：标题行加燃料物品图标（复用 `TitleWithIconTooltipComponent`，与其他类别一致）；三行子类别（熔炉/鼓风炉/烟熏炉）的工作站图标改用 `workstationsIconsForPrefix(stationCategoryPrefix(i))` 聚合查询——JEI 插件注册的 mod 工作站（如 BetterEnd 末地石冶炼炉注册为 blasting）现在显示在对应行上（原 `stationIcons(i)` 只取内建代表，已删除 `stationIcons`/`furnaceWorkstation` 死代码）
- **ESC 不关闭 pin 界面**（`PinOverlayManager.handleEscape`）：ESC 只关闭查询 viewer，pin 只能按预览键（默认 A）关闭或随宿主界面关闭；`topmostPin` 死代码删除
- **工作站 usage 查询架构修复**（`RecipeViewerIndex.rebuildEngine`）：工作站 items 按 typeId 聚合（builtin+config+external 全部条目的 fallbackIcons 合并进引擎 stationItems），此前 external 与 builtin 共享 typeId（如 `minecraft:blasting`）时引擎索引只含 builtin 条目 → 查询 mod 工作站（BetterEnd `end_stone_smelter` 注册为 blasting catalyst）usage 时 viewer 打开但 0 对象。修复后：任何注册工作站块的 usage 查询都返回整个 type（JEI 语义）
- **合成器（crafter）加入合成类别工作站**（`BUILTIN_WORKSTATIONS` CRAFTING 条目 items 加 `minecraft:crafter`）：usage 查询合成器显示全部合成配方；`recipeFitsScreen` 的 `crafting_` → `AbstractCraftingMenu` 路径不受影响（CrafterMenu 不继承 AbstractCraftingMenu，crafter 界面无配方书放置，仅查询语义生效）

**2026-08-22 早间（二）：JEI 插件类别数据源配方书驱动（两分支同步）**——带配方书的 mod（如 Farmer's Delight 厨锅）的 JEI 插件类别，其配方数据源**自动跟随配方书解锁状态**，不写死任何 mod 路径。初版按 RecipeBookCategory 判定（recipeBookCategoryIds），实机发现 JEI `registerRecipes` 收集在此环境不可靠（FD 用 Fabric `SynchronizedRecipes` 传 RecipeHolder，且配方同步晚于收集时机）→ **重构为 known craftingStation 归属**（最终实现）：
- 归属：`PluginRecipeIndexer` 遍历 `RecipeViewerIndex.knownEntries()`（配方书已解锁条目），仅取 **mod 配方书类别**（category 的 id namespace ≠ minecraft）的条目，解析其 display 声明的 `craftingStation()`（FD cooking 配方的 display 自带厨锅 `ItemSlotDisplay(COOKING_POT)`），用 catalysts 反查（`typeUidForStation`）归属到 JEI type，注册 type（数据源 = known 解锁子集，stations = catalysts）
- 时序：在 JEI 全量注册之后执行（配方书数据优先）；known 重建 → rebuildEngine → 重建监听器 → collectAndInject 重新收集，解锁变化动态跟随；mod 自动解锁 → known 全量 → 全部显示
- 无匹配（纯 JEI 类别如 BetterEnd infusion）→ 保持 JEI 全量路径；vanilla 类别条目被排除（归 rebuildEngine 管，且防止经 mod 的 crafting_table catalyst 误归属）
- 已知环境问题（未修）：26.2 实例 FD `registerRecipes` 的 recipes 为空（fabric 配方同步晚于收集时机），JEI 全量路径对 mod type 全部 0 可索引——配方书驱动路径不受影响
- `RecipeViewerIndex` 新增 public `knownEntries()` / `resolveCraftingStation(RecipeDisplayEntry)` / `toIndexed(RecipeDisplayEntry)`；`RecipeViewerEngine` 新增 `isVanillaType(String)`

## Deployment

Test instances at `/home/avalonia/data/MinecraftLib/versions/{GAME_VERSION}-{MOD_LOADER}/mods/` (`MOD_LOADER` capitalized: `Fabric`/`NeoForge`). Deploy pattern:
```bash
cp fabric/build/libs/brbe-ava-fabric-*.jar /home/avalonia/data/MinecraftLib/versions/1.21.11-Fabric/mods/
cp neoforge/build/libs/brbe-ava-neoforge-*.jar /home/avalonia/data/MinecraftLib/versions/1.21.11-NeoForge/mods/
```
构建完必须部署；部署前将实例内同版本 JAR 备份为 `*.jar.bak.YYYYMMDD`，再覆盖旧版本产物。
- **pin/viewer 配方状态基于真实物品栏**（2026-08-21，两分支同步）：`PartialCraftingUtil.realInventorySlots()`（items+armor；offhand 由 `offhandStack()` 单独计入）替代屏幕容器槽位。`PinOverlayManager.refreshRecipeStates` 哈希、`PinOverlay.create/refreshRecipeState`（craftable 判定 + partial 标记）、`RecipeViewerOverlay` 两处 `prepareForViewer` 均改用真实物品栏——创造模式物品栏的合成网格与 carried 可能来自创造标签（虚拟物品），不再被当作材料。
- **常规检索空间统一**（2026-08-21，两分支同步）：`PartialCraftingUtil.searchSpaceSlots()` 是配方状态判定的**唯一槽位来源**——玩家真实物品栏（items+armor）+ 打开容器菜单的合成网格（工作台 3×3 / 背包 2×2 / 熔炉 input+fuel），**排除合成台/熔炉结果栏**（刚合成的产物不算可用材料）。carried（拿起物品）由 `slotHash`/`prepareForViewer` 参数传入，offhand 由 `offhandStack()` 内部计入；craftable 判定（stacked）统一走 `fillSearchSpaceStackedContents`。配方书 mixin、pin（create/refreshRecipeState/refreshRecipeStates）、viewer（2 处 prepareForViewer）、幽灵浮层（PartialGhostOverlayUtil.prepare）、RecipeStateDiagnostic 全部改走该入口。
- **预览/pin 残缺红罩**（2026-08-21，两分支同步）：残缺配方状态下界面本体盖整块红罩（`0x60FF3333`）。曾两度尝试按槽位标记/挖洞后按用户要求回退，保持整块红罩。
