# headless-jei

无头 JEI 独立项目（每版本独立 Gradle 工程，架构对应各核心分支）：

| 目录 | MC | 架构 | 状态 |
|------|----|------|------|
| `1.21.1/` | 1.21.1 | Architectury 三模块（common/fabric/neoforge，Java 21） | 源码+桥已就位；构建验证受限（见下） |
| `1.21.11/` | 1.21.11 | Fabric 单模块（fabric-loom-remap 1.14.6，Java 21） | 源码已复制；桥切割进行中 |
| `26.2/` | 26.2 | Fabric 单模块（fabric-loom 1.17.18 no-remap，Java 25） | 待复制 |

## 定位

真实 JEI 缺席时提供嵌入式 JEI 运行时（官方源码内嵌 fork），对外暴露**轻量桥**：

- `JeiRecipeRegistry`（`com.alonie.brbe.jei.api`）：类型 uid → 配方条目值对象（物品输入/输出 + 槽位布局）
- `JeiPopupRenderer`：把条目渲染为完整 JEI UI（类别背景/槽位/动画，`createRecipeLayoutDrawable`）
- 收集由 `com.alonie.brbe.jei.plugins.*` 完成（入口点扫描 + anvil/brewing/grindstone 运行时配方）

消费者（BRBE 主 mod）编译依赖本工程产物（`libs/` 或直接 mod 打包），JOIN 时从 registry 拉数据转进自己的查询引擎。

## 已知构建问题（2026-08-28）

新建工程冷配置在 1.21.1（architectury + neoforge 平台）下偶发
`Could not find method neoForge()` / `Failed to compute checksum`——与核心分支
老旧 `.gradle` 缓存状态有关；fabric-loom-remap 冷管线（1.21.11）验证可用。
修复方向：清 `~/.gradle/caches/fabric-loom` 或重启后全量重建。
