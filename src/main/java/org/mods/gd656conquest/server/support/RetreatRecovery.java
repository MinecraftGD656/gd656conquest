package org.mods.gd656conquest.server.support;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.room.model.RoomGameplayId;

/** Restores the deployed equipment blueprint, never a pending UI selection or a new random roll. */
public final class RetreatRecovery {
    private static final String KEY = "gd656_retreat_loadout";
    private RetreatRecovery() {}

    public static void captureCurrent(ServerPlayer player) {
        var context = SupportContext.of(player);
        if (context == null || !RoomGameplayId.BREAKTHROUGH.equals(context.room().gameplayId())) return;
        capture(player, context.state(), context.room().roomId());
    }
    public static void capture(ServerPlayer player, RunningRuntimeState state, String room) {
        player.getPersistentData().put(KEY, snapshot(room, state.getActiveMatchInstanceId(), player.getInventory().save(new ListTag())));
    }
    public static CompoundTag snapshot(String room, long match, ListTag inventory) {
        var saved = new CompoundTag();
        saved.putString("room", room);
        saved.putLong("match", match);
        saved.put("inventory", inventory.copy());
        return saved;
    }
    public static boolean matches(CompoundTag saved, String room, long match) {
        return saved != null && saved.getString("room").equals(room) && saved.getLong("match") == match
            && saved.contains("inventory", Tag.TAG_LIST);
    }
    public static ListTag inventoryFor(CompoundTag saved, String room, long match) {
        return matches(saved, room, match) ? saved.getList("inventory", Tag.TAG_COMPOUND).copy() : null;
    }
    public static void restore(ServerPlayer player, RunningRuntimeState state, String room) {
        if (player == null || !player.isAlive() || player.isSpectator() || state.isInRespawnFlow(player.getUUID())) return;
        var saved = player.getPersistentData().getCompound(KEY);
        ListTag inventory = inventoryFor(saved, room, state.getActiveMatchInstanceId());
        if (inventory != null) {
            // Preserve live weapon/attachment tags; restore the original inventory allowances around them.
            for (int i = 0; i < inventory.size(); i++) {
                CompoundTag entry = inventory.getCompound(i);
                int slot = entry.getByte("Slot") & 255;
                if (slot >= player.getInventory().items.size()) continue;
                ItemStack baseline = ItemStack.of(entry);
                ItemStack current = player.getInventory().getItem(slot);
                ItemStack weapon = restoredWeapon(current, baseline);
                if (weapon == null) continue;
                CompoundTag replacement = weapon.save(new CompoundTag());
                replacement.putByte("Slot", (byte) slot);
                inventory.set(i, replacement);
            }
            player.stopUsingItem();
            if (ModList.get().isLoaded("tacz")) Tacz.cancelReload(player);
            player.getInventory().load(inventory);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
        player.setHealth(player.getMaxHealth());
    }
    private static ItemStack restoredWeapon(ItemStack current, ItemStack baseline) {
        ItemStack weapon = baseline.copy();
        if (ModList.get().isLoaded("tacz") && Tacz.isGun(baseline)) {
            if (Tacz.sameGun(current, baseline)) weapon = current.copy();
            Tacz.fill(weapon, baseline);
            return weapon;
        }
        if (org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge.isGunItem(baseline)) {
            if (!current.isEmpty() && current.getItem() == baseline.getItem()) weapon = current.copy();
            Sbw.fill(weapon, baseline);
            return weapon;
        }
        return null;
    }
    private static final class Sbw {
        static void fill(ItemStack stack, ItemStack baseline) {
            var data = com.atsuishio.superbwarfare.data.gun.GunData.from(stack);
            var original = com.atsuishio.superbwarfare.data.gun.GunData.from(baseline);
            data.ammo.set(Math.max(0, data.get(com.atsuishio.superbwarfare.data.gun.GunProp.MAGAZINE)));
            data.virtualAmmo.set(original.virtualAmmo.get());
            var reload = data.reload;
            reload.setState(com.atsuishio.superbwarfare.data.gun.value.ReloadState.NOT_RELOADING);
            reload.setStage(0);
            reload.reloadTimer.reset(); reload.prepareTimer.reset(); reload.prepareLoadTimer.reset();
            reload.iterativeLoadTimer.reset(); reload.finishTimer.reset();
            reload.reloadStarter.finish(); reload.singleReloadStarter.finish(); reload.stage3Starter.finish();
        }
    }
    /** Kept isolated so the mod can load without TACZ. */
    private static final class Tacz {
        static boolean isGun(ItemStack stack) { return !stack.isEmpty() && com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null; }
        static boolean sameGun(ItemStack a, ItemStack b) {
            if (a.isEmpty() || a.getItem() != b.getItem()) return false;
            var gun = com.tacz.guns.api.item.IGun.getIGunOrNull(a);
            return gun != null && gun.getGunId(a).equals(com.tacz.guns.api.item.IGun.getIGunOrNull(b).getGunId(b));
        }
        static void fill(ItemStack stack, ItemStack baseline) {
            var gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).ifPresent(index -> {
                gun.setCurrentAmmoCount(stack, Math.max(0, com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(stack, index.getGunData())));
                gun.setBulletInBarrel(stack, index.getGunData().getBolt() != com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT);
            });
            if (gun.useDummyAmmo(stack)) gun.setDummyAmmoAmount(stack,
                Math.max(gun.getDummyAmmoAmount(baseline), gun.hasMaxDummyAmmo(stack) ? gun.getMaxDummyAmmoAmount(stack) : 0));
        }
        static void cancelReload(ServerPlayer player) {
            var operator = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player);
            if (operator != null) operator.cancelReload();
        }
    }
}
