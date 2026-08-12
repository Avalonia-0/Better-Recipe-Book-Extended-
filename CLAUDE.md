# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Branch Architecture

This is a **multi-version Minecraft mod**. Each git branch targets a specific Minecraft version:

| Branch | MC Version | Loom Plugin | Java | Mappings |
|--------|-----------|-------------|------|----------|
| `1.21.1` | 1.21.1 | `dev.architectury.loom` | 21 | Yarn (via Architectury remap) |
| `1.21.11` | 1.21.11 | `dev.architectury.loom` | 21 | Yarn (via Architectury remap) |
| `26.1.2` | 26.1.2 | `dev.architectury.loom-no-remap` | 25 | Mojang official |
| `26.2` | 26.2 | `dev.architectury.loom-no-remap` | 25 | Mojang official |

**CRITICAL**: `build.gradle` on 26.1.2/26.2 enforces that `minecraft_version` in `gradle.properties` matches the git branch name. Switching branches without updating `gradle.properties` will fail.

## Build Commands

```bash
# Compile only (fast, for checking errors)
./gradlew :common:compileJava :fabric:compileJava :neoforge:compileJava

# Full build (produces JARs)
./gradlew :fabric:build :neoforge:build -x test -x check

# Clean build
./gradlew clean :fabric:build :neoforge:build -x test -x check

# Reset cross-branch cache corruption
./gradlew cleanLoomCache && rm -rf .gradle
```

JAR outputs: `fabric/build/libs/brbe-ava-fabric-{mcversion}-{version}.jar` (neoforge: `brbe-ava-neoforge-{mcversion}-{version}.jar`)

## Project Structure

```
common/         # Shared code (95% of all code lives here)
  src/main/java/com/alonie/
    brbe/                   # Main mod: Better Recipe Book Extended
      mixins/               # Mixin classes targeting vanilla MC classes
      config/               # Config class + sub-configs
      util/                 # PartialCraftingUtil, IncompatibleCraftingUtil, etc.
      generic/              # GenericRecipeButton, GenericRecipeBookComponent, etc.
      smithingtable/        # Smithing table recipe book
      brewingstand/         # Brewing stand recipe book
    recipebookispain_extended/  # RBIP sub-mod: creative tabs in recipe book

fabric/         # Fabric platform module (entrypoints, compat, JEI mixins)
neoforge/       # NeoForge platform module (entrypoints, compat, JEI plugins)
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

- **26.2 NeoForge**: NeoForge 26.2.0.0-beta exits silently after classloader build — pre-release beta bug, not BRBE.
- **26.2 Fabric**: Works but Cloth Config is embedded (no official 26.2 release yet).
- **26.1.2 NeoForge**: JEI plugin disabled (JEI jar not available for this exact version).

## Deployment

Test instances at `/home/avalonia/data/MinecraftLib/versions/{GAME_VERSION}-{MOD_LOADER}/mods/` (`MOD_LOADER` capitalized: `Fabric`/`NeoForge`). Deploy pattern:
```bash
cp fabric/build/libs/brbe-ava-fabric-*.jar /home/avalonia/data/MinecraftLib/versions/1.21.11-Fabric/mods/
cp neoforge/build/libs/brbe-ava-neoforge-*.jar /home/avalonia/data/MinecraftLib/versions/1.21.11-NeoForge/mods/
```
构建完必须部署；部署前将实例内同版本 JAR 备份为 `*.jar.bak.YYYYMMDD`，再覆盖旧版本产物。
