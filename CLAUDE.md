# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BetterRecipeBook (Extended) is a client-side Minecraft mod that overhauls the vanilla recipe book UI. Multi-loader via Architectury: common logic in `common/`, platform entrypoints in `fabric/` and `neoforge/`. Mod ID: `brbe`.

## Branch Strategy & Build Commands

| Branch | MC Version | JDK | Gradle | Recipe API | Filter Button |
|--------|-----------|-----|--------|------------|---------------|
| `1.21.1` | 1.21.1 | 21+ (use JDK 22) | 8.13 | `RecipeHolder<?>` + `ResourceLocation` | `StateSwitchingButton` |
| `1.21.11` | 1.21.11 | 21+ (use JDK 22) | 8.13 | `RecipeDisplayEntry` + `RecipeDisplayId` | `CycleButton<Boolean>` |
| `26.1.2` | 26.1.2 | **25** | **9.4.0** | `RecipeDisplayEntry` + `RecipeDisplayId` | `CycleButton<Boolean>` |

**Always commit before switching branches.** Stale `common/build/` artifacts pollute cross-branch builds — run `./gradlew :common:clean` after switching if you see unexpected errors (especially `RecipeDisplayId not present`).

```bash
# 1.21.1 / 1.21.11
export JAVA_HOME=/usr/local/jdk-22.0.2+9
./gradlew :fabric:build :neoforge:build -x test -x check --no-daemon -Djava.net.useSystemProxies=true

# 26.1.2
export JAVA_HOME=/usr/local/jdk-25.0.3+9
./gradlew :fabric:build :neoforge:build -x test -x check --no-daemon -Djava.net.useSystemProxies=true

# Cross-branch pollution cleanup
./gradlew :common:clean
```

JEI jars for compilation go in `libs/` (gitignored). Build files also reference absolute paths in `~/Downloads/1.21.1/`.

## Key Version-Specific API Differences

### Recipe System & Button Lifecycle
- **1.21.1:** `RecipeCollection.getRecipes()` → `List<RecipeHolder<?>>`. Button uses `getOrderedRecipes()` (private) for cycling; has `currentIndex` field. `init(RecipeCollection, RecipeBookPage)` — 2 params.
- **1.21.11+:** `RecipeCollection.getRecipes()` → `List<RecipeDisplayEntry>`. Button has `selectedEntries` (List<ResolvedEntry>) + `getCurrentRecipe()`. `init(RecipeCollection, boolean, RecipeBookPage, ContextMap)` — 4 params.

### Filtering
- **1.21.1:** `RecipeBook.isFiltering(RecipeBookMenu<?, ?> menu)` on the stats object. `updateCollections(boolean)` — 1 param. `RecipeBookPage.updateCollections(List, boolean)` — 2 params.
- **1.21.11+:** `RecipeBookComponent.isFiltering()` parameterless. `updateCollections(boolean, boolean)` — 2 params. `RecipeBookPage.updateCollections(List, boolean, boolean)` — 3 params.

### Rendering
- **1.21.1 / 1.21.11:** `renderWidget(GuiGraphics, int, int, float)`. `renderFakeItem(ItemStack, int, int)`.
- **26.1.2:** `extractWidgetRenderState(GuiGraphicsExtractor, int, int, float)`. `fakeItem(ItemStack, int, int)`.

### Character Events (ClientCompat)
- **1.21.11:** `CharacterEvent(char, int)` — 2 args
- **26.1.2:** `CharacterEvent(int codepoint)` — 1 arg

## Module Layout

```
common/      — All logic, mixins, assets, config — bundled into both platform JARs
fabric/      — Fabric entrypoints, JEI/REI plugins
neoforge/    — NeoForge entrypoints, access transformer
```

### Key Packages in `common/src/main/java/com/alonie/brbe/`

| Package | Purpose |
|---------|---------|
| `mixins/incompletecrafting/` | Partial-materials detection + cycling filter + overlay overlay |
| `mixins/incompatibleenvironment/` | Show 3×3 recipes in survival inventory |
| `mixins/accessors/` | `@Accessor` interfaces for private fields |
| `generic/` | Abstract recipe book framework (brewing/smithing) |
| `brewingstand/`, `smithingtable/` | Concrete recipe book implementations |
| `config/` | Cloth Config AutoConfig (TOML) |
| `compat/` | JEI/REI/MouseWheelie compatibility bridges |
| `util/` | `PartialCraftingUtil`, `IncompatibleCraftingUtil`, `ClientCompat` |

## The Generic Recipe Book Framework

Abstract framework in `generic/` parameterized over `<M extends AbstractContainerMenu, C, R extends GenericRecipe>`. Implemented concretely for brewing stand and smithing table. To add a new recipe book type, extend `GenericRecipeBookComponent` and implement 4 methods.

## Key Patterns

### Partial Crafting Data Injection (incompletecrafting)

`PartialCraftingUtil` tracks which recipes have partial materials via `WeakHashMap<RecipeCollection, Set<Id>>`. The flow per frame:

1. `RecipeBookComponentMixin` intercepts `updateCollections()` → calls `markPartialMaterials(collection, slots)` for every collection
2. Partial recipe IDs are injected into the vanilla `craftable` set via `RecipeCollectionAccessor` → makes `isCraftable(id)` return true for partials
3. `RecipeButtonMixin` intercepts `getSelectedRecipes`/`getOrderedRecipes` → filters out completely uncraftable recipes when craftable/partial exist (craftable first, then partial)
4. `RecipeBookComponentMixin` intercepts `RecipeBookPage.updateCollections` → sorts collections: craftable → partial → other

`DisableCraftableFilter` (on `RecipeBookComponent`) hides the filter button and disables filtering — making this data injection the primary mechanism for controlling recipe visibility.

### ItemViewCompat

Abstraction for JEI/REI recipe lookup. Both mods register a handler via `ItemViewCompat.setHandler()`. Common code calls `openRecipeView(stack)` / `openUsageView(stack)`.

### Mixin Configuration

Mixin JSONs in `common/src/main/resources/`:

| File | Required | Purpose |
|------|----------|---------|
| `mixins.brbe-common.json` | true | Core mixins (~45 entries) |
| `mixins.brbe-common-compat.json` | false | MouseWheelie compat |
| `mixins.brbe-jei.json` | false | JEI overlay hiding |
| `mixins.brbe-rei-common.json` | false | REI key handling |

**Warning:** Avoid creating `mixins.brbe-jei.json` in the `fabric/` module — `jar` task uses `DuplicatesStrategy.EXCLUDE`, which silently drops the common version. Use a different filename (e.g., `mixins.brbe-jei-fabric.json`).

## Config

Cloth Config AutoConfig with TOML serialization (`Config.java`). Key fields: `enablePinning`, `showAllRecipesInSurvival`, `hideReiJeiOverlay`, `showModName`, `keepCentered`. Sub-config categories: `NewRecipes`, `InstantCraft`, `AlternativeRecipes`, `Scrolling`.

## Key Dependencies

- Architectury 13.x (1.21.1/1.21.11) or 20.x (26.1.2)
- Cloth Config 15.x
- Fabric API or NeoForge
- JEI/REI (optional, compile-only)

Access widener: `brbe.common.accesswidener` opens `PotionBrewing$Mix`.
