package org.mods.gd656conquest.client.support;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mods.gd656conquest.common.support.SupportHeroKitTypeRegistry;
import org.mods.gd656conquest.client.gui.elements.ChoiceListDialog;
import org.mods.gd656conquest.client.gui.elements.entries.FixedChoiceConfigEntry;
import org.mods.gd656conquest.network.NetworkManager;
import org.mods.gd656conquest.network.packet.SupportUiActionPacket;
import org.mods.gd656conquest.network.packet.SupportUiRequestPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SupportScreen extends Screen {
    private final Screen parent;
    private ChoiceListDialog choiceDialog;

    public SupportScreen(Screen parent) {
        super(Component.translatable("gd656conquest.client.gui.support.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.choiceDialog = new ChoiceListDialog(minecraft);
        refreshFromState();
        requestSnapshot();
    }

    public void refreshFromState() {
        if (choiceDialog == null) {
            return;
        }
        ClientSupportUiState.Snapshot snapshot = ClientSupportUiState.snapshot();
        if (snapshot == null) {
            choiceDialog.show(
                "",
                Component.translatable("gd656conquest.client.gui.support.title").getString() + " | 正在加载...",
                List.of(),
                ignored -> {
                },
                this::onClose
            );
            return;
        }
        List<FixedChoiceConfigEntry.Choice> choices = new ArrayList<>();
        for (ClientSupportUiState.HeroKitEntryState heroKit : snapshot.heroKits()) {
            String displayName = heroKit.displayName().isBlank() ? heroKit.id() : heroKit.displayName();
            String prefix = heroKit.unlocked() ? "§f" : "§7";
            String typeText = SupportHeroKitTypeRegistry.displayName(heroKit.type());
            String lockText = heroKit.unlocked() ? "" : " §c未解锁";
            String label = String.format(
                Locale.ROOT,
                "%s%s §8[%s] §7| %d分 | %s%s",
                prefix,
                displayName,
                heroKit.id(),
                heroKit.cost(),
                typeText,
                lockText
            );
            choices.add(new FixedChoiceConfigEntry.Choice(heroKit.id(), label));
        }
        String title = Component.translatable("gd656conquest.client.gui.support.title").getString()
            + " | 剩余分数: " + snapshot.remainingScore();
        if (!snapshot.statusText().isBlank()) {
            title += " | " + snapshot.statusText();
        }
        choiceDialog.show(
            "",
            title,
            choices,
            ignored -> {
            },
            this::onClose,
            null,
            null,
            "",
            0,
            0,
            choice -> isUnlocked(snapshot, choice),
            this::handleChoiceClick,
            choice -> isUnlocked(snapshot, choice),
            choice -> {
                ClientSupportUiState.HeroKitEntryState heroKit = findHeroKit(snapshot, choice);
                return heroKit == null ? null : ChoiceListDialog.RowIcon.texture(SupportHeroKitTypeRegistry.texture(heroKit.type()));
            },
            null
        );
    }

    private void requestSnapshot() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        NetworkManager.getChannel().sendToServer(new SupportUiRequestPacket());
    }

    private boolean isUnlocked(ClientSupportUiState.Snapshot snapshot, FixedChoiceConfigEntry.Choice choice) {
        if (snapshot == null || choice == null) {
            return false;
        }
        for (ClientSupportUiState.HeroKitEntryState heroKit : snapshot.heroKits()) {
            if (heroKit.id().equals(choice.value())) {
                return heroKit.unlocked();
            }
        }
        return false;
    }

    private void handleChoiceClick(FixedChoiceConfigEntry.Choice choice) {
        if (choice == null || minecraft == null || minecraft.player == null) {
            return;
        }
        NetworkManager.getChannel().sendToServer(SupportUiActionPacket.deployHeroKit(choice.value()));
    }

    private ClientSupportUiState.HeroKitEntryState findHeroKit(
        ClientSupportUiState.Snapshot snapshot,
        FixedChoiceConfigEntry.Choice choice
    ) {
        if (snapshot == null || choice == null) {
            return null;
        }
        for (ClientSupportUiState.HeroKitEntryState heroKit : snapshot.heroKits()) {
            if (heroKit.id().equals(choice.value())) {
                return heroKit;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (choiceDialog != null) {
            choiceDialog.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return choiceDialog != null && choiceDialog.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return choiceDialog != null && choiceDialog.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return choiceDialog != null && choiceDialog.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return choiceDialog != null && choiceDialog.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return choiceDialog != null && choiceDialog.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
