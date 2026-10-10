package org.mods.gd656conquest.server.compat.ragdoll;

/** Added to optional Ragdollified packets by the compatibility mixin. */
public interface RagdollPacketScope {
    long gd656conquest$generation();
    void gd656conquest$generation(long generation);
}
