package org.mods.gd656conquest.server.limit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

/**
 * GD656Mods: 服务器人数限制管理命令。
 *
 * <pre>
 * /gdc limit player                  查看当前状态
 * /gdc limit player on|off           开关人数限制
 * /gdc limit player set &lt;人数&gt;       设置计数上限(默认 128)
 * /gdc limit player reason &lt;"字符串"&gt; 设置超限踢出时显示的提示
 * </pre>
 *
 * 管理员(权限 ≥2)不计入人数、也不受限制, 因此本条命令本身也只对权限 ≥2 开放。
 */
public final class PlayerLimitCommands {
    private PlayerLimitCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> buildTree() {
        return Commands.literal("limit")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("player")
                .executes(PlayerLimitCommands::executeStatus)
                .then(Commands.literal("on").executes(PlayerLimitCommands::executeOn))
                .then(Commands.literal("off").executes(PlayerLimitCommands::executeOff))
                .then(Commands.literal("set")
                    .then(Commands.argument(
                            "amount",
                            IntegerArgumentType.integer(PlayerLimitDataStore.MIN_MAX_PLAYERS, PlayerLimitDataStore.MAX_MAX_PLAYERS)
                        )
                        .executes(PlayerLimitCommands::executeSet)))
                .then(Commands.literal("reason")
                    .then(Commands.argument("text", StringArgumentType.string())
                        .executes(PlayerLimitCommands::executeReason))));
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        PlayerLimitDataStore store = store(context);
        Component status = Component.literal("人数限制: " + (store.isEnabled() ? "已开启" : "已关闭")
            + " | 上限=" + store.getMaxPlayers()
            + " | 当前计入人数=" + PlayerLimitService.countCountedPlayers(context.getSource().getServer())
            + "(不含管理员)"
            + " | 提示=" + store.getReason());
        CommandFeedbacks.line(context.getSource(), status);
        return 1;
    }

    private static int executeOn(CommandContext<CommandSourceStack> context) {
        store(context).setEnabled(true);
        return reply(context, "人数限制已开启，上限=" + store(context).getMaxPlayers());
    }

    private static int executeOff(CommandContext<CommandSourceStack> context) {
        store(context).setEnabled(false);
        return reply(context, "人数限制已关闭");
    }

    private static int executeSet(CommandContext<CommandSourceStack> context) {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        store(context).setMaxPlayers(amount);
        return reply(context, "人数上限已设为 " + store(context).getMaxPlayers());
    }

    private static int executeReason(CommandContext<CommandSourceStack> context) {
        String text = StringArgumentType.getString(context, "text");
        store(context).setReason(text);
        return reply(context, "超限提示已设为: " + store(context).getReason());
    }

    private static PlayerLimitDataStore store(CommandContext<CommandSourceStack> context) {
        return ConquestDataManager.of(context.getSource().getServer()).playerLimit();
    }

    private static int reply(CommandContext<CommandSourceStack> context, String message) {
        return CommandFeedbacks.info(context.getSource(), message);
    }
}
