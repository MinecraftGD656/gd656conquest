"""P6-B source migration: real TACZ suppression, shared recovery gate and world vignette."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
assert subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=ROOT, text=True).strip() == '2e642f6'
ns = {'__file__': str(ROOT / 'tools/migrate_world_filters.py')}
exec((ROOT / 'tools/migrate_world_filters.py').read_text(encoding='utf-8').split('for relative in FILES:')[0], ns)
SOURCE, BASE, official, name = (ns[k] for k in ('SOURCE', 'BASE', 'official', 'name'))
JAVA = ROOT / 'src/main/java' / BASE
FILES = ('server/room/model/RoomSuppressionConfig.java', 'server/support/TaczSuppression.java',
         'server/support/SuppressionNetwork.java', 'network/packet/SuppressionPacket.java',
         'client/room/ClientSuppressionState.java', 'client/room/SuppressionHudEvents.java',
         'mixin/client/SuppressionWorldMixin.java', 'mixin/compat/tacz/SupportSuppressionMixin.java',
         'mixin/compat/tacz/SupportShotMixin.java', 'mixin/compat/tacz/SupportBulletMixin.java',
         'mixin/compat/tacz/KineticBulletSupportAccessor.java')
for relative in FILES:
    path = JAVA / relative
    assert not path.exists(), f'Refusing to overwrite {path}'
    text = official((SOURCE / BASE / relative).read_text(encoding='utf-8'))
    if relative == 'mixin/client/SuppressionWorldMixin.java':
        text = text.replace('@Mixin(value=GameRenderer.class,remap=false)', '@Mixin(GameRenderer.class)')
        text = re.sub(r'\b[mf]_\d+_\b', lambda m: name(m.group()), text)
        text = text.replace('ordinal=0)', 'remap=false,ordinal=0)')
        text = text.replace('@Inject(method={"onResourceManagerReload","close"},at=@At("HEAD"),require=1)',
            '@Inject(method="reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V",at=@At("HEAD"),require=1)\n'
            '    private void gd656$reload(CallbackInfo ci){ClientSuppressionState.release();}\n'
            '    @Inject(method="close",remap=false,at=@At("HEAD"),require=1)')
    if relative == 'server/support/SuppressionNetwork.java':
        text = text.replace('    public static void send(', '    /** Append after existing message IDs; accept only client-bound traffic. */\n'
            '    public static int register(net.minecraftforge.network.simple.SimpleChannel channel,int id){\n'
            '        channel.registerMessage(id++,SuppressionPacket.class,SuppressionPacket::encode,SuppressionPacket::decode,\n'
            '            SuppressionPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));\n'
            '        return id;\n'
            '    }\n'
            '    public static void send(', 1)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')

def edit(relative, transform):
    path = JAVA / relative
    before = path.read_text(encoding='utf-8')
    after = transform(before)
    assert after != before, relative
    path.write_text(after, encoding='utf-8')

# Restore just the real suppression sections omitted in the P5 runtime stage.
full = official((SOURCE / BASE / 'server/support/SupportRules.java').read_text(encoding='utf-8'))
start = full.index('    public static double nearGain(')
end = full.index('    /** Progress belongs', start)
prior = full.replace('import org.mods.gd656conquest.server.room.model.RoomSuppressionConfig;\n', '')
prior = prior[:prior.index('    public static double nearGain(')] + prior[prior.index('    /** Progress belongs'):]
assert (JAVA / 'server/support/SupportRules.java').read_text(encoding='utf-8') == prior
edit('server/support/SupportRules.java', lambda text: full)
full = official((SOURCE / BASE / 'server/support/SupportRuntime.java').read_text(encoding='utf-8'))
def runtime(text):
    text = text.replace('        MedicalTreatment treatment;\n', '        MedicalTreatment treatment;\n        final SupportRules.Suppression suppression=new SupportRules.Suppression();\n', 1)
    text = text.replace('        long pickupUntil,', '        final Map<UUID,Double> shotGains=new HashMap<>();\n        final Map<UUID,Long> shotTimes=new HashMap<>();\n        long pickupUntil,', 1)
    text = text.replace('        int cursor;\n', '        int cursor;\n        boolean visual;\n', 1)
    text = text.replace('        rt.recipients.remove(p.getUUID());', '        var old=rt.recipients.remove(p.getUUID());\n        if(old!=null && old.visual)SuppressionNetwork.send(p,false,.25F);', 1)
    block = full[full.index('    public static boolean suppressionBlocks('):full.index('    public static boolean damageBlocks(')]
    text = text.replace('    public static boolean damageBlocks(', block + '    public static boolean damageBlocks(', 1)
    text = text.replace('return (s!=null &&', 'return suppressionBlocks(p) || (s!=null &&', 1)
    block = full[full.index('            s.shotTimes.entrySet()'):full.index('            if(s.treatment!=null)')]
    text = text.replace('            if(s.treatment!=null)', block + '            if(s.treatment!=null)', 1)
    text = text.replace('damageBlocks(p) ||', 'damageBlocks(p) || suppressionBlocks(p) ||', 1)
    assert text == full, 'Unexpected P5 runtime divergence; inspect rather than replacing original logic'
    return text
edit('server/support/SupportRuntime.java', runtime)

def common(text):
    params = text.split('public record RoomCommonConfig(', 1)[1].split(') {', 1)[0].strip()
    names = [line.strip().rstrip(',').split()[-1] for line in params.splitlines()]
    constructor = '\n    /** Compatibility with the preceding five-extension configuration. */\n    public RoomCommonConfig(\n    ' + params + '\n    ) {\n        this(' + ', '.join(names) + ', null);\n    }\n'
    text = text.replace('    RoomNaturalRegenConfig naturalRegenConfig\n) {', '    RoomNaturalRegenConfig naturalRegenConfig,\n    RoomSuppressionConfig suppressionConfig\n) {', 1)
    text = text.replace('    public RoomCommonConfig {', '    public RoomCommonConfig {\n        suppressionConfig = suppressionConfig == null ? RoomSuppressionConfig.defaults() : suppressionConfig;', 1)
    return text.replace('    public static RoomCommonConfig defaults()', constructor + '\n    public static RoomCommonConfig defaults()', 1)
edit('server/room/model/RoomCommonConfig.java', common)
for relative in ('server/data/roomdefinition/RoomDefinitionRepository.java', 'server/room/config/RoomDefinitionGroupConfigEditor.java',
                 'server/room/entry/RoomAdminConfigTemplateService.java', 'server/room/entry/RoomCommands.java'):
    def copy(text):
        pattern = r'(?P<var>\w+(?:\.commonConfig\(\))?)\.naturalRegenConfig\(\)(?P<tail>\s*\n\s*\))'
        text, count = re.subn(pattern, lambda m: m.group('var') + '.naturalRegenConfig(),\n' + ' ' * 12 + m.group('var') + '.suppressionConfig()' + m.group('tail'), text)
        assert count == 1, relative
        return text
    edit(relative, copy)
source = (SOURCE / BASE / 'server/room/config/RoomConfigDescriptorRegistry.java').read_text(encoding='utf-8')
lines = [line for line in source.splitlines() if 'commonConfig.suppressionConfig.' in line]
assert len(lines) == 9
edit('server/room/config/RoomConfigDescriptorRegistry.java', lambda text: text.replace('        common(descriptors, RoomConfigCategory.RULES, "naturalRegenMode"', '\n'.join(lines) + '\n        common(descriptors, RoomConfigCategory.RULES, "naturalRegenMode"', 1))
edit('server/room/config/BoundaryConfigNumbers.java', lambda text: text.replace('        boolean medical =',
    '        if (id.toLowerCase(Locale.ROOT).startsWith("suppression")) {\n'
    '            try {\n'
    '                double value = Double.parseDouble(raw.trim());\n'
    '                if (!Double.isFinite(value) || value < 0) throw new NumberFormatException();\n'
    '                return value;\n'
    '            } catch (NumberFormatException invalid) {\n'
    '                throw new IllegalArgumentException("压制配置必须为有限的非负数", invalid);\n'
    '            }\n'
    '        }\n'
    '        boolean medical =', 1))
edit('client/room/ClientBoundaryState.java', lambda text: text.replace('suppression.set(0F); // Suppression is migrated in its own later stage.', 'suppression.set(ClientSuppressionState.opacity());', 1))
edit('network/NetworkManager.java', lambda text: text.replace('"bf1-0.0.8"', '"bf1-0.0.12"')
    .replace('        packetId = KillerCardNetwork.register(CHANNEL, packetId);', '        packetId = KillerCardNetwork.register(CHANNEL, packetId);\n        packetId = org.mods.gd656conquest.server.support.SuppressionNetwork.register(CHANNEL, packetId);', 1))
for relative in ('assets/gd656conquest/shaders/post/suppression_world.json', 'assets/gd656conquest/shaders/program/suppression_world.json', 'assets/gd656conquest/shaders/program/suppression_world.fsh'):
    path = ROOT / 'src/main/resources' / relative
    assert not path.exists(), path
    path.write_bytes((SOURCE / relative).read_bytes())
path = ROOT / 'src/main/resources/gd656conquest.mixins.json'
text = path.read_text(encoding='utf-8')
text = text.replace('    "server.NaturalFoodRegenMixin",', '    "server.NaturalFoodRegenMixin",\n' + '\n'.join('    "' + relative.removeprefix('mixin/').removesuffix('.java').replace('/', '.') + '",' for relative in FILES if relative.startswith('mixin/compat/')), 1)
text = text.replace('    "client.BoundaryWorldRenderMixin",', '    "client.BoundaryWorldRenderMixin",\n    "client.SuppressionWorldMixin",', 1)
path.write_text(text, encoding='utf-8')
path = ROOT / 'gradle.properties'
text = path.read_text(encoding='utf-8');assert 'migration_version=0.0.11' in text
path.write_text(text.replace('migration_version=0.0.11', 'migration_version=0.0.12'), encoding='utf-8')
print('Imported P6-B: suppression state/TACZ hooks/S2C packet/world vignette/six-field copies.')
