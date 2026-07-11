package org.mods.gd656conquest.server.room.runtime.shelling;

import org.mods.gd656conquest.server.data.shelling.ShellingImpactType;

import java.util.List;

public final class ShellingSequenceProfiles {
    private static final String MISSILE_LOOP_SOUND_ID = "gd656conquest:sfx.shelling.loop_missile";
    private static final String MISSILE_ALARM_SOUND_ID = "gd656conquest:sfx.shelling.bombraid_sfx";
    private static final List<String> MISSILE_LAUNCH_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.launch_missile_1",
        "gd656conquest:sfx.shelling.launch_missile_2",
        "gd656conquest:sfx.shelling.launch_missile_3",
        "gd656conquest:sfx.shelling.launch_missile_4",
        "gd656conquest:sfx.shelling.launch_missile_5"
    );
    private static final List<String> MISSILE_INCOMING_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.incoming_missile_1",
        "gd656conquest:sfx.shelling.incoming_missile_2",
        "gd656conquest:sfx.shelling.incoming_missile_3",
        "gd656conquest:sfx.shelling.incoming_missile_4"
    );
    private static final long MISSILE_WARNING_DURATION_TICKS = 30L * 20L;
    private static final long MISSILE_APPROACH_START_TICKS = 28L * 20L;
    private static final double MISSILE_INCOMING_RADIUS = 100.0D;
    private static final float MISSILE_INCOMING_MAX_VOLUME = 1.5F;
    private static final float MISSILE_LAUNCH_VOLUME = 0.6F;
    private static final float MISSILE_LOOP_MAX_VOLUME = 0.5F;
    private static final double LOCAL_LOOP_RADIUS = 200.0D;
    private static final float LOCAL_LOOP_MAX_VOLUME = 0.5F;
    private static final String LIGHTSHELL_LOOP_SOUND_ID = "gd656conquest:sfx.shelling.loop_lightshell";
    private static final String LIGHTSHELL_LAUNCH_SOUND_ID = "gd656conquest:sfx.shelling.launch_lightshell";
    private static final List<String> LIGHTSHELL_INCOMING_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.incoming_lightshell_1",
        "gd656conquest:sfx.shelling.incoming_lightshell_2",
        "gd656conquest:sfx.shelling.incoming_lightshell_3",
        "gd656conquest:sfx.shelling.incoming_lightshell_4",
        "gd656conquest:sfx.shelling.incoming_lightshell_5"
    );
    private static final long LIGHTSHELL_SPAWN_DELAY_TICKS = 4L * 20L;
    private static final double LIGHTSHELL_INCOMING_RADIUS = 25.0D;
    private static final float LIGHTSHELL_INCOMING_MAX_VOLUME = 1.5F;
    private static final long SMOKESHELL_SPAWN_DELAY_TICKS = 3L * 20L;
    private static final String MEDIUMSHELL_LOOP_SOUND_ID = "gd656conquest:sfx.shelling.loop_mediumshell";
    private static final String MEDIUMFLAMESHELL_LOOP_SOUND_ID = "gd656conquest:sfx.shelling.loop_mediumflameshell";
    private static final String MEDIUMSHELL_LAUNCH_SOUND_ID = "gd656conquest:sfx.shelling.launch_mediumshell";
    private static final List<String> MEDIUMSHELL_INCOMING_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.incoming_mediumshell_1",
        "gd656conquest:sfx.shelling.incoming_mediumshell_2",
        "gd656conquest:sfx.shelling.incoming_mediumshell_3",
        "gd656conquest:sfx.shelling.incoming_mediumshell_4",
        "gd656conquest:sfx.shelling.incoming_mediumshell_5",
        "gd656conquest:sfx.shelling.incoming_mediumshell_6"
    );
    private static final List<String> MEDIUMFLAMESHELL_INCOMING_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.incoming_mediumflameshell_1",
        "gd656conquest:sfx.shelling.incoming_mediumflameshell_2",
        "gd656conquest:sfx.shelling.incoming_mediumflameshell_3",
        "gd656conquest:sfx.shelling.incoming_mediumflameshell_4",
        "gd656conquest:sfx.shelling.incoming_mediumflameshell_5",
        "gd656conquest:sfx.shelling.incoming_mediumflameshell_6"
    );
    private static final long MEDIUMSHELL_SPAWN_DELAY_TICKS = 6L * 20L;
    private static final double MEDIUMSHELL_INCOMING_RADIUS = 50.0D;
    private static final float MEDIUMSHELL_INCOMING_MAX_VOLUME = 1.5F;
    private static final String HEAVYSHELL_LOOP_SOUND_ID = "gd656conquest:sfx.shelling.loop_heavyshell";
    private static final String HEAVYSHELL_LAUNCH_SOUND_ID = "gd656conquest:sfx.shelling.launch_heavyshell";
    private static final List<String> HEAVYSHELL_INCOMING_SOUND_IDS = List.of(
        "gd656conquest:sfx.shelling.incoming_heavyshell_1",
        "gd656conquest:sfx.shelling.incoming_heavyshell_2",
        "gd656conquest:sfx.shelling.incoming_heavyshell_3",
        "gd656conquest:sfx.shelling.incoming_heavyshell_4",
        "gd656conquest:sfx.shelling.incoming_heavyshell_5",
        "gd656conquest:sfx.shelling.incoming_heavyshell_6"
    );
    private static final long HEAVYSHELL_SPAWN_DELAY_TICKS = 8L * 20L;
    private static final double HEAVYSHELL_INCOMING_RADIUS = 75.0D;
    private static final float HEAVYSHELL_INCOMING_MAX_VOLUME = 1.5F;

    private ShellingSequenceProfiles() {
    }

    public static boolean isSequenced(ShellingImpactType impactType) {
        return resolve(impactType) != null;
    }

    public static ShellingSequenceProfile resolve(ShellingImpactType impactType) {
        if (impactType == null) {
            return null;
        }
        return switch (impactType) {
            case MISSILE -> new ShellingSequenceProfile(
                ShellingSequenceKind.MISSILE,
                "导弹炮击",
                MISSILE_LOOP_SOUND_ID,
                0.0D,
                MISSILE_LOOP_MAX_VOLUME,
                MISSILE_LAUNCH_SOUND_IDS,
                MISSILE_LAUNCH_VOLUME,
                List.of(),
                1.0F,
                MISSILE_APPROACH_START_TICKS,
                MISSILE_WARNING_DURATION_TICKS,
                MISSILE_INCOMING_RADIUS,
                MISSILE_INCOMING_MAX_VOLUME,
                MISSILE_INCOMING_SOUND_IDS,
                "导弹已发射，正在接近目标区",
                "已发起导弹打击，预计来袭",
                "枚导弹"
            );
            case LIGHTSHELL -> new ShellingSequenceProfile(
                ShellingSequenceKind.LIGHTSHELL,
                "轻型炮击",
                LIGHTSHELL_LOOP_SOUND_ID,
                LOCAL_LOOP_RADIUS,
                LOCAL_LOOP_MAX_VOLUME,
                List.of(),
                1.0F,
                List.of(LIGHTSHELL_LAUNCH_SOUND_ID),
                1.0F,
                LIGHTSHELL_SPAWN_DELAY_TICKS,
                0L,
                LIGHTSHELL_INCOMING_RADIUS,
                LIGHTSHELL_INCOMING_MAX_VOLUME,
                LIGHTSHELL_INCOMING_SOUND_IDS,
                "轻型炮击已开始覆盖目标区",
                "已发起轻型炮击，预计来袭",
                "枚轻型炮弹"
            );
            case SMOKESHELL -> new ShellingSequenceProfile(
                ShellingSequenceKind.SMOKESHELL,
                "烟雾弹",
                "",
                0.0D,
                0.0F,
                List.of(LIGHTSHELL_LAUNCH_SOUND_ID),
                1.0F,
                List.of(),
                1.0F,
                SMOKESHELL_SPAWN_DELAY_TICKS,
                0L,
                0.0D,
                0.0F,
                List.of(),
                "烟雾弹已开始覆盖目标区",
                "已发起烟雾弹支援，预计来袭",
                "枚烟雾弹"
            );
            case MEDIUMSHELL -> new ShellingSequenceProfile(
                ShellingSequenceKind.MEDIUMSHELL,
                "中型炮击",
                MEDIUMSHELL_LOOP_SOUND_ID,
                LOCAL_LOOP_RADIUS,
                LOCAL_LOOP_MAX_VOLUME,
                List.of(),
                1.0F,
                List.of(MEDIUMSHELL_LAUNCH_SOUND_ID),
                1.0F,
                MEDIUMSHELL_SPAWN_DELAY_TICKS,
                0L,
                MEDIUMSHELL_INCOMING_RADIUS,
                MEDIUMSHELL_INCOMING_MAX_VOLUME,
                MEDIUMSHELL_INCOMING_SOUND_IDS,
                "中型炮击已开始覆盖目标区",
                "已发起中型炮击，预计来袭",
                "枚中型炮弹"
            );
            case HEAVYSHELL -> new ShellingSequenceProfile(
                ShellingSequenceKind.HEAVYSHELL,
                "重型炮击",
                HEAVYSHELL_LOOP_SOUND_ID,
                LOCAL_LOOP_RADIUS,
                LOCAL_LOOP_MAX_VOLUME,
                List.of(),
                1.0F,
                List.of(HEAVYSHELL_LAUNCH_SOUND_ID),
                1.0F,
                HEAVYSHELL_SPAWN_DELAY_TICKS,
                0L,
                HEAVYSHELL_INCOMING_RADIUS,
                HEAVYSHELL_INCOMING_MAX_VOLUME,
                HEAVYSHELL_INCOMING_SOUND_IDS,
                "重型炮击已开始覆盖目标区",
                "已发起重型炮击，预计来袭",
                "枚重型炮弹"
            );
            case FLAMESHELL -> new ShellingSequenceProfile(
                ShellingSequenceKind.FLAMESHELL,
                "中型燃烧炮击",
                MEDIUMFLAMESHELL_LOOP_SOUND_ID,
                LOCAL_LOOP_RADIUS,
                LOCAL_LOOP_MAX_VOLUME,
                List.of(),
                1.0F,
                List.of(MEDIUMSHELL_LAUNCH_SOUND_ID),
                1.0F,
                MEDIUMSHELL_SPAWN_DELAY_TICKS,
                0L,
                MEDIUMSHELL_INCOMING_RADIUS,
                MEDIUMSHELL_INCOMING_MAX_VOLUME,
                MEDIUMFLAMESHELL_INCOMING_SOUND_IDS,
                "中型燃烧炮击已开始覆盖目标区",
                "已发起中型燃烧炮击，预计来袭",
                "枚中型燃烧炮弹"
            );
            default -> null;
        };
    }

    public static String missileAlarmSoundId() {
        return MISSILE_ALARM_SOUND_ID;
    }
}
