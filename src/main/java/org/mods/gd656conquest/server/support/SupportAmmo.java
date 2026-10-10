package org.mods.gd656conquest.server.support;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.mods.gd656conquest.server.room.runtime.*;
import org.mods.gd656conquest.server.loadout.domain.CustomLoadoutSlot;
import org.mods.gd656conquest.server.compat.superbwarfare.SuperbWarfareCompatBridge;
import org.mods.gd656conquest.common.item.ConquestItems;

/** Reserve-only transactions: never regrant a loadout or modify ammunition already loaded. */
public final class SupportAmmo {
    private SupportAmmo() {}
    public static List<Target> targets(ServerPlayer p) {
        var loadout=new RoomPlayerLoadoutService(p.server);
        var grant=new RoomLoadoutGrantService(p.server);
        var resolved=loadout.resolveConfiguredLoadoutItems(p);
        List<Target> list=new ArrayList<>();
        for(var slot:CustomLoadoutSlot.values()) {
            var i=resolved.get(slot);
            if(i==null || i.entry()==null || !i.entry().isAmmoBoxRefillEnabled() || i.hotbarSlot()<0)continue;
            ItemStack actual=p.getInventory().getItem(i.hotbarSlot());
            ItemStack expected=i.configuredStack();
            if(expected==null || expected.isEmpty())continue;
            boolean gadget=slot==CustomLoadoutSlot.GADGET_1 || slot==CustomLoadoutSlot.GADGET_2;
            boolean throwable=slot==CustomLoadoutSlot.THROWABLE;
            if(loadout.isAmmoManagedGunItem(expected)) {
                if(actual.isEmpty() || !sameGun(actual,expected))continue;
                var meta=grant.resolveTaczAmmoMeta(actual);
                String pool=meta!=null ? "tacz:"+meta.ammoId() : "sbw:"+i.hotbarSlot();
                list.add(new Target(i,actual,meta,SupportAmmoGrant.cap(actual,meta,i.entry().getExtraData()),pool,gadget||throwable));
            } else if((gadget||throwable) && !isSupportBag(expected)
                && expected.getItem()!=ConquestItems.AMMO_BOX.get() && expected.getItem()!=ConquestItems.MED_BOX.get()) {
                list.add(new Target(i,actual,null,Math.max(1,expected.getCount()),"item:"+i.hotbarSlot(),true));
            }
        }
        // A shared physical ammo pool has one reserve count and the sum of deployed allowances.
        Map<String,Integer> caps=new HashMap<>();
        for(var t:list)if(t.meta!=null)caps.merge(t.pool,t.cap,(a,b)->(int)Math.min(Integer.MAX_VALUE,(long)a+b));
        for(var t:list)if(t.meta!=null)t.cap=caps.get(t.pool);
        return list;
    }
    public static boolean isSupportBag(ItemStack s) {
        return s!=null && !s.isEmpty() && (s.getItem()==ConquestItems.AMMO_BAG.get() || s.getItem()==ConquestItems.MEDIC_BAG.get());
    }
    private static boolean sameGun(ItemStack a,ItemStack b) {
        if(a.getItem()!=b.getItem())return false;
        return !ModList.get().isLoaded("tacz") || Tacz.same(a,b);
    }
    public static int bag(ServerPlayer p) {
        var c=SupportContext.of(p);
        if(c==null || org.mods.gd656conquest.server.room.model.RoomGameplayId.GUNMASTER.equals(c.room().gameplayId())
            || !org.mods.gd656conquest.server.facility.support.HeroKitSupportService.canRefillFromAmmoBox(c.state(),p.getUUID()))return 0;
        List<Target> all=targets(p);
        List<Target> guns=new ArrayList<>();
        for(var t:all)if(!t.slow)guns.add(t);
        int held=p.getInventory().selected;
        guns.sort(Comparator.comparingInt(t->t.item.hotbarSlot()==held ? -1 : t.item.hotbarSlot()));
        for(var t:guns) {
            int added=t.add(p,SupportRules.ammoBurst(t.magazine(),true));
            if(added>0){sync(p);SupportRuntime.ammoSupplied(p);return added;}
        }
        return 0;
    }
    public static void sync(ServerPlayer p) { p.getInventory().setChanged(); p.containerMenu.broadcastChanges(); }
    public static final class Target {
        public final RoomPlayerLoadoutService.ResolvedLoadoutItem item;
        private final ItemStack stack;
        private final RoomLoadoutGrantService.TaczAmmoMeta meta;
        private int cap;
        public final String pool;
        public final boolean slow;
        Target(RoomPlayerLoadoutService.ResolvedLoadoutItem item,ItemStack stack,RoomLoadoutGrantService.TaczAmmoMeta meta,int cap,String pool,boolean slow) {
            this.item=item;this.stack=stack;this.meta=meta;this.cap=cap;this.pool=pool;this.slow=slow;
        }
        public String key() { return item.slot().name()+":"+item.entry().getId()+":"+pool; }
        public long interval() {
            if(item.slot()==CustomLoadoutSlot.THROWABLE) {
                int configured=item.entry().getReplenishSeconds();
                return (configured>0?configured:15)*20L;
            }
            return 100;
        }
        public int reserve(ServerPlayer p) {
            if(meta!=null) {
                ItemStack sample=new RoomLoadoutGrantService(p.server).createTaczAmmoStack(meta.ammoId(),1);
                int count=0;
                for(var s:p.getInventory().items)if(ItemStack.isSameItemSameTags(s,sample))count+=s.getCount();
                return count;
            }
            if(SuperbWarfareCompatBridge.isGunItem(stack))return SuperbWarfareCompatBridge.getReserveAmmo(stack);
            return ItemStack.isSameItemSameTags(stack,item.configuredStack())?stack.getCount():0;
        }
        public boolean missing(ServerPlayer p) { return reserve(p)<cap && cap>0; }
        public int magazine() {
            if(meta!=null && ModList.get().isLoaded("tacz"))return Tacz.magazine(stack);
            if(SuperbWarfareCompatBridge.isGunItem(stack)) {
                try {
                    var cls=Class.forName("com.atsuishio.superbwarfare.data.gun.GunData");
                    Object data=cls.getMethod("from",ItemStack.class).invoke(null,stack);
                    Object value=cls.getMethod("get",Class.forName("com.atsuishio.superbwarfare.data.gun.GunProp")).invoke(data,
                        Class.forName("com.atsuishio.superbwarfare.data.gun.GunProp").getField("MAGAZINE").get(null));
                    return Math.max(1,((Number)value).intValue());
                }catch(ReflectiveOperationException | IllegalArgumentException e){return 1;}
            }
            return 1;
        }
        public int add(ServerPlayer p,int wanted) {
            int amount=Math.min(wanted,Math.max(0,cap-reserve(p)));
            if(amount<=0)return 0;
            if(meta!=null) {
                int[] slots=item.slot()==CustomLoadoutSlot.PRIMARY_WEAPON?range(18,26)
                    :item.slot()==CustomLoadoutSlot.SECONDARY_WEAPON?range(29,35):new int[]{31+Math.max(0,item.slot().getIndex()-3)};
                return new RoomLoadoutGrantService(p.server).addTaczAmmoToPreferredSlots(p,meta,null,amount,slots);
            }
            if(SuperbWarfareCompatBridge.isGunItem(stack))
                return SuperbWarfareCompatBridge.applyReserveAmmo(stack,reserve(p)+amount)?amount:0;
            ItemStack current=p.getInventory().getItem(item.hotbarSlot());
            if(!current.isEmpty() && !ItemStack.isSameItemSameTags(current,item.configuredStack()))return 0;
            int n=Math.min(amount,item.configuredStack().getMaxStackSize()-reserve(p));
            if(n<=0)return 0;
            ItemStack copy=item.configuredStack().copy();copy.setCount(reserve(p)+n);
            p.getInventory().setItem(item.hotbarSlot(),copy);return n;
        }
    }
    private static int[] range(int start,int end) { return java.util.stream.IntStream.rangeClosed(start,end).toArray(); }
    /** Isolated to allow the conquest mod to start when TACZ is absent. */
    private static final class Tacz {
        static boolean same(ItemStack a,ItemStack b) {
            var ga=com.tacz.guns.api.item.IGun.getIGunOrNull(a);var gb=com.tacz.guns.api.item.IGun.getIGunOrNull(b);
            return ga==null || gb==null || ga.getGunId(a).equals(gb.getGunId(b));
        }
        static int magazine(ItemStack s) {
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(s);
            if(gun==null)return 1;
            return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(s))
                .map(i->Math.max(1,com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(s,i.getGunData()))).orElse(1);
        }
    }
}
