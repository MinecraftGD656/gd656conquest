package org.mods.gd656conquest.server.room.gameplay.breakthrough;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.*;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.room.entry.RoomCoreRuntimeManager;
import org.mods.gd656conquest.server.room.model.*;

public final class BriefingTimelineEditorService {
    private static final Gson GSON = new Gson();
    private BriefingTimelineEditorService() {}
    private static String revision(String json) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))); }
    public static void handle(ServerPlayer player, BriefingTimelineEditPacket request) {
        if (player == null || player.server == null) return;
        if (!player.hasPermissions(2)) { player.sendSystemMessage(Component.literal("只有管理员可以编辑战况时间轴")); return; }
        try {
            var data = ConquestDataManager.of(player.server);
            RoomDefinition room = data.roomDefinitions().findDefinition(request.roomId()).orElseThrow(() -> new IllegalArgumentException("房间不存在"));
            if (!"breakthrough".equals(room.gameplayId().value())) throw new IllegalArgumentException("仅行动模式支持战况时间轴");
            var current = room.breakthroughConfig().battalionConfig();
            String json = GSON.toJson(current);
            if (request.save()) {
                if (!revision(json).equals(request.revision())) throw new IllegalArgumentException("房间配置已改变，请重新打开时间轴后编辑");
                var submitted = GSON.fromJson(request.json(), RoomBattalionConfig.class);
                if (submitted == null) throw new IllegalArgumentException("无效时间轴配置");
                var updated = new RoomBattalionConfig(current.count(),current.briefingViewPosition(),current.briefingSeconds(),current.preparationSeconds(),submitted.timelines(),current.settlementTiming());
                if (GSON.toJson(updated).length()>262144) throw new IllegalArgumentException("时间轴配置过大");
                var gameplay = GSON.toJsonTree(room.breakthroughConfig()).getAsJsonObject();
                gameplay.add("battalionConfig",GSON.toJsonTree(updated));
                room = new RoomDefinition(room.roomId(),room.displayName(),room.gameplayId(),room.lifecycleState(),room.commonConfig(),GSON.fromJson(gameplay,RoomBreakthroughConfig.class));
                room = data.roomDefinitions().saveDefinition(room);
                RoomCoreRuntimeManager.of(player.server).hotUpdateRoomDefinition(room,player.server.getTickCount());
                json = GSON.toJson(room.breakthroughConfig().battalionConfig());
            }
            if (json.length() > 262144) throw new IllegalArgumentException("时间轴配置过大");
            NetworkManager.sendToPlayer(player,new BriefingTimelineSnapshotPacket(request.roomId(),!request.save(),revision(json),json,request.save()?"已保存；进行中的对局使用开局配置，下一场生效":""));
        } catch (Exception error) {
            String message = error.getMessage() == null ? "配置无效" : error.getMessage();
            NetworkManager.sendToPlayer(player,new BriefingTimelineSnapshotPacket(request.roomId(),false,"","",message.substring(0,Math.min(1000,message.length()))));
        }
    }
}
