package org.mods.gd656conquest.client.config;

import java.io.InputStream;
import java.util.Properties;

public final class ClientPacketBypassConfig {
    private static final String RESOURCE_PATH = "/gd656conquest-client-bypass.properties";

    private static final boolean BYPASS_DEATH_FLOW_PACKETS;
    private static final boolean BYPASS_DOWNED_STATE_PACKETS;
    private static final boolean BYPASS_SUPPORT_UI_PACKETS;
    private static final boolean BYPASS_LOADOUT_UI_PACKETS;
    private static final boolean BYPASS_ROOM_EDITOR_PACKETS;
    private static final boolean BYPASS_VEHICLE_ADMIN_EDITOR_PACKETS;

    static {
        Properties properties = new Properties();
        try (InputStream stream = ClientPacketBypassConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (Exception ignored) {
        }
        BYPASS_DEATH_FLOW_PACKETS = getBoolean(properties, "bypassDeathFlowPackets");
        BYPASS_DOWNED_STATE_PACKETS = getBoolean(properties, "bypassDownedStatePackets");
        BYPASS_SUPPORT_UI_PACKETS = getBoolean(properties, "bypassSupportUiPackets");
        BYPASS_LOADOUT_UI_PACKETS = getBoolean(properties, "bypassLoadoutUiPackets");
        BYPASS_ROOM_EDITOR_PACKETS = getBoolean(properties, "bypassRoomEditorPackets");
        BYPASS_VEHICLE_ADMIN_EDITOR_PACKETS = getBoolean(properties, "bypassVehicleAdminEditorPackets");
    }

    private ClientPacketBypassConfig() {
    }

    public static boolean bypassDeathFlowPackets() {
        return BYPASS_DEATH_FLOW_PACKETS;
    }

    public static boolean bypassDownedStatePackets() {
        return BYPASS_DOWNED_STATE_PACKETS;
    }

    public static boolean bypassSupportUiPackets() {
        return BYPASS_SUPPORT_UI_PACKETS;
    }

    public static boolean bypassLoadoutUiPackets() {
        return BYPASS_LOADOUT_UI_PACKETS;
    }

    public static boolean bypassRoomEditorPackets() {
        return BYPASS_ROOM_EDITOR_PACKETS;
    }

    public static boolean bypassVehicleAdminEditorPackets() {
        return BYPASS_VEHICLE_ADMIN_EDITOR_PACKETS;
    }

    private static boolean getBoolean(Properties properties, String key) {
        return Boolean.parseBoolean(properties.getProperty(key, "false"));
    }
}
