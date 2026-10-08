package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P11CooldownClientHarness;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Excluded read-only bridge to package-private actual payload/mirror values. */
public final class P11CooldownInputObservation {
    private P11CooldownInputObservation() {}
    public static void submitted(Object object) {
        if (object instanceof CastIntentPayload payload) {
            var intent = payload.intent();
            P11CooldownClientHarness.submitted(intent.sequence(), intent.slot(), intent.presenceMask(),
                    intent.aimHint().isEmpty() && intent.entityHint().isEmpty());
        }
    }
    public static void mirror(Object object, long generation, Object value) {
        if (!(object instanceof P7ClientMirror mirror) || !(value instanceof SkillCooldownSnapshot snapshot)
                || generation <= 0 || generation != mirror.currentDispatchGeneration()
                || mirror.cooldownSnapshot().orElse(null) != snapshot
                || mirror.lastAppliedCooldownSequence() != snapshot.syncSequence()) return;
        var entry = snapshot.entries().stream().filter(item -> item.slot() == 0).findFirst().orElse(null);
        P11CooldownClientHarness.snapshot(generation, snapshot.syncSequence(), snapshot.sourceEpoch(), snapshot.sourceVersion(),
                snapshot.sourceState().name(), snapshot.sourceReason().name(), entry == null ? null : entry.reference().toString(),
                entry == null ? "ABSENT" : entry.state().name(), entry == null ? "NONE" : entry.reason().name(),
                entry == null ? 0 : entry.remainingTicks());
    }

    /** Called after the sole original HUD drawString returns, never instead of drawing. */
    public static void drawn(Component originalText) {
        if (!com.yo1no.gramarye.P11CooldownServerHarness.selected()) return;
        try {
            var minecraft = Minecraft.getInstance();
            var mirror = P7ClientLifecycleEvents.mirror();
            var observed = mirror.cooldownSnapshot();
            boolean exact = minecraft.isSameThread() && minecraft.player != null && minecraft.level != null
                    && !minecraft.options.hideGui && originalText.equals(P7CooldownHud.label(observed));
            if (observed.isEmpty()) return;
            var snapshot = observed.orElseThrow();
            var entry = snapshot.entries().stream().filter(item -> item.slot() == 0).findFirst().orElse(null);
            if (entry == null) return;
            P11CooldownClientHarness.hudDrawn(exact, mirror.currentDispatchGeneration(), snapshot.syncSequence(),
                    entry.reference().toString(), entry.state().name(), entry.remainingTicks());
        } catch (RuntimeException | LinkageError problem) {
            P11CooldownClientHarness.hudObservationFailed();
        }
    }

    public static void disconnected(Object object) {
        if (!(object instanceof P7ClientMirror mirror)) return;
        P11CooldownClientHarness.mirrorDisconnected(mirror.currentDispatchGeneration(),
                mirror.cooldownSnapshot().isEmpty() && mirror.lastAppliedCooldownSequence() == 0);
    }
}
