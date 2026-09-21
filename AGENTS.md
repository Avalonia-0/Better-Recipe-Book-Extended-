# Repository Guidelines

## Project Structure & Module Organization
Single-module Fabric project (no Architectury) targeting **Minecraft 26.3** (Java 25). All sources under `src/main/java/com/alonie/brbe/...` and resources under `src/main/resources/`. Uses official `net.fabricmc.fabric-loom` 1.17.18 (Minecraft 26.1+ is unobfuscated — no remap needed). Build outputs are generated in `build/` and should not be committed.

The embedded headless JEI runtime is a **separate project**: `headless-jei/26.3` on the `headless-jei` git branch. Its jar is committed here under `libs/` (compile reference), embedded into the mod jar via `fabric.mod.json` `jars`, and additionally wired as a dev-runtime dependency because Fabric Loader does **not** expand nested jars in dev runs.

## Version Baseline (26.3)
Minecraft 26.3 · Fabric Loader 0.19.5 · Fabric API 0.161.0+26.3 · Cloth Config 26.3.158 · Fabric Loom 1.17.18 · mod_version 2.3. See `docs/26.3-api-changes.md` for the 26.2 → 26.3 API deltas (GLFW→SDL3, renderpearl render pipelines, the removal of `PotionBrewing`/`FuelValues`/`ComposterBlock.COMPOSTABLES`, and more).

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:

- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk sh gradlew build` compiles and produces `build/libs/brbe-ava-fabric-26.3-2.3.jar`.
- `JAVA_HOME=/usr/lib/jvm/java-25-openjdk sh gradlew runClient` starts a Fabric dev client.
- `sh gradlew clean build` removes old outputs and rebuilds from scratch.

Note: `gradlew` is tracked without the executable bit in this repository — invoke it as `sh gradlew ...`, and always set `JAVA_HOME` to a JDK 25.

There is no dedicated automated test suite in this repository. Use the client run tasks to verify behavior after code changes.

## Coding Style & Naming Conventions
The codebase uses Java 25, UTF-8, and standard 4-space indentation. Keep packages under `com.alonie.brbe` and follow the existing naming patterns:

- Classes: `PascalCase` such as `BetterRecipeBookClientFabric`
- Methods and fields: `camelCase`
- Constants: `UPPER_SNAKE_CASE`

Prefer small, direct changes that match the surrounding structure. Keep platform-specific code under `com.alonie.brbe.fabric` and `com.alonie.brbe.brewingstand.fabric`.

## Testing Guidelines
No JUnit or integration test harness is configured. Validation is done by launching the Fabric client and checking the affected recipe book flows in game.

## Commit & Pull Request Guidelines
Git history shows short, task-focused commit subjects, often in Chinese, describing the visible change or fix. Keep commits similarly concise and specific. For pull requests, include:

- a brief summary of the change
- the affected loader/module(s)
- reproduction or verification steps
- screenshots or video for UI-facing changes

## Configuration Notes
Version and dependency pins live in `gradle.properties`. Update those values carefully, because they control the generated artifact names and the loader-specific build configuration.
