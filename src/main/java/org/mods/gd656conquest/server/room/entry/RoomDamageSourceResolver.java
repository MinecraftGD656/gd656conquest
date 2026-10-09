package org.mods.gd656conquest.server.room.entry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.lang.reflect.Method;

public final class RoomDamageSourceResolver {
    private RoomDamageSourceResolver() {
    }

    public static ServerPlayer resolveAttackingPlayer(DamageSource damageSource, Entity victim) {
        ServerPlayer killer = resolveAttackingPlayer(damageSource);
        if (killer != null) {
            return killer;
        }
        if (victim instanceof LivingEntity living) {
            LivingEntity credit = living.getKillCredit();
            if (credit instanceof ServerPlayer player) {
                return player;
            }
        }
        return null;
    }

    public static ServerPlayer resolveAttackingPlayer(DamageSource damageSource) {
        if (damageSource == null) {
            return null;
        }
        Entity sourceEntity = damageSource.getEntity();
        if (sourceEntity instanceof ServerPlayer player) {
            return player;
        }
        Entity directEntity = damageSource.getDirectEntity();
        if (directEntity instanceof ServerPlayer player) {
            return player;
        }
        if (sourceEntity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        if (directEntity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        ServerPlayer reflectedOwner = resolveOwnerReflectively(sourceEntity);
        if (reflectedOwner != null) {
            return reflectedOwner;
        }
        return resolveOwnerReflectively(directEntity);
    }

    private static ServerPlayer resolveOwnerReflectively(Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            Method getOwnerMethod = entity.getClass().getMethod("getOwner");
            Object owner = getOwnerMethod.invoke(entity);
            return owner instanceof ServerPlayer player ? player : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
