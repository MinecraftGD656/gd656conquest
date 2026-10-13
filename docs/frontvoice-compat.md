# BF1 阵营语音联动

从征服版本 `0.1.11` 起，接入目标为 `gd656frontvoice-0.0.11-meow1-1.20.1-forge.jar`。
沿用 `FrontlineVoiceBridge` 的可选反射接口，不将 FrontVoice 打包进征服模组。

## 房间语音设置

房间配置中的 `campAVoicePack` 和 `campBVoicePack` 分别控制双方语音包。
房间编辑器的语音包选项由 `RoomVoicePack.suggestions()` 提供。

| ID | 语音包 |
|---|---|
| `ENG` | 英国 |
| `FRA` | 法国 |
| `GER` | 德国 |
| `ITA` | 意大利 |
| `RUS` | 俄国 |
| `TKR` | 奥斯曼 |
| `USA` | 美国 |

例如，房间 JSON 的 `commonConfig` 中设置：

```json
"campAVoicePack": "ENG",
"campBVoicePack": "GER"
```

输入不区分大小写，忽略前后空格。未设置语音包时沿用美国语音默认值；
非空的未知 ID 会在房间校验中报错，并标记相应配置项。

## 旧配置兼容

读取房间配置时，旧 ID 按目标 JAR 的实际别名转换：

- `US` → `USA`
- `CN` → `ENG`
- `RU` → `RUS`

转换后的标准 ID 保存在内存配置中，房间下次保存时写入 JSON；不会主动批量改写现有房间文件。
目标 JAR 不含中文语音包，`CN` 在此版本中代表英国英语。
空值仍允许保留；未知值不会在读取时被替换成默认值，以便房间校验提示问题。

## 联动接口

- 征服通过既有 `playCue`、`playCueAt`、`playThirdPersonSound` 和 `playConfirmedDeath` 接口调用 FrontVoice。
- FrontVoice 通过 `FrontlineVoiceGdcqCompatApi.resolveVoicePack()` 获取标准国家语音 ID。
- 友军判断要求双方处于同一服务器、同一房间、同一阵营。
- 倒地求救事件 ID 使用 `gd656frontvoice:tp.<国家小写ID>.downed_help`。
- 倒地循环音效与救援语音继续使用现有事件和指令。

当前修复版通过现有构建脚本生成，网络协议为 `bf1-0.1.11-fix`，客户端与服务端需同时更新征服模组。
要使用语音联动，客户端和服务端还需独立安装上述 FrontVoice JAR。
FrontVoice 未添加为开发运行依赖；尚未进行游戏内验证。
