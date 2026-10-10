"""P6-A: import room/global regeneration and wire the original source mechanisms."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
assert subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=ROOT, text=True).strip() == 'e80aa2b'
ns = {'__file__': str(ROOT / 'tools/migrate_world_filters.py')}
exec((ROOT / 'tools/migrate_world_filters.py').read_text(encoding='utf-8').split('for relative in FILES:')[0], ns)
SOURCE, BASE, official, name = (ns[k] for k in ('SOURCE', 'BASE', 'official', 'name'))
JAVA = ROOT / 'src/main/java' / BASE

for relative in ('server/room/model/RoomNaturalRegenConfig.java', 'server/player/regen/NaturalRegenService.java',
                 'server/core/command/NaturalRegenCommands.java'):
    target = JAVA / relative
    assert not target.exists(), f'Refusing to overwrite {target}'
    target.write_text(official((SOURCE / BASE / relative).read_text(encoding='utf-8')), encoding='utf-8')

# The source food mixin already calls the shared pause predicate. It currently covers
# real box/bag treatment; the subsequent suppression stage will extend that predicate.
text = official((SOURCE / BASE / 'mixin/server/SuppressedFoodRegenMixin.java').read_text(encoding='utf-8'))
text = re.sub(r'\b[mf]_\d+_\b', lambda m: name(m.group()), text)
text = text.replace('@Mixin(value=FoodData.class,remap=false)', '@Mixin(FoodData.class)')
text = text.replace('SuppressedFoodRegenMixin', 'NaturalFoodRegenMixin')
target = JAVA / 'mixin/server/NaturalFoodRegenMixin.java'
assert not target.exists()
target.write_text(text, encoding='utf-8')

def edit(relative, transform):
    path = JAVA / relative
    prior = path.read_text(encoding='utf-8')
    after = transform(prior)
    assert after != prior, relative
    path.write_text(after, encoding='utf-8')

def common(text):
    # Preserve the 49/51/53 argument APIs; the canonical record now has 54 fields.
    params = text.split('public record RoomCommonConfig(', 1)[1].split(') {', 1)[0].strip()
    names = [line.strip().rstrip(',').split()[-1] for line in params.splitlines()]
    constructor = '\n    /** Compatibility with the preceding four-extension configuration. */\n    public RoomCommonConfig(\n    ' + params + '\n    ) {\n        this(' + ', '.join(names) + ', null);\n    }\n'
    text = text.replace('    Double medicalBoxDamageCooldownSeconds\n) {', '    Double medicalBoxDamageCooldownSeconds,\n    RoomNaturalRegenConfig naturalRegenConfig\n) {', 1)
    text = text.replace('    public RoomCommonConfig {', '    public RoomCommonConfig {\n        naturalRegenConfig = naturalRegenConfig == null ? RoomNaturalRegenConfig.defaults() : naturalRegenConfig;', 1)
    return text.replace('    public static RoomCommonConfig defaults()', constructor + '\n    public static RoomCommonConfig defaults()', 1)
edit('server/room/model/RoomCommonConfig.java', common)
for relative in ('server/data/roomdefinition/RoomDefinitionRepository.java', 'server/room/config/RoomDefinitionGroupConfigEditor.java',
                 'server/room/entry/RoomAdminConfigTemplateService.java', 'server/room/entry/RoomCommands.java'):
    def copy(text):
        pattern = r'(?P<var>\w+(?:\.commonConfig\(\))?)\.medicalBoxDamageCooldownSeconds\(\)(?P<tail>\s*\n\s*\))'
        text, count = re.subn(pattern, lambda m: m.group('var') + '.medicalBoxDamageCooldownSeconds(),\n' + ' ' * 12 + m.group('var') + '.naturalRegenConfig()' + m.group('tail'), text)
        assert count == 1, relative
        return text
    edit(relative, copy)

descriptors = (SOURCE / BASE / 'server/room/config/RoomConfigDescriptorRegistry.java').read_text(encoding='utf-8')
lines = [line for line in descriptors.splitlines() if 'commonConfig.naturalRegenConfig.' in line]
assert len(lines) == 6
edit('server/room/config/RoomConfigDescriptorRegistry.java', lambda text: text.replace('        common(descriptors, RoomConfigCategory.RULES, "medicalBoxDamageCooldownSeconds"', '\n'.join(lines) + '\n        common(descriptors, RoomConfigCategory.RULES, "medicalBoxDamageCooldownSeconds"', 1))
edit('server/room/config/BoundaryConfigNumbers.java', lambda text: text.replace('        boolean medical =', '        if ("passiveregenhealamount".equals(id.toLowerCase(Locale.ROOT))) {\n            try {\n                double amount = Double.parseDouble(raw.trim());\n                if (!Double.isFinite(amount) || amount < 0 || amount > 1024) throw new NumberFormatException();\n                return amount;\n            } catch (NumberFormatException invalid) {\n                throw new IllegalArgumentException("自然回血回复量必须为 0–1024 HP 的有限数值", invalid);\n            }\n        }\n        boolean medical =', 1))
# The existing native parser rejects negative delay values. Interval must also reject zero.
edit('server/room/config/RoomDefinitionScalarConfigEditor.java', lambda text: text.replace('        if (requiresPositiveInt(configId)) {', '        if ("passiveregenintervalticks".equals(configId) || requiresPositiveInt(configId)) {', 1))

def model(text):
    text = text.replace('private Integer passiveRegenIntervalTicks = 3;', 'private Integer passiveRegenIntervalTicks = 10;')
    text = text.replace('    private Integer passiveRegenStartDelaySeconds = 6;', '    private Integer passiveRegenStartDelaySeconds = 6;\n    private Boolean boolPassiveRegeneration = true;\n    private Double passiveRegenHealAmount = 0.5D;')
    extra = '\n    public Boolean getBoolPassiveRegeneration() { return boolPassiveRegeneration; }\n    public void setBoolPassiveRegeneration(Boolean value) { boolPassiveRegeneration = value; }\n    public Double getPassiveRegenHealAmount() { return passiveRegenHealAmount; }\n    public void setPassiveRegenHealAmount(Double value) { passiveRegenHealAmount = value; }\n'
    return text.replace('    public Double getMaxScopePistol()', extra + '\n    public Double getMaxScopePistol()', 1)
edit('server/data/serverconfig/ServerConfigDataModel.java', model)
def store(text):
    text = text.replace('== null ? 3 : get().getPassiveRegenIntervalTicks()', '== null ? 10 : get().getPassiveRegenIntervalTicks()')
    text = text.replace('model.setPassiveRegenIntervalTicks(3);', 'model.setPassiveRegenIntervalTicks(10);')
    extra = '    public synchronized boolean isPassiveRegenerationEnabled() {\n        return !Boolean.FALSE.equals(get().getBoolPassiveRegeneration());\n    }\n\n    public synchronized double getPassiveRegenHealAmount() {\n        return org.mods.gd656conquest.server.room.model.RoomNaturalRegenConfig.amount(get().getPassiveRegenHealAmount());\n    }\n\n'
    text = text.replace('    public synchronized double getMaxScopePistol()', extra + '    public synchronized double getMaxScopePistol()', 1)
    return text.replace('    private void normalize(ServerConfigDataModel model) {', '    private void normalize(ServerConfigDataModel model) {\n        if (model.getBoolPassiveRegeneration() == null) model.setBoolPassiveRegeneration(true);\n        model.setPassiveRegenHealAmount(org.mods.gd656conquest.server.room.model.RoomNaturalRegenConfig.amount(model.getPassiveRegenHealAmount()));', 1)
edit('server/data/serverconfig/ServerConfigStore.java', store)
def passive(text):
    text = text.replace('    private static final float HEAL_AMOUNT = 2.0F;\n', '')
    text = text.replace('        if (org.mods.gd656conquest.server.support.SupportRuntime.pausePassive(player)) return;', '        if (!NaturalRegenService.enabled(player) || org.mods.gd656conquest.server.support.SupportRuntime.pausePassive(player)) return;\n        startDelaySeconds = NaturalRegenService.startDelay(player, startDelaySeconds);\n        healIntervalTicks = NaturalRegenService.interval(player, healIntervalTicks);', 1)
    return text.replace('player.heal(HEAL_AMOUNT);', 'player.heal(NaturalRegenService.amount(player));', 1)
edit('server/player/regen/PassiveRegenService.java', passive)
def commands(text):
    # Delegate the two new keys, allowing every unknown key to continue the original path.
    text = text.replace('SERVER_CONFIG_KEYS = List.of(', 'SERVER_CONFIG_KEYS = NaturalRegenCommands.keys(List.of(', 1)
    start = text.index('SERVER_CONFIG_KEYS = NaturalRegenCommands.keys(List.of(')
    end = text.index('    );', start)
    text = text[:end] + text[end:].replace('    );', '    ));', 1)
    hooks = {
        'executeConfigGetSingle': '        Integer regenResult = NaturalRegenCommands.get(context);\n        if (regenResult != null) return regenResult;\n',
        'executeConfigSet': '        Integer regenResult = NaturalRegenCommands.set(context);\n        if (regenResult != null) return regenResult;\n',
        'suggestServerConfigValues': '        var regenSuggestions = NaturalRegenCommands.suggest(context, builder);\n        if (regenSuggestions != null) return regenSuggestions;\n',
        'normalizeServerConfigIdSafe': '        String regenId = NaturalRegenCommands.normalize(raw);\n        if (regenId != null) return regenId;\n',
        'readConfigValue': '        String regenValue = NaturalRegenCommands.read(store, configId);\n        if (regenValue != null) return regenValue;\n'
    }
    for method, hook in hooks.items():
        match = re.search(r'private static [^\n]+\b' + method + r'\([^\n]+\) [^{]*\{', text)
        assert match, method
        text = text[:match.end()] + '\n' + hook.rstrip('\n') + text[match.end():]
    start = text.index('    private static int executeConfigGetAll(')
    end = text.index('        return 1;', start)
    return text[:end] + '        NaturalRegenCommands.showAdded(context);\n' + text[end:]
edit('server/core/command/ConquestServerCommands.java', commands)

path = ROOT / 'src/main/resources/gd656conquest.mixins.json'
text = path.read_text(encoding='utf-8')
assert 'server.NaturalFoodRegenMixin' not in text
path.write_text(text.replace('"server.SupportDropMixin"', '"server.SupportDropMixin",\n    "server.NaturalFoodRegenMixin"', 1), encoding='utf-8')
path = ROOT / 'gradle.properties'
text = path.read_text(encoding='utf-8')
assert 'migration_version=0.0.10' in text
path.write_text(text.replace('migration_version=0.0.10', 'migration_version=0.0.11'), encoding='utf-8')
print('Imported P6-A natural regeneration; protocol and suppression state are unchanged.')
