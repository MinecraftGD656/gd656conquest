package org.mods.gd656conquest.common.audio;

import java.util.ArrayList;
import java.util.List;

/** Sound completion is reported by the client audio backend; gaps never use hard-coded clip lengths. */
public final class OperationsVoiceQueue {
    public record Request(String id, float volume, float pitch, OperationsVoiceContext scope, String followup, int gapTicks, int priority) {
        public Request(String id, float volume, float pitch) { this(id, volume, pitch, null, "", 0, 0); }
    }
    private final List<Request> pending = new ArrayList<>();
    private OperationsVoiceContext context;
    private Request current, delayed;
    private long delayedAt;
    private boolean suppressFollowup;

    public boolean updateContext(OperationsVoiceContext next) {
        context = next;
        pending.removeIf(request -> !valid(request));
        if (delayed != null && !valid(delayed)) delayed = null;
        if (current != null && !valid(current)) { current = null; suppressFollowup = false; return true; }
        return false;
    }
    public void enqueue(Request request) {
        if (!valid(request)) return;
        var cue = OperationsVoiceCue.fromId(request.id());
        if (cue != null && cue.reminder()) {
            // A late opening instruction must not follow a ten-second urgency reminder.
            pending.removeIf(item -> sameScope(item, request) && isInitiate(item));
            if (delayed != null && sameScope(delayed, request) && isInitiate(delayed)) delayed = null;
            if (current != null && sameScope(current, request)) suppressFollowup = true;
        }
        pending.add(request);
    }
    public Request poll(long tick) {
        if (current != null) return null;
        pending.removeIf(request -> !valid(request));
        if (delayed != null) {
            if (!valid(delayed)) delayed = null;
            else if (tick < delayedAt) return null;
            else { pending.add(0, delayed); delayed = null; }
        }
        if (pending.isEmpty()) return null;
        int best = 0;
        for (int i = 1; i < pending.size(); i++) if (pending.get(i).priority() > pending.get(best).priority()) best = i;
        current = pending.remove(best); suppressFollowup = false;
        return current;
    }
    public void finished(long tick) {
        if (current != null && !suppressFollowup && !current.followup().isEmpty()) {
            var followup = new Request(current.followup(), current.volume(), current.pitch(), current.scope(), "", 0, current.priority());
            if (valid(followup)) { delayed = followup; delayedAt = tick + current.gapTicks(); }
        }
        current = null; suppressFollowup = false;
    }
    private boolean valid(Request request) {
        return request.scope() == null || (request.scope().sameStage(context) && context.allows(OperationsVoiceCue.fromId(request.id())));
    }
    private static boolean sameScope(Request a, Request b) {
        return a.scope() != null && a.scope().sameStage(b.scope());
    }
    private static boolean isInitiate(Request request) {
        var cue = OperationsVoiceCue.fromId(request.id()); return cue != null && cue.initiate();
    }
    public int pendingCount() { return pending.size(); }
    public void clear() { pending.clear(); current = delayed = null; context = null; suppressFollowup = false; }
}
