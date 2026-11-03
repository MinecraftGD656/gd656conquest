# GD656Conquest

GD656Conquest 是一个面向 Minecraft 1.20.1 / Forge 的多人战术射击玩法框架模组。它在服务端提供房间化的对局生命周期、据点与阶段目标、配装与支援系统，在客户端提供配套的 HUD、界面与音频表现，让 Minecraft 服务器能够组织起接近《战地》系列“征服”模式的多人对局。

- **许可证**：GNU General Public License v3.0 or later（GPL-3.0-or-later），见 [LICENSE](LICENSE)
- **运行环境**：Minecraft 1.20.1、Forge 47.x、Java 17
- **开发状态**：早期版本（0.10.x），玩法规则、配置项与网络协议仍可能变动
- **维护状态**：已进入低维护，除严重问题外不再主动更新；全部功能与版本号线自 **0.11.0** 起由继任模组 **Battlefield656Core**（`battlefield656core`）继承，详见[维护状态与继任模组](#维护状态与继任模组)

## 功能概览

**对局与房间**

- 房间以 JSON 定义持久化在存档中（玩法、地图、出生点、边界、阵营、结算参数等约 180 项配置），支持模板与历史版本目录。
- 房间状态机（`IDLE → MATCHING → RUNNING → ENDING → IDLE`，另有锁定、维护、停用态）；全局可限制同时进行的房间数。
- 匹配队列、最低开局人数与等待倒计时、对局加载过场、中途加入、断线重连票据、阵营切换与自动换边提示、小队分组。
- 房间维度装载/卸载、对局结束后的热重置与断点恢复。

**玩法模式**

- 征服（据点占领与得分推进）、突破（分阶段推进与兵力池）、突袭（先爆破后推点，含炸弹安放/拆除/引信）、团队死斗、枪王；含 SOLO 变体。

**交战与支援**

- 部署/重新部署流程、据点边界烘焙与渲染、倒地与复活（除颤/注射）、弹药箱与医疗箱、无线电信标、地面传感器、载具乘员快捷栏锁定与越界处理。
- 配装系统：兵种、装备库、武器配件、精英兵模板；炮击支援模板与呼叫流程。
- 排行与社交：称号、组队（party）、跑酷与排行榜、靶场、对局结算与历史统计。

**客户端与网络**

- 自研 GUI 组件库与房间/配装/载具编辑器界面，HUD 渲染、字幕与播报、音乐导演与据点音效、按键映射、调试覆盖层与 HUD 预览。
- 单一网络通道 `gd656conquest:main`，约 60 个包，协议版本随模组版本推进，客户端与服务端不一致时直接拒绝连接。
- 25 个 Mixin（客户端渲染与界面、服务端逻辑、第三方联动补丁），用于在不侵入原版代码的前提下补齐表现与兼容行为。

## 运行环境与依赖

必需：

- Minecraft 1.20.1 + Forge 47.x（`mods.toml` 中声明强制依赖）
- [GD656Killicon](https://www.mcmod.cn/class/21672.html) 1.0+（同作者的击杀图标模组）
- PlayerAnimator 1.0+（仅客户端）

可选软联动（未安装时相应联动逻辑自动跳过，其余功能不受影响）：YWZJ 载具、卓越前线（Superb Warfare）、永恒枪械工坊：零（TACZ）、LR 战术工坊、沉浸式飞机、Explosion Overhaul、Curios、Spotting、Ping Wheel、Xaero 小地图、Modern UI、GD656MotionHUD、GD656Peek。

## 安装

1. 安装 Minecraft 1.20.1 的 Forge 47.x 服务端/客户端。
2. 将构建产物与上述必需前置放入 `mods/` 目录。
3. 客户端与服务端的 GD656Conquest 版本必须一致，否则连接会被拒绝。

## 从源码构建

```bash
# 需要 JDK 17
./gradlew build            # Windows: gradlew.bat build
```

产物位于 `build/libs/gd656conquest-<version>.jar`。部分联动依赖从本地 `../联动模组jar/` 目录读取，缺失时相关联动模块需自行在 `build.gradle` 中调整。

构建产物之外，仓库中的 `docs/`、`技术文档/`、`开发临时文件/` 为开发过程资料与素材工作区，不参与打包。

## 常用命令

所有命令挂在根命令 `/gd656conquest`（简写 `/gdc`）下，主要子命令：`room`（房间）、`loadout`（配装）、`equipment`（军服模板）、`herokits`（精英兵）、`shelling`（炮击模板）、`party`（组队）、`title`（称号）、`parkour`（跑酷）、`range`（靶场）、`player`、`hub`、`deploy`、`redeploy`、`level`、`config`、`pack`、`migrate`、`action`、`debug`。逐条用法见 `技术文档/01-指令参考手册.md`。

## 目录结构

```
src/main/java/org/mods/gd656conquest/
├── common/    方块、实体、物品、比赛与据点等共享模型
├── server/    房间、玩法、配装、社交、数据持久化（18 个 ServerModule）
├── client/    HUD、GUI、渲染、音频、输入与本地状态镜像
├── network/   网络通道与约 60 个数据包
└── mixin/     客户端/服务端/第三方联动的注入补丁
src/main/resources/   assets（模型、音效、语言）与 data（维度）、mods.toml
```

## 维护状态与继任模组

GD656Conquest 的开发已转入继任模组 **Battlefield656Core**（mod id `battlefield656core`）：

- **功能承接**：本模组的全部功能在 Battlefield656Core 中继承并继续演进，本仓库不再新增功能。
- **版本号承接**：本仓库版本线终止于 0.10.x；Battlefield656Core 自 **0.11.0** 起接续同一条版本线，后续版本号不再在本仓库发布。
- **更新策略**：本仓库之后只处理严重问题——严重的安全漏洞，可明确复现的崩溃；普通缺陷修复与体验优化请联系作者本人。
- 本仓库代码、文档与许可证继续有效，仍按 [LICENSE](LICENSE) 中的 GPL-3.0-or-later 授权。

## 许可证

本项目以 **GNU General Public License v3.0 or later**（SPDX：`GPL-3.0-or-later`）授权发布，许可证全文见 [LICENSE](LICENSE)。

Copyright (C) 2026 MinecraftGD656

在遵守 GPLv3 条件的前提下，你可以自由使用、修改、分发本项目（含商业用途）：分发时须提供完整对应的源码、保留版权与许可声明、以同一许可证授权衍生作品，且不得附加额外限制。本程序按 GPLv3 第 15、16 条以“现状”提供，不附带任何担保。

请注意授权边界：Forge/FML 运行时以 LGPL-2.1 发布，Minecraft 本体、官方映射与 Mojang EULA 约束的内容不在本仓库的授权范围内。

## 关于 AI 生成代码

本项目的**绝大部分代码、资源定义与文档由大语言模型（AI）生成或在 AI 辅助下完成**，包括服务端玩法逻辑、网络协议、客户端渲染与界面、数据模型以及本说明。维护者负责提出需求、确定架构、审查与整合生成结果，并完成编译与游戏内实测，但并未对每一行代码做独立的人工审计。

因此请知悉：

- AI 生成的代码可能残留未被发现的逻辑错误、边界条件缺陷或安全隐患，用于生产服务器前请先在测试环境验证并做好存档备份。
- 项目按 GPLv3“现状”提供，不附带任何明示或暗示担保；使用风险由使用者承担。
- 欢迎通过 issue / pull request 指出问题或提交修复，来自人工审查与实机测试的反馈对本项目尤其重要。

## 第三方素材与商标

`src/main/resources/assets/` 下包含音频与图像资源，其中部分素材并非本项目原创（部分音效取自《战地》系列游戏）。此类素材的版权归各自权利人所有，**不随本项目代码一并按 GPL-3.0-or-later 授权**；再分发或商业使用前请自行确认其授权条件，必要时替换为可自由再分发的素材。

“GD656Conquest”为本项目名称；文中出现的其他模组名、游戏名与商标归各自作者或权利人所有，提及仅为说明兼容关系，不表示任何隶属或背书关系。

## 反馈

问题与建议请提交至本仓库的 issue 跟踪器；报告缺陷时请附上 Minecraft、Forge 与 GD656Conquest 版本，以及 `logs/latest.log` 中的相关片段。如前述，本仓库只受理严重问题，其余问题与功能需求请反馈到继任模组 Battlefield656Core。

---

## English Summary

GD656Conquest is a multiplayer tactical-shooter gameplay framework for Minecraft 1.20.1 (Forge 47.x, Java 17). It implements server-side room and match lifecycle management, capture-point and staged objectives, five game modes (Conquest, Breakthrough, Rush, Team Deathmatch, Gunmaster), loadouts, elite kits, artillery support, squads and parties, plus a client-side HUD, custom GUI framework, audio direction and a synchronized network protocol (`gd656conquest:main`, ~60 packets).

Most of the source code, resource definitions and documentation were written by, or with the assistance of, large language models; the maintainer specified requirements, reviewed and integrated the generated output, and performed compilation and in-game testing, without a line-by-line manual audit.

Licensed under the GNU General Public License v3.0 or later — see [LICENSE](LICENSE). Copyright (C) 2026 MinecraftGD656. Audio and image assets may originate from third parties and are not covered by this license. Provided “as is”, without warranty of any kind.

**Maintenance status:** this repository is in low-maintenance mode and will only receive fixes for severe issues (crashes, save-data corruption, security vulnerabilities, connectivity failures). All functionality and the version line continue in the successor mod **Battlefield656Core** (`battlefield656core`) starting from 0.11.0; please report non-critical issues and feature requests there.
