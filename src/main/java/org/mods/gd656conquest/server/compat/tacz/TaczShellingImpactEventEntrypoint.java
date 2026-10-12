package org.mods.gd656conquest.server.compat.tacz;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;

public final class TaczShellingImpactEventEntrypoint {
    private static final String TACZ_AMMO_HIT_BLOCK_EVENT_CLASS = "com.tacz.guns.api.event.server.AmmoHitBlockEvent";
    private static final String TACZ_AMMO_HIT_ENTITY_EVENT_CLASS = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post";
    private static final String TACZ_AMMO_HIT_ENTITY_EVENT_CLASS_ALT = "com.tacz.guns.api.event.common.EntityHurtByGunEvent";

    private static volatile boolean methodsResolved;
    private static volatile Method hitBlockGetAmmoMethod;
    private static volatile Method hitEntityGetBulletMethod;
    private static volatile Method hitEntityGetLogicalSideMethod;

    @SubscribeEvent
    public void onForgeEvent(Event event) {
        if (event == null) {
            return;
        }
        String className = event.getClass().getName();
        if(className.equals("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre")) {
            try {
                Object bullet=event.getClass().getMethod("getBullet").invoke(event);
                if(bullet instanceof Entity entity&&event.isCancelable()&&ShellingPresentationProtection.harmless(entity))event.setCanceled(true);
            } catch(ReflectiveOperationException error) {com.mojang.logging.LogUtils.getLogger().warn("Unable to inspect shelling gun damage",error);}
            return;
        }
        if (TACZ_AMMO_HIT_BLOCK_EVENT_CLASS.equals(className)) {
            handleHitBlock(event);
        } else if (TACZ_AMMO_HIT_ENTITY_EVENT_CLASS.equals(className) || TACZ_AMMO_HIT_ENTITY_EVENT_CLASS_ALT.equals(className)) {
            handleHitEntity(event);
        }
    }

    private void handleHitBlock(Event event) {
        ensureMethodsResolved(event.getClass(), null);
        if (hitBlockGetAmmoMethod != null) {
            try {
                Object ammo = hitBlockGetAmmoMethod.invoke(event);
                if (ammo instanceof Entity entity) {
                    dispatchImpact(entity);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void handleHitEntity(Event event) {
        ensureMethodsResolved(null, event.getClass());
        if (hitEntityGetLogicalSideMethod != null) {
            try {
                Object logicalSide = hitEntityGetLogicalSideMethod.invoke(event);
                if (logicalSide != null && !"SERVER".equalsIgnoreCase(String.valueOf(logicalSide))) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        if (hitEntityGetBulletMethod != null) {
            try {
                Object bullet = hitEntityGetBulletMethod.invoke(event);
                if (bullet instanceof Entity entity) {
                    dispatchImpact(entity);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void dispatchImpact(Entity projectile) {
        if (projectile == null || projectile.level() == null || projectile.level().getServer() == null) {
            return;
        }
        TaczShellingTestService.of(projectile.level().getServer()).onTaczProjectileImpact(projectile);
    }

    private static void ensureMethodsResolved(Class<?> blockEventClass, Class<?> entityEventClass) {
        if (methodsResolved) {
            return;
        }
        synchronized (TaczShellingImpactEventEntrypoint.class) {
            if (methodsResolved) {
                return;
            }
            if (blockEventClass != null && hitBlockGetAmmoMethod == null) {
                try {
                    hitBlockGetAmmoMethod = blockEventClass.getMethod("getAmmo");
                } catch (Exception ignored) {
                }
            }
            if (entityEventClass != null && hitEntityGetBulletMethod == null) {
                try {
                    hitEntityGetBulletMethod = entityEventClass.getMethod("getBullet");
                    hitEntityGetLogicalSideMethod = entityEventClass.getMethod("getLogicalSide");
                } catch (Exception ignored) {
                }
            }
            if (hitBlockGetAmmoMethod != null && hitEntityGetBulletMethod != null) {
                methodsResolved = true;
            }
        }
    }
}