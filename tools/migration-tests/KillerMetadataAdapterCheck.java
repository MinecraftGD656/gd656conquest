package tests;

import java.util.Optional;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import org.mods.gd656conquest.server.room.entry.FatalHitMetadata;
import org.mods.gd656conquest.server.room.entry.KillWeaponResolver;

/** Actual observer registration and extension/failure isolation, without game or registry fixtures. */
public final class KillerMetadataAdapterCheck {
    static int checks;
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static final class Unrelated extends Event {}
    public static final class BrokenHit extends Event {
        public Object getHurtEntity() { throw new IllegalStateException("optional provider failure"); }
        public Object getBullet() { return null; }
        public boolean isHeadShot() { return true; }
    }
    public static final class WeaponName {
        public String getWeaponName() { return "Original grenade"; }
    }
    public static final class BrokenWeapon {
        public String getGunId() { throw new IllegalStateException("optional getter failure"); }
        public String getWeaponName() { return "Fallback provider name"; }
    }
    public static void main(String[] args) throws Exception {
        var context = new KillWeaponResolver.Context(null, null, null, null);
        var expected = new KillWeaponResolver.Weapon("Extension weapon", "pack:weapon", "pack:skin");
        check(KillWeaponResolver.resolve(context, null).equals(KillWeaponResolver.Weapon.unknown()), "Missing source is unknown, not later held weapon");
        try (var broken = KillWeaponResolver.register(c -> { throw new IllegalStateException("adapter failure"); });
             var empty = KillWeaponResolver.register(c -> Optional.empty());
             var good = KillWeaponResolver.register(c -> Optional.of(expected))) {
            check(KillWeaponResolver.resolve(context, null).equals(expected), "Failing and empty adapters cannot block working extension");
        }
        check(KillWeaponResolver.resolve(context, null).equals(KillWeaponResolver.Weapon.unknown()), "Closed extensions are removed");
        try (var broken = KillWeaponResolver.register(c -> { throw new NoClassDefFoundError("absent optional mod"); })) {
            check(KillWeaponResolver.resolve(context, null).equals(KillWeaponResolver.Weapon.unknown()), "Optional linkage failure does not break death flow");
        }
        check(KillWeaponResolver.extract(new WeaponName(), null).name().equals("Original grenade"), "Public provider name supported without gun-mod dependency");
        check(KillWeaponResolver.extract(new BrokenWeapon(), null).name().equals("Fallback provider name"), "Throwing optional getter permits another metadata capability");
        check(KillWeaponResolver.extract(null, null) == null, "Missing direct capability has no invented weapon");
        var bus = BusBuilder.builder().build();
        bus.register(FatalHitMetadata.class);
        check(!bus.post(new Unrelated()), "Generic observer registers on actual Forge bus and leaves unrelated events intact");
        check(!bus.post(new BrokenHit()), "Reflection failure cannot escape or cancel actual EventBus post");
        bus.unregister(FatalHitMetadata.class);
        System.out.println("PASS actual Forge metadata observer / extension failure isolation: " + checks);
    }
}
