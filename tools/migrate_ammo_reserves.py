"""Import the deployment/reserve stage from the pinned source; never overwrite Java files."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
namespace = {'__file__': str(ROOT / 'tools/migrate_world_filters.py')}
exec((ROOT / 'tools/migrate_world_filters.py').read_text(encoding='utf-8').split('for relative in FILES:')[0], namespace)
SOURCE, BASE, official = (namespace[key] for key in ('SOURCE', 'BASE', 'official'))

for relative in ('server/support/SupportAmmoGrant.java', 'server/support/SupportAmmo.java'):
    target = ROOT / 'src/main/java' / BASE / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    text = official((SOURCE / BASE / relative).read_text(encoding='utf-8'))
    if relative.endswith('/SupportAmmo.java'):
        # The next box/bag stage supplies the room permission, burst, healing and sound runtime.
        # Import the real reserve targets now, without a placeholder for that later bag API.
        start = text.index('    public static int bag(ServerPlayer p) {')
        end = text.index('    public static void sync(ServerPlayer p)', start)
        text = text[:start] + text[end:]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
    print(relative)
