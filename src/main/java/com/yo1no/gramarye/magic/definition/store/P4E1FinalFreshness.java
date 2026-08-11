package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Ordered, metadata-only final-currentness Gate for one B2-A audited candidate. */
final class P4E1FinalFreshness {
    private P4E1FinalFreshness() {
    }

    static VerificationResult verify(Input input) {
        Objects.requireNonNull(input, "input");
        if (!input.serviceCurrent()) {
            return lost(FailureCode.SERVER_FRESHNESS_LOST);
        }
        if (!input.serverCurrent()) {
            return lost(FailureCode.SERVER_FRESHNESS_LOST);
        }
        if (!input.callChainCurrent()) {
            return lost(FailureCode.CALL_CHAIN_FRESHNESS_LOST);
        }
        if (!input.playerListCurrent()) {
            return lost(FailureCode.SERVER_FRESHNESS_LOST);
        }
        if (!input.reservationCurrent()) {
            return lost(FailureCode.INDEX_RESERVATION_LOST);
        }
        if (!input.storeCurrent()) {
            return lost(FailureCode.STORE_SOURCE_FRESHNESS_LOST);
        }
        var journal = Objects.requireNonNull(
                input.journalCurrentness(), "journalCurrentness");
        switch (journal) {
            case LIFECYCLE_UNAVAILABLE -> {
                return lost(FailureCode.JOURNAL_FRESHNESS_LOST);
            }
            case TARGET_INVALID -> {
                return lost(FailureCode.JOURNAL_TARGET_PROOF_LOST);
            }
            case CURRENT -> { }
        }
        if (!input.inventoryCurrent()) {
            return lost(FailureCode.INVENTORY_PROVIDER_FRESHNESS_LOST);
        }
        var directory = Objects.requireNonNull(
                input.directoryCurrentness(), "directoryCurrentness");
        switch (directory) {
            case P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.DirectoryRace ignored -> {
                return lost(FailureCode.DIRECTORY_RACE_DETECTED);
            }
            case P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.SelectedFileLost
                    ignored -> {
                return lost(FailureCode.SELECTED_FILE_FRESHNESS_LOST);
            }
            case P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.Unchanged ignored -> { }
        }
        if (!input.onlineCurrent()) {
            return lost(FailureCode.ONLINE_SOURCE_FRESHNESS_LOST);
        }
        if (!input.integratedAndArbitrationCurrent()) {
            return lost(FailureCode.INTEGRATED_OWNER_FRESHNESS_LOST);
        }
        if (!input.reservationStillCurrent()) {
            return lost(FailureCode.INDEX_RESERVATION_LOST);
        }
        return new VerificationResult.Verified(new FreshnessSeal());
    }

    private static VerificationResult.Lost lost(FailureCode code) {
        return new VerificationResult.Lost(code);
    }

    /** Exact ordered checkpoint adapter; the service implementation remains package-private. */
    interface Input {
        boolean serviceCurrent();

        boolean serverCurrent();

        boolean callChainCurrent();

        boolean playerListCurrent();

        boolean reservationCurrent();

        boolean storeCurrent();

        P4E1PendingJournalObservation.Currentness journalCurrentness();

        boolean inventoryCurrent();

        P4E1PlayerDataDirectorySnapshot.FinalVerificationResult directoryCurrentness();

        boolean onlineCurrent();

        boolean integratedAndArbitrationCurrent();

        boolean reservationStillCurrent();
    }

    enum FailureCode {
        SERVER_FRESHNESS_LOST,
        CALL_CHAIN_FRESHNESS_LOST,
        INDEX_RESERVATION_LOST,
        STORE_SOURCE_FRESHNESS_LOST,
        JOURNAL_FRESHNESS_LOST,
        JOURNAL_TARGET_PROOF_LOST,
        INVENTORY_PROVIDER_FRESHNESS_LOST,
        DIRECTORY_RACE_DETECTED,
        SELECTED_FILE_FRESHNESS_LOST,
        ONLINE_SOURCE_FRESHNESS_LOST,
        INTEGRATED_OWNER_FRESHNESS_LOST
    }

    sealed interface VerificationResult {
        record Verified(FreshnessSeal seal) implements VerificationResult {
            public Verified {
                Objects.requireNonNull(seal, "seal");
            }
        }

        record Lost(FailureCode code) implements VerificationResult {
            public Lost {
                Objects.requireNonNull(code, "code");
            }
        }
    }

    /** Nonforgeable identity seal retained only by the current Complete permit cell. */
    static final class FreshnessSeal {
        private FreshnessSeal() {
        }
    }
}
