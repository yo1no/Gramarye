package com.yo1no.gramarye.magic.network;

interface P7ClientMirrorDispatchPort {
    void onIntentAcknowledgement(IntentAcknowledgement acknowledgement);

    void onPlayerManaSnapshot(PlayerManaSnapshot snapshot);

    void onSkillCooldownSnapshot(SkillCooldownSnapshot snapshot);
}
