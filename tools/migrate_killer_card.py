"""One-shot import of the killer metadata/card/manual deployment/chat stage; pinned source, no Java overwrites."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / 'gd656conquest'
PIN = '955b76d8adc264703eb49141bdb99b06e84cba46'
assert subprocess.check_output(['git', '-C', str(SOURCE), 'rev-parse', 'HEAD'], text=True).strip() == PIN
assert not subprocess.check_output(['git', '-C', str(SOURCE), 'status', '--porcelain'], text=True).strip()
BASE = 'org/mods/gd656conquest/'
FILES = ['common/player/KillerCardInfo.java', 'common/player/DownedDeployModel.java', 'common/player/ManualDeathDeployModel.java', 'client/room/KillerCardModel.java', 'client/room/KillerCardLayout.java', 'client/room/RoomChatLayout.java', 'client/room/ClientKillerCardState.java', 'client/room/ClientDownedDeployState.java', 'client/room/ClientManualDeathDeployState.java', 'client/room/ClientRoomChatState.java', 'server/room/entry/MatchDuelStats.java', 'server/room/entry/KillWeaponResolver.java', 'server/room/entry/FatalHitMetadata.java', 'server/room/entry/KillerIntelService.java', 'server/room/entry/DownedDeployService.java', 'server/room/entry/ManualDeathDeployService.java', 'network/packet/KillerCardPacket.java', 'network/packet/DownedDeployPacket.java', 'mixin/client/KillerCardChatAccessor.java', 'mixin/client/RoomChatMixin.java', 'mixin/client/RoomChatScreenMixin.java', 'mixin/client/RoomChatSuggestionsMixin.java']
mapping = {}
for line in (ROOT / 'build/createMcpToSrg/output.tsrg').read_text().splitlines():
    if not line.startswith('\t') or line.startswith('\t\t'):
        continue
    parts = line.split()
    if len(parts) in (2, 3) and re.fullmatch(r'[mf]_\d+_', parts[-1]):
        mapping.setdefault(parts[-1], set()).add(parts[0])

def name(value):
    assert len(mapping.get(value, set())) == 1, (value, mapping.get(value))
    return next(iter(mapping[value]))

def official(text):
    def token(match):
        value = match.group()
        return name(value) if re.fullmatch(r'[mf]_\d+_', value) else value
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|\b[mf]_\d+_\b', token, text)

for relative in FILES:
    target = ROOT / 'src/main/java' / BASE / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    text = official((SOURCE / BASE / relative).read_text(encoding='utf-8'))
    if '/mixin/' in relative or relative.startswith('mixin/'):
        # Official member names in annotation strings are remapped by the current compiler/refmap.
        text = re.sub(r'\b[mf]_\d+_\b', lambda m: name(m.group()), text)
        text = text.replace(', remap = false)', ')')
        text = text.replace('/** Read-only chat bounds; the card moves instead of changing chat rendering. */',
                            '/** Read-only native chat lines and geometry for the shared room layout. */')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
    print(relative)

# The only new visual resource consumed by this stage.
relative = 'assets/gd656conquest/textures/gui/killer_card/headshot.png'
target = ROOT / 'src/main/resources' / relative
assert not target.exists(), f'Refusing to overwrite {target}'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes((SOURCE / relative).read_bytes())
print(relative)
