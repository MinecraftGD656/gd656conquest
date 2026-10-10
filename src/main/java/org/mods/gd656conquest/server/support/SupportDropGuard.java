package org.mods.gd656conquest.server.support;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import java.util.UUID;
public final class SupportDropGuard {
    private static final ThreadLocal<UUID> Q=new ThreadLocal<>();
    private SupportDropGuard(){}
    public static boolean isQ(ServerPlayer p){return p.getUUID().equals(Q.get());}
    public static boolean drop(ServerPlayer p,boolean all) {
        if(!p.getAbilities().instabuild && SupportAmmo.isSupportBag(p.getMainHandItem())) {
            var s=SupportRuntime.of(p.server).recipient(p);
            if(all || s==null || !SupportContext.available(p) || p.server.getTickCount()<s.throwUntil) {
                p.containerMenu.broadcastFullState();return false;
            }
        }
        UUID previous=Q.get();Q.set(p.getUUID());
        try{return p.drop(all);}finally{if(previous==null)Q.remove();else Q.set(previous);}
    }
    public static boolean blockClick(ServerPlayer p,ServerboundContainerClickPacket packet) {
        if(p.getAbilities().instabuild)return false;
        int slot=packet.getSlotNum();
        if(slot==-999 && SupportAmmo.isSupportBag(p.containerMenu.getCarried()))return true;
        return packet.getClickType()==ClickType.THROW && slot>=0 && slot<p.containerMenu.slots.size()
            && SupportAmmo.isSupportBag(p.containerMenu.slots.get(slot).getItem());
    }
}
