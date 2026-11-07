package org.mods.gd656conquest.client.loadout.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.mods.gd656conquest.client.compat.tacz.TaczGunPropertyPanelCompat;

/** A local, read-only view of a server loadout snapshot. All positions use GUI pixels. */
public final class LoadoutItemPreview {
    private ItemStack item = ItemStack.EMPTY;
    private int left;
    private int top;
    private int width;
    private int height;
    private float yaw = -30.0f;
    private float pitch = 12.0f;
    private float zoom = 1.0f;
    private boolean dragging;

    /** A changed snapshot refreshes the preview. The caller retains ownership of its stack. */
    public void setItem(ItemStack serverSnapshot) {
        ItemStack next = serverSnapshot == null ? ItemStack.EMPTY : serverSnapshot;
        if (ItemStack.matches(this.item, next)) return;
        this.item = next.copy();
        this.resetView();
    }

    public void setBounds(int left, int top, int width, int height) {
        int nextWidth = Math.max(0, width);
        int nextHeight = Math.max(0, height);
        if (this.left != left || this.top != top || this.width != nextWidth || this.height != nextHeight) {
            this.clearInteraction();
        }
        this.left = left;
        this.top = top;
        this.width = nextWidth;
        this.height = nextHeight;
    }

    public boolean contains(double x, double y) {
        return x >= this.left && x < this.left + this.width
            && y >= this.top && y < this.top + this.height;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.width <= 0 || this.height <= 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        graphics.flush();
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(graphics,this.left, this.top, this.left + this.width, this.top + this.height);
        try {
            if (this.item.isEmpty()) {
                graphics.drawCenteredString(minecraft.font, "—", this.left + this.width / 2,
                    this.top + this.height / 2 - 4, 0xff969c9f);
            } else if (TaczPreviewAccess.hasModel(this.item)) {
                this.renderModel(graphics, minecraft);
            } else {
                this.renderItemIcon(graphics);
            }
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }
    }

    private void renderModel(GuiGraphics graphics, Minecraft minecraft) {
        PoseStack pose = graphics.pose();
        float pixelsPerUnit = Math.min(this.width / 3.4f, this.height / 2.6f) * this.zoom;
        Lighting.setupFor3DItems();
        pose.pushPose();
        try {
            pose.translate(this.left + this.width / 2.0f, this.top + this.height / 2.0f, 150.0f);
            pose.scale(pixelsPerUnit, -pixelsPerUnit, pixelsPerUnit);
            pose.mulPose(Axis.XP.rotationDegrees(this.pitch));
            pose.mulPose(Axis.YP.rotationDegrees(this.yaw));
            TaczPreviewAccess.beforeGuiRender();
            minecraft.getItemRenderer().renderStatic(this.item, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                graphics.bufferSource(), minecraft.level, 0);
            graphics.flush();
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }

    private void renderItemIcon(GuiGraphics graphics) {
        float size = Math.min(this.width, this.height) * 0.65f * this.zoom;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(this.left + (this.width - size) / 2.0f,
                this.top + (this.height - size) / 2.0f, 0.0f);
            pose.scale(size / 16.0f, size / 16.0f, 1.0f);
            graphics.renderItem(this.item, 0, 0);
        } finally {
            pose.popPose();
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (!this.contains(x, y) || this.item.isEmpty()) return false;
        if (button == 2) {
            this.resetView();
            return true;
        }
        if (button != 0 || !TaczPreviewAccess.hasModel(this.item)) return false;
        this.dragging = true;
        return true;
    }

    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (!this.dragging || button != 0) return false;
        if (!this.contains(x, y)) {
            this.clearInteraction();
            return false;
        }
        this.yaw = (this.yaw + (float)deltaX * 0.7f) % 360.0f;
        this.pitch = Math.max(-75.0f, Math.min(75.0f, this.pitch + (float)deltaY * 0.5f));
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !this.dragging) return false;
        this.clearInteraction();
        return true;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (!this.contains(x, y) || this.item.isEmpty() || delta == 0.0) return false;
        this.zoom = Math.max(0.55f, Math.min(1.8f, this.zoom * (float)Math.pow(1.12, delta)));
        return true;
    }

    public void resetView() {
        this.yaw = -30.0f;
        this.pitch = 12.0f;
        this.zoom = 1.0f;
        this.clearInteraction();
    }

    /** Call when a dialog or a different tab takes over input. */
    public void clearInteraction() {
        this.dragging = false;
    }

    public boolean canRenderProperties() {
        return !this.item.isEmpty() && TaczGunPropertyPanelCompat.getInstance().canRender(this.item);
    }

    public int getPropertiesWidth() {
        return TaczGunPropertyPanelCompat.getInstance().getPanelWidth();
    }

    public int getPropertiesHeight() {
        return TaczGunPropertyPanelCompat.getInstance().getPanelHeight(this.item);
    }

    public void renderProperties(GuiGraphics graphics, Font font, int x, int y, int height) {
        if (height <= 0 || !this.canRenderProperties()) return;
        org.mods.gd656killicon.client.gui.theme.BattlefieldGuiClip.enable(graphics,x, y, x + this.getPropertiesWidth(), y + height);
        try {
            TaczGunPropertyPanelCompat.getInstance().render(graphics, font, x, y, height, this.item.copy());
        } finally {
            graphics.disableScissor();
        }
    }
}
