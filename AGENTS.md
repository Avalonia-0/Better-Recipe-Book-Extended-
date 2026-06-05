# Repository Guidelines

## Project Structure & Module Organization
This is an Architectury-based Minecraft mod with three Gradle modules:

- `common/`: shared gameplay logic, mixins, config, utilities, and resources.
- `fabric/`: Fabric entrypoints, platform implementations, and Fabric-only integrations.
- `neoforge/`: NeoForge entrypoints, platform implementations, and NeoForge packaging.

Source code lives under `*/src/main/java/com/alonie/brbe/...` and resources under `*/src/main/resources/`. Build outputs are generated in each module’s `build/` directory and should not be committed.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root:

- `./gradlew build` compiles all modules and produces distributable jars.
- `./gradlew :fabric:runClient` starts a Fabric dev client.
- `./gradlew :neoforge:runClient` starts a NeoForge dev client.
- `./gradlew clean build` removes old outputs and rebuilds from scratch.

There is no dedicated automated test suite in this repository. Use the client run tasks to verify behavior after code changes.

## Coding Style & Naming Conventions
The codebase uses Java 21, UTF-8, and standard 4-space indentation. Keep packages under `com.alonie.brbe` and follow the existing naming patterns:

- Classes: `PascalCase` such as `BetterRecipeBookClientFabric`
- Methods and fields: `camelCase`
- Constants: `UPPER_SNAKE_CASE`

Prefer small, direct changes that match the surrounding module structure. Keep platform-specific code in the loader module, not in `common/`, unless the behavior is truly shared.

## Testing Guidelines
No JUnit or integration test harness is configured. Validation is done by launching the relevant loader client and checking the affected recipe book flows in game. When adding or changing mixins, verify both loader targets if the code path is shared.

## Commit & Pull Request Guidelines
Git history shows short, task-focused commit subjects, often in Chinese, describing the visible change or fix. Keep commits similarly concise and specific. For pull requests, include:

- a brief summary of the change
- the affected loader/module(s)
- reproduction or verification steps
- screenshots or video for UI-facing changes

## Configuration Notes
Version and dependency pins live in `gradle.properties`. Update those values carefully, because they control the generated artifact names and the loader-specific build configuration.
