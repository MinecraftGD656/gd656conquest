package org.mods.gd656conquest.server.data.roomstatisticdata;

public final class RoomStatisticDataModel {
    private String roomId = "room_01";
    private Integer totalPlayCount = 0;
    private Integer totalMatches = 0;
    private Integer campAWins = 0;
    private Integer campBWins = 0;
    private Integer drawMatches = 0;
    private Long totalMatchDurationSeconds = 0L;
    private Integer ratingVoteCount = 0;
    private Integer ratingScoreTotal = 0;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Integer getTotalPlayCount() {
        return totalPlayCount;
    }

    public void setTotalPlayCount(Integer totalPlayCount) {
        this.totalPlayCount = totalPlayCount;
    }

    public Integer getTotalMatches() {
        return totalMatches;
    }

    public void setTotalMatches(Integer totalMatches) {
        this.totalMatches = totalMatches;
    }

    public Integer getCampAWins() {
        return campAWins;
    }

    public void setCampAWins(Integer campAWins) {
        this.campAWins = campAWins;
    }

    public Integer getCampBWins() {
        return campBWins;
    }

    public void setCampBWins(Integer campBWins) {
        this.campBWins = campBWins;
    }

    public Integer getDrawMatches() {
        return drawMatches;
    }

    public void setDrawMatches(Integer drawMatches) {
        this.drawMatches = drawMatches;
    }

    public Long getTotalMatchDurationSeconds() {
        return totalMatchDurationSeconds;
    }

    public void setTotalMatchDurationSeconds(Long totalMatchDurationSeconds) {
        this.totalMatchDurationSeconds = totalMatchDurationSeconds;
    }

    public Integer getRatingVoteCount() {
        return ratingVoteCount;
    }

    public void setRatingVoteCount(Integer ratingVoteCount) {
        this.ratingVoteCount = ratingVoteCount;
    }

    public Integer getRatingScoreTotal() {
        return ratingScoreTotal;
    }

    public void setRatingScoreTotal(Integer ratingScoreTotal) {
        this.ratingScoreTotal = ratingScoreTotal;
    }

    public int totalPlayCountValue() {
        return Math.max(0, totalPlayCount == null ? 0 : totalPlayCount);
    }

    public int totalMatchesValue() {
        return Math.max(0, totalMatches == null ? 0 : totalMatches);
    }

    public int campAWinsValue() {
        return Math.max(0, campAWins == null ? 0 : campAWins);
    }

    public int campBWinsValue() {
        return Math.max(0, campBWins == null ? 0 : campBWins);
    }

    public int drawMatchesValue() {
        return Math.max(0, drawMatches == null ? 0 : drawMatches);
    }

    public long totalMatchDurationSecondsValue() {
        return Math.max(0L, totalMatchDurationSeconds == null ? 0L : totalMatchDurationSeconds);
    }

    public int ratingVoteCountValue() {
        return Math.max(0, ratingVoteCount == null ? 0 : ratingVoteCount);
    }

    public int ratingScoreTotalValue() {
        return Math.max(0, ratingScoreTotal == null ? 0 : ratingScoreTotal);
    }
}
