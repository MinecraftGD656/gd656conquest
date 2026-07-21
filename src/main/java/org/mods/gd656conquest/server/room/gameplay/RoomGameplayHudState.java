package org.mods.gd656conquest.server.room.gameplay;

public record RoomGameplayHudState(
    int campAScore,
    int campBScore,
    int elapsedSeconds,
    String mode,
    int campAGoal,
    int campBGoal,
    int remainSeconds,
    String topStatus,
    String eventText,
    String focusPayload,
    String audioState,
    String extraPayload
) {
    public static RoomGameplayHudState defaults() {
        return new RoomGameplayHudState(0, 0, 0, "SCORE_TARGET", 0, 0, 0, "-", "-", "-", "RUNNING", "-");
    }
}
