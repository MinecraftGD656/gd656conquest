package org.mods.gd656conquest.mixin.client.compat.ragdoll;

import java.util.Map;
import org.mods.gd656conquest.server.compat.ragdoll.RagdollSyncScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollManager", remap = false)
public abstract class ClientRagdollReplacementMixin {
    @Redirect(method = {"tickRagdollStreamClient", "tickRagdollSyncClient"}, require = 1,
        at = @At(value = "INVOKE", target = "Lcom/raiiiden/ragdollified/client/ClientRagdoll;getSnapshot()Lcom/raiiiden/ragdollified/client/ClientRagdoll$TransformSnapshot;"))
    private static com.raiiiden.ragdollified.client.ClientRagdoll.TransformSnapshot gd656conquest$prepareCurrentSender(
            @org.spongepowered.asm.mixin.injection.Coerce Object body) {
        try {
            int id = (Integer)body.getClass().getMethod("getOriginalEntityId").invoke(body);
            RagdollSyncScope.prepareTransmit(id, body,
                Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager"));
            return (com.raiiiden.ragdollified.client.ClientRagdoll.TransformSnapshot)
                body.getClass().getMethod("getSnapshot").invoke(body);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    @Redirect(method = {"tickRagdollStreamClient", "tickRagdollSyncClient"}, require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"))
    private static void gd656conquest$traceSentPose(net.minecraftforge.network.simple.SimpleChannel channel, Object packet) {
        RagdollSyncScope.traceSend(packet);
        channel.sendToServer(packet);
    }

    @Redirect(method = "processSpawnQueue", require = 1,
        at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object gd656conquest$prepareReplacement(Map<?, ?> map, Object key) {
        Object previous = map.remove(key);
        try { RagdollSyncScope.prepareSpawn((Integer)key,
            Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager")); }
        catch (ClassNotFoundException error) { throw new IllegalStateException(error); }
        return previous;
    }

    @Redirect(method = "processSpawnQueue", require = 1,
        at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object gd656conquest$commitReplacement(Map map, Object key, Object body) {
        Object previous = map.put(key, body);
        RagdollSyncScope.commitSpawn((Integer)key, body);
        return previous;
    }

    @Redirect(method = {"tickRagdollStreamClient", "tickRagdollSyncClient"}, require = 1,
        at = @At(value = "INVOKE", target = "Lcom/raiiiden/ragdollified/client/ClientRagdoll;isDestroyed()Z"))
    private static boolean gd656conquest$skipDisplacedBody(@org.spongepowered.asm.mixin.injection.Coerce Object body) {
        try {
            int id = (Integer)body.getClass().getMethod("getOriginalEntityId").invoke(body);
            return !RagdollSyncScope.canTransmit(id, body)
                || (Boolean)body.getClass().getMethod("isDestroyed").invoke(body);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
