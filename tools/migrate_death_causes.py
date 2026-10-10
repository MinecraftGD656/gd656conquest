"""One-shot, pinned import of the death-cause stage; never overwrites target Java files."""
from pathlib import Path
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'gd656conquest'
PIN = '955b76d8adc264703eb49141bdb99b06e84cba46'
assert subprocess.check_output(['git', '-C', str(SOURCE), 'rev-parse', 'HEAD'], text=True).strip() == PIN
assert not subprocess.check_output(['git', '-C', str(SOURCE), 'status', '--porcelain'], text=True).strip()
BASE = 'org/mods/gd656conquest/'
FILES = [
    'common/player/DeathCause.java', 'server/room/entry/DeathCauseClassifier.java',
    'server/room/entry/RoomDeathCauseService.java', 'server/room/entry/KilliconFireOwnership.java',
    'client/room/DeathCauseBanner.java', 'client/room/ClientDeathCauseState.java',
    'network/packet/DeathCausePacket.java', 'mixin/server/KillCommandMixin.java',
]
mapping = {}
for line in (ROOT / 'build/createMcpToSrg/output.tsrg').read_text().splitlines():
    if not line.startswith('\t') or line.startswith('\t\t'):
        continue
    parts = line.split()
    if len(parts) in (2, 3) and re.fullmatch(r'[mf]_\d+_', parts[-1]):
        mapping.setdefault(parts[-1], set()).add(parts[0])

def official(text):
    def token(match):
        value = match.group()
        if re.fullmatch(r'[mf]_\d+_', value):
            names = mapping.get(value, set())
            assert len(names) == 1, (value, names)
            return next(iter(names))
        return value
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|\b[mf]_\d+_\b', token, text)

for relative in FILES:
    target = ROOT / 'src/main/java' / BASE / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    text = official((SOURCE / BASE / relative).read_text(encoding='utf-8'))
    if relative.endswith('/RoomDeathCauseService.java'):
        # Card capture and vehicle destruction belong to their own migration stages.
        text = re.sub(r'^.*(?:import .*BoundaryVehicleDestruction|BoundaryVehicleDestruction\.(?:clear|tick)|KillerIntelService\.(?:capture|publish|forget|clear|tick)).*\n', '', text, flags=re.M)
        text = text.replace('import org.mods.gd656conquest.server.room.runtime.UnifiedBoundaryService;',
                            'import org.mods.gd656conquest.server.room.runtime.RoomDeathBoundaryQuery;')
        text = text.replace('UnifiedBoundaryService.isOutside', 'RoomDeathBoundaryQuery.isOutside')
        text = text.replace('import java.util.Set;', 'import java.util.Set;\nimport org.mods.gd656conquest.server.room.runtime.ScopedDeathAttribution;')
        text = text.replace('    private static final ThreadLocal<Entity> COMMAND_TARGET = new ThreadLocal<>();\n    private static final ThreadLocal<FireOwner> FIRE_OWNER = new ThreadLocal<>();',
                            '    private static final ScopedDeathAttribution<Entity, FireOwner> ATTRIBUTION = new ScopedDeathAttribution<>();')
        old = '''        Entity previous = COMMAND_TARGET.get();
        COMMAND_TARGET.set(target);
        try { target.kill(); }
        finally {
            if (previous == null) COMMAND_TARGET.remove(); else COMMAND_TARGET.set(previous);
        }'''
        assert old in text
        text = text.replace(old, '        ATTRIBUTION.commandKill(target, target::kill);')
        text = text.replace('COMMAND_TARGET.get()', 'ATTRIBUTION.commandTarget()').replace('FIRE_OWNER.get()', 'ATTRIBUTION.fireSnapshot()')
        old = '''        FireOwner previous = ATTRIBUTION.fireSnapshot();
        // Freeze geometry before listeners can dismount, teleport or heal the player proxy.
        FIRE_OWNER.set(new FireOwner(player, KilliconFireOwnership.resolve(player, source), RoomDeathBoundaryQuery.isOutside(player)));
        try { return transition.getAsBoolean(); }
        finally { if (previous == null) FIRE_OWNER.remove(); else FIRE_OWNER.set(previous); }'''
        assert old in text
        text = text.replace(old, '''        // Freeze geometry before listeners can dismount, teleport or heal the player proxy.
        FireOwner snapshot = new FireOwner(player, KilliconFireOwnership.resolve(player, source), RoomDeathBoundaryQuery.isOutside(player));
        return ATTRIBUTION.withFireSnapshot(snapshot, transition);''')
    elif relative.endswith('/ClientDeathCauseState.java'):
        text = text.replace('lastFrame = System.nanoTime(); ClientKillerCardState.begin(packet); ClientManualDeathDeployState.begin(packet);',
                            'lastFrame = System.nanoTime();')
        text = re.sub(r'^.*if \(!ClientDownedState.isActive\(\).*ClientKillerCardState.hide\(\);\n', '', text, flags=re.M)
        text = text.replace(' ClientKillerCardState.reset();', '')
        text = text.replace('ClientDeathFilter.deathActive()', 'ClientDeathFlowState.isDeathObservationPhase() || ClientDownedState.isActive()')
    elif relative.endswith('/KillCommandMixin.java'):
        text = text.replace('method = "m_137813_(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;)I", remap = false,',
                            'method = "kill(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;)I",')
        text = text.replace('value = "INVOKE", remap = false, target = "Lnet/minecraft/world/entity/Entity;m_6074_()V"',
                            'value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;kill()V"')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
    print(relative)

# Only the death-cause keys are merged; other later-stage resource additions are not imported.
for locale in ('en_us', 'zh_cn'):
    target = ROOT / f'src/main/resources/assets/gd656conquest/lang/{locale}.json'
    source = SOURCE / f'assets/gd656conquest/lang/{locale}.json'
    data = json.loads(target.read_text(encoding='utf-8'))
    added = {key: value for key, value in json.loads(source.read_text(encoding='utf-8')).items()
             if key.startswith('gd656conquest.death_cause.')}
    assert added, locale
    for key, value in added.items():
        assert key not in data or data[key] == value, (locale, key)
    data.update(added)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{locale}: {len(added)} death cause translations')
