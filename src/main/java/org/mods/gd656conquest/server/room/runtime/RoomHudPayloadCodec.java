package org.mods.gd656conquest.server.room.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class RoomHudPayloadCodec {
    private RoomHudPayloadCodec() {
    }

    public static String encodeText(String value) {
        String safe = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }
}
