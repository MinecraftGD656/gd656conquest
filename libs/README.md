# 本地开发依赖

将以下原始运行版 JAR 放在本目录。二进制文件被 `.gitignore` 排除，
不会被打进 GD 成品；Gradle 会为开发环境生成 official 映射副本。

- `player-animation-lib-forge-1.0.2-rc1+1.20.jar`
- `ywzj_vehicle-1.20.1-0.5.7-all.jar`
- `Explosion-Overhaul-0.1.5.9-a-656260819.jar`
- `gd656killicon-1.3.3-1.20.1-forge.jar`

当前工作区的来源为 `../build-project/dependencies/mods/`。
可以通过 `-PlocalModsDir=<目录>` 使用其他位置，并在 `gradle.properties`
中调整对应版本。不要把已经映射过的开发 JAR 当成这里的原始依赖。
