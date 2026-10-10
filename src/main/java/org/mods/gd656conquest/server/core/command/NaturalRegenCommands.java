package org.mods.gd656conquest.server.core.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.*;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.serverconfig.ServerConfigStore;
import org.mods.gd656conquest.server.shared.text.CommandFeedbacks;

/** Adds global passive-healing settings to the existing get/set and suggestion commands. */
public final class NaturalRegenCommands {
    private NaturalRegenCommands() {}
    public static List<String> keys(List<String> original) {
        var keys=new LinkedHashSet<>(original);keys.add("boolPassiveRegeneration");keys.add("passiveRegenHealAmount");return List.copyOf(keys);
    }
    public static String normalize(String raw) {
        if(raw==null)return null;
        return switch(raw.trim().toLowerCase(Locale.ROOT)) {
            case "boolpassiveregeneration","passiveregeneration","passiveregenenabled" -> "boolPassiveRegeneration";
            case "passiveregenhealamount","passiveregenamount" -> "passiveRegenHealAmount";
            default -> null;
        };
    }
    private static ServerConfigStore store(CommandContext<CommandSourceStack> c) {
        return ConquestDataManager.of(c.getSource().getServer()).serverConfig();
    }
    public static String read(ServerConfigStore store,String id) {
        return switch(id) {
            case "boolPassiveRegeneration" -> String.valueOf(store.isPassiveRegenerationEnabled());
            case "passiveRegenHealAmount" -> String.valueOf(store.getPassiveRegenHealAmount());
            default -> null;
        };
    }
    public static void showAdded(CommandContext<CommandSourceStack> c) {
        var store=store(c);
        CommandFeedbacks.line(c.getSource(),Component.literal("boolPassiveRegeneration="+store.isPassiveRegenerationEnabled()));
        CommandFeedbacks.line(c.getSource(),Component.literal("passiveRegenHealAmount="+store.getPassiveRegenHealAmount()));
    }
    public static Integer get(CommandContext<CommandSourceStack> c) {
        String id=normalize(StringArgumentType.getString(c,"configId"));if(id==null)return null;
        return CommandFeedbacks.info(c.getSource(),id+"="+read(store(c),id));
    }
    public static Object parse(String id,String value)throws CommandSyntaxException {
        if("boolPassiveRegeneration".equals(id)) {
            if("true".equalsIgnoreCase(value))return true;if("false".equalsIgnoreCase(value))return false;
        } else if("passiveRegenHealAmount".equals(id)) {
            try {double amount=Double.parseDouble(value);if(Double.isFinite(amount) && amount>=0 && amount<=1024)return amount;}
            catch(NumberFormatException ignored) {}
        }
        throw new SimpleCommandExceptionType(Component.literal("自然回血配置值无效：开关使用 true/false，回复量使用 0–1024 HP")).create();
    }
    public static Integer set(CommandContext<CommandSourceStack> c)throws CommandSyntaxException {
        String id=normalize(StringArgumentType.getString(c,"configId"));if(id==null)return null;
        var value=parse(id,StringArgumentType.getString(c,"value").trim());var store=store(c);var model=store.get();
        if(value instanceof Boolean b)model.setBoolPassiveRegeneration(b);else model.setPassiveRegenHealAmount((Double)value);
        store.save(model);return CommandFeedbacks.info(c.getSource(),"已更新服务端配置: "+id+"="+read(store,id));
    }
    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> c,SuggestionsBuilder builder) {
        String id=normalize(StringArgumentType.getString(c,"configId"));if(id==null)return null;
        var values=id.equals("boolPassiveRegeneration")?List.of("true","false"):List.of("0.5","1","2","3");
        for(String value:values)if(value.startsWith(builder.getRemainingLowerCase()))builder.suggest(value);
        return builder.buildFuture();
    }
}
