"""One-shot import of the death/revive world rendering stage; pinned source, no Java overwrites."""
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
    'client/room/DeathFilterTransition.java', 'client/room/ClientDeathFilter.java',
    'client/room/ClientReviveEffect.java', 'mixin/client/DeathWorldRenderMixin.java',
    'mixin/client/DeathKillerOutlineMixin.java', 'mixin/client/DeathPostChainAccessor.java',
]
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
        text = text.replace('/** SRG names deliberately match the project\'s checked SRG binary build. */',
                            '/** World/UI boundary hooks use official names and the generated production refmap. */')
        text = text.replace('target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0)',
                            'target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", remap = false, ordinal = 0)')
        if relative.endswith('/DeathWorldRenderMixin.java'):
            # GameRenderer is not a ResourceManagerReloadListener in the current Forge binary.
            # Its render-thread reload listener actually invokes reloadShaders(ResourceProvider).
            text = text.replace('@Inject(method = {"onResourceManagerReload", "close"}, at = @At("HEAD"), require = 1)',
                '''@Inject(method = "reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("HEAD"), require = 1)
    private void gd656conquest$releaseDeathResourcesOnReload(CallbackInfo ci) {
        ClientDeathFilter.releaseResources();
    }
    @Inject(method = "close", remap = false, at = @At("HEAD"), require = 1)''')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
    print(relative)

# Copy only resources consumed by this stage; retain all earlier assets and normal metadata.
for relative in [f'assets/gd656conquest/shaders/post/{n}.json' for n in ('death_world', 'revive_world')] + [
        f'assets/gd656conquest/shaders/program/{n}.{extension}'
        for n in ('death_world', 'revive_world', 'revive_bright', 'revive_blur') for extension in ('json', 'fsh')]:
    target = ROOT / 'src/main/resources' / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes((SOURCE / relative).read_bytes())
    print(relative)
