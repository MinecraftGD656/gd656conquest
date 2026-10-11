package org.mods.gd656conquest.client.room;

import org.mods.gd656conquest.common.player.DeathCause;
import org.mods.gd656conquest.common.player.KillerCardInfo;

/** Pure lifetime state: closing a death cannot be undone by duplicate or delayed metadata. */
public final class KillerCardModel {
    private long latestId;
    private boolean closed = true;
    private DeathCause cause;
    private KillerCardInfo info;
    public boolean begin(long id, DeathCause value) {
        if (id <= latestId) return false;
        latestId = id; closed = false; cause = value; info = null;
        return true;
    }
    public boolean accept(long id, KillerCardInfo value) {
        if (closed || id != latestId || (cause == null || !cause.allowsKillerCard()) || info != null || value == null) return false;
        info = value; return true;
    }
    public KillerCardInfo info() { return closed || cause == null || !cause.allowsKillerCard() ? null : info; }
    public void close() { closed = true; info = null; }
    public void reset() { latestId = 0; closed = true; cause = null; info = null; }
}
