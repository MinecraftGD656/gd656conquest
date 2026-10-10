"""Rebuild read-only migration evidence from the adjacent, pinned workspace.

Run from any directory: python tools/migration_inventory.py
Only docs/migration evidence files are written; MIGRATION.md stays hand maintained.
"""
from collections import Counter
from pathlib import Path
import hashlib
import json
import re
import subprocess
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
WORKSPACE = PROJECT.parent
SOURCE = WORKSPACE / "gd656conquest"
OUT = PROJECT / "docs/migration"
BASE = "acb7636877d2d515c2a616d6703295120bec8e33"
FINAL = "955b76d8adc264703eb49141bdb99b06e84cba46"
TARGET_BASE = "4cf4ef4d09f64e5505ad0004ea1af99566ef5808"
PREFIX = "org/mods/gd656conquest/"

# Explicitly reviewed ASM destinations. prepare(), patch(), resource assembly,
# and configuration-copy rewriting must all be considered, not just hit() keys.
ASM_TARGETS = [
    ("P1/P2", "Gd656conquest", "<init>", "注册 DownedCameraConfig；迁入源码构造器的合法位置，不能照搬字节码前插位置"),
    ("P1", "server/room/core/RoomCoreService", "tryHandlePlayerDownedDamage; handlePlayerFatalState; handleDownedAction", "新增倒地入口和显式 UUID 救援重载；迁移致命状态判定；消费出界死亡快照"),
    ("P1", "server/room/entry/RoomCoreRuntimeManager", "tryHandlePlayerDownedDamage; handleDownedAction", "将 prepare 阶段新增的入口/重载写成真实 Java 方法；保留 coreService 空值分支"),
    ("P1/P2/P3", "server/room/runtime/RoomPlayerRespawnService", "startReviveAttempt; onPlayerDeath; openManualDeathDeploy; downedDeployDelay; handleDownedAction; tryEnterDownedState; activateDownedState; finishDownedState; skipDownedStateForRedeploy; completeRevive; forceRedeployDeath; resolveActiveReviveContext; buildNearbyRescuerEntries; resolveNearestDownedRescueHint; sendDownedStart; sendDownedUpdate; showBeingRevived; showDownedCountdown; showKillerActionbar; tick", "四个移植方法、显式救援目标、布娃娃/镜头生命周期、救援位置、普通死亡手动部署、快照、旧提示关闭；onPlayerDeath 最后被 transplant 覆盖，核对最终方法体"),
    ("P1/P2", "server/room/runtime/RoomPlayerStateService", "normalizePlayerModelState; applyDownedState", "清理镜头和布娃娃；维持倒地代理物理，核对两个 return 分支"),
    ("P1/P2/P3", "server/room/entry/RoomServerModule", "onLivingDeath; onServerTick; onServerStopped; onPlayerLogout; onPlayerChangedDimension", "完整死亡事件先派发，再接管房间流程；传递 DamageSource；统一击杀归属；tick 与退出/停服清理"),
    ("P0-P6", "network/NetworkManager", "PROTOCOL_VERSION; init", "成品协议 114；保留原数据包顺序并注册十个扩展包；packetId 递增十次；中间版本兼容性单独验证"),
    ("P1/P2", "client/animation/ClientPlayerAnimatorDebugState", "stopPreview; applyPreviewState", "停止强制姿态；布娃娃就绪时不再套用旧预览身体姿态"),
    ("P1/P3", "client/room/ClientDownedState", "killerCardRemainingTicks; requestReadyDeployment; tick; render; startOrUpdate; startDebugPreview; clear", "prepare 新增计时/单次部署接口；面板渲染、倒地快照、调试预览和长按门槛；clear 通知强制姿态清理"),
    ("P1", "client/room/ClientReviveState", "tick; resolveCandidateTarget", "救援请求携带明确 UUID；目标资格和距离改为身体状态/位置；核对源码差异与 ClientHooks 最终处理"),
    ("P2/P3/P4", "client/room/ClientDeathFlowState", "currentDarknessAlpha; currentHurtFlashAlpha; applyDesaturation; setBoundaryPenaltyActive; clear; deathFilterKillerId; openDeploySequence; openDeployInstant; openRespawnWaitInstant; startRespawnTransition; startMatchEndTransition; adoptCameraPose; applyCameraOverride; hasActiveCameraOverride; shouldHideFirstPersonHands; enterDownedView", "统一世界滤镜/死亡镜头所有权；保留部署交接顺序、原因横幅隐藏、边界激活与姿态清理；新增真实相机接口"),
    ("P2", "client/room/ClientRoomLockState", "shouldBlockLookInput", "镜头拥有者优先阻断视角输入"),
    ("P5", "common/item/AmmoBagItem", "use (m_7203_); interactLivingEntity (m_6880_)", "原类没有 Git 差异，成品两个入口改为 SupportRuntime，必须纳入"),
    ("P5", "common/item/MedicalBagItem", "use (m_7203_); interactLivingEntity (m_6880_); applyHealEffect", "接入 SupportRuntime；关闭旧生命恢复效果"),
    ("P5", "server/room/runtime/RoomPlayerLoadoutService", "placeLoadoutItem; applyLoadout", "初始包库存和部署结束实际弹药上限核对"),
    ("P5", "server/room/runtime/RoomLoadoutGrantService", "grantConfiguredWeaponAmmoByExtraData", "替换为两轮发弹；核对私有成员访问并改成合理源码接口"),
    ("P5", "server/room/runtime/RoomDeathSupportDropService", "死亡补给实体生成处（原 addFreshEntity 调用）", "原类没有 Git 差异；生成时标记死亡奖励包/寿命"),
    ("P5", "server/player/state/PlayerItemInteractionService", "handlePlayerItemToss; handleEntityItemPickup; handleMedicalBagPickup", "新投放/拾取处理成功则停止旧路径；关闭旧医疗包效果"),
    ("P6", "server/player/regen/PassiveRegenService", "onPlayerTick; resetPlayer", "按房间选配置、压制暂停、等待/间隔/回复量、玩家专属声音和清理"),
    ("P6", "server/core/command/ConquestServerCommands", "SERVER_CONFIG_IDS; normalizeServerConfigIdSafe; readConfigValue; suggestServerConfigValues; executeConfigGetSingle; executeConfigSet; executeConfigGetAll", "原类没有 Git 差异；接入 NaturalRegenCommands 的新项、get/set/补全/全量显示"),
    ("P5", "server/room/runtime/RoomItemReplenishService", "replenishConfiguredItem", "原类没有 Git 差异；替换包库存恢复入口"),
    ("P5", "server/room/runtime/RoomSupportBoxService", "processMedicalBoxAura; processAmmoBoxAura; tryUseNearbyAmmoBox; tryTriggerMedicalBoxForPlayer; setOwnerUuid 调用点; tryUse*; spawn*", "新光环、权限/来源元数据、关闭旧交互；spawn 中容量常量改为医疗 100 / 弹药 20，该修改没有独立 hit 标记"),
    ("P4", "server/loadout/domain/PlayerClassType", "枚举显示名初始化", "只改两个中文显示名；内部键值、输入别名及兵种逻辑保留"),
    ("P4/P5/P6", "server/room/config/RoomDefinitionScalarConfigEditor", "parseNumberValue", "接入 BoundaryConfigNumbers 范围解析，原解析分支仍保留"),
    ("P4", "server/room/runtime/RoomRuleRuntimeService", "processBoundaryRule; clearBoundaryPenalty", "统一玩家出界判定与清理；clear 方法没有独立 hit 标记"),
    ("P4", "server/room/vehicle/VehicleBoundaryPenaltyService", "processAircraftBoundaryTimeouts; updateAircraftBoundaryState; handleBoundaryTimeout", "关闭旧飞行载具计时；玩家当前载具走原生摧毁接口"),
    ("P4/P5/P6", "server/data/roomdefinition/RoomDefinitionRepository", "RoomCommonConfig 构造复制调用", "全局 preserveBoundaryConfig 扫描；核对从原配置拷贝的六个扩展字段"),
    ("P4/P5/P6", "server/room/config/RoomDefinitionGroupConfigEditor", "RoomCommonConfig 构造复制调用", "全局 preserveBoundaryConfig 扫描；核对列表/分组编辑后字段保留"),
    ("P4/P5/P6", "server/room/entry/RoomAdminConfigTemplateService", "RoomCommonConfig 构造复制调用", "全局 preserveBoundaryConfig 扫描；模板复制后字段保留"),
    ("P4/P5/P6", "server/room/entry/RoomCommands", "RoomCommonConfig 构造复制调用", "全局 preserveBoundaryConfig 扫描；武器配置等旧构造复制后字段保留"),
]


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args], encoding="utf-8").strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def feature(path):
    n = Path(path).stem
    lower = path.lower()
    if path.startswith("assets/"):
        if "suppression" in lower:
            return "P6"
        if "boundary" in lower or "inform_leaving_area" in lower:
            return "P4"
        if "killer_card/" in lower:
            return "P3"
        if "shaders/" in lower:
            return "P2"
    if n == "KillCommandMixin":
        return "P3"
    if n in {"NetworkManager", "RoomDeathFlowPacket", "RoomDownedActionPacket", "RoomCommonConfig", "RoomConfigDescriptorRegistry", "RoomConfigValidator"}:
        return "SHARED"
    if "Suppression" in n or "SuppressedFood" in n or n in {"SupportRules", "TaczSuppression", "KineticBulletSupportAccessor", "SupportShotMixin", "SupportBulletMixin"}:
        return "P6"
    if "Regen" in n or "serverconfig/" in path or n in {"NaturalRegenCommands", "ConquestServerCommands"}:
        return "P6"
    if "Support" in n or "Ammo" in n or n in {"MedicalBagItem", "PlayerItemInteractionService", "RoomLoadoutGrantService", "RoomPlayerLoadoutService", "RoomItemReplenishService"}:
        return "P5"
    if "Boundary" in n or n in {"FlightBoundaryGeometry", "RoomRuleRuntimeService", "PlayerClassType", "RoomDefinitionRepository", "RoomDefinitionGroupConfigEditor", "RoomDefinitionScalarConfigEditor", "RoomAdminConfigTemplateService", "RoomCommands"} or "inform_leaving_area" in path:
        return "P4"
    if any(x in n for x in ["Killer", "DeathCause", "DeathCauseClassifier", "FatalHit", "MatchDuel", "KillWeapon", "Killicon", "RoomChat", "DownedDeploy", "ManualDeathDeploy"]) or "killer_card/" in path:
        return "P3"
    if n == "ClientReviveState":
        return "P1"
    if any(x in n for x in ["Camera", "DownedView", "Revive", "Prone", "DeathFilter", "DeathWorld", "DeathPost", "DeathFlowState", "RoomLock"]) or "shaders/" in path:
        return "P2"
    if any(x in path for x in ["ragdoll", "Ragdoll"]) or n in {"RoomCoreService", "RoomCoreRuntimeManager", "RoomPlayerRespawnService", "RoomPlayerStateService", "RoomServerModule", "ServerPlayerDeathMixin", "ClientDownedState", "ClientPlayerAnimatorDebugState"}:
        return "P1"
    return "SHARED"


def main():
    if git(SOURCE, "rev-parse", "HEAD") != FINAL:
        raise SystemExit("Source HEAD differs from the frozen final commit; review constants before regenerating.")
    if git(SOURCE, "status", "--porcelain"):
        raise SystemExit("Source worktree is dirty; commit or separately inventory it before regenerating.")
    changes = {}
    for row in git(SOURCE, "diff", "--name-status", BASE, FINAL).splitlines():
        status, path = row.split("\t")
        if status not in {"A", "M", "D"}:
            raise SystemExit("Unexpected diff status: " + row)
        changes[path] = status
    build_path = WORKSPACE / "build-project/build-mod.ps1"
    build = build_path.read_text(encoding="utf-8-sig")
    compiled = {PREFIX + p for p in re.findall(r"^ '([^']+\.java)'", build, re.M)}
    asm_path = WORKSPACE / "build-project/tools/BuildRagdollJar.java"
    asm = asm_path.read_text(encoding="utf-8-sig")
    audit_path = WORKSPACE / "dist/gd656conquest-0.9.3-ragdoll.24.jar.audit.txt"
    audit = audit_path.read_text(encoding="utf-8-sig")
    class_entries = [x for x in audit.splitlines() if x.endswith(".class")]
    audited_sources = {x.split("$")[0].removesuffix(".class") + ".java" for x in class_entries}
    targets = {PREFIX + t[1] + ".java": t for t in ASM_TARGETS}
    if audited_sources != compiled | set(targets):
        raise SystemExit("Reviewed ASM targets plus compiled sources do not cover the artifact audit exactly.")
    paths = set(changes) | compiled | audited_sources | set(targets)
    original_path = WORKSPACE / "gd656conquest-0.9.3-normal.jar"
    artifact_path = WORKSPACE / "dist/gd656conquest-0.9.3-ragdoll.24.jar"
    files = []
    with zipfile.ZipFile(original_path) as original, zipfile.ZipFile(artifact_path) as artifact:
        for path in sorted(paths):
            source = SOURCE / path
            if not source.is_file():
                raise SystemExit("Missing source: " + path)
            is_java = path.endswith(".java")
            target = ("src/main/java/" if is_java else "docs/legacy/" if path.endswith(".md") else "src/main/resources/") + path
            artifact_entry = None
            if not is_java:
                present = path in artifact.namelist()
                artifact_entry = {"present": present, "matches_source_bytes": present and artifact.read(path) == source.read_bytes(),
                                  "differs_from_original": present and (path not in original.namelist() or artifact.read(path) != original.read(path))}
            files.append({"source": "gd656conquest/" + path, "target": target,
                          "git_change": changes.get(path, "UNCHANGED"), "feature": feature(path),
                          "selectively_compiled": path in compiled, "asm_target": path in targets,
                          "in_artifact_class_audit": path in audited_sources,
                          "target_exists_at_inventory": (PROJECT / target).exists(),
                          "source_sha256": sha(source), "artifact_resource": artifact_entry})
        observed_class_changes = {p for p in artifact.namelist() if p.endswith(".class") and (p not in original.namelist() or artifact.read(p) != original.read(p))}
        missing_audit = observed_class_changes - set(class_entries)
        if missing_audit:
            raise SystemExit("Actual class changes missing from audit: " + str(sorted(missing_audit)))
        if set(class_entries) != observed_class_changes:
            raise SystemExit("Artifact audit and actual changed class entries do not match exactly.")
        if set(class_entries) - set(artifact.namelist()):
            raise SystemExit("Audit refers to missing artifact class entries.")
    audit_counts = dict((key, int(count)) for key, count in re.findall(r"([^,{}]+)=(\d+)", audit.splitlines()[-1]))
    audit_counts = {key.strip(): value for key, value in audit_counts.items()}
    markers = [{"line": asm[:m.start()].count("\n") + 1, "expression": m.group(1)} for m in re.finditer(r"\bhit\(([^;]+)\);", asm)]
    support_paths = set((WORKSPACE / "verification/tests").glob("*.java"))
    support_paths.update((WORKSPACE / "verification").glob("verify-*.ps1"))
    support_paths.update((WORKSPACE / "build-project/tools").glob("*.java"))
    support_paths.update((WORKSPACE / "build-project").glob("*.ps1"))
    support_paths.update(WORKSPACE / "build-project" / name for name in ["build.gradle", "settings.gradle", "gradle.properties", "dependency-manifest.json", "BUILD.md"])
    support = [{"path": p.relative_to(WORKSPACE).as_posix(), "sha256": sha(p)} for p in sorted(support_paths) if p.is_file()]
    stamps = [{"path": p.relative_to(WORKSPACE).as_posix(), "sha256": sha(p)} for p in [original_path, artifact_path, audit_path, WORKSPACE / "ragdollified-1.20.1-1.1.1-hotfix-RELEASE.jar", PROJECT / "build.gradle", PROJECT / "gradle.properties"]]
    stats = {"git_changed_files": len(changes), "git_status_counts": dict(Counter(changes.values())),
             "changed_java": sum(p.endswith(".java") for p in changes), "compiled_business_sources": len(compiled),
             "audited_classes": len(class_entries), "audited_source_groups": len(audited_sources),
             "inventory_files": len(files), "asm_destination_classes": len(targets),
             "audit_only_sources": sorted(audited_sources - set(changes)),
             "audit_keys": len(audit_counts), "static_hit_occurrences": len(markers),
             "actual_changed_class_entries": len(observed_class_changes),
             "standalone_verification_java": len(list((WORKSPACE / "verification/tests").glob("*.java")))}
    evidence = {"snapshot_date": "2026-10-11", "source_baseline": BASE, "source_final": FINAL,
                "target_baseline": TARGET_BASE, "observed_target_head": git(PROJECT, "rev-parse", "HEAD"),
                "observed_target_branch": git(PROJECT, "branch", "--show-current"),
                "stats": stats, "hashes": stamps, "files": files, "support_files": support,
                "asm_destinations": [dict(zip(["phase", "class", "methods", "migration"], t)) for t in ASM_TARGETS],
                "static_hit_markers": markers, "artifact_audit_counts": audit_counts}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "inventory.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    def link(source):
        return "../../../" + source
    md = ["# 文件迁移明细（自动生成证据）", "", "入口与完成状态见 ../../MIGRATION.md。这里的 P 编号表示主要归属，不是完整依赖；SHARED 必须分批处理。", "", "快照来源：反编译原版 → 最后提交的 Git 差异，加选择性编译列表、ASM 目标及 .24 成品类审计的并集。", "", f"Git 差异 {len(changes)} 项，完整文件清单 {len(files)} 项。目标存在只表示已有同路径文件，不能据此认定功能已迁移。", "", "A=新增；M=修改；UNCHANGED=Git 无差异但成品/接入需要迁移。编译/ASM/审计分别记录三种独立证据。", ""]
    for group in ["P1", "P2", "P3", "P4", "P5", "P6", "SHARED"]:
        md += ["## " + group, "", "| 来源 | 目标 | Git | 编译/ASM/审计 | 目标已有 |", "| --- | --- | --- | --- | --- |"]
        for f in files:
            if f["feature"] != group:
                continue
            modes = "/".join("是" if f[k] else "—" for k in ["selectively_compiled", "asm_target", "in_artifact_class_audit"])
            md.append(f"| [{f['source']}]({link(f['source'])}) | `{f['target']}` | {f['git_change']} | {modes} | {'是' if f['target_exists_at_inventory'] else '否'} |")
        md.append("")
    md += ["## 成品资源核对", "", "字节不同需要复核构建来源；不能只把 Git 改动当作已发行行为。Java 源码与 class 不进行字节等价比较。", "", "| 资源 | 成品包含 | 成品等于当前源码文件 | 相对原版成品改变 |", "| --- | --- | --- | --- |"]
    for f in files:
        a = f["artifact_resource"]
        if a:
            md.append("| `" + f["source"] + "` | " + " | ".join("是" if a[k] else "否" for k in ["present", "matches_source_bytes", "differs_from_original"]) + " |")
    (OUT / "files.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    md = ["# ASM 与编译前临时接口迁移清单", "", "按源码人工审阅归纳；本表覆盖 prepare、方法移植、patch、公共配置复制。audit 标记是补充证据，不能代替方法体审阅。", "", "每个目标的完整源码路径为 org/mods/gd656conquest/<类>.java，迁入 src/main/java/。", "", "| 批次 | 原类 | 方法/位置 | 迁移要求 |", "| --- | --- | --- | --- |"]
    for phase, cls, methods, migration in ASM_TARGETS:
        md.append(f"| {phase} | [{cls}]({link('gd656conquest/' + PREFIX + cls + '.java')}) | `{methods}` | {migration} |")
    md += ["", "## 编译前接口和方法移植", "", "- RoomCoreService、RoomCoreRuntimeManager、RoomPlayerRespawnService 的显式 UUID 动作重载：改为真实源码方法，沿调用链传参；评估是否仍需要 DownedReviveRequest 线程局部上下文。", "- tryHandlePlayerDownedDamage、openManualDeathDeploy 的编译占位方法不能留空；downedDeployDelay 应调用原延迟计算。", "- ClientDownedState 的 killerCardRemainingTicks / requestReadyDeployment 与 ClientDeathFlowState 的 deathFilterKillerId / adoptCameraPose 都要成为真实接口。", "- extract-patch-methods.ps1 实际抽取四个方法：tryHandlePlayerDownedDamage、startReviveAttempt、onPlayerDeath、openManualDeathDeploy。末尾日志只提两个方法，不能据此遗漏后两者。", "- Patch 声明壳、prepared API JAR、ASM 槽位/跳转、Forge 编译访问辅助不迁入运行模组；业务行为迁入源码后由 ForgeGradle 编译/重混淆。", "- preserveBoundaryConfig 扫描全体原类，成品审计四处；保留 flightVehicleOutsideBoundaryDeathSeconds、flightBoundaryPaddingBlocks、allowEnemySupportResupply、suppressionConfig、medicalBoxDamageCooldownSeconds、naturalRegenConfig。核对构造复制的最终源码。", "- 仍针对 Minecraft/TACZ/ragdollified/载具的 Mixin 单独保留并转换映射，迁移 GD 自身 ASM 接入不等于删除所有 Mixin。", "", "## 静态 hit() 源码位置", "", "自动抽取全部调用表达式；含工具通用动态键。循环内一次静态调用可能对应多个成品接入。", "", "| BuildRagdollJar.java 行 | 表达式 |", "| --- | --- |"]
    for marker in markers:
        md.append(f"| {marker['line']} | `{marker['expression'].replace('|', '&#124;')}` |")
    md += ["", "## 固定 .24 成品审计键", "", "这些次数用于寻找旧实现，源码迁移后不要求保持相同字节码形状。", "", "| 审计键 | 成品计数 |", "| --- | --- |"]
    md += [f"| `{key}` | {count} |" for key, count in sorted(audit_counts.items())]
    (OUT / "asm-hooks.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    md = ["# 构建与验证输入快照", "", "这些文件主要位于两个 Git 项目之外。本次记录内容哈希；后续迁移需要将手工维护的验证代码收纳到目标仓库。", "", "记录文件存在，不代表本次已经执行测试或确认新工程通过。具体迁移方式见 ../../MIGRATION.md。", "", "| 工作区路径 | SHA-256 |", "| --- | --- |"]
    md += [f"| [{s['path']}]({link(s['path'])}) | `{s['sha256']}` |" for s in support]
    md += ["", "## 二进制与目标构建文件", "", "| 路径 | SHA-256 |", "| --- | --- |"]
    md += [f"| `{s['path']}` | `{s['sha256']}` |" for s in stamps]
    (OUT / "inputs.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
