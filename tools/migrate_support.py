"""Import actual box/bag runtime; defer only the not-yet-migrated suppression dependencies."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
namespace = {'__file__': str(ROOT / 'tools/migrate_world_filters.py')}
exec((ROOT / 'tools/migrate_world_filters.py').read_text(encoding='utf-8').split('for relative in FILES:')[0], namespace)
SOURCE, BASE, official, name = (namespace[key] for key in ('SOURCE', 'BASE', 'official', 'name'))

def body(text, signature, replacement):
    begin = text.index('{', text.index(signature))
    depth = 1
    end = begin + 1
    while depth:
        if text[end] == '{': depth += 1
        elif text[end] == '}': depth -= 1
        end += 1
    return text[:begin] + '{' + replacement + '}' + text[end:]

for relative in ('server/support/SupportContext.java', 'server/support/SupportRules.java',
                 'server/support/SupportRuntime.java', 'server/support/SupportDropGuard.java',
                 'client/room/ClientAmmoSupplySound.java', 'mixin/client/AmmoSupplySoundAccessor.java',
                 'mixin/server/SupportDropMixin.java'):
    target = ROOT / 'src/main/java' / BASE / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    text = official((SOURCE / BASE / relative).read_text(encoding='utf-8'))
    if relative.endswith('/SupportRules.java'):
        text = text.replace('import org.mods.gd656conquest.server.room.model.RoomSuppressionConfig;\n', '')
        start = text.index('    public static double nearGain(')
        end = text.index('    /** Progress belongs', start)
        text = text[:start] + text[end:]
    if relative.endswith('/SupportRuntime.java'):
        text = text.replace('        final SupportRules.Suppression suppression=new SupportRules.Suppression();\n', '')
        text = text.replace('        final Map<UUID,Double> shotGains=new HashMap<>();\n', '')
        text = text.replace('        final Map<UUID,Long> shotTimes=new HashMap<>();\n', '')
        text = text.replace('        boolean visual;\n', '')
        text = text.replace('        var old=rt.recipients.remove(p.getUUID());\n        if(old!=null && old.visual)SuppressionNetwork.send(p,false,.25F);',
                            '        rt.recipients.remove(p.getUUID());')
        start = text.index('    public static boolean suppressionBlocks(')
        end = text.index('    public static boolean damageBlocks(', start)
        text = text[:start] + text[end:]
        text = text.replace('return suppressionBlocks(p) || (s!=null', 'return (s!=null')
        start = text.index('            s.shotTimes.entrySet()')
        end = text.index('            if(s.treatment!=null)', start)
        text = text[:start] + text[end:]
        text = text.replace('damageBlocks(p) || suppressionBlocks(p) ||', 'damageBlocks(p) ||')
        assert 'Suppression' not in text and 'suppression' not in text
    if relative.endswith('/SupportDropMixin.java'):
        text = re.sub(r'\b[mf]_\d+_\b', lambda m: name(m.group()), text)
        text = text.replace('@Mixin(value=ServerGamePacketListenerImpl.class,remap=false)', '@Mixin(ServerGamePacketListenerImpl.class)')
    if relative.endswith('/AmmoSupplySoundAccessor.java'):
        text = text.replace('@Accessor(value="f_120226_",remap=false)', '@Accessor("instanceToChannel")')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
    print(relative)

# The preceding stage imported only the independent targets. Add the genuine bag API now.
target = ROOT / 'src/main/java' / BASE / 'server/support/SupportAmmo.java'
full = official((SOURCE / BASE / 'server/support/SupportAmmo.java').read_text(encoding='utf-8'))
start = full.index('    public static int bag(ServerPlayer p) {')
end = full.index('    public static void sync(ServerPlayer p)', start)
assert target.read_text(encoding='utf-8') == full[:start] + full[end:], 'Unexpected previous SupportAmmo edits'
target.write_text(full, encoding='utf-8')
