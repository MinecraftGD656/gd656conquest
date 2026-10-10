package org.mods.gd656conquest.client.room;

import java.lang.reflect.Field;

public final class RagdollPlaybackGuard {
    private static final ClassValue<Field[]> FIELDS = new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            try {
                Field[] fields = new Field[4];
                String[] names = {"current", "pending", "segmentStartNanos", "phase"};
                for (int i = 0; i < fields.length; i++) { fields[i] = type.getDeclaredField(names[i]); fields[i].setAccessible(true); }
                return fields;
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
    };
    private RagdollPlaybackGuard() {}

    /** A completed segment with no successor stays completed; it must never rewind. */
    public static Object holdCompleted(Object playback, long nowNanos) {
        try {
            Field[] fields = FIELDS.get(playback.getClass());
            Object current = fields[0].get(playback);
            if (current != null && fields[1].get(playback) == null
                    && nowNanos - fields[2].getLong(playback) > 200_000_000L) {
                fields[3].setFloat(playback, 1.0F);
                return current;
            }
            return null;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
