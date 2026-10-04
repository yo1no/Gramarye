package com.yo1no.gramarye;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Non-pausing projection of the connection-owned controller, not a request owner. */
final class P11ClientTransitionScreen extends Screen {
    private Button retry;

    P11ClientTransitionScreen() {
        super(Component.translatable("screen.gramarye.transition.title"));
    }

    @Override
    protected void init() {
        retry = addRenderableWidget(Button.builder(
                Component.translatable("screen.gramarye.transition.retry"),
                button -> P11ClientTransitions.retry())
                .bounds(width / 2 - 100, height / 2 + 22, 200, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("screen.gramarye.transition.leave"), button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 48, 200, 20).build());
        retry.active = P11ClientTransitions.retryArmed();
    }

    @Override
    public void tick() {
        P11ClientTransitions.observeNeutral();
        retry.active = P11ClientTransitions.retryArmed();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (CommonInputs.selected(key) && !P11ClientTransitions.freshActivation(key)) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        P11ClientTransitions.observeNeutral();
        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        P11ClientTransitions.observeNeutral();
        return super.mouseReleased(x, y, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new P11ClientLeaveScreen());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 62, 0xFFFFFF);
        var state = P11ClientTransitions.view();
        String outcome = state == null ? "unknown"
                : state.outcome().name().toLowerCase(Locale.ROOT);
        graphics.drawCenteredString(font, Component.translatable(
                "screen.gramarye.transition." + outcome), width / 2, height / 2 - 32, 0xFFFFFF);
        if (state != null && state.reason() != P11TransitionProtocol.Reason.NONE) {
            graphics.drawCenteredString(font, Component.translatable(
                    "screen.gramarye.transition.reason."
                            + state.reason().name().toLowerCase(Locale.ROOT)),
                    width / 2, height / 2 - 12, 0xDDDDDD);
        }
    }
}
