package org.mods.gd656conquest.common.pack;

public final class PackAuditConstants {
    public static final String TYPE_RESOURCE_PACK = "RESOURCE_PACK";
    public static final String TYPE_SHADER_PACK = "SHADER_PACK";

    public static final String SOURCE_KIND_FILE = "FILE";
    public static final String SOURCE_KIND_DIRECTORY = "DIRECTORY";

    public static final String REASON_LOGIN = "LOGIN";
    public static final String REASON_TIMER = "TIMER";
    public static final String REASON_RESOURCE_RELOAD = "RESOURCE_RELOAD";
    public static final String REASON_SHADER_RELOAD = "SHADER_RELOAD";
    public static final String REASON_MANUAL_UPDATE = "MANUAL_UPDATE";

    public static final String DISCONNECT_MESSAGE = "Resource package loading failed.";

    private PackAuditConstants() {
    }
}
