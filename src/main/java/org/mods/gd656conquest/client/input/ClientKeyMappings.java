package org.mods.gd656conquest.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories.gd656conquest";
    public static final KeyMapping OPEN_LOADOUT_SCREEN = new KeyMapping(
            "key.gd656conquest.open_loadout_screen",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Z,
            CATEGORY
    );
    public static final KeyMapping PLACE_TACTICAL_MARKER = new KeyMapping(
            "key.gd656conquest.place_tactical_marker",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_Q,
            CATEGORY
    );
    public static final KeyMapping COMMON_USE = new KeyMapping(
            "key.gd656conquest.common_use",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F,
            CATEGORY
    );

    private ClientKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        if (event == null) {
            return;
        }
        event.register(OPEN_LOADOUT_SCREEN);
        event.register(PLACE_TACTICAL_MARKER);
        event.register(COMMON_USE);
    }
}
