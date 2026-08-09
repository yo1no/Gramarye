package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/** Single-use B1 journal roots plus retained exact lifecycle/proof identity witness. */
final class P4E1PendingJournalObservation {
    private P4E1PendingJournalObservation() {
    }

    enum FailureCode {
        JOURNAL_NOT_READY,
        JOURNAL_UNAVAILABLE,
        JOURNAL_TARGET_INVALID
    }

    sealed interface Result {
        record Available(Ready observation) implements Result {
            public Available {
                Objects.requireNonNull(observation, "observation");
            }
        }

        record Incomplete(FailureCode code) implements Result {
            public Incomplete {
                Objects.requireNonNull(code, "code");
            }
        }
    }

    @FunctionalInterface
    interface TargetSink {
        void target(SkillReference reference);
    }

    static final class Ready {
        private final SkillDefinitionStoreSubmissionPort owner;
        private final MinecraftServer serverIdentity;
        private final P4E1GlobalSourceCapture.StoreReadyWitness storeWitness;
        private final GramaryeSkillSavedData adapterIdentity;
        private final SkillSavedDataState.Ready savedDataReadyIdentity;
        private final PendingAttachmentJournalState.Ready journalReadyIdentity;
        private final OpaquePendingAttachmentUpdatesBlob sourcePendingIdentity;
        private final OpaquePendingAttachmentUpdatesBlob innerPendingIdentity;
        private final JournalTargetAuditProof proofIdentity;
        private PendingAttachmentJournal rootBacking;
        private Stage stage = Stage.NEW;

        Ready(
                SkillDefinitionStoreSubmissionPort owner,
                MinecraftServer serverIdentity,
                P4E1GlobalSourceCapture.StoreReadyWitness storeWitness,
                GramaryeSkillSavedData adapterIdentity,
                SkillSavedDataState.Ready savedDataReadyIdentity,
                PendingAttachmentJournalState.Ready journalReadyIdentity,
                OpaquePendingAttachmentUpdatesBlob sourcePendingIdentity,
                OpaquePendingAttachmentUpdatesBlob innerPendingIdentity,
                JournalTargetAuditProof proofIdentity,
                PendingAttachmentJournal rootBacking) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.storeWitness = Objects.requireNonNull(storeWitness, "storeWitness");
            this.adapterIdentity = Objects.requireNonNull(adapterIdentity, "adapterIdentity");
            this.savedDataReadyIdentity = Objects.requireNonNull(
                    savedDataReadyIdentity, "savedDataReadyIdentity");
            this.journalReadyIdentity = Objects.requireNonNull(
                    journalReadyIdentity, "journalReadyIdentity");
            this.sourcePendingIdentity = Objects.requireNonNull(
                    sourcePendingIdentity, "sourcePendingIdentity");
            this.innerPendingIdentity = Objects.requireNonNull(
                    innerPendingIdentity, "innerPendingIdentity");
            this.proofIdentity = Objects.requireNonNull(proofIdentity, "proofIdentity");
            this.rootBacking = Objects.requireNonNull(rootBacking, "rootBacking");
        }

        int rootCount(
                SkillDefinitionStoreSubmissionPort candidate,
                MinecraftServer server,
                P4E1GlobalSourceCapture.StoreReadyWitness witness) {
            requireNew(candidate, server, witness);
            return rootBacking.entryCount();
        }

        void drain(
                SkillDefinitionStoreSubmissionPort candidate,
                MinecraftServer server,
                P4E1GlobalSourceCapture.StoreReadyWitness witness,
                TargetSink sink) {
            requireNew(candidate, server, witness);
            Objects.requireNonNull(sink, "sink");
            var journal = rootBacking;
            rootBacking = null;
            stage = Stage.WITNESS_ONLY;
            for (var entry : journal.entries()) {
                sink.target(entry.targetPointer());
            }
        }

        void discardRoots(
                SkillDefinitionStoreSubmissionPort candidate,
                MinecraftServer server,
                P4E1GlobalSourceCapture.StoreReadyWitness witness) {
            requireNew(candidate, server, witness);
            rootBacking = null;
            stage = Stage.WITNESS_ONLY;
        }

        void discardWitness() {
            if (stage != Stage.WITNESS_ONLY) {
                throw new IllegalStateException("P4E1_JOURNAL_WITNESS_LIFECYCLE_MISMATCH");
            }
            stage = Stage.DISCARDED;
        }

        void discardForFailure(
                SkillDefinitionStoreSubmissionPort candidate,
                MinecraftServer server,
                P4E1GlobalSourceCapture.StoreReadyWitness witness) {
            requireWitness(candidate, server, witness);
            rootBacking = null;
            stage = Stage.DISCARDED;
        }

        boolean matchesCurrentIdentities(
                SkillDefinitionStoreSubmissionPort candidate,
                MinecraftServer server,
                P4E1GlobalSourceCapture.StoreReadyWitness witness,
                GramaryeSkillSavedData adapter) {
            requireWitness(candidate, server, witness);
            if (adapter != adapterIdentity
                    || adapter.state() != savedDataReadyIdentity
                    || savedDataReadyIdentity.innerCarrier().pending() != innerPendingIdentity
                    || sourcePendingIdentity != innerPendingIdentity
                    || journalReadyIdentity.sourcePending() != sourcePendingIdentity
                    || journalReadyIdentity.targetAuditProof() != proofIdentity) {
                return false;
            }
            if (!(savedDataReadyIdentity.journalLifecycle()
                    instanceof PendingAttachmentJournalLifecycle.Installed installed)) {
                return false;
            }
            return installed.state() == journalReadyIdentity;
        }

        JournalTargetAuditProof proofIdentity() {
            return proofIdentity;
        }

        private void requireNew(
                SkillDefinitionStoreSubmissionPort candidate,
                MinecraftServer server,
                P4E1GlobalSourceCapture.StoreReadyWitness witness) {
            requireWitness(candidate, server, witness);
            if (stage != Stage.NEW || rootBacking == null) {
                throw new IllegalStateException("P4E1_JOURNAL_ROOT_HANDLE_LIFECYCLE_MISMATCH");
            }
        }

        private void requireWitness(
                SkillDefinitionStoreSubmissionPort candidate,
                MinecraftServer server,
                P4E1GlobalSourceCapture.StoreReadyWitness witness) {
            if (owner != Objects.requireNonNull(candidate, "candidate")) {
                throw new IllegalStateException("P4E1_JOURNAL_ROOT_HANDLE_OWNER_MISMATCH");
            }
            if (serverIdentity != Objects.requireNonNull(server, "server")) {
                throw new IllegalStateException("P4E1_JOURNAL_ROOT_HANDLE_SERVER_MISMATCH");
            }
            if (storeWitness != Objects.requireNonNull(witness, "witness")) {
                throw new IllegalStateException("P4E1_JOURNAL_ROOT_HANDLE_STORE_MISMATCH");
            }
            if (stage == Stage.DISCARDED) {
                throw new IllegalStateException("P4E1_JOURNAL_WITNESS_DISCARDED");
            }
        }
    }

    private enum Stage {
        NEW,
        WITNESS_ONLY,
        DISCARDED
    }
}
