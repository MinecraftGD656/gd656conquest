package org.mods.gd656conquest.mixin.server;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.data.ConquestDataManager;
import org.mods.gd656conquest.server.player.lock.ServerPlayerLockService;
import org.mods.gd656conquest.server.social.chat.ServerChatFormatService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656conquest$freezeBattalionPlayer(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (gd656conquest$isBattalionIntermission()) ci.cancel();
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true, require = 1)
    private void gd656conquest$freezeBattalionVehicle(net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (gd656conquest$isBattalionIntermission()) ci.cancel();
    }

    private boolean gd656conquest$isBattalionIntermission() {
        return player != null && player.server != null && player.server.isSameThread()
            && "battalion_intermission".equals(ServerPlayerLockService.of(player.server).resolve(player).reason());
    }

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleChat(ServerboundChatPacket packet, CallbackInfo ci) {
        if (packet == null || player == null || packet.message() == null) {
            return;
        }
        ServerChatFormatService.broadcastFormatted(player, packet.message());
        ci.cancel();
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (packet == null) {
            return;
        }
        if (gd656conquest$shouldBlockManagedVehicleCreativeAmmoExtraction(packet)) {
            ci.cancel();
            gd656conquest$syncContainerState();
            return;
        }
        if (gd656conquest$shouldBlockInventoryOperation()) {
            ci.cancel();
            gd656conquest$syncContainerState();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (packet == null) {
            return;
        }
        if (gd656conquest$shouldBlockInventoryOperation()) {
            ci.cancel();
            gd656conquest$syncContainerState();
        }
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (packet == null || !gd656conquest$shouldBlockRoomItemOperation()) {
            return;
        }
        ci.cancel();
        gd656conquest$syncContainerState();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (packet == null || !gd656conquest$shouldBlockRoomItemOperation()) {
            return;
        }
        ci.cancel();
        gd656conquest$syncContainerState();
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (packet == null || !gd656conquest$shouldBlockRoomItemOperation()) {
            return;
        }
        ci.cancel();
        gd656conquest$syncContainerState();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handlePlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet == null || !gd656conquest$shouldBlockRoomItemOperation()) {
            return;
        }
        ci.cancel();
        gd656conquest$syncContainerState();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (packet == null || !gd656conquest$shouldBlockRoomItemOperation()) {
            return;
        }
        ci.cancel();
    }

    private boolean gd656conquest$shouldBlockInventoryOperation() {
        if (player == null || player.getServer() == null) {
            return false;
        }
        ServerPlayerLockService lockService = ServerPlayerLockService.of(player.getServer());
        if (player.hasPermissions(2) && !lockService.shouldIgnoreAdminInventoryBypass(player)) {
            return false;
        }
        return lockService.shouldBlockInventoryOperation(player);
    }

    private boolean gd656conquest$shouldBlockRoomItemOperation() {
        if (player == null || player.getServer() == null) {
            return false;
        }
        ServerPlayerLockService lockService = ServerPlayerLockService.of(player.getServer());
        if (player.hasPermissions(2) && !"battalion_intermission".equals(lockService.resolve(player).reason())) {
            return false;
        }
        return lockService.shouldBlockGameplayInteraction(player);
    }

    private boolean gd656conquest$shouldBlockManagedVehicleCreativeAmmoExtraction(ServerboundContainerClickPacket packet) {
        if (player == null || packet == null) {
            return false;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!VehicleCompatFacade.isManagedVehicleContainerMenu(menu)) {
            return false;
        }
        if (VehicleCompatFacade.isCreativeAmmoStack(menu.getCarried())
            || VehicleCompatFacade.isCreativeAmmoStack(packet.getCarriedItem())) {
            return true;
        }
        int slotNum = packet.getSlotNum();
        if (VehicleCompatFacade.isManagedVehicleCreativeAmmoSlot(menu, slotNum)) {
            return true;
        }
        for (var entry : packet.getChangedSlots().int2ObjectEntrySet()) {
            if (VehicleCompatFacade.isCreativeAmmoStack(entry.getValue())) {
                return true;
            }
            if (VehicleCompatFacade.isManagedVehicleCreativeAmmoSlot(menu, entry.getIntKey())) {
                return true;
            }
        }
        return false;
    }

    private void gd656conquest$syncContainerState() {
        if (player == null) {
            return;
        }
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }
}
