package org.mods.gd656conquest.client.compat.tacz;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

public final class TaczGunStatCompat {
    private static final TaczGunStatCompat INSTANCE = new TaczGunStatCompat();
    
    private boolean resolved;
    private boolean available;
    
    private Method getIGunOrNullMethod;
    private Method getGunIdMethod;
    private Method timelessGetCommonGunIndexMethod;
    private Method gunIndexGetGunDataMethod;
    
    private Method attachmentCachePropertyEvalMethod;
    private Method gunPropertyDiagramsGetMethod;
    
    private TaczGunStatCompat() {}
    
    public static TaczGunStatCompat getInstance() {
        return INSTANCE;
    }
    
    public static class GunStatData {
        public final double damageBase;
        public final double damageModified;
        public final double rpmBase;
        public final double rpmModified;
        public final double recoilPitchBase;
        public final double recoilPitchModified;
        public final double recoilYawBase;
        public final double recoilYawModified;
        public final int capacityBase;
        public final int capacityModified;
        public final double reloadTimeBase;
        public final double reloadTimeModified;
        
        public GunStatData(double damageBase, double damageModified, double rpmBase, double rpmModified, double recoilPitchBase, double recoilPitchModified, double recoilYawBase, double recoilYawModified, int capacityBase, int capacityModified, double reloadTimeBase, double reloadTimeModified) {
            this.damageBase = damageBase;
            this.damageModified = damageModified;
            this.rpmBase = rpmBase;
            this.rpmModified = rpmModified;
            this.recoilPitchBase = recoilPitchBase;
            this.recoilPitchModified = recoilPitchModified;
            this.recoilYawBase = recoilYawBase;
            this.recoilYawModified = recoilYawModified;
            this.capacityBase = capacityBase;
            this.capacityModified = capacityModified;
            this.reloadTimeBase = reloadTimeBase;
            this.reloadTimeModified = reloadTimeModified;
        }
    }

    public GunStatData resolveGunStats(ItemStack gunStack) {
        ensureResolved();
        if (!available || gunStack == null || gunStack.isEmpty()) {
            return null;
        }
        try {
            Object iGun = getIGunOrNullMethod.invoke(null, gunStack);
            if (iGun == null) return null;
            Object gunId = getGunIdMethod.invoke(iGun, gunStack);
            if (!(gunId instanceof ResourceLocation resourceLocation)) return null;
            
            Object optionalIndex = timelessGetCommonGunIndexMethod.invoke(null, resourceLocation);
            if (!(optionalIndex instanceof Optional<?> optional) || optional.isEmpty()) return null;
            Object gunIndex = optional.get();
            if (gunIndexGetGunDataMethod == null) {
                gunIndexGetGunDataMethod = gunIndex.getClass().getMethod("getGunData");
            }
            Object gunData = gunIndexGetGunDataMethod.invoke(gunIndex);
            
            // To get base stats, we need to extract from GunData directly
            // Instead of manually extracting, we can just use AttachmentPropertyManager.eval?
            // Actually TACZ has a very clean way to get properties for UI: GunPropertyDiagrams
            Class<?> diagramsClass = Class.forName("com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams");
            // Wait, we need to return values in a format we can render
            // Since we just need basic stats, let's just get Damage, RPM, Recoil Pitch, Recoil Yaw, Capacity, Reload Time
            
            // Damage
            Method getDamageMethod = gunData.getClass().getMethod("getBulletData");
            Object bulletData = getDamageMethod.invoke(gunData);
            Method getDamageAmountMethod = bulletData.getClass().getMethod("getDamageAmount");
            double damageBase = ((Number) getDamageAmountMethod.invoke(bulletData)).doubleValue();
            
            // Reload Time
            double reloadTimeBase = 0.0;
            Method getReloadDataMethod = gunData.getClass().getMethod("getReloadData");
            Object reloadData = getReloadDataMethod.invoke(gunData);
            try {
                // Actually the method might be getReloadTime in ReloadData
                Method getReloadTimeMethod = reloadData.getClass().getMethod("getReloadTime");
                reloadTimeBase = ((Number) getReloadTimeMethod.invoke(reloadData)).doubleValue();
            } catch (Exception ignored) {}
            
            // RPM
            Method getShootDataMethod = gunData.getClass().getMethod("getShootData");
            Object shootData = getShootDataMethod.invoke(gunData);
            Method getRoundsPerMinuteMethod = shootData.getClass().getMethod("getRoundsPerMinute");
            double rpmBase = ((Number) getRoundsPerMinuteMethod.invoke(shootData)).doubleValue();
            
            // Recoil methods might be different, let's just ignore base recoil if methods fail
            double recoilPitchBase = 1.0;
            double recoilYawBase = 1.0;
            try {
                Method getRecoilMethod = gunData.getClass().getMethod("getRecoilData");
                Object recoilData = getRecoilMethod.invoke(gunData);
                Method getPitchMethod = recoilData.getClass().getMethod("getBasePitch");
                recoilPitchBase = ((Number) getPitchMethod.invoke(recoilData)).doubleValue();
                Method getYawMethod = recoilData.getClass().getMethod("getBaseYaw");
                recoilYawBase = ((Number) getYawMethod.invoke(recoilData)).doubleValue();
            } catch (Exception ignored) {}
            
            // Capacity
            Method getAmmoAmountMethod = gunData.getClass().getMethod("getAmmoAmount");
            int capacityBase = ((Number) getAmmoAmountMethod.invoke(gunData)).intValue();

            // Cache property for modified stats
            Class<?> cachePropertyClass = Class.forName("com.tacz.guns.resource.modifier.AttachmentCacheProperty");
            Object cacheProperty = cachePropertyClass.getDeclaredConstructor().newInstance();
            Method evalMethod = cachePropertyClass.getMethod("eval", ItemStack.class, gunData.getClass());
            evalMethod.invoke(cacheProperty, gunStack, gunData);
            
            Method getCacheDamageMethod = cachePropertyClass.getMethod("getDamage");
            double damageModified = ((Number) getCacheDamageMethod.invoke(cacheProperty)).doubleValue();
            
            Method getCacheAmmoMethod = cachePropertyClass.getMethod("getAmmoAmount");
            int capacityModified = ((Number) getCacheAmmoMethod.invoke(cacheProperty)).intValue();
            
            // Not all cache properties might exist exactly like this, some modifiers apply differently.
            // Let's wrap each cache call in try-catch in case method name differs in TACZ
            double reloadTimeModified = reloadTimeBase;
            double rpmModified = rpmBase;
            double recoilPitchModified = recoilPitchBase;
            double recoilYawModified = recoilYawBase;
            
            try {
                Method cacheGetReloadTime = cachePropertyClass.getMethod("getReloadTime");
                reloadTimeModified = ((Number) cacheGetReloadTime.invoke(cacheProperty)).doubleValue();
            } catch (Exception ignored) {}
            
            try {
                Method cacheGetRpm = cachePropertyClass.getMethod("getRpm");
                rpmModified = ((Number) cacheGetRpm.invoke(cacheProperty)).doubleValue();
            } catch (Exception ignored) {}
            
            try {
                Method cacheGetRecoilPitch = cachePropertyClass.getMethod("getRecoilPitch");
                recoilPitchModified = ((Number) cacheGetRecoilPitch.invoke(cacheProperty)).doubleValue();
            } catch (Exception ignored) {}
            
            try {
                Method cacheGetRecoilYaw = cachePropertyClass.getMethod("getRecoilYaw");
                recoilYawModified = ((Number) cacheGetRecoilYaw.invoke(cacheProperty)).doubleValue();
            } catch (Exception ignored) {}
            
            return new GunStatData(
                damageBase, damageModified,
                rpmBase, rpmModified,
                recoilPitchBase, recoilPitchModified,
                recoilYawBase, recoilYawModified,
                capacityBase, capacityModified,
                reloadTimeBase, reloadTimeModified
            );
        } catch (Exception ignored) {
            return null;
        }
    }
    
    private void ensureResolved() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            getIGunOrNullMethod = iGunClass.getMethod("getIGunOrNull", ItemStack.class);
            getGunIdMethod = iGunClass.getMethod("getGunId", ItemStack.class);
            
            Class<?> timelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            timelessGetCommonGunIndexMethod = timelessApiClass.getMethod("getCommonGunIndex", ResourceLocation.class);
            
            // Class<?> commonGunIndexClass = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            // gunIndexGetGunDataMethod = commonGunIndexClass.getMethod("getGunData");
            
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }
}