# 原始源码构建

环境：Windows、Java 17 JDK、Gradle Wrapper 8.8、ForgeGradle 6.0.54，
Minecraft 1.20.1 / Forge 47.4.18 / official 1.20.1 映射。

## 构建

在本项目目录运行：

```powershell
.\build.ps1 -Clean
```

脚本自动寻找本机 Java 17 JDK，优先复用工作区的 `../build-tools/gradle-cache`。
若不存在共享缓存，则使用项目下被忽略的 `.gradle-user-home`。
需要先准备 `libs/README.md` 列出的五个本地 JAR。其他依赖由 Gradle 下载。

已有完整缓存时可以离线构建：

```powershell
.\build.ps1 -Offline -Clean
```

自定义 JDK 与缓存目录：

```powershell
.\build.ps1 -JavaHome 'C:\path\to\jdk-17' -GradleUserHome 'C:\path\to\gradle-cache'
```

也可以使用标准 Wrapper；请先将 `JAVA_HOME` 设为 Java 17 JDK：

```powershell
.\gradlew.bat --gradle-user-home ..\build-tools\gradle-cache clean build
```

## 输出

- `build/libs/gd656conquest-0.9.3-bf1-0.1.0.jar`：当前默认完整模组，网络协议为 `bf1-0.1.0`，六项客户端 bypass 开关均为 false。
- `build/libs/gd656conquest-0.9.3-bf1-0.1.0-bypass.jar`：原工程已有的客户端绕过配置变体。
- `build/libs/gd656conquest-0.9.3-bf1-0.1.0-nores.jar`：原工程已有的无 assets 变体。
- `build/libs/gd656conquest-0.9.3-bf1-0.1.0-resources.zip`：原工程已有的 assets 资源包。

JAR 构建包含 ForgeGradle 重混淆，不打包第三方依赖。普通安装选择没有变体后缀的主 JAR。
构建日志保存在 `.build-logs/build.log`。

主产物格式固定为 `gd656conquest-0.9.3-bf1-x.x.x.jar`；`gradle.properties` 的
`migration_version` 控制末尾的迁移版本，同时写入 `mods.toml` 和 manifest。
未迁移原始源码记为 `0.0.0`。每轮以成功构建主 JAR 为完成条件；历史迁移内容见
[迁移版本记录](MIGRATION.md)，迁移后的功能更新写入对应功能文档。协议变化时客户端与服务端须一起更新。

## 安装与兼容

普通安装选择主 JAR，放入 Minecraft/Forge 实例的 mods 目录，不同时安装 bypass/nores 变体。
客户端与服务端使用相同模组版本，当前严格网络协议为 `bf1-0.1.0`；
旧反编译 `.24/114` 不作为当前协议兼容版。
依赖按模组元数据安装；要使用布娃娃功能，客户端和服务端都需安装
`ragdollified-1.20.1-1.1.1-hotfix-RELEASE.jar`，缺席时保留原倒地动画。
开发环境依赖版本及本地 JAR 清单见 [libs/README.md](../libs/README.md)。

迁移专用导入/验证脚本和测试已归档，不参与日常构建；唯一迁移明细见 [MIGRATION.md](MIGRATION.md)。
测试、文档和未打包工具修复不增加模组版本，仅 JAR 本体实际内容变化才升版。
用户已确认 0.0.12 实机表现符合预期；多人/配置升级/重载/第三方组合专项结果在迁移明细中记录。
0.1.0 增加行动模式营切换与战况简报，配置和实机验收步骤见 [行动模式说明](operations-battalions.md)。
营机制回归测试保存在本地 `tools/battalion-tests`，不随仓库发布。配置 Java 17 和依赖缓存后，使用 `gradlew.bat --offline -I tools/battalion-tests/init.gradle battalionTest` 运行；将任务改为 `check` 或 `build` 可在本地检查或构建时一并执行。普通仓库构建不依赖这些本地测试文件。

## 构建环境说明

- 将失效的 `../联动模组jar` 引用替换为项目 `libs/`，可通过 Gradle 参数 `-PlocalModsDir=<目录>` 覆盖。
- 本地依赖以 `local.mods` 坐标声明，参与 ForgeGradle 开发映射；不再使用 `fg.deobf(files(...))`。
- Modrinth、Curse Maven、本地依赖仓库分别限制到对应 group，避免无关坐标查询。
- 使用工作区已有载具 `0.5.7-all`，替代原配置的 `0.5.4.2-all`；Explosion Overhaul 使用 `0.1.5.9-a-656260819` 变体。
- 补充 `mods.toml` 已声明必需的 Killicon 开发运行依赖。
- 固定 ForgeGradle 版本、启用 UTF-8，限制 Gradle worker 数为 2。
- Wrapper 使用 Gradle 官方发行地址及 SHA-256 校验，复用当前工作区已经安装的 Gradle 8.8 缓存。

