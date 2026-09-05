package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7ClientMirrorDispatchFactory {
    private static final P7ClientMirrorDispatchPort DISCONNECTED =
            new DisconnectedPort();
    private static final ForwardingPort PRODUCTION = new ForwardingPort();

    private P7ClientMirrorDispatchFactory() {
        throw new AssertionError("no instances");
    }

    static P7ClientMirrorDispatchPort production() {
        return PRODUCTION;
    }

    static void installClient(P7ClientMirrorDispatchPort clientPort) {
        PRODUCTION.install(clientPort);
    }

    private static final class ForwardingPort implements P7ClientMirrorDispatchPort {
        private P7ClientMirrorDispatchPort delegate = DISCONNECTED;

        private synchronized void install(P7ClientMirrorDispatchPort clientPort) {
            Objects.requireNonNull(clientPort, "clientPort");
            if (delegate == clientPort) {
                return;
            }
            if (delegate != DISCONNECTED) {
                throw new P7SemanticInvariantException(
                        "client mirror dispatch port is already installed");
            }
            delegate = clientPort;
        }

        @Override
        public synchronized long captureDispatchGeneration() {
            return delegate.captureDispatchGeneration();
        }

        @Override
        public synchronized void onIntentAcknowledgement(
                long dispatchGeneration, IntentAcknowledgement acknowledgement) {
            delegate.onIntentAcknowledgement(dispatchGeneration, acknowledgement);
        }

        @Override
        public synchronized void onPlayerManaSnapshot(
                long dispatchGeneration, PlayerManaSnapshot snapshot) {
            delegate.onPlayerManaSnapshot(dispatchGeneration, snapshot);
        }

        @Override
        public synchronized void onSkillCooldownSnapshot(
                long dispatchGeneration, SkillCooldownSnapshot snapshot) {
            delegate.onSkillCooldownSnapshot(dispatchGeneration, snapshot);
        }
    }

    private static final class DisconnectedPort implements P7ClientMirrorDispatchPort {
        @Override
        public long captureDispatchGeneration() {
            return 0L;
        }

        @Override
        public void onIntentAcknowledgement(
                long dispatchGeneration, IntentAcknowledgement acknowledgement) {}

        @Override
        public void onPlayerManaSnapshot(
                long dispatchGeneration, PlayerManaSnapshot snapshot) {}

        @Override
        public void onSkillCooldownSnapshot(
                long dispatchGeneration, SkillCooldownSnapshot snapshot) {}
    }
}
