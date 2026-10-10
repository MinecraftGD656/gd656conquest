package tests;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.mods.gd656conquest.server.support.SupportAmmoGrant;

/** Regression cases for finite slots, increased stacks, partial grants and deployed ceilings. */
public final class AmmoGrantTest {
    static int checks;
    static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    static class Inventory implements SupportAmmoGrant.Slots {
        final int[] counts, limits;
        final List<String> writes = new ArrayList<>();
        Inventory(int size, int limit) {
            counts = new int[size]; limits = new int[size]; Arrays.fill(limits, limit);
        }
        public int count(int slot) { return slot < 0 || slot >= counts.length ? 0 : counts[slot]; }
        public int capacity(int slot) { return slot < 0 || slot >= counts.length ? 0 : limits[slot]; }
        public int add(int slot, int amount) {
            int actual = Math.min(amount, Math.max(0, limits[slot] - counts[slot]));
            counts[slot] += actual; writes.add(slot + ":" + actual); return actual;
        }
    }
    static int[] slots(int count) { return java.util.stream.IntStream.range(0, count).toArray(); }
    static ClassNode type(String name) throws Exception {
        try (var in = AmmoGrantTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            var node = new ClassNode(); new ClassReader(in.readAllBytes()).accept(node, 0); return node;
        }
    }
    static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    static long calls(MethodNode m, String owner, String name) {
        return Arrays.stream(m.instructions.toArray()).filter(i -> i instanceof MethodInsnNode c
            && c.owner.endsWith(owner) && c.name.equals(name)).count();
    }
    static int ceiling(CompoundTag tag, String pool, int configured) throws Exception {
        var read = SupportAmmoGrant.class.getDeclaredMethod("readCap", CompoundTag.class, String.class, int.class);
        read.setAccessible(true); return (Integer)read.invoke(null, tag, pool, configured);
    }
    public static void main(String[] args) throws Exception {
        var expanded = new Inventory(10, 10000);
        check(SupportAmmoGrant.distribute(1000, 64, slots(9), expanded) == 1000, "expanded stacks receive full configured reserve");
        check(expanded.writes.subList(0, 9).equals(List.of("0:64", "1:64", "2:64", "3:64", "4:64", "5:64", "6:64", "7:64", "8:64")), "first pass spreads nine gun-pack groups before filling");
        check(expanded.counts[0] == 488 && expanded.counts[8] == 64, "second pass fills 424 remaining rounds");
        check(expanded.counts[9] == 0, "does not spill into undesignated slots");
        var vanilla = new Inventory(10, 64);
        int actual = SupportAmmoGrant.distribute(1000, 64, slots(9), vanilla);
        check(actual == 576, "full designated slots report 576 actually granted");
        check(vanilla.counts[9] == 0, "insufficient capacity does not overflow to other inventory slots");
        var partial = new Inventory(3, 100);
        partial.counts[0] = 60; partial.limits[1] = 0;
        check(SupportAmmoGrant.distribute(120, 64, slots(3), partial) == 120, "existing ammo and occupied slots handled");
        check(Arrays.equals(partial.counts, new int[]{100, 0, 80}), "existing count retained and blocked slot untouched");
        var small = new Inventory(2, 16);
        check(SupportAmmoGrant.distribute(100, 64, new int[]{-1, 0, 0, 1, 99}, small) == 32, "actual smaller stack limit, duplicate and invalid slots");
        var failed = new Inventory(2, 100) { public int add(int slot, int amount) { return 0; } };
        check(SupportAmmoGrant.distribute(1000, 64, slots(2), failed) == 0, "failed writes never inflate the actual grant ceiling");
        for (int group : new int[]{1, 2, 64}) for (int limit : new int[]{0, 1, 16, 64, 10000})
            for (int wanted : new int[]{0, 1, 4, 63, 64, 65, 576, 1000, 10000, Integer.MAX_VALUE}) {
                var inventory = new Inventory(10, limit);
                int granted = SupportAmmoGrant.distribute(wanted, group, slots(9), inventory);
                check(granted == Math.min(wanted, 9 * limit), "finite grant capacity and large requests");
                check(Arrays.stream(inventory.counts).sum() == granted, "returned ceiling equals actual reserve written");
                check(inventory.counts[9] == 0 && Arrays.stream(inventory.counts).allMatch(n -> n >= 0 && n <= limit), "all writes respect designated slots and real capacity");
            }
        check(SupportAmmoGrant.distribute(100, 64, null, expanded) == 0, "no destination slots grants zero");
        var tag = new CompoundTag();
        tag.m_128405_(SupportAmmoGrant.CAP, 576);
        tag.m_128359_("gd656_support_reserve_pool", "tacz:test:ammo");
        check(ceiling(tag, "tacz:test:ammo", 1000) == 576, "partial initial grant overrides the configured ceiling");
        check(ceiling(TagParser.m_129359_(tag.toString()), "tacz:test:ammo", 1000) == 576, "real Minecraft NBT serialization preserves deployed ceiling");
        check(ceiling(tag, "tacz:test:ammo", 10) == 576, "editing configuration does not change an existing deployed ceiling");
        tag.m_128405_(SupportAmmoGrant.CAP, 1000);
        check(ceiling(tag, "tacz:test:ammo", 576) == 1000, "redeployment replaces an older ceiling with actual new grant");
        tag.m_128405_(SupportAmmoGrant.CAP, 0);
        check(ceiling(tag, "tacz:test:ammo", 1000) == 0, "zero actual grant remains zero and never falls back to configured reserve");
        check(ceiling(null, "tacz:test:ammo", 1000) == 1000, "legacy deployed weapons retain configured fallback until redeployment");
        check(ceiling(tag, "tacz:other:ammo", 1000) == 1000, "a different ammo pool does not inherit another pool's ceiling");
        String root = "org.mods.gd656conquest.server.";
        var grant = method(type(root + "room.runtime.RoomLoadoutGrantService"), "grantConfiguredWeaponAmmoByExtraData");
        check(calls(grant, "SupportAmmoGrant", "grant") == 1 && calls(grant, "RoomLoadoutGrantService", "grantTaczAmmoByExtraData") == 0, "shipped initial grant delegates to two-pass distribution");
        var deploy = method(type(root + "room.runtime.RoomPlayerLoadoutService"), "applyLoadout");
        check(calls(deploy, "SupportAmmoGrant", "finishLoadout") == 1, "final deployment freezes actual pool after all equipment placement");
        var targets = method(type(root + "support.SupportAmmo"), "targets");
        check(calls(targets, "SupportAmmoGrant", "cap") == 1, "box and bag targets read actual deployed ceiling");
        var finish = method(type(root + "support.SupportAmmoGrant"), "finishLoadout");
        check(calls(finish, "Math", "min") == 1, "overlapping designated slots cannot leave an inflated ceiling");
        var cap = method(type(root + "support.SupportAmmoGrant"), "readCap");
        check(calls(cap, "CompoundTag", "m_128451_") == 1, "saved ceiling is read from persistent weapon NBT");
        System.out.println("PASS initial ammo distribution / shipped grant and ceiling hooks: " + checks);
    }
}
