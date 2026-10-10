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

- `build/libs/gd656conquest-0.9.3-bf1-0.0.9.jar`：本轮迁移的默认完整模组，网络协议为 `bf1-0.0.8`，六项客户端 bypass 开关均为 false。
- `build/libs/gd656conquest-0.9.3-bf1-0.0.9-bypass.jar`：原工程已有的客户端绕过配置变体。
- `build/libs/gd656conquest-0.9.3-bf1-0.0.9-nores.jar`：原工程已有的无 assets 变体。
- `build/libs/gd656conquest-0.9.3-bf1-0.0.9-resources.zip`：原工程已有的 assets 资源包。

JAR 构建包含 ForgeGradle 重混淆，不打包第三方依赖。普通安装选择没有变体后缀的主 JAR。
构建日志保存在 `.build-logs/build.log`。

主产物格式固定为 `gd656conquest-0.9.3-bf1-x.x.x.jar`；`gradle.properties` 的
`migration_version` 控制末尾的迁移版本，同时写入 `mods.toml` 和 manifest。
未迁移原始源码记为 `0.0.0`。每轮以成功构建主 JAR 为完成条件，功能范围与来源对应见
[迁移版本记录](docs/migration/versions.md)。协议变化时客户端与服务端须一起更新。

本轮目标选择验证可在构建后运行（Java 17 JDK 的 `java`）：

```powershell
java -cp build/classes/java/main tools/migration-tests/ReviveTargetSelectionCheck.java
```

该验证覆盖明确目标、无效目标禁止替换、无目标回退及服务端拒绝分支；不启动 Minecraft。

第二轮可一次运行救援选择、死亡协调器及真实 Forge EventBus 验证：

```powershell
.\tools\verify-death-dispatch.ps1
```

入口使用 Java 17 的源码运行模式和真实生产 class，测试不会打包进模组；默认复用工作区
或本项目 Gradle 缓存中的 EventBus/ASM/日志依赖，不依赖旧反编译构建的夹具或 classpath。
可用 `-JavaExecutable <java.exe>`、`-GradleUserHome <缓存>` 或 `-EventBusClasspath <依赖classpath>` 覆盖。

第三轮身体验证使用当前主 JAR、原始 Ragdollified JAR 和本项目的 Forge SRG 开发依赖：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
.\gradlew.bat --gradle-user-home ..\build-tools\gradle-cache --offline writeMigrationVerificationClasspath
.\tools\verify-ragdoll-body.ps1 -JavaHome $env:JAVA_HOME
```

验证包含代次隔离、视野/订阅策略、四种 GD 数据包、真实 Mixin 的两侧应用及依赖缺席、
原生包往返/播放与身体替换/发送者检查。替换流程测试只隔离引擎创建/销毁、运行门禁和网络运输，
保留实际依赖的替换及计数决策；它不等于 Minecraft 启动或物理性能测试。
可用 `-GradleUserHome` 或 `-DependencyClasspath` 指定同一项目的依赖输入；测试和生成文件不打包。

身体功能需要两端都安装已核验的 `ragdollified-1.20.1-1.1.1-hotfix-RELEASE.jar`；
缺少它时保留原倒地动画。GD 协议与原生布娃娃同步格式已有变化，两端应同时更新本轮 GD JAR。

第四轮死亡原因验证使用相同依赖导出，读取本轮主 JAR 与当前原始 Killicon 依赖：

```powershell
.\tools\verify-death-causes.ps1 -JavaHome $env:JAVA_HOME
```

首次或依赖变更后先运行上面的 `writeMigrationVerificationClasspath`。
验证包含生产分类器/作用域/横幅、数据包往返和限制、真实 Forge SRG 的 `/kill` 与死亡钩子
Mixin/refmap 应用、Killicon 火焰归属接口及生产调用链。它不启动游戏或使用旧反编译测试夹具。
第四轮新增死亡原因包；第五轮新增镜头与趴姿解除包，第七轮追加卡片/部署快照包，当前协议为 `bf1-0.0.8`，客户端与服务端须同时更新。

第五轮镜头/复活姿态验证同样使用当前主 JAR 与本项目依赖：

```powershell
.\tools\verify-downed-view.ps1 -JavaHome $env:JAVA_HOME
```

入口包含生产镜头构图/运动/归位数学、实际数据包、救援/部署的生产调用顺序、
实际 Forge Camera/MouseHandler 与 TACZ 爬行类的 Mixin/refmap，以及 TACZ 缺席检查。
原始 TACZ 验证 JAR 默认从工作区 `../dist/dependencies/tacz-*.jar` 唯一匹配，
可用 `-TaczJar <原始TACZ.jar>` 覆盖；这是验证输入，不新增打包依赖。
测试不使用旧世界夹具，也不替代真实 Minecraft 控制器/碰撞/多人验收。

服务器配置文件 `gd656conquest-downed-server.toml` 的 `deathCameraMode` 可设为
`FIXED`（默认）或 `AUTOMATIC`。两种模式均在救援后恢复倒地前朝向并保持趴姿，
重新按跳跃键或 TACZ 爬行键解除。第六轮已接入世界去色及复活曝光/bloom。

第六轮世界滤镜验证读取本轮主 JAR、当前 Forge SRG 和本工程依赖导出：

```powershell
.\tools\verify-world-filters.ps1 -JavaHome $env:JAVA_HOME
```

包含死亡/复活时间线、真实 Mixin/refmap 与世界/HUD 顺序、资源重载/关闭入口、
服务器隔离和成品范围检查。GPU 部分创建隐藏 OpenGL 上下文，直接编译成品中的 GLSL，
验证五段复活图、半尺寸 bloom、缩放及天空 alpha；需要可用的本机 OpenGL 驱动。
LWJGL/native 与 vanilla blit 从本工程 Gradle 缓存读取，临时文件进入被忽略的 build 目录。
保留 `build/libs/gd656conquest-0.9.3-bf1-0.0.5.jar` 用于前后成品对照。
这不替代 Minecraft 实机、第三方 shader/瞄具或联机验收。

第六轮是客户端视觉增量，协议保持 `bf1-0.0.5`；第七轮已追加两个 S2C 包，
当前协议为 `bf1-0.0.8`，两端须一起更新。

第七轮击杀者信息卡、手动部署与聊天布局验证：

```powershell
.\tools\verify-killer-card.ps1 -JavaHome $env:JAVA_HOME
```

读取当前主 JAR、本工程依赖及当前 refmap；保留 `build/libs/gd656conquest-0.9.3-bf1-0.0.6.jar`
用于范围核对。包含实际卡片/对决/布局/输入模型、两个包的真实往返与非法字段拒绝、
真实 Forge EventBus 注册及可选元数据扩展失败隔离、ChatComponent/ChatScreen/CommandSuggestions
三类的真实 Mixin 与服务器侧隔离、服务端门槛及退出清理的静态调用顺序。
不会使用旧世界夹具，不等于游戏中的击杀者快照/爆头/多人完整流程验收。
第六、七轮的范围脚本绑定各自阶段；0.0.8 的范围审计与卡片/滤镜回归使用下述第八轮脚本；0.0.9 的范围审计使用第九轮脚本。

第八轮统一出界、配置和载具验证（该脚本的范围检查绑定 0.0.8）：

```powershell
.\tools\verify-boundary.ps1 -JavaHome $env:JAVA_HOME
```

保留 `build/libs/gd656conquest-0.9.3-bf1-0.0.7.jar` 作为范围审计输入。
验证当前主 JAR 的计时/几何、真实 Gson/编辑/校验/重载/模板复制、包往返、语音冷却；
使用当前 refmap 在真实 Forge GameRenderer 与 TACZ 上应用死亡及边界 Mixin，检查重载/关闭独立接入、
360 项真实 HUD 事件、SERVER 隔离和 TACZ 缺席；原始 YWZJ 0.5.7-all/Superb Warfare 依赖用于爆炸钩子及缺席检查。
载具请求/乘员时序检查使用成品方法字节码与服务端/载具夹具，不启动 Minecraft。
GPU 检查编译当前产物 GLSL 并读回像素；压制 uniform 值本轮固定为 0，不要求尚未迁入的压制着色器。
同一脚本回归死亡/复活滤镜、卡片、手动部署与元数据扩展；死亡归属、镜头检查仍可运行上面的专用脚本。
第八轮复用旧包槽位传输出界剩余 tick/乘坐类型，协议语义变更为 `bf1-0.0.8`，客户端和服务端一起更新。

第九轮部署发弹与储备上限验证：

```powershell
.\tools\verify-ammo-reserves.ps1 -JavaHome $env:JAVA_HOME
```

读取当前主 JAR 和本工程 Forge SRG/依赖导出；保留
`build/libs/gd656conquest-0.9.3-bf1-0.0.8.jar` 作为本轮成品范围对照。
验证两轮发弹、有限/扩容/小容量格子、实际部署 NBT 上限及旧枪回退；
使用成品方法字节码、背包/房间及枪械元数据适配器和真实 Minecraft NBT，验证
最终配备覆盖修正、同弹池合并、弹药箱/包限额补给与无效玩家拒绝。
并核对仅两个原业务类的方法发生有意改变；其他生产 class、Mixin/refmap、资源、网络注册和协议保持 0.0.8。
测试适配器不打包，也不等于实际 TACZ/Superb Warfare 枪械或 Minecraft 多人玩法验收。
本轮版本 `0.0.9`，没有数据包变化，协议保持 `bf1-0.0.8`。
新箱包光环、权限/投包/死亡掉包、医疗与私人音效仍按 P5 后续批次迁入；当前旧包数量规则和公共补给声音暂保留。

## 本次环境修复

- 将失效的 `../联动模组jar` 引用替换为项目 `libs/`，可通过 Gradle 参数 `-PlocalModsDir=<目录>` 覆盖。
- 本地依赖以 `local.mods` 坐标声明，参与 ForgeGradle 开发映射；不再使用 `fg.deobf(files(...))`。
- Modrinth、Curse Maven、本地依赖仓库分别限制到对应 group，避免无关坐标查询。
- 使用工作区已有载具 `0.5.7-all`，替代原配置的 `0.5.4.2-all`；Explosion Overhaul 使用 `0.1.5.9-a-656260819` 变体。
- 补充 `mods.toml` 已声明必需的 Killicon 开发运行依赖。
- 固定 ForgeGradle 版本、启用 UTF-8，限制 Gradle worker 数为 2。
- Wrapper 使用 Gradle 官方发行地址及 SHA-256 校验，复用当前工作区已经安装的 Gradle 8.8 缓存。

