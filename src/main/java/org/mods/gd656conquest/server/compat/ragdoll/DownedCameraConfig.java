package org.mods.gd656conquest.server.compat.ragdoll;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.mods.gd656conquest.common.player.DownedCameraMode;

/** Server-owned policy. The chosen mode is frozen for each downed session. */
public final class DownedCameraConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.EnumValue<DownedCameraMode> MODE;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MODE = builder.comment("死亡后摄像机模式：FIXED=固定头部视角，AUTOMATIC=自动运镜。玩家均不能操控。",
                "Both modes revive prone, facing the direction captured before being downed.")
            .defineEnum("deathCameraMode", DownedCameraMode.FIXED);
        SPEC = builder.build();
    }
    private DownedCameraConfig() {}
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "gd656conquest-downed-server.toml");
    }
    public static DownedCameraMode mode() {
        return MODE.get();
    }
}
