package org.mods.gd656conquest.client.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 本模组的 Forge 标准客户端配置文件(gd656conquest-client.toml)。
 *
 * <p>仅用于客户端,与服务器配置无关。</p>
 */
public final class ClientScreenHudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SCREEN_HUD;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("屏幕HUD(Screen HUD)配置")
            .push("hud");
        ENABLE_SCREEN_HUD = builder
            .comment(
                "是否启用本模组的游戏内屏幕HUD。",
                "禁用后,本模组游戏内顶部的全部HUD(包括所有据点HUD)与游戏内3D空间据点图标将不再显示;",
                "死亡后的部署阶段(部署点选择)界面元素不受影响,仍会正常渲染。",
                "修改本文件后由 Forge 自动热重载,即时生效。",
                "Default: true"
            )
            .define("enableScreenHud", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientScreenHudConfig() {
    }

    /** 注册客户端配置,生成 gd656conquest-client.toml。必须在 mod 构造阶段调用。 */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "gd656conquest-client.toml");
    }

    /** 是否启用本模组的游戏内屏幕HUD。 */
    public static boolean isScreenHudEnabled() {
        return ENABLE_SCREEN_HUD.get();
    }
}
