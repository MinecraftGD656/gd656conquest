package org.mods.gd656conquest.mixin.server;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;
import org.mods.gd656conquest.server.compat.vehicle.VehicleCompatFacade;
import org.mods.gd656conquest.server.compat.ywzjvehicle.YwzjVehicleCompatBridge;
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
        if (packet == null) {
            return;
        }
        // GD656Mods: 房间禁用背包时禁止丢弃物品(DROP_ITEM / DROP_ALL_ITEMS), 物品留在背包。
        // 注意: 客户端按 Q 会先本地移除背包物品并生成预测掉落物, 服务端 cancel 后必须强制
        // 全量同步背包, 否则客户端背包不会回滚、物品凭空消失(见 gd656conquest$syncContainerState)。
        if (isDropAction(packet.getAction()) && gd656conquest$shouldBlockInventoryOperation()) {
            ci.cancel();
            gd656conquest$syncContainerState();
            return;
        }
        if (!gd656conquest$shouldBlockRoomItemOperation()) {
            return;
        }
        ci.cancel();
        gd656conquest$syncContainerState();
    }

    private static boolean isDropAction(ServerboundPlayerActionPacket.Action action) {
        return action == ServerboundPlayerActionPacket.Action.DROP_ITEM
            || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS;
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (packet == null) {
            return;
        }
        // GD656Mods: 房间禁用背包时, 禁止打开联动模组(卓越前线/万众载具)的载具容器:
        // 卓越前线 Shift+右键载具打开容器, 万众载具已摧毁载具右键打开容器; 正常上车不受影响。
        // 注: 卓越前线 shift+右键若手持撬棍/维修工具会先执行拆车回收(物品进背包), 该交互一并
        // 被拦截, 符合"禁用背包=背包内容不可变化"的语义; 普通右键拆车回收不受本逻辑影响。
        // 注: 容器打开完全由服务端 OpenScreenPacket 驱动, 此处 cancel 后无需再强制同步背包。
        if (gd656conquest$shouldBlockVehicleContainerInteract(packet)) {
            ci.cancel();
            return;
        }
        if (!gd656conquest$shouldBlockRoomItemOperation()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void gd656conquest$handleContainerButtonClick(ServerboundContainerButtonClickPacket packet, CallbackInfo ci) {
        if (packet == null) {
            return;
        }
        // GD656Mods: 房间禁用背包时, 禁止骑乘联动模组载具按 E 打开载具库存(HasCustomInventoryScreen 机制)。
        if (gd656conquest$shouldBlockInventoryOperation()
            && VehicleCompatFacade.isVehicleEntity(player == null ? null : player.getVehicle())) {
            ci.cancel();
        }
    }

    private boolean gd656conquest$shouldBlockVehicleContainerInteract(ServerboundInteractPacket packet) {
        if (!gd656conquest$shouldBlockInventoryOperation()) {
            return false;
        }
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Entity target = packet.getTarget(serverLevel);
        if (!VehicleCompatFacade.isVehicleEntity(target)) {
            return false;
        }
        // 仅拦截"右键交互", 不拦截攻击载具(左键/蓄力攻击不受禁用背包影响)
        boolean[] isInteraction = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
                isInteraction[0] = true;
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 location) {
                isInteraction[0] = true;
            }

            @Override
            public void onAttack() {
            }
        });
        if (!isInteraction[0]) {
            return false;
        }
        // 万众载具: 已摧毁载具右键只用于打开容器(未摧毁右键为上车), 已摧毁才拦截;
        // 卓越前线: Shift+右键打开载具容器, 非 Shift 右键为上车/操作, 仅 Shift+右键拦截。
        if (YwzjVehicleCompatBridge.isVehicleEntity(target)) {
            return YwzjVehicleCompatBridge.isDestroyedVehicle(target);
        }
        return packet.isUsingSecondaryAction();
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
        if (player.hasPermissions(2)) {
            return false;
        }
        return ServerPlayerLockService.of(player.getServer()).shouldBlockGameplayInteraction(player);
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
        // 强制全量同步背包, 回滚客户端已本地执行的丢弃/容器操作。
        // broadcastChanges 只在槽位有变化时才发包, 而服务端槽位并未变化(cancel 了操作),
        // 不会通知客户端恢复, 必须直接下发容器内容包。
        gd656conquest$forceSyncMenu(player.inventoryMenu);
        if (player.containerMenu != player.inventoryMenu) {
            gd656conquest$forceSyncMenu(player.containerMenu);
        }
    }

    private void gd656conquest$forceSyncMenu(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }
        player.connection.send(new ClientboundContainerSetContentPacket(
            menu.containerId,
            menu.incrementStateId(),
            menu.getItems(),
            menu.getCarried()
        ));
    }
}
