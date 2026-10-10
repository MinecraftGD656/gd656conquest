"""Collect conservative source-level P1 dependencies; does not modify game code.

This is lexical evidence, not a Java compiler call graph. Runtime Mixin and
reflection requirements are reviewed separately in docs/migration/p1-dependencies.md.
"""
from collections import deque
from pathlib import Path
import hashlib
import json
import re
import subprocess

PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT.parent / "gd656conquest"
OUT = PROJECT / "docs/migration"
FINAL = "955b76d8adc264703eb49141bdb99b06e84cba46"
ROOTS = ["RoomCoreService", "RoomCoreRuntimeManager", "RoomServerModule",
         "RoomPlayerRespawnService", "RoomPlayerStateService", "ClientReviveState"]
SELECTED = {
    "RoomCoreService": ["handleDownedAction", "tryHandlePlayerDownedDamage", "handlePlayerFatalState", "handlePlayerDeathEvent"],
    "RoomCoreRuntimeManager": ["handleDownedAction", "tryHandlePlayerDownedDamage", "handlePlayerDeathEvent"],
    "RoomServerModule": ["onLivingDeath", "onServerTick", "onServerStopped", "onPlayerLogout", "onPlayerChangedDimension"],
    "RoomPlayerRespawnService": ["onPlayerDeath", "openManualDeathDeploy", "downedDeployDelay", "tryEnterDownedState", "activateDownedState", "handleDownedAction", "startReviveAttempt", "forceRedeployDeath", "completeRevive", "finishDownedState", "skipDownedStateForRedeploy", "resolveActiveReviveContext", "buildNearbyRescuerEntries", "resolveNearestDownedRescueHint", "tick", "sendDownedStart", "sendDownedUpdate"],
    "RoomPlayerStateService": ["normalizePlayerModelState", "applyDownedState"],
    "ClientReviveState": ["tick", "resolveCandidateTarget"],
    "RoomDownedActionPacket": ["reviveStart", "encode", "decode", "handle"],
    "ClientDownedState": ["killerCardRemainingTicks", "requestReadyDeployment"],
    "ClientDeathFlowState": ["deathFilterKillerId", "adoptCameraPose"],
}


def mask(text):
    pattern = r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*'
    return re.sub(pattern, lambda m: "".join("\n" if c == "\n" else " " for c in m.group()), text, flags=re.S)


def methods(text):
    clean = mask(text)
    pattern = r'\b(?:public|private|protected)\s+(?:(?:static|final|synchronized|native|abstract)\s+)*(?:[\w.$<>?,\[\]]+\s+)+?(\w+)\s*\(([^)]*)\)\s*(?:throws\s+[^{;]+)?\s*\{'
    found = []
    matches = list(re.finditer(pattern, clean))
    for index, m in enumerate(matches):
        depth, end = 1, m.end()
        while depth and end < len(clean):
            depth += (clean[end] == "{") - (clean[end] == "}")
            end += 1
        limit = matches[index + 1].start() if index + 1 < len(matches) else len(clean)
        boundary = "balanced"
        if depth or end > limit:
            # Some legacy decompiled method bodies are incomplete. Do not treat
            # their contents as a valid Java AST or transplant them wholesale.
            end = limit
            boundary = "next-declaration-fallback"
        parameters = m.group(2).strip()
        arity = len(parameters.split(",")) if parameters else 0
        found.append({"name": m.group(1), "arity": arity,
                      "line": text[:m.start()].count("\n") + 1,
                      "signature": " ".join(clean[m.start():m.end()-1].split()),
                      "start": m.start(), "end": end, "body_boundary": boundary})
    return found


def main():
    head = subprocess.check_output(["git", "-C", str(SOURCE), "rev-parse", "HEAD"], text=True).strip()
    if head != FINAL or subprocess.check_output(["git", "-C", str(SOURCE), "status", "--porcelain"], text=True).strip():
        raise SystemExit("Expected the clean, pinned final source worktree.")
    inventory = json.loads((OUT / "inventory.json").read_text(encoding="utf-8"))
    modified = {f["source"].removeprefix("gd656conquest/").removesuffix(".java").replace("/", "."): f
                for f in inventory["files"] if f["source"].endswith(".java")}
    source_paths = {p.relative_to(SOURCE).with_suffix("").as_posix().replace("/", "."): p
                    for p in (SOURCE / "org").rglob("*.java")}
    root_classes = [next(k for k in modified if k.rsplit(".", 1)[-1] == n) for n in ROOTS]
    texts = {k: p.read_text(encoding="utf-8-sig") for k, p in source_paths.items() if k in modified}
    for cls, text in texts.items():
        if hashlib.sha256(source_paths[cls].read_bytes()).hexdigest() != modified[cls]["source_sha256"]:
            raise SystemExit("Source differs from inventory: " + cls)
    edges = {}
    for cls, text in texts.items():
        clean = mask(text)
        package = re.search(r'\bpackage\s+([\w.]+)', clean).group(1)
        imports = re.findall(r'\bimport\s+(?:static\s+)?([\w.*]+)', clean)
        refs = {imp for imp in imports if imp in modified}
        body = re.sub(r'\b(?:package|import)\s+[^;]+;', lambda m: " " * len(m.group()), clean)
        wildcards = {imp[:-2] for imp in imports if imp.endswith(".*")}
        for candidate in modified:
            pkg, simple = candidate.rsplit(".", 1)
            if ((pkg == package or pkg in wildcards) and re.search(r'\b' + re.escape(simple) + r'\b', body)) or re.search(r'\b' + re.escape(candidate) + r'\b', body):
                refs.add(candidate)
        edges[cls] = sorted(refs - {cls})
    parents = {k: None for k in root_classes}
    todo = deque(root_classes)
    while todo:
        cls = todo.popleft()
        for dep in edges[cls]:
            if dep not in parents:
                parents[dep] = cls
                todo.append(dep)
    closure = sorted(parents)
    records = []
    for name, wanted in SELECTED.items():
        cls = next(k for k in modified if k.rsplit(".", 1)[-1] == name)
        source = source_paths[cls]
        target = PROJECT / "src/main/java" / source.relative_to(SOURCE)
        old = methods(target.read_text(encoding="utf-8-sig")) if target.is_file() else []
        selected = [m for m in methods(texts[cls]) if m["name"] in wanted]
        if set(wanted) - {m["name"] for m in selected}:
            raise SystemExit("Missing selected methods in " + name + ": " + str(set(wanted) - {m["name"] for m in selected}))
        for m in selected:
            state = "同名同参数数量入口已有；须合并方法体" if any(x["name"] == m["name"] and x["arity"] == m["arity"] for x in old) else "该参数数量入口缺失；新增方法/重载"
            body = mask(texts[cls][m["start"]:m["end"]])
            direct = [k for k in edges[cls] if re.search(r'\b' + re.escape(k.rsplit(".", 1)[-1]) + r'\b', body)]
            records.append({"class": cls, "source": source.relative_to(PROJECT.parent).as_posix(),
                            "target": target.relative_to(PROJECT).as_posix(), "method": m["name"],
                            "arity": m["arity"], "line": m["line"], "signature": m["signature"],
                            "target_entry": state, "direct_class_tokens": direct,
                            "body_boundary": m["body_boundary"]})
    closure_records = []
    for cls in closure:
        chain = [cls]
        while parents[chain[-1]] is not None:
            chain.append(parents[chain[-1]])
        closure_records.append({"class": cls, "feature": modified[cls]["feature"],
                                "source": modified[cls]["source"], "target": modified[cls]["target"],
                                "target_exists": (PROJECT / modified[cls]["target"]).is_file(),
                                "shortest_candidate_chain": list(reversed(chain))})
    snapshot = {"source_commit": FINAL,
                "target_observed_commit": subprocess.check_output(["git", "-C", str(PROJECT), "rev-parse", "HEAD"], text=True).strip(),
                "methodology": "保守词法扫描 imports/全限定名/同包及通配导入类名；只沿 151 个盘点过的迁移 Java 类追踪；非编译器调用图，不解析反射字符串、Mixin 配置和全部字段接收者类型。",
                "roots": root_classes, "candidate_closure_count": len(closure), "edges": edges,
                "selected_methods": records, "closure": closure_records}
    (OUT / "p1-dependency-evidence.json").write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    md = ["# P1 静态依赖证据", "", "人工结论和实际实施范围见 [第一批依赖分析](p1-dependencies.md)。本文件由 tools/migration_p1_dependencies.py 生成。", "", snapshot["methodology"], "", f"六个起点得到 {len(closure)} 个整类依赖候选。这不是必须一次迁移的文件数量，也不是经过编译证明的最小依赖集合。", "", "## 六个起点的直接候选", "", "| 起点 | 迁移类依赖候选 |", "| --- | --- |"]
    md += ["| `" + cls.rsplit(".", 1)[-1] + "` | " + ", ".join(k.rsplit(".", 1)[-1] for k in edges[cls]) + " |" for cls in root_classes]
    md += ["", "## 逐方法入口核对", "", "参数数量用于定位重载，不代表完整 Java 类型检查。direct_class_tokens 只记录方法体中出现的类名；字段接收者调用由人工表补充。反编译方法体不完整时使用下一声明定位边界，并明确标记。", "", "| 来源及行 | 签名 | 原始项目入口 | 方法体出现的迁移类 | 方法边界 |", "| --- | --- | --- | --- | --- |"]
    for r in records:
        md.append(f"| [{r['class'].rsplit('.',1)[-1]}:{r['line']}](../../../{r['source']}) | `{r['signature']}` | {r['target_entry']} | {', '.join(k.rsplit('.',1)[-1] for k in r['direct_class_tokens'])} | {r['body_boundary']} |")
    md += ["", "## 整类候选与一条最短来源链", "", "本表用于识别整类复制带来的范围扩张，不能据此认定同批必须迁入。", "", "| 来源 | 主要批次 | 目标已有 | 候选依赖链 |", "| --- | --- | --- | --- |"]
    for r in closure_records:
        md.append(f"| [{r['class'].rsplit('.',1)[-1]}](../../../{r['source']}) | {r['feature']} | {'是' if r['target_exists'] else '否'} | {' → '.join(k.rsplit('.',1)[-1] for k in r['shortest_candidate_chain'])} |")
    (OUT / "p1-dependency-evidence.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    print(f"P1 evidence: {len(root_classes)} roots, {len(records)} selected method entries, {len(closure)} conservative whole-class candidates.")


if __name__ == "__main__":
    main()
