package org.mods.gd656conquest.server.room.runtime;

import org.mods.gd656conquest.server.room.core.RunningRuntimeState;
import org.mods.gd656conquest.server.facility.support.SupportScoreService;

public final class ShellingRequestCancellation {
    private ShellingRequestCancellation() {}

    /** Claim the outstanding request before callbacks/refunds. Executed sequences have no request. */
    public static boolean cancel(RunningRuntimeState state, RunningRuntimeState.ShellingRequestState request, boolean refund) {
        if (state == null || request == null || !state.getActiveShellingsByPlayer().remove(request.playerId(), request)) return false;
        if (refund && request.reservedCost() > 0) {
            int spent = SupportScoreService.getSpentScore(state, request.playerId());
            SupportScoreService.setSpentScore(state, request.playerId(), spent - request.reservedCost());
        }
        return true;
    }
}
