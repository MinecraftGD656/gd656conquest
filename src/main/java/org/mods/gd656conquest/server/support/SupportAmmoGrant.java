package org.mods.gd656conquest.server.support;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.server.room.runtime.RoomLoadoutGrantService;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;

/** Initial reserve distribution and the persistent ceiling for this deployed weapon. */
public final class SupportAmmoGrant {
    public static final String CAP = "gd656_support_reserve_cap";
    private static final String POOL = "gd656_support_reserve_pool";
    private SupportAmmoGrant() {}

    public interface Slots {
        int count(int slot);
        int capacity(int slot);
        int add(int slot, int amount);
    }

    /** Spread gun-pack sized groups first, then fill remaining capacity in the same slots. */
    public static int distribute(int requested, int groupSize, int[] preferred, Slots slots) {
        if (requested <= 0 || groupSize <= 0 || preferred == null) return 0;
        int remaining = requested;
        int[] distinct = Arrays.stream(preferred).distinct().toArray();
        for (boolean secondPass : new boolean[]{false, true}) {
            for (int slot : distinct) {
                if (remaining == 0) return requested;
                int limit = Math.max(0, slots.capacity(slot));
                if (!secondPass) limit = Math.min(limit, groupSize);
                int room = Math.max(0, limit - Math.max(0, slots.count(slot)));
                int wanted = Math.min(remaining, room);
                if (wanted > 0) remaining -= Math.max(0, Math.min(wanted, slots.add(slot, wanted)));
            }
        }
        return requested - remaining;
    }

    public static void grant(RoomLoadoutGrantService service, ServerPlayer player,
                             ItemStack gun, int configured, int[] preferred) {
        if (player == null || gun == null || gun.isEmpty()) return;
        var meta = service.resolveTaczAmmoMeta(gun);
        if (meta == null) {
            if (SuperbWarfareCompatBridge.isGunItem(gun)) {
                if (configured > 0) SuperbWarfareCompatBridge.applyReserveAmmo(gun, configured);
                record(gun, "sbw", configured > 0 ? SuperbWarfareCompatBridge.getReserveAmmo(gun) : 0);
            }
            return;
        }
        ItemStack sample = service.createTaczAmmoStack(meta.ammoId(), 1);
        int added = sample.isEmpty() ? 0 : distribute(configured, meta.stackSize(), preferred, new Slots() {
            private boolean valid(int slot) {
                if (slot < 0 || slot >= player.getInventory().items.size()) return false;
                ItemStack current = player.getInventory().getItem(slot);
                return current.isEmpty() || ItemStack.isSameItemSameTags(current, sample);
            }
            public int count(int slot) {
                if (!valid(slot)) return 0;
                ItemStack current = player.getInventory().getItem(slot);
                return current.isEmpty() ? 0 : current.getCount();
            }
            public int capacity(int slot) {
                if (!valid(slot)) return 0;
                ItemStack current = player.getInventory().getItem(slot);
                return Math.max(0, (current.isEmpty() ? sample : current).getMaxStackSize());
            }
            public int add(int slot, int amount) {
                if (!valid(slot)) return 0;
                int before = count(slot);
                ItemStack current = player.getInventory().getItem(slot);
                if (current.isEmpty()) {
                    current = service.createTaczAmmoStack(meta.ammoId(), amount);
                    if (current.isEmpty()) return 0;
                } else current.grow(amount);
                player.getInventory().setItem(slot, current);
                return Math.max(0, count(slot) - before);
            }
        });
        record(gun, "tacz:" + meta.ammoId(), added);
    }

    private static void record(ItemStack gun, String pool, int actual) {
        gun.getOrCreateTag().putInt(CAP, Math.max(0, actual));
        gun.getOrCreateTag().putString(POOL, pool);
    }

    public static int cap(ItemStack gun, RoomLoadoutGrantService.TaczAmmoMeta meta, int configured) {
        String pool = meta == null ? "sbw" : "tacz:" + meta.ammoId();
        return readCap(gun.getTag(), pool, configured);
    }

    private static int readCap(CompoundTag tag, String pool, int configured) {
        return tag != null && tag.contains(CAP, 3) && pool.equals(tag.getString(POOL))
            ? Math.max(0, tag.getInt(CAP)) : Math.max(0, configured);
    }

    /** Later equipment can overwrite overlapping ammo slots; freeze the final deployed amount. */
    public static void finishLoadout(ServerPlayer player) {
        if (player == null || player.server == null) return;
        Map<String, List<ItemStack>> pools = new LinkedHashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            var tag = stack.getTag();
            if (stack.isEmpty() || tag == null || !tag.contains(CAP, 3)) continue;
            String pool = tag.getString(POOL);
            if (pool.startsWith("tacz:")) pools.computeIfAbsent(pool, k -> new ArrayList<>()).add(stack);
        }
        var service = new RoomLoadoutGrantService(player.server);
        for (var entry : pools.entrySet()) {
            var meta = service.resolveTaczAmmoMeta(entry.getValue().get(0));
            if (meta == null || !entry.getKey().equals("tacz:" + meta.ammoId())) continue;
            ItemStack sample = service.createTaczAmmoStack(meta.ammoId(), 1);
            if (sample.isEmpty()) continue;
            long actual = 0;
            for (ItemStack stack : player.getInventory().items)
                if (ItemStack.isSameItemSameTags(stack, sample)) actual += stack.getCount();
            for (ItemStack gun : entry.getValue()) {
                int allowed = (int)Math.min(actual, cap(gun, meta, 0));
                record(gun, entry.getKey(), allowed);
                actual -= allowed;
            }
        }
    }
}
