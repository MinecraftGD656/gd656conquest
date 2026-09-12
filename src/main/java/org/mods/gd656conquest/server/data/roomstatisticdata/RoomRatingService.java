package org.mods.gd656conquest.server.data.roomstatisticdata;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataModel;
import org.mods.gd656conquest.server.data.playerdata.PlayerDataStore;
import org.mods.gd656conquest.server.room.model.RoomDefinition;

import java.util.Locale;
import java.util.Objects;

public final class RoomRatingService {
    private final MinecraftServer server;

    public RoomRatingService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public RatingResult submitRating(ServerPlayer player, String roomId, int rating) {
        if (player == null) {
            return RatingResult.failure("只有玩家可以提交房间评分");
        }
        if (rating < 1 || rating > 5) {
            return RatingResult.failure("评分必须在 1 到 5 之间");
        }
        RoomDefinition room = ConquestDataManager.of(server).roomDefinitions().findDefinition(roomId).orElse(null);
        if (room == null) {
            return RatingResult.failure("房间不存在: " + roomId);
        }
        String normalizedRoomId = room.roomId();
        PlayerDataStore playerDataStore = ConquestDataManager.of(server).playerData();
        RoomStatisticDataStore roomStatisticDataStore = ConquestDataManager.of(server).roomStatisticData();
        PlayerDataModel playerData = playerDataStore.getPlayerData(player.getUUID());
        PlayerDataModel.RoomRatingRecord existingRecord = playerData.getRoomRatings().get(normalizedRoomId);
        int previousRating = existingRecord == null ? 0 : existingRecord.getRating();

        PlayerDataModel.RoomRatingRecord updatedRecord = new PlayerDataModel.RoomRatingRecord();
        updatedRecord.setRoomId(normalizedRoomId);
        updatedRecord.setRating(rating);
        updatedRecord.setUpdatedAtEpochMillis(System.currentTimeMillis());
        playerData.getRoomRatings().put(normalizedRoomId, updatedRecord);
        playerDataStore.savePlayerRoomRatings(player.getUUID(), playerData);

        RoomStatisticDataModel roomStatistics = roomStatisticDataStore.getRoomStatistics(normalizedRoomId);
        if (previousRating >= 1 && previousRating <= 5) {
            roomStatistics.setRatingScoreTotal(Math.max(0, roomStatistics.ratingScoreTotalValue() - previousRating + rating));
        } else {
            roomStatistics.setRatingVoteCount(roomStatistics.ratingVoteCountValue() + 1);
            roomStatistics.setRatingScoreTotal(roomStatistics.ratingScoreTotalValue() + rating);
        }
        roomStatisticDataStore.saveRoomStatistics(roomStatistics, RoomStatisticDataStore.ROOM_RATING_WRITE_DELAY_TICKS);

        boolean updated = previousRating >= 1 && previousRating <= 5;
        return RatingResult.success(
            updated
                ? String.format(Locale.ROOT, "已更新 %s 的评分：%d -> %d", normalizedRoomId, previousRating, rating)
                : String.format(Locale.ROOT, "已为 %s 提交评分：%d", normalizedRoomId, rating),
            normalizedRoomId,
            previousRating,
            rating,
            room.gameplayConfig().mapId()
        );
    }

    public record RatingResult(
        boolean success,
        String message,
        String roomId,
        int previousRating,
        int currentRating,
        String mapId
    ) {
        public static RatingResult success(String message, String roomId, int previousRating, int currentRating, String mapId) {
            return new RatingResult(true, message, roomId, previousRating, currentRating, mapId == null ? "" : mapId);
        }

        public static RatingResult failure(String message) {
            return new RatingResult(false, message, "", 0, 0, "");
        }
    }
}
