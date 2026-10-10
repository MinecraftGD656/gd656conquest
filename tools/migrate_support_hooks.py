"""One-time original-source activation for the imported support runtime (no binary patching)."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/org/mods/gd656conquest'
SUPPORT = 'org.mods.gd656conquest.server.support.SupportRuntime'
assert 'migration_version=0.0.9' in (ROOT / 'gradle.properties').read_text(encoding='utf-8'), 'Expected prior migration version'
assert subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=ROOT, text=True).strip() == '0b7383f', 'Expected committed 0.0.9 input'
assert SUPPORT + '.useBag' not in (JAVA / 'common/item/AmmoBagItem.java').read_text(encoding='utf-8'), 'Refusing to repeat activation'

def replace_body(text, signature, replacement, remove=False):
    pos = text.index(signature)
    begin = text.index('{', pos)
    depth, end = 1, begin + 1
    while depth:
        if text[end] == '{': depth += 1
        elif text[end] == '}': depth -= 1
        end += 1
    return text[:pos] + text[end:] if remove else text[:begin] + '{\n' + replacement + '\n    }' + text[end:]

def edit(relative, change):
    path = JAVA / relative
    old = path.read_text(encoding='utf-8')
    new = change(old)
    assert new != old, f'No change: {relative}'
    path.write_text(new, encoding='utf-8')
    print(relative)

for relative in ('common/item/AmmoBagItem.java', 'common/item/MedicalBagItem.java'):
    def bags(text):
        text = replace_body(text, 'public InteractionResultHolder<ItemStack> use(',
                            f'        return {SUPPORT}.useBag(level, player, hand);')
        text = replace_body(text, 'public InteractionResult interactLivingEntity(',
                            f'        return {SUPPORT}.interactBag(stack, user, target, hand);')
        if relative.endswith('MedicalBagItem.java'):
            text = replace_body(text, '    private static void applyHealEffect(', '', remove=True)
        return text
    edit(relative, bags)

def events(text):
    for signature, hook in (('handlePlayerItemToss', 'toss'), ('handleEntityItemPickup', 'pickup')):
        token = f'public static void {signature}'
        pos = text.index('{', text.index(token)) + 1
        text = text[:pos] + f'\n        if (event != null && {SUPPORT}.{hook}(event)) return;' + text[pos:]
    return replace_body(text, '    private static void handleMedicalBagPickup(', '', remove=True).replace(
        '            handleMedicalBagPickup(event, player, itemEntity, stack);',
        '            // All support bags are handled by SupportRuntime before the legacy path.')
edit('server/player/state/PlayerItemInteractionService.java', events)

def boxes(text):
    text = text.replace('AMMO_BOX_MAX_USE_COUNT = 16;', 'AMMO_BOX_MAX_USE_COUNT = 20;')
    text = text.replace('MEDICAL_BOX_MAX_USE_COUNT = 16;', 'MEDICAL_BOX_MAX_USE_COUNT = 100;')
    for field in ('ammoBox', 'medicalBox'):
        text = text.replace(f'{field}.setOwnerUuid(null);', f'{field}.setOwnerUuid(null);\n            {SUPPORT}.initializeBox({field}, null, room);')
        text = text.replace(f'{field}.setOwnerUuid(ownerId);', f'{field}.setOwnerUuid(ownerId);\n        {SUPPORT}.initializeBox({field}, ownerId, itemEntity);')
    assert text.count('player.level().addFreshEntity(dropped);') == 2
    text = text.replace('player.level().addFreshEntity(dropped);', f'{SUPPORT}.stampBoxThrow(dropped, player);\n        player.level().addFreshEntity(dropped);')
    for medical in (False, True):
        which = 'Medical' if medical else 'Ammo'
        text = replace_body(text, f'private void process{which}BoxAura(',
                            f'        {SUPPORT}.boxes(server, active{which}BoxIds, gameTick, {str(medical).lower()});')
    text = replace_body(text, 'public boolean tryUseNearbyAmmoBox(', '        return false; // Replaced by the recipient-owned aura runtime.')
    text = replace_body(text, 'private boolean tryTriggerMedicalBoxForPlayer(', '        return false; // The old potion and notification mechanism has been replaced.')
    return text
edit('server/room/runtime/RoomSupportBoxService.java', boxes)

def replenish(text):
    return replace_body(text, 'private boolean replenishConfiguredItem(',
        f'        return {SUPPORT}.recharge(player, item, gameTick, lastReplenishTickBySlot);')
edit('server/room/runtime/RoomItemReplenishService.java', replenish)
edit('server/room/runtime/RoomDeathSupportDropService.java', lambda text: text.replace(
    '        victim.level().addFreshEntity(dropEntity);',
    f'        {SUPPORT}.markDeathReward(dropEntity, victim);\n        victim.level().addFreshEntity(dropEntity);'))

def loadout(text):
    begin = text.index('{', text.index('private ItemStack placeLoadoutItem(')) + 1
    text = text[:begin] + f'\n        {SUPPORT}.prepareLoadoutStack(stack);' + text[begin:]
    # The old compatibility API keeps its maxRounds limit; its successful cue becomes private.
    begin = text.index('        player.serverLevel().playSound(', text.index('public AmmoSupplyResult trySupplyAmmoByBag('))
    end = text.index('        return new AmmoSupplyResult(added);', begin)
    return text[:begin] + f'        {SUPPORT}.ammoSupplied(player);\n' + text[end:]
edit('server/room/runtime/RoomPlayerLoadoutService.java', loadout)

def regen(text):
    begin = text.index('{', text.index('public static void onPlayerTick(')) + 1
    text = text[:begin] + f'\n        if ({SUPPORT}.pausePassive(player)) return;' + text[begin:]
    text = text.replace('        player.heal(HEAL_AMOUNT);', '''        float healthBefore = player.getHealth();
        player.heal(HEAL_AMOUNT);
        if (player.getHealth() <= healthBefore) {
            return;
        }''')
    begin = text.index('            player.serverLevel().playSound(')
    end = text.index('            REGEN_STARTED_SOUND_PLAYED_BY_PLAYER.put(', begin)
    text = text[:begin] + '''            player.playNotifySound(SoundEvent.createVariableRangeEvent(PLAYER_HEAL_SOUND_ID),
                SoundSource.PLAYERS, 1.0F, 1.0F);
''' + text[end:]
    begin = text.index('{', text.index('public static void resetPlayer(')) + 1
    return text[:begin] + f'\n        {SUPPORT}.reset(player);' + text[begin:]
edit('server/player/regen/PassiveRegenService.java', regen)

def config(text):
    # Keep both prior constructors while extending the actual record/Gson canonical path.
    start = text.index('    public RoomCommonConfig(\n')
    end = text.index('\n    public static RoomCommonConfig defaults()', start)
    prior = text[start:end]
    compat = prior.replace('    String roomDescription\n', '    String roomDescription,\n    Integer flightVehicleOutsideBoundaryDeathSeconds,\n    Double flightBoundaryPaddingBlocks\n')
    compat = compat.replace('roomDescription, null, null);', 'roomDescription, flightVehicleOutsideBoundaryDeathSeconds, flightBoundaryPaddingBlocks, null, null);')
    text = text[:start] + prior.replace('roomDescription, null, null);', 'roomDescription, null, null, null, null);') + compat + text[end:]
    text = text.replace('    Double flightBoundaryPaddingBlocks\n) {', '    Double flightBoundaryPaddingBlocks,\n    Boolean allowEnemySupportResupply,\n    Double medicalBoxDamageCooldownSeconds\n) {', 1)
    text = text.replace('        playerMaxY = Math.max(0, playerMaxY);', '''        allowEnemySupportResupply = allowEnemySupportResupply == null ? Boolean.TRUE : allowEnemySupportResupply;
        medicalBoxDamageCooldownSeconds = medicalBoxDamageCooldownSeconds == null || !Double.isFinite(medicalBoxDamageCooldownSeconds)
            ? 3.0D : Math.max(0.0D, Math.min(60.0D, medicalBoxDamageCooldownSeconds));
        playerMaxY = Math.max(0, playerMaxY);''', 1)
    return text
edit('server/room/model/RoomCommonConfig.java', config)
for relative in ('server/data/roomdefinition/RoomDefinitionRepository.java',
                 'server/room/config/RoomDefinitionGroupConfigEditor.java',
                 'server/room/entry/RoomAdminConfigTemplateService.java', 'server/room/entry/RoomCommands.java'):
    def copies(text):
        return re.sub(r'(\s+)([\w.()]+)\.flightBoundaryPaddingBlocks\(\)(\s*\n\s*\);)',
            lambda m: m[1]+m[2]+'.flightBoundaryPaddingBlocks(),'+m[1]+m[2]+'.allowEnemySupportResupply(),'+m[1]+m[2]+'.medicalBoxDamageCooldownSeconds()'+m[3], text)
    edit(relative, copies)
edit('server/room/config/RoomConfigDescriptorRegistry.java', lambda text: text.replace(
    '        common(descriptors, RoomConfigCategory.RULES, "flightBoundaryPaddingBlocks",',
    '''        common(descriptors, RoomConfigCategory.RULES, "allowEnemySupportResupply", "可通过敌方箱包回复", "commonConfig.allowEnemySupportResupply", RoomConfigValueType.BOOLEAN, List.of());
        common(descriptors, RoomConfigCategory.RULES, "medicalBoxDamageCooldownSeconds", "医疗箱受伤后回复冷却秒数", "commonConfig.medicalBoxDamageCooldownSeconds", RoomConfigValueType.NUMBER, List.of());
        common(descriptors, RoomConfigCategory.RULES, "flightBoundaryPaddingBlocks",'''))
edit('server/room/config/BoundaryConfigNumbers.java', lambda text: text.replace(
    '        if (!"flightboundarypaddingblocks".equals(id.toLowerCase(Locale.ROOT))) return null;',
    '''        boolean medical = "medicalboxdamagecooldownseconds".equals(id.toLowerCase(Locale.ROOT));
        if (!medical && !"flightboundarypaddingblocks".equals(id.toLowerCase(Locale.ROOT))) return null;''').replace(
    'if (!Double.isFinite(value) || value < 0)', 'if (!Double.isFinite(value) || value < 0 || (medical && value > 60))'))

path = ROOT / 'src/main/resources/gd656conquest.mixins.json'
text = path.read_text(encoding='utf-8').replace('    "server.ServerPlayerDeathMixin",',
    '    "server.SupportDropMixin",\n    "server.ServerPlayerDeathMixin",').replace('    "client.KillerCardChatAccessor",',
    '    "client.AmmoSupplySoundAccessor",\n    "client.KillerCardChatAccessor",')
path.write_text(text, encoding='utf-8')
path = ROOT / 'gradle.properties'
path.write_text(path.read_text(encoding='utf-8').replace('migration_version=0.0.9', 'migration_version=0.0.10'), encoding='utf-8')
