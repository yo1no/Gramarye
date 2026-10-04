package com.yo1no.gramarye;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Deliberately not DeathScreen.TitleConfirmScreen: Stay never means respawn. */
final class P11ClientLeaveScreen extends Screen {
    P11ClientLeaveScreen() {
        super(Component.translatable("screen.gramarye.transition.leave_confirm"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("screen.gramarye.transition.stay"),
                button -> onClose()).bounds(width / 2 - 102, height / 2 + 20, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.gramarye.transition.leave"),
                button -> leave()).bounds(width / 2 + 2, height / 2 + 20, 100, 20).build());
    }

    private void leave() {
        P11ClientTransitions.leaveTransport();
        if (minecraft.level != null) { minecraft.level.disconnect(); }
        minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        minecraft.setScreen(new TitleScreen());
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (CommonInputs.selected(key) && !P11ClientTransitions.freshActivation(key)) { return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() { minecraft.setScreen(new P11ClientTransitionScreen()); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 20, 0xFFFFFF);
    }
}
