package org.mods.gd656conquest.client.audio;

import org.mods.gd656conquest.common.match.MatchResultType;

public final class ClientMusicTriggerService {
    private ClientMusicTriggerService() {
    }

    public static void tick() {
        ClientMusicDirector.tick();
        ClientObjectiveAudioCueService.tick();
    }

    public static void onDeathStarted() {
        ClientMusicDirector.onDeathStarted();
    }

    public static void onMatchEndTransitionStarted() {
        ClientMusicDirector.onMatchEndTransitionStarted();
    }

    public static void onDeployTravelStarted() {
        ClientMusicDirector.onDeployTravelStarted();
    }

    public static void onDeployOpened() {
        ClientMusicDirector.onDeployOpened();
    }

    public static void onRespawnTransitionStarted() {
        ClientMusicDirector.onRespawnTransitionStarted();
    }

    public static void onMatchResult(MatchResultType resultType) {
        ClientMusicDirector.onMatchResult(resultType);
    }

    public static void reset() {
        ClientMusicDirector.reset();
        ClientObjectiveAudioCueService.reset();
    }
}
