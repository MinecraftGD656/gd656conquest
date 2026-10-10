"""One-shot import of the camera/prone stage from the pinned source; no Java overwrites."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'gd656conquest'
PIN = '955b76d8adc264703eb49141bdb99b06e84cba46'
assert subprocess.check_output(['git', '-C', str(SOURCE), 'rev-parse', 'HEAD'], text=True).strip() == PIN
assert not subprocess.check_output(['git', '-C', str(SOURCE), 'status', '--porcelain'], text=True).strip()
BASE = 'org/mods/gd656conquest/'
FILES = [
    'common/player/DownedCameraMode.java', 'common/player/RevivedPronePose.java',
    'server/compat/ragdoll/DownedCameraConfig.java', 'server/compat/ragdoll/DownedViewService.java',
    'client/room/ClientDownedCamera.java', 'client/room/DownedCameraMath.java', 'client/room/ReviveTransition.java',
    'network/packet/DownedViewPacket.java', 'network/packet/RevivedProneReleasePacket.java',
    'mixin/client/DownedCameraAccessor.java', 'mixin/compat/tacz/RevivedProneCrawlMixin.java',
    'mixin/client/compat/tacz/RevivedProneInputMixin.java', 'mixin/client/compat/tacz/RevivedProneReleaseMixin.java',
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
    if relative.endswith('/ClientDownedCamera.java'):
        # The card and GPU effect are later consumers of this real camera-owned timeline.
        text = text.replace('                ClientKillerCardState.hide();\n', '')
        text = text.replace('ClientKillerCardState.hide(); ', '')
    elif relative.endswith('/DownedCameraAccessor.java'):
        for srg in ('m_90581_', 'm_90572_', 'f_90560_'):
            assert len(mapping[srg]) == 1
            text = text.replace('"' + srg + '"', '"' + next(iter(mapping[srg])) + '"')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
    print(relative)

# Restore only the camera-specific portions deliberately deferred in the body-stage import.
relative = 'client/room/ClientDownedRagdollState.java'
target = ROOT / 'src/main/java' / BASE / relative
assert 'ClientDownedCamera' not in target.read_text(encoding='utf-8')
target.write_text(official((SOURCE / BASE / relative).read_text(encoding='utf-8')), encoding='utf-8')
print(relative + ': restore native/ordinary camera integration')
