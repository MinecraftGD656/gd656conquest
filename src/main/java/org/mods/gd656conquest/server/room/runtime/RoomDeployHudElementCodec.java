package org.mods.gd656conquest.server.room.runtime;

import java.util.List;

public final class RoomDeployHudElementCodec {
    private RoomDeployHudElementCodec() {
    }

    public static String encodeAll(List<RoomDeployHudElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "-";
        }
        StringBuilder payload = new StringBuilder(Math.max(32, elements.size() * 48));
        for (RoomDeployHudElement element : elements) {
            append(payload, element);
        }
        return payload.length() == 0 ? "-" : payload.toString();
    }

    public static void append(StringBuilder payload, RoomDeployHudElement element) {
        if (payload == null || element == null || element.code() == null || element.code().isBlank()) {
            return;
        }
        if (payload.length() > 0) {
            payload.append(';');
        }
        payload.append(element.kind().payloadToken())
            .append(',')
            .append(element.code())
            .append(',');
        appendFixed(payload, element.x(), 3);
        payload.append(',');
        appendFixed(payload, element.y(), 3);
        payload.append(',');
        appendFixed(payload, element.z(), 3);
        payload.append(',')
            .append(element.ownerToken())
            .append(',')
            .append(element.drivingToken())
            .append(',');
        appendFixed(payload, element.progress(), 2);
        if (!element.note().isBlank() || !element.extraColumns().isEmpty()) {
            payload.append(',')
                .append(RoomHudPayloadCodec.encodeText(element.note()));
        }
        for (String extra : element.extraColumns()) {
            payload.append(',')
                .append(extra == null ? "" : extra);
        }
    }

    private static void appendFixed(StringBuilder payload, double value, int scale) {
        if (payload == null) {
            return;
        }
        if (!Double.isFinite(value)) {
            payload.append('0');
            if (scale > 0) {
                payload.append('.');
                appendZeroDigits(payload, scale);
            }
            return;
        }
        long factor = scale <= 0 ? 1L : powerOfTen(scale);
        long scaled = Math.round(value * factor);
        if (scaled < 0L) {
            payload.append('-');
            scaled = -scaled;
        }
        payload.append(scaled / factor);
        if (scale <= 0) {
            return;
        }
        payload.append('.');
        long fractional = scaled % factor;
        long divisor = factor / 10L;
        while (divisor > 0L) {
            payload.append((char) ('0' + (fractional / divisor)));
            fractional %= divisor;
            divisor /= 10L;
        }
    }

    private static long powerOfTen(int scale) {
        long value = 1L;
        for (int i = 0; i < scale; i++) {
            value *= 10L;
        }
        return value;
    }

    private static void appendZeroDigits(StringBuilder payload, int count) {
        for (int i = 0; i < count; i++) {
            payload.append('0');
        }
    }
}
