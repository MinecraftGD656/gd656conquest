package org.mods.gd656conquest.server.support;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.mods.gd656conquest.common.entity.*;
import org.mods.gd656conquest.common.item.ConquestItems;
import org.mods.gd656conquest.server.integration.KilliconScoreboardBridge;
import org.mods.gd656conquest.server.room.runtime.RoomDeathSupportDropService;
import org.mods.gd656conquest.server.room.model.*;
import org.mods.gd656conquest.server.facility.support.HeroKitSupportService;
import org.mods.gd656conquest.server.compat.frontlinevoice.FrontlineVoiceBridge;
import org.mods.gd656conquest.server.room.runtime.audio.RoomThirdPersonAudioCueRegistry;

@Mod.EventBusSubscriber(modid="gd656conquest",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SupportRuntime {
    private static final SoundEvent AMMO_SUPPLY_SOUND=SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("gd656conquest","sfx.ammo_bag_pickup"));
    private static final SoundEvent MEDICAL_SUPPLY_SOUND=SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("gd656conquest","sfx.player_heal"));
    private static final Map<MinecraftServer,SupportRuntime> SERVERS=new IdentityHashMap<>();
    private final MinecraftServer server;
    private final Map<UUID,List<ItemStack>> pendingRefunds=new HashMap<>();
    private final Map<UUID,Recipient> recipients=new HashMap<>();
    private final Map<UUID,ItemEntity> bags=new LinkedHashMap<>();
    private SupportRuntime(MinecraftServer server){this.server=server;}
    public static SupportRuntime of(MinecraftServer server){return SERVERS.computeIfAbsent(server,SupportRuntime::new);}
    public static final class Recipient {
        final String room;
        MedicalTreatment treatment;
        final SupportRules.Suppression suppression=new SupportRules.Suppression();
        final Map<String,SupportRules.Progress> progress=new HashMap<>();
        final Map<UUID,Double> shotGains=new HashMap<>();
        final Map<UUID,Long> shotTimes=new HashMap<>();
        long pickupUntil,throwUntil,medicalRangeUntil,weaponUntil;
        long lastDamage=-1;
        long lastAmmoSoundTick=Long.MIN_VALUE;
        long lastMedicalSoundTick=Long.MIN_VALUE;
        int cursor;
        boolean visual;
        Recipient(String room){this.room=room;}
    }
    private static final class MedicalTreatment {
        final UUID owner; final String room,camp; long next;
        MedicalTreatment(UUID owner,String room,String camp,long next){this.owner=owner;this.room=room;this.camp=camp;this.next=next;}
    }
    public Recipient recipient(ServerPlayer p) {
        var c=SupportContext.of(p); if(c==null)return null;
        var old=recipients.get(p.getUUID());
        if(old!=null && !old.room.equals(c.room().roomId()))reset(p);
        return recipients.computeIfAbsent(p.getUUID(),id->new Recipient(c.room().roomId()));
    }
    public static void reset(ServerPlayer p) {
        if(p==null || p.server==null)return;
        var rt=SERVERS.get(p.server);if(rt==null)return;
        var old=rt.recipients.remove(p.getUUID());
        if(old!=null && old.visual)SuppressionNetwork.send(p,false,.25F);
    }
    public static boolean suppressionBlocks(ServerPlayer p) {
        var c=SupportContext.of(p);if(c==null || !c.room().commonConfig().suppressionConfig().enabled())return false;
        var rt=SERVERS.get(p.server);var s=rt==null?null:rt.recipients.get(p.getUUID());
        return s!=null && s.room.equals(c.room().roomId()) && s.suppression.blocksHealing(p.server.getTickCount(),c.room().commonConfig().suppressionConfig());
    }
    public static boolean damageBlocks(ServerPlayer p) {
        var c=SupportContext.of(p);if(c==null)return false;
        var rt=SERVERS.get(p.server);var s=rt==null?null:rt.recipients.get(p.getUUID());
        return s!=null && s.room.equals(c.room().roomId()) && SupportRules.damageBlocksHealing(
            p.server.getTickCount(),s.lastDamage,c.room().commonConfig().medicalBoxDamageCooldownSeconds());
    }
    public static boolean pausePassive(ServerPlayer p) {
        if(p==null || p.server==null)return false;
        var rt=SERVERS.get(p.server);var s=rt==null?null:rt.recipients.get(p.getUUID());
        return suppressionBlocks(p) || (s!=null && (s.treatment!=null || p.server.getTickCount()<s.medicalRangeUntil));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damaged(LivingDamageEvent event) {
        if(!event.isCanceled() && event.getAmount()>0 && event.getEntity() instanceof ServerPlayer p) {
            var s=of(p.server).recipient(p);if(s!=null){s.treatment=null;s.lastDamage=p.server.getTickCount();}
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)reset(p);}
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)reset(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){SERVERS.remove(e.getServer());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        var rt=SERVERS.get(server);if(rt!=null)rt.tick();
    }
    private void tick() {
        long now=server.getTickCount();
        pendingRefunds.entrySet().removeIf(x->{var p=server.getPlayerList().getPlayer(x.getKey());if(p==null)return false;x.getValue().removeIf(stack->p.getInventory().add(stack));return x.getValue().isEmpty();});
        for(var entry:List.copyOf(recipients.entrySet())) {
            ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());
            var s=entry.getValue();var c=SupportContext.of(p);
            if(p==null || !SupportContext.available(p) || c==null || !s.room.equals(c.room().roomId())) {
                if(p!=null)reset(p);else recipients.remove(entry.getKey());continue;
            }
            s.shotTimes.entrySet().removeIf(x->{if(now-x.getValue()>600){s.shotGains.remove(x.getKey());return true;}return false;});
            var cfg=c.room().commonConfig().suppressionConfig();
            boolean visual=cfg.enabled() && s.suppression.active(now,cfg);
            if(visual!=s.visual){s.visual=visual;SuppressionNetwork.send(p,visual,cfg.vignetteOpacity().floatValue());}
            if(s.treatment!=null) {
                var t=s.treatment;
                if(p.getHealth()>=p.getMaxHealth() || !t.room.equals(c.room().roomId())
                    || (!c.room().commonConfig().allowEnemySupportResupply() && !t.camp.equals(c.camp().name())))s.treatment=null;
                else if(now>=t.next) {
                    heal(p,2.5F,t.owner,t.camp);t.next=now+20;
                    if(p.getHealth()>=p.getMaxHealth())s.treatment=null;
                }
            }
        }
        if(now%10==0)bags.entrySet().removeIf(entry->{
            ItemEntity e=entry.getValue();
            if(!e.isAlive())return true;
            if(now-e.getPersistentData().getLong("gd656_support_created")>=600){e.discard();return true;}
            return false;
        });
    }
    public static float heal(ServerPlayer p,float amount,UUID owner,String camp) {
        float before=p.getHealth();p.heal(amount);float actual=Math.max(0,p.getHealth()-before);
        if(actual>0){medicalSupplied(p);award(p,owner,camp,"HEALING",actual/2F);}
        return actual;
    }
    private static void award(ServerPlayer receiver,UUID owner,String camp,String bonus,float amount) {
        if(owner==null || owner.equals(receiver.getUUID()))return;
        var c=SupportContext.of(receiver);
        if(c==null || !c.camp().name().equals(camp))return;
        ServerPlayer giver=receiver.server.getPlayerList().getPlayer(owner);
        var gc=SupportContext.of(giver);
        if(gc!=null && gc.room().roomId().equals(c.room().roomId()) && gc.camp()==c.camp())
            KilliconScoreboardBridge.tryAwardConquestBonus(giver,bonus,amount,"");
    }
    public static InteractionResultHolder<ItemStack> useBag(Level level,Player raw,InteractionHand hand) {
        ItemStack held=raw.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.sidedSuccess(held,true);
        if(!(raw instanceof ServerPlayer p) || !throwBag(p,hand))return InteractionResultHolder.success(held);
        return InteractionResultHolder.sidedSuccess(held,false);
    }
    public static InteractionResult interactBag(ItemStack stack,Player raw,net.minecraft.world.entity.LivingEntity target,InteractionHand hand) {
        if(raw.level().isClientSide)return InteractionResult.SUCCESS;
        return raw instanceof ServerPlayer p && throwBag(p,hand)?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
    private static boolean throwBag(ServerPlayer p,InteractionHand hand) {
        if(!SupportContext.available(p))return false;
        var rt=of(p.server);var s=rt.recipient(p);long now=p.server.getTickCount();
        ItemStack held=p.getItemInHand(hand);if(!SupportAmmo.isSupportBag(held))return false;
        if(!p.getAbilities().instabuild && now<s.throwUntil)return false;
        ItemStack copy=held.copy();copy.setCount(1);
        ItemEntity e=new ItemEntity(p.level(),p.getX(),p.getEyeY()-.2,p.getZ(),copy);
        e.setDeltaMovement(p.getLookAngle().scale(.55).add(p.getDeltaMovement().scale(.15)));
        markBag(e,p);
        if(!p.level().addFreshEntity(e)){rt.bags.remove(e.getUUID());return false;}
        if(!p.getAbilities().instabuild)held.shrink(1);
        if(!p.getAbilities().instabuild)s.throwUntil=now+10;
        SupportAmmo.sync(p);return true;
    }
    public static void markBag(ItemEntity e,ServerPlayer p) {
        SupportContext.stamp(e,p);long now=p.server.getTickCount();
        e.getPersistentData().putLong("gd656_support_created",now);
        e.setPickUpDelay(0);e.lifespan=600;
        e.getItem().getOrCreateTag().putUUID("gd656_support_bag_id",UUID.randomUUID());
        var rt=of(p.server);
        if(!p.getAbilities().instabuild) {
            List<ItemEntity> owned=rt.bags.values().stream().filter(b->b.isAlive() && !RoomDeathSupportDropService.isDeathSupportDrop(b) && b.getItem().getItem()==e.getItem().getItem()
                && b.getPersistentData().hasUUID("gd656_support_owner") && b.getPersistentData().getUUID("gd656_support_owner").equals(p.getUUID())).toList();
            if(owned.size()>=3){owned.get(0).discard();rt.bags.remove(owned.get(0).getUUID());}
            FrontlineVoiceBridge.playCue(p,e.getItem().getItem()==ConquestItems.AMMO_BAG.get()
                ?RoomThirdPersonAudioCueRegistry.PLAYER_THROW_AMMO:RoomThirdPersonAudioCueRegistry.PLAYER_THROW_MEDKIT);
        }
        rt.bags.put(e.getUUID(),e);
    }
    /** Return true for every support bag, including creative vanilla pickup, to skip legacy auto-use. */
    public static boolean pickup(EntityItemPickupEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return false;
        ItemEntity e=event.getItem();ItemStack stack=e.getItem();if(!SupportAmmo.isSupportBag(stack))return false;
        if(event.isCanceled())return true;
        if(RoomDeathSupportDropService.isPickupBlocked(p,e)){event.setCanceled(true);return true;}
        var d=e.getPersistentData();
        boolean reward=RoomDeathSupportDropService.isDeathSupportDrop(e);
        UUID owner=d.hasUUID("gd656_support_owner")?d.getUUID("gd656_support_owner"):null;
        long now=p.server.getTickCount();
        // The thrower's pickup grace period applies before creative inventory pickup too.
        if(!reward && p.getUUID().equals(owner) && now-d.getLong("gd656_support_created")<10) {
            event.setCanceled(true);return true;
        }
        if(p.getAbilities().instabuild) {
            if(stack.getTag()!=null)stack.getTag().remove("gd656_support_bag_id");
            return true;
        }
        event.setCanceled(true);
        if(!SupportContext.available(p))return true;
        var rt=of(p.server);var s=rt.recipient(p);
        if(now<s.pickupUntil)return true;
        if(reward && !d.getString("gd656_support_room").isEmpty()
            && !d.getString("gd656_support_room").equals(s.room))return true;
        if(!reward && !SupportContext.allowed(p,e,owner,false))return true;
        var c=SupportContext.of(p);String camp=reward?"":d.getString("gd656_support_camp");
        if(stack.getItem()==ConquestItems.MEDIC_BAG.get()) {
            if(s.treatment!=null || p.getHealth()>=p.getMaxHealth())return true;
            s.treatment=new MedicalTreatment(reward?null:owner,c.room().roomId(),reward?c.camp().name():camp,now+20);
            if(heal(p,2.5F,reward?null:owner,camp)<=0){s.treatment=null;return true;}
            if(p.getHealth()>=p.getMaxHealth())s.treatment=null;
        } else {
            if(SupportAmmo.bag(p)<=0)return true;
            award(p,reward?null:owner,camp,"AMMO_SUPPLY",1);
        }
        stack.shrink(1);s.pickupUntil=now+20;
        if(stack.isEmpty()){e.discard();rt.bags.remove(e.getUUID());}
        else e.setItem(stack);
        return true;
    }
    public static boolean toss(ItemTossEvent event) {
        if(!(event.getPlayer() instanceof ServerPlayer p) || !SupportAmmo.isSupportBag(event.getEntity().getItem()))return false;
        if(event.isCanceled())return true;
        var rt=of(p.server);var s=rt.recipient(p);long now=p.server.getTickCount();
        if(!p.getAbilities().instabuild && (!SupportContext.available(p) || s==null || now<s.throwUntil || !SupportDropGuard.isQ(p) || event.getEntity().getItem().getCount()!=1)) {
            event.setCanceled(true);ItemStack restore=event.getEntity().getItem().copy();
            if(!p.getInventory().add(restore) && !restore.isEmpty()) {
                if(p.containerMenu.getCarried().isEmpty())p.containerMenu.setCarried(restore);
                else rt.pendingRefunds.computeIfAbsent(p.getUUID(),id->new ArrayList<>()).add(restore);
            }
            event.getEntity().discard();SupportAmmo.sync(p);return true;
        }
        markBag(event.getEntity(),p);
        if(!p.getAbilities().instabuild)s.throwUntil=now+10;
        return true;
    }
    public static void prepareLoadoutStack(ItemStack stack) {
        if(SupportAmmo.isSupportBag(stack))stack.setCount(Math.min(2,stack.getMaxStackSize()));
    }
    public static void markDeathReward(Entity raw,ServerPlayer victim) {
        if(!(raw instanceof ItemEntity e) || e.level().isClientSide)return;
        var server=e.level().getServer();if(server==null)return;
        SupportContext.stamp(e,victim);
        e.lifespan=600;
        e.getPersistentData().putLong("gd656_support_created",server.getTickCount());
        e.getItem().getOrCreateTag().putUUID("gd656_support_bag_id",UUID.randomUUID());
        of(server).bags.put(e.getUUID(),e);
    }
    public static void initializeBox(Entity box,UUID owner,Object context) {
        if(context instanceof RoomDefinition room)box.getPersistentData().putString("gd656_support_room",room.roomId());
        else if(context instanceof ItemEntity item) {
            var from=item.getPersistentData();var to=box.getPersistentData();
            to.putString("gd656_support_room",from.getString("gd656_support_room"));
            to.putString("gd656_support_camp",from.getString("gd656_support_camp"));
            if(owner!=null)to.putUUID("gd656_support_owner",owner);
        }
        if(box instanceof MedicalBoxEntity)box.getPersistentData().putFloat("gd656_medical_pool",100);
    }
    public static void stampBoxThrow(Entity boxItem,ServerPlayer p){SupportContext.stamp(boxItem,p);}
    public static void boxes(MinecraftServer server,Set<UUID> ids,long now,boolean medical) {
        if(now%10!=0)return;
        var rt=of(server);Map<UUID,Entity> nearest=new HashMap<>();
        for(UUID id:List.copyOf(ids)) {
            Entity box=null;for(var level:server.getAllLevels()){box=level.getEntity(id);if(box!=null)break;}
            if(box==null || !box.isAlive()){ids.remove(id);continue;}
            UUID owner=medical?((MedicalBoxEntity)box).getOwnerUuid():((AmmoBoxEntity)box).getOwnerUuid();
            boolean permanent=medical?((MedicalBoxEntity)box).isPermanentSupport():((AmmoBoxEntity)box).isPermanentSupport();
            int capacity=medical?((MedicalBoxEntity)box).getUseRemaining():((AmmoBoxEntity)box).getUseRemaining();
            if(capacity<=0){box.discard();ids.remove(id);continue;}
            final Entity source=box;
            for(ServerPlayer p:box.level().getEntitiesOfClass(ServerPlayer.class,box.getBoundingBox().inflate(3),SupportContext::available)) {
                if(p.distanceToSqr(box)>9 || !SupportContext.allowed(p,box,owner,permanent))continue;
                rt.recipient(p);
                Entity old=nearest.get(p.getUUID());
                if(old==null || p.distanceToSqr(box)<p.distanceToSqr(old))nearest.put(p.getUUID(),box);
            }
        }
        if(medical)for(var entry:nearest.entrySet()) {
            var p=server.getPlayerList().getPlayer(entry.getKey());var s=rt.recipient(p);
            if(damageBlocks(p) || suppressionBlocks(p) || p.getHealth()>=p.getMaxHealth())continue;
            var box=(MedicalBoxEntity)entry.getValue();var d=box.getPersistentData();
            float pool=box.isPermanentSupport()?Float.MAX_VALUE:d.getFloat("gd656_medical_pool");
            float actual=heal(p,Math.min(1,pool),box.getOwnerUuid(),d.getString("gd656_support_camp"));
            if(actual>0){s.medicalRangeUntil=now+11;if(!box.isPermanentSupport()) {
                pool=Math.max(0,pool-actual);d.putFloat("gd656_medical_pool",pool);box.setUseRemaining((int)Math.ceil(pool));
                if(pool<=0)box.discard();
            }}
        }
        else for(var entry:List.copyOf(rt.recipients.entrySet())) {
            var p=server.getPlayerList().getPlayer(entry.getKey());if(p==null || !SupportContext.available(p))continue;
            var c=SupportContext.of(p);var s=entry.getValue();
            if(c==null || !s.room.equals(c.room().roomId()))continue;
            var box=(AmmoBoxEntity)nearest.get(entry.getKey());
            boolean eligible=box!=null && !RoomGameplayId.GUNMASTER.equals(c.room().gameplayId()) && HeroKitSupportService.canRefillFromAmmoBox(c.state(),p.getUUID());
            var all=SupportAmmo.targets(p);List<SupportAmmo.Target> guns=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(var t:all) {
                if(!t.slow){guns.add(t);continue;}
                String key=t.key();seen.add(key);
                var progress=s.progress.computeIfAbsent(key,k->new SupportRules.Progress());
                if(progress.ready(now,t.missing(p),eligible,t.interval()) && (box.isPermanentSupport() || box.getUseRemaining()>0)) {
                    if(t.add(p,1)>0){progress.supplied();debitAmmo(box,p);}
                }
            }
            s.progress.keySet().retainAll(seen);
            if(!eligible || now<s.weaponUntil || guns.isEmpty())continue;
            for(int n=0;n<guns.size();n++) {
                int index=Math.floorMod(s.cursor+n,guns.size());var t=guns.get(index);
                if((box.isPermanentSupport() || box.getUseRemaining()>0) && t.add(p,SupportRules.ammoBurst(t.magazine(),false))>0) {
                    s.cursor=(index+1)%guns.size();s.weaponUntil=now+20;debitAmmo(box,p);break;
                }
            }
        }
    }
    private static void debitAmmo(AmmoBoxEntity box,ServerPlayer p) {
        if(!box.isPermanentSupport()){box.setUseRemaining(Math.max(0,box.getUseRemaining()-1));if(box.getUseRemaining()<=0)box.discard();}
        SupportAmmo.sync(p);ammoSupplied(p);award(p,box.getOwnerUuid(),box.getPersistentData().getString("gd656_support_camp"),"AMMO_SUPPLY",1);
    }
    /** Only successful transactions call this; simultaneous equipment refills share one cue. */
    public static void ammoSupplied(ServerPlayer p) {
        var s=of(p.server).recipient(p);if(s==null)return;
        long now=p.server.getTickCount();if(s.lastAmmoSoundTick==now)return;
        s.lastAmmoSoundTick=now;
        p.playNotifySound(AMMO_SUPPLY_SOUND,SoundSource.PLAYERS,1.0F,1.0F);
    }
    /** Medical boxes and bags request a cue only after HP has actually increased. */
    private static void medicalSupplied(ServerPlayer p) {
        var s=of(p.server).recipient(p);if(s==null)return;
        long now=p.server.getTickCount();if(s.lastMedicalSoundTick==now)return;
        s.lastMedicalSoundTick=now;
        p.playNotifySound(MEDICAL_SUPPLY_SOUND,SoundSource.PLAYERS,1.0F,1.0F);
    }
    /** Only support bags recharge independently; other consumables require a nearby ammo box. */
    public static boolean recharge(ServerPlayer p,org.mods.gd656conquest.server.room.runtime.RoomPlayerLoadoutService.ResolvedLoadoutItem item,long now,Map<Integer,Long> last) {
        if(item==null || !SupportAmmo.isSupportBag(item.configuredStack()))return false;
        ItemStack current=p.getInventory().getItem(item.hotbarSlot());
        if(!current.isEmpty() && !ItemStack.isSameItemSameTags(current,item.configuredStack()))return false;
        int key=item.slot().getIndex();int count=current.isEmpty()?0:current.getCount();
        if(count>=2){last.put(key,now);return false;}
        long start=last.computeIfAbsent(key,k->now);if(now-start<120)return false;
        ItemStack next=item.configuredStack().copy();next.setCount(count+1);
        p.getInventory().setItem(item.hotbarSlot(),next);last.put(key,now);return true;
    }
}
