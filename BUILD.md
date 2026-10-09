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
需要先准备 `libs/README.md` 列出的四个本地 JAR。其他依赖由 Gradle 下载。

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

- `build/libs/gd656conquest-0.9.3-normal.jar`：默认完整模组，网络协议 098，六项客户端 bypass 开关均为 false。
- `build/libs/gd656conquest-0.9.3-bypass.jar`：原工程已有的客户端绕过配置变体。
- `build/libs/gd656conquest-0.9.3-nores.jar`：原工程已有的无 assets 变体。
- `build/libs/gd656conquest-0.9.3-resources.zip`：原工程已有的 assets 资源包。

JAR 构建包含 ForgeGradle 重混淆，不打包第三方依赖。普通安装选择 `normal.jar`。
构建日志保存在 `.build-logs/build.log`。

## 本次环境修复

- 将失效的 `../联动模组jar` 引用替换为项目 `libs/`，可通过 Gradle 参数 `-PlocalModsDir=<目录>` 覆盖。
- 本地依赖以 `local.mods` 坐标声明，参与 ForgeGradle 开发映射；不再使用 `fg.deobf(files(...))`。
- Modrinth、Curse Maven、本地依赖仓库分别限制到对应 group，避免无关坐标查询。
- 使用工作区已有载具 `0.5.7-all`，替代原配置的 `0.5.4.2-all`；Explosion Overhaul 使用 `0.1.5.9-a-656260819` 变体。
- 补充 `mods.toml` 已声明必需的 Killicon 开发运行依赖。
- 固定 ForgeGradle 版本、启用 UTF-8，限制 Gradle worker 数为 2。
- Wrapper 使用 Gradle 官方发行地址及 SHA-256 校验，复用当前工作区已经安装的 Gradle 8.8 缓存。

