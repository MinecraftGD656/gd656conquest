package org.mods.gd656conquest.server.room.model;

import java.util.Locale;

/** Project passive healing and vanilla food healing remain independently configurable. */
public record RoomNaturalRegenConfig(String mode, Boolean passiveEnabled, Boolean vanillaEnabled,
    Integer startDelaySeconds, Integer intervalTicks, Double healAmount) {
    public RoomNaturalRegenConfig {
        boolean legacyDisabled=isLegacyDisabled(mode);
        mode=normalizeMode(mode);
        passiveEnabled=legacyDisabled?false:passiveEnabled==null?true:passiveEnabled;
        vanillaEnabled=legacyDisabled?false:vanillaEnabled==null?false:vanillaEnabled;
        startDelaySeconds=startDelaySeconds==null?6:Math.max(0,startDelaySeconds);
        intervalTicks=intervalTicks==null?10:Math.max(1,intervalTicks);
        healAmount=amount(healAmount);
    }
    public static String normalizeMode(String raw) {
        if(raw==null || raw.isBlank())return "房间";
        if(isLegacyDisabled(raw))return "房间";
        return switch(raw.trim().toLowerCase(Locale.ROOT)) {
            case "全局","global" -> "全局";
            case "房间","room" -> "房间";
            default -> throw new IllegalArgumentException("自然回血配置来源必须为：全局或房间");
        };
    }
    /** Legacy disabled policies become room settings with both healing switches off. */
    private static boolean isLegacyDisabled(String raw) {
        if(raw==null)return false;
        return switch(raw.trim().toLowerCase(Locale.ROOT)) {
            case "禁用","disabled","disable","off" -> true;
            default -> false;
        };
    }
    public static double amount(Double value) {
        return value==null || !Double.isFinite(value) || value<0?.5:Math.min(1024,value);
    }
    public Settings resolve(boolean globalPassive,boolean globalVanilla,int globalDelay,int globalInterval,double globalAmount) {
        if("房间".equals(mode))return new Settings(passiveEnabled,vanillaEnabled,startDelaySeconds,intervalTicks,healAmount.floatValue());
        return new Settings(globalPassive,globalVanilla,Math.max(0,globalDelay),Math.max(1,globalInterval),(float)amount(globalAmount));
    }
    public static RoomNaturalRegenConfig defaults(){return new RoomNaturalRegenConfig(null,null,null,null,null,null);}
    public record Settings(boolean passiveEnabled,boolean vanillaEnabled,int startDelaySeconds,int intervalTicks,float healAmount) {}
}
