"""One-shot body-stage import from the pinned decompiled project using local official mappings.

Does not overwrite existing source files. Later edits live in the target source, not this importer.
Camera observation, corpse camera pins and prone release remain separate migration stages.
"""
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
    'server/compat/ragdoll/' + n + '.java' for n in (
        'DownedRagdollBridge', 'RagdollHitCacheGuard', 'RagdollSyncScope', 'RagdollPacketScope',
        'RagdollRecordView', 'RagdollSubscriptionPolicy', 'RagdollSubscriptions')
] + [
    'client/room/' + n + '.java' for n in (
        'ClientDownedRagdollState', 'DownedRagdollClientHooks', 'ClientRagdollSubscriptions',
        'RagdollPlaybackGuard', 'RagdollVisibilityMath')
] + [
    'network/packet/' + n + '.java' for n in (
        'DownedRagdollStatePacket', 'RagdollCatalogPacket', 'RagdollObservationPacket', 'RagdollSubscribePacket')
] + [
    'mixin/compat/ragdoll/' + n + '.java' for n in (
        'ServerRagdollHitCacheMixin', 'RagdollDeathMomentumMixin', 'RagdollPacketScopeMixin', 'RagdollSubscriptionMixin')
] + [
    'mixin/client/compat/ragdoll/' + n + '.java' for n in (
        'ClientRagdollHitCacheMixin', 'RagdollSpawnMomentumMixin', 'ClientRagdollReplacementMixin',
        'RagdollPlaybackMixin', 'RagdollVisibilityMixin')
]

# SRG IDs are stable; reject ambiguous official names rather than guess at overloaded owners.
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
    # Keep reflection names and Mixin descriptor strings unchanged.
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|\b[mf]_\d+_\b', token, text)

def remove_method(text, name):
    match = re.search(r'\n    (?:public|private) static [^\n]+\b' + name + r'\([^\n]*\)\s*\{', text)
    assert match, name
    start = text.index('{', match.start())
    depth = 1
    end = start + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[:match.start()] + text[end:]

for relative in FILES:
    target = ROOT / 'src/main/java' / BASE / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    text = official((SOURCE / BASE / relative).read_text(encoding='utf-8'))
    if relative.endswith('/DownedRagdollBridge.java'):
        text = text.replace('DownedRagdollEvents.isFreshDeathRecord', 'RagdollFatalContext.isFreshDeathRecord')
        text = text.replace('DownedViewService.safePronePosition(player, body.anchor, body.origin)',
                            'ReviveBodyPlacement.safePosition(player, body.anchor, body.origin)')
        text = text.replace('                player.setForcedPose(Pose.SWIMMING);\n                player.setPose(Pose.SWIMMING);\n', '')
        text = text.replace('DownedViewService.reviveYaw(player)', 'body.yaw')
        text = text.replace('        final Vec3 origin;', '        final Vec3 origin;\n        final float yaw;')
        text = text.replace('            this.origin = player.position();',
                            '            this.origin = player.position();\n            this.yaw = player.getYRot();')
    elif relative.endswith('/RagdollSubscriptions.java'):
        # Forced ordinary-corpse observation belongs to the later death-camera service.
        text = text.replace('\n            || DownedViewService.observesCorpse(p,t.record)', '')
    elif relative.endswith('/ClientDownedRagdollState.java'):
        for name in ('beginDeathObservation', 'endDeathObservation', 'deathSnapshot'):
            text = remove_method(text, name)
        text = re.sub(r'    private static (?:int|UUID|Vec3|Object) observedDeath[^;]+;\n', '', text)
        text = text.replace('        endDeathObservation();\n', '')
        text = text.replace('return cameraAttached && mc.player != null && (ClientDownedCamera.observingDeath() && deathSnapshot()!=null\n            || ClientDownedState.isActive() && isBodyReady(mc.player.getUUID()));',
                            'return cameraAttached && mc.player != null && ClientDownedState.isActive()\n            && isBodyReady(mc.player.getUUID());')
        text = text.replace('                if (ClientDownedCamera.ownsCamera()) {\n                    if (cameraAttached) detachCamera();\n                } else if (!cameraAttached || cameraEntityId != entry.entityId) {',
                            '                if (!cameraAttached || cameraEntityId != entry.entityId) {')
        start = text.index('        if (ClientDownedCamera.usesFixedDeathCamera()')
        end = text.index('        if (!attachForSelf', start)
        text = text[:start] + text[end:]
        start = text.index('    public static Vec3 cameraAnchor()')
        end = text.index('    /** Only the published head position', start)
        text = text[:start] + '''    public static Vec3 cameraAnchor() {
        Minecraft mc = Minecraft.getInstance();
        Entry entry = mc.player == null ? null : ENTRIES.get(mc.player.getUUID());
        return entry != null && ready(entry) ? entry.anchor : null;
    }

''' + text[end:]
        text = text.replace('!ClientDownedCamera.observingDeath() && entry != null && ready(entry)', 'entry != null && ready(entry)')
        text = text.replace('body == null ? deathSnapshot() : invokeOn(api.snapshot, body)', 'body == null ? null : invokeOn(api.snapshot, body)')
        assert 'ClientDownedCamera' not in text and 'observedDeath' not in text and 'deathSnapshot' not in text
    elif relative.endswith('/DownedRagdollClientHooks.java'):
        start = text.index('        if (org.mods.gd656conquest.common.player.RevivedPronePose.active')
        end = text.index('        PlayerAnimatorDebugDimensionsState.clearPlayer', start)
        text = text[:start] + text[end:]
        text = text.replace('        ClientKillerCardState.hide();\n', '')
    elif relative.endswith('/ClientRagdollSubscriptions.java'):
        # Reflection needs both development official and production SRG names.
        text = text.replace('if(fov==null){fov=GameRenderer.class.getDeclaredMethod("m_109141_",Camera.class,float.class,boolean.class);fov.setAccessible(true);}',
            'if(fov==null){try{fov=GameRenderer.class.getDeclaredMethod("getFov",Camera.class,float.class,boolean.class);}'
            'catch(NoSuchMethodException ignored){fov=GameRenderer.class.getDeclaredMethod("m_109141_",Camera.class,float.class,boolean.class);}fov.setAccessible(true);}')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
print(f'Imported {len(FILES)} body/sync source files; original files require explicit integration patches.')
