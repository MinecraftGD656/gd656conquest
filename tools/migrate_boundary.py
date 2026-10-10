"""Import the boundary stage from the pinned decompile source; never overwrite Java files."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
# Reuse the verified official-mapping lexer and source-pin checks, not the earlier import loop.
namespace = {'__file__': str(ROOT / 'tools/migrate_world_filters.py')}
exec((ROOT / 'tools/migrate_world_filters.py').read_text().split('for relative in FILES:')[0], namespace)
SOURCE, BASE, official, name = (namespace[key] for key in ('SOURCE', 'BASE', 'official', 'name'))
FILES = [
    'server/room/runtime/BoundaryCountdown.java', 'server/room/runtime/FlightBoundaryGeometry.java',
    'server/room/runtime/UnifiedBoundaryService.java', 'server/compat/vehicle/BoundaryVehicleDestruction.java',
    'server/room/config/BoundaryConfigNumbers.java', 'client/room/BoundaryPresentation.java',
    'client/room/ClientBoundaryState.java', 'client/room/BoundaryHudEvents.java',
    'mixin/client/BoundaryWorldRenderMixin.java', 'mixin/client/compat/tacz/BoundaryCrosshairMixin.java',
    'mixin/compat/ywzjvehicle/BoundaryDestructionMixin.java',
    'mixin/compat/superbwarfare/BoundaryDestructionMixin.java',
]
for relative in FILES:
    target = ROOT / 'src/main/java' / BASE / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    text = official((SOURCE / BASE / relative).read_text(encoding='utf-8'))
    if relative.endswith('/UnifiedBoundaryService.java'):
        text = text.replace('org.mods.gd656conquest.server.compat.ragdoll.DownedRagdollEvents',
                            'org.mods.gd656conquest.server.room.runtime.RoomDeathEventDispatcher')
        text = text.replace('DownedRagdollEvents.isStillAliveOutsideDeployment',
                            'RoomDeathEventDispatcher.isStillAliveOutsideDeployment')
    if relative.endswith('/BoundaryConfigNumbers.java'):
        begin = text.index('        if (!"flightboundarypaddingblocks"')
        end = text.index('return null;', begin) + len('return null;')
        text = text[:begin] + '        if (!"flightboundarypaddingblocks".equals(id.toLowerCase(Locale.ROOT))) return null;' + text[end:]
    if relative.endswith('/ClientBoundaryState.java'):
        text = text.replace('suppression.set(ClientSuppressionState.opacity());',
                            'suppression.set(0F); // Suppression is migrated in its own later stage.')
    if relative.endswith('/BoundaryWorldRenderMixin.java'):
        text = re.sub(r'\b[mf]_\d+_\b', lambda match: name(match.group()), text)
        text = text.replace('@Mixin(value = GameRenderer.class, remap = false)', '@Mixin(GameRenderer.class)')
        text = text.replace('target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0)',
                            'target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", remap = false, ordinal = 0)')
        text = text.replace('@Inject(method = {"onResourceManagerReload", "close"}, at = @At("HEAD"), require = 1)',
            '''@Inject(method = "reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("HEAD"), require = 1)
    private void gd656conquest$releaseBoundaryResourcesOnReload(CallbackInfo ci) { ClientBoundaryState.releaseResources(); }
    @Inject(method = "close", remap = false, at = @At("HEAD"), require = 1)''')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
    print(relative)
for relative in [
    'assets/gd656conquest/shaders/post/boundary_world.json',
    'assets/gd656conquest/shaders/program/boundary_world.json',
    'assets/gd656conquest/shaders/program/boundary_world.fsh',
    'assets/gd656conquest/sounds/voice/broadcast/vo_mp_mother_inform_leaving_area.ogg',
]:
    target = ROOT / 'src/main/resources' / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes((SOURCE / relative).read_bytes())
    print(relative)
