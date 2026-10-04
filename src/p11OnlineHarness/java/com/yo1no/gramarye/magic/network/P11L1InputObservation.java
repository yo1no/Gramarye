package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P11L1ClientHarness;

/** Read the actual original payload; never construct an intent/session/sequence. */
public final class P11L1InputObservation {
    private P11L1InputObservation() {}
    public static void submitted(Object object) {
        if (object instanceof CastIntentPayload payload) {
            var intent = payload.intent();
com.yo1no.gramarye.P11L1HostStopClientProbe.submitted(intent.sequence(), intent.slot(), intent.presenceMask(),
        intent.aimHint().isEmpty() && intent.entityHint().isEmpty());
P11L1ClientHarness.submitted(intent.sequence(), intent.slot(), intent.presenceMask(),
                    intent.aimHint().isEmpty() && intent.entityHint().isEmpty());
        }
    }
    public static void mirror(Object object, long generation, Object snapshot) {
        if (!(object instanceof P7ClientMirror mirror) || generation <= 0 || generation != mirror.captureDispatchGeneration()) { return; }
        if (snapshot instanceof PlayerManaSnapshot mana && mirror.lastAppliedManaSequence() == mana.syncSequence()) {
com.yo1no.gramarye.P11L1HostStopClientProbe.metadata(true, mana.syncSequence());
P11L1ClientHarness.metadata(true, mana.syncSequence());
        } else if (snapshot instanceof SkillCooldownSnapshot cooldown && mirror.lastAppliedCooldownSequence() == cooldown.syncSequence()) {
com.yo1no.gramarye.P11L1HostStopClientProbe.metadata(false, cooldown.syncSequence());
P11L1ClientHarness.metadata(false, cooldown.syncSequence());
        }
    }
}
