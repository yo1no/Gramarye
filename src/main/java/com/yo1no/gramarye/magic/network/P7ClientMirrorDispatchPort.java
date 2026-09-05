package com.yo1no.gramarye.magic.network;

interface P7ClientMirrorDispatchPort {
    long captureDispatchGeneration();

    void onIntentAcknowledgement(
            long dispatchGeneration, IntentAcknowledgement acknowledgement);

    void onPlayerManaSnapshot(
            long dispatchGeneration, PlayerManaSnapshot snapshot);

    void onSkillCooldownSnapshot(
            long dispatchGeneration, SkillCooldownSnapshot snapshot);
}
