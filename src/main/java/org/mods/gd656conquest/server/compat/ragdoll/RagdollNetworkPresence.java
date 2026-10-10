package org.mods.gd656conquest.server.compat.ragdoll;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/** Optional network gate without linking external or client classes on a dedicated server. */
public final class RagdollNetworkPresence {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean resolved;
    private static SimpleChannel channel;

    private RagdollNetworkPresence() {
    }

    public static boolean availableFor(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return false;
        }
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("ragdollified")) {
                try {
                    channel = (SimpleChannel) Class.forName("com.raiiiden.ragdollified.network.ModNetwork")
                        .getField("CHANNEL").get(null);
                } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                    LOGGER.warn("Ragdollified channel unavailable; keeping the original death hook", error);
                }
            }
        }
        return channel != null && channel.isRemotePresent(player.connection.connection);
    }
}
